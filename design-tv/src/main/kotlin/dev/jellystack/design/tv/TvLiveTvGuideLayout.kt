package dev.jellystack.design.tv

import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes

/*
 * The pure part of the Live TV guide grid: which time the grid shows, which programs it draws as cells, and where D-pad
 * focus goes. No Compose here, so every rule is a plain unit test.
 *
 * Times are minutes. The grid has an origin (the half hour the guide was opened in) and a window of
 * [GUIDE_VISIBLE_MINUTES] that moves along the origin in steps of [GUIDE_STEP_MINUTES], up to [GUIDE_MAX_MINUTES].
 */

/** The grid moves, and its time marks sit, every half hour. */
internal const val GUIDE_STEP_MINUTES = 30

/** What one screen of the grid shows. */
internal const val GUIDE_VISIBLE_MINUTES = 180

/** The guide reaches this far from its origin; programs later than that are not loaded. */
internal const val GUIDE_MAX_MINUTES = 720

/** Programs load this far beyond the window, so the next step to the right is already there. */
internal const val GUIDE_LOOKAHEAD_MINUTES = 180

/** A clipped sliver narrower than this is dropped: it cannot show text and is not worth a focus stop. */
internal const val GUIDE_MIN_CELL_MINUTES = 5f

/** A program that is cut by the window edge counts as visible when this much of it (or all of it) shows. */
internal const val GUIDE_REVEAL_MINUTES = 20f

/** The key of the one cell of a row without programs; it plays the channel like a running program does. */
internal const val GUIDE_EMPTY_KEY = "guide-empty"

private const val SECONDS_PER_MINUTE = 60f
private const val SECONDS_PER_STEP = GUIDE_STEP_MINUTES * 60L

/**
 * The half hour [now] is in, as the guide's origin. [utcOffsetSeconds] is the zone's offset, so the marks fall on the
 * clock's half hours and not on UTC's.
 */
internal fun guideOrigin(
    now: Instant,
    utcOffsetSeconds: Int,
): Instant {
    val local = now.epochSeconds + utcOffsetSeconds
    return Instant.fromEpochSeconds(local.floorDiv(SECONDS_PER_STEP) * SECONDS_PER_STEP - utcOffsetSeconds)
}

/** The grid's time range: [visibleMinutes] from [offsetMinutes] after [origin]. */
internal data class GuideWindow(
    val origin: Instant,
    val offsetMinutes: Int = 0,
    val visibleMinutes: Int = GUIDE_VISIBLE_MINUTES,
) {
    val maxOffsetMinutes: Int get() = (GUIDE_MAX_MINUTES - visibleMinutes).coerceAtLeast(0)
    val start: Instant get() = origin + offsetMinutes.minutes
    val end: Instant get() = start + visibleMinutes.minutes

    /** Programs starting at or after this are out of reach. */
    val horizon: Instant get() = origin + GUIDE_MAX_MINUTES.minutes

    /** How far programs must be loaded for this window: its end plus a lookahead, never past the horizon. */
    val loadUntil: Instant
        get() = origin + minOf(GUIDE_MAX_MINUTES, offsetMinutes + visibleMinutes + GUIDE_LOOKAHEAD_MINUTES).minutes

    /** Minutes from the window's start to [at]; negative before the window. */
    fun minutesFromStart(at: Instant): Float = (at - start).inWholeSeconds / SECONDS_PER_MINUTE

    /** Minutes from the origin to [at]. */
    fun minutesFromOrigin(at: Instant): Float = (at - origin).inWholeSeconds / SECONDS_PER_MINUTE

    fun withOffset(minutes: Int): GuideWindow = copy(offsetMinutes = minutes.coerceIn(0, maxOffsetMinutes))

    fun shiftedBySteps(steps: Int): GuideWindow = withOffset(offsetMinutes + steps * GUIDE_STEP_MINUTES)

    /**
     * This window, moved by the least needed (to a half hour) so that [program] shows: either enough of it to read, or
     * all of it when it is short. A program that shows already leaves the window as it is.
     */
    fun revealing(program: LiveTvProgram): GuideWindow {
        val startMinute = minutesFromOrigin(program.start)
        val endMinute = minutesFromOrigin(program.end)
        val needed = minOf(endMinute - startMinute, GUIDE_REVEAL_MINUTES)
        val first = offsetMinutes.toFloat()
        val shown = minOf(endMinute, first + visibleMinutes) - maxOf(startMinute, first)
        if (shown >= needed) return this
        return withOffset(floor(startMinute / GUIDE_STEP_MINUTES).toInt() * GUIDE_STEP_MINUTES)
    }
}

/**
 * One cell of a row: a [program] (null for the placeholder of a row without any) between [startMinute] and
 * [endMinute], counted from the window's start and already clipped to the window. [cutsStart] and [cutsEnd] tell that
 * the program goes on beyond that edge.
 */
internal data class GuideCell(
    val program: LiveTvProgram?,
    val startMinute: Float,
    val endMinute: Float,
    val cutsStart: Boolean = false,
    val cutsEnd: Boolean = false,
) {
    val key: String get() = program?.id ?: GUIDE_EMPTY_KEY
    val minutes: Float get() = endMinute - startMinute
}

/** The width of [minutes] when one minute is [minuteWidth] wide. */
internal fun guideCellWidth(
    minutes: Float,
    minuteWidth: Float,
): Float = minutes * minuteWidth

/**
 * The cells of one row for [window], from [programs] sorted by start: programs outside the window are left out, those
 * at its edges are clipped, and an overlap is resolved in favour of the program that started first. A row without any
 * cell gets the one placeholder cell over the whole window, so that focus has somewhere to land.
 */
internal fun guideRowCells(
    programs: List<LiveTvProgram>,
    window: GuideWindow,
): List<GuideCell> {
    val visible = window.visibleMinutes.toFloat()
    val cells = ArrayList<GuideCell>()
    var cursor = 0f
    for (program in programs.takeWhile { window.minutesFromStart(it.start) < visible }) {
        val cell = clippedCell(program, window, cursor) ?: continue
        cells += cell
        cursor = cell.endMinute
    }
    return cells.ifEmpty { listOf(GuideCell(null, 0f, visible)) }
}

/** The part of [program] that shows after [cursor], the end of the cell before it; null when too little does. */
private fun clippedCell(
    program: LiveTvProgram,
    window: GuideWindow,
    cursor: Float,
): GuideCell? {
    val start = window.minutesFromStart(program.start)
    val end = window.minutesFromStart(program.end)
    val from = maxOf(start, cursor)
    val to = minOf(end, window.visibleMinutes.toFloat())
    val cut = from > start || to < end
    val tooSmall = to - from <= 0f || (cut && to - from < GUIDE_MIN_CELL_MINUTES)
    return if (tooSmall) null else GuideCell(program, from, to, cutsStart = from > start, cutsEnd = to < end)
}

/** The index of the cell at [minute] (from the window's start), else the nearest one; null without cells. */
internal fun guideFocusTarget(
    cells: List<GuideCell>,
    minute: Float,
): Int? {
    val inside = cells.indexOfFirst { minute >= it.startMinute && minute < it.endMinute }
    if (inside >= 0) return inside
    return cells.indices.minByOrNull { index ->
        val cell = cells[index]
        if (minute < cell.startMinute) cell.startMinute - minute else minute - cell.endMinute
    }
}

internal enum class GuideDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Where the focus is: the [row] and the cell's [key], and the time ([anchorMinutes] after the origin) it holds when
 * it moves up or down, so those moves land on what airs at the same time.
 */
internal data class GuideFocus(
    val row: Int,
    val key: String,
    val anchorMinutes: Float,
)

/** What a D-pad press does in the grid. */
internal sealed interface GuideMove {
    /** The press is used up; nothing changes (an edge of the grid). */
    data object Stay : GuideMove

    /** Past the top row: focus goes to the header. */
    data object LeaveUp : GuideMove

    /** Past the first program at the origin: the navigation rail opens. */
    data object LeaveLeft : GuideMove

    /** Focus moves to [focus]; the window changes when it has to. */
    data class To(
        val focus: GuideFocus,
        val window: GuideWindow,
    ) : GuideMove
}

/**
 * The focus the grid gives [row] for the time [minutesFromOrigin] in [window]: the cell that airs then, or the nearest.
 */
internal fun guideFocusAt(
    row: Int,
    minutesFromOrigin: Float,
    window: GuideWindow,
    programs: List<LiveTvProgram>,
): GuideFocus {
    val cells = guideRowCells(programs, window)
    val index = guideFocusTarget(cells, minutesFromOrigin - window.offsetMinutes) ?: 0
    return GuideFocus(row, cells[index].key, minutesFromOrigin)
}

/**
 * The effect of a D-pad press on [focus]. Up and down keep the time and never move the window: they land on the cell
 * of the next row that airs at the same time. Left and right go to the neighbouring program and move the window by as
 * little as shows it; with no neighbour right moves the window one step on (up to the 12 hours), and left leaves for
 * the rail at the origin.
 */
internal fun guideMove(
    direction: GuideDirection,
    focus: GuideFocus,
    window: GuideWindow,
    rowCount: Int,
    programsOf: (row: Int) -> List<LiveTvProgram>,
): GuideMove =
    when (direction) {
        GuideDirection.UP,
        GuideDirection.DOWN,
        -> {
            val row = focus.row + if (direction == GuideDirection.UP) -1 else 1
            when {
                row < 0 -> GuideMove.LeaveUp
                row >= rowCount -> GuideMove.Stay
                else -> GuideMove.To(guideFocusAt(row, focus.anchorMinutes, window, programsOf(row)), window)
            }
        }
        GuideDirection.LEFT,
        GuideDirection.RIGHT,
        -> moveAlongRow(direction == GuideDirection.RIGHT, focus, window, programsOf(focus.row))
    }

private fun moveAlongRow(
    forward: Boolean,
    focus: GuideFocus,
    window: GuideWindow,
    programs: List<LiveTvProgram>,
): GuideMove {
    val current = programs.firstOrNull { it.id == focus.key }
    val neighbour =
        when {
            current == null -> null
            forward -> programs.firstOrNull { it.start >= current.end && it.start < window.horizon }
            else -> programs.lastOrNull { it.end <= current.start }
        }
    return when {
        neighbour != null -> moveToProgram(neighbour, focus.row, window, programs)
        !forward && window.offsetMinutes == 0 -> GuideMove.LeaveLeft
        else -> stepWindow(if (forward) 1 else -1, focus, window, programs)
    }
}

private fun moveToProgram(
    program: LiveTvProgram,
    row: Int,
    window: GuideWindow,
    programs: List<LiveTvProgram>,
): GuideMove {
    val moved = window.revealing(program)
    val anchor = maxOf(moved.minutesFromOrigin(program.start), moved.offsetMinutes.toFloat())
    // A neighbour the cells drop (a sliver, an overlap) falls back to the cell at its time.
    val target = guideFocusAt(row, anchor, moved, programs)
    return GuideMove.To(target.copy(anchorMinutes = anchor), moved)
}

/** With no program to go to the window moves one step; the focus keeps its time, kept inside the new window. */
private fun stepWindow(
    steps: Int,
    focus: GuideFocus,
    window: GuideWindow,
    programs: List<LiveTvProgram>,
): GuideMove {
    val moved = window.shiftedBySteps(steps)
    if (moved == window) return GuideMove.Stay
    val first = moved.offsetMinutes.toFloat()
    val anchor = focus.anchorMinutes.coerceIn(first, first + moved.visibleMinutes - 1)
    return GuideMove.To(guideFocusAt(focus.row, anchor, moved, programs), moved)
}
