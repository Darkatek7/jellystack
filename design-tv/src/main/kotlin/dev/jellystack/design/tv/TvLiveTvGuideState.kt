package dev.jellystack.design.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/** Where the guide grid was left; the coordinator keeps it so Back from the player returns to the same cell. */
internal class TvLiveTvGuideMemory {
    var origin: Instant? = null
    var offsetMinutes: Int = 0
    var focus: GuideFocus? = null
}

/** The cell of a row, which is the key of the program in it. */
internal data class GuideCellId(
    val row: Int,
    val key: String,
)

/**
 * Hands focus to cells by identity. A D-pad move can target a cell that does not exist yet (a row scrolled in, a
 * program the window just revealed); the request waits until the cell has composed and registered.
 */
internal class GuideFocusRegistry(
    private val scope: CoroutineScope,
) {
    private val requesters = HashMap<GuideCellId, FocusRequester>()
    private var pending: GuideCellId? = null

    fun register(
        id: GuideCellId,
        requester: FocusRequester,
    ) {
        requesters[id] = requester
        if (pending == id) {
            pending = null
            request(requester)
        }
    }

    fun unregister(
        id: GuideCellId,
        requester: FocusRequester,
    ) {
        if (requesters[id] === requester) requesters.remove(id)
    }

    fun focus(id: GuideCellId) {
        val requester = requesters[id]
        if (requester == null) {
            pending = id
        } else {
            pending = null
            request(requester)
        }
    }

    /** Focus landed somewhere else for another reason; an old request must not take it away later. */
    fun clearPending() {
        pending = null
    }

    private fun request(requester: FocusRequester) {
        scope.launch {
            // The cell has composed, but may not be placed yet; focus can only go to a placed node.
            withFrameNanos { }
            try {
                requester.requestFocus()
            } catch (_: IllegalStateException) {
                // The cell left the composition again before it could take focus.
            }
        }
    }
}

/** The program (or the empty row) that has focus, for the pane under the grid. */
internal class GuideFocusInfo(
    val row: Int,
    val program: LiveTvProgram?,
)

/** What the grid does besides moving focus: scroll a row in, and leave to the header or the rail. */
internal class GuideExits(
    val scrollToRow: (Int) -> Unit,
    val toHeader: () -> Unit,
    val toRail: () -> Boolean,
)

/**
 * The state of the guide grid. The window, the focused cell and the opened details are Compose state; they are read by
 * the rows (the window, in composition: a step recomposes the few rows on screen), the pane (the focus) and the layout
 * of the now line. Which cell has focus and its time anchor are plain fields: nothing draws from them.
 */
@Stable
internal class TvLiveTvGuideState(
    origin: Instant,
    private val memory: TvLiveTvGuideMemory,
    internal val registry: GuideFocusRegistry,
) {
    var window by mutableStateOf(GuideWindow(origin, memory.offsetMinutes.takeIf { memory.origin == origin } ?: 0))
        private set
    var focusInfo by mutableStateOf<GuideFocusInfo?>(null)
        private set
    var detailsOpen by mutableStateOf(false)
        private set

    private var focus: GuideFocus? = null
    private var expected: GuideCellId? = null
    private var rowCount = 0
    private var programsOf: (Int) -> List<LiveTvProgram> = { emptyList() }
    var exits: GuideExits = GuideExits({}, {}, { false })

    /** Refreshed by the grid on every composition, so key presses see the programs on screen now. */
    fun update(
        rowCount: Int,
        programsOf: (Int) -> List<LiveTvProgram>,
    ) {
        this.rowCount = rowCount
        this.programsOf = programsOf
    }

    fun toggleDetails() {
        detailsOpen = !detailsOpen
    }

    /** A cell took focus: by a move of the grid (then its time is kept) or from outside (then it sets the time). */
    fun onCellFocused(
        row: Int,
        cell: GuideCell,
    ) {
        val id = GuideCellId(row, cell.key)
        val moved = expected == id
        expected = null
        if (!moved) registry.clearPending()
        val current = focus
        val first = window.offsetMinutes.toFloat()
        val anchor =
            when {
                moved && current != null -> current.anchorMinutes
                cell.program != null -> maxOf(window.minutesFromOrigin(cell.program.start), first)
                else -> first
            }
        val next = GuideFocus(row, cell.key, anchor)
        focus = next
        focusInfo = GuideFocusInfo(row, cell.program)
        detailsOpen = false
        memory.origin = window.origin
        memory.offsetMinutes = window.offsetMinutes
        memory.focus = next
    }

    /** A D-pad press while a cell has focus; true when the grid used it. */
    fun onDirection(direction: GuideDirection): Boolean {
        val current = focus ?: return false
        return when (val move = guideMove(direction, current, window, rowCount, programsOf)) {
            GuideMove.Stay -> true
            GuideMove.LeaveUp -> {
                exits.toHeader()
                true
            }
            GuideMove.LeaveLeft -> exits.toRail()
            is GuideMove.To -> {
                moveTo(move.focus, move.window)
                true
            }
        }
    }

    /** Focus to the cell that airs now in [row] (or [remembered]: the cell the grid was left on). */
    fun enter(
        row: Int,
        nowMinutes: Float,
    ) {
        val remembered = memory.focus?.takeIf { memory.origin == window.origin && it.row < rowCount }
        val target =
            remembered
                ?: guideFocusAt(row, maxOf(nowMinutes, window.offsetMinutes.toFloat()), window, programsOf(row))
        moveTo(target, window)
    }

    private fun moveTo(
        target: GuideFocus,
        newWindow: GuideWindow,
    ) {
        val id = GuideCellId(target.row, target.key)
        focus = target
        expected = id
        if (newWindow != window) window = newWindow
        exits.scrollToRow(target.row)
        registry.focus(id)
    }
}

/** Scrolls [row] in when it is not among the rows composed; a row on screen is brought in by the focus itself. */
internal fun scrollRowIntoComposition(
    listState: LazyListState,
    scope: CoroutineScope,
    row: Int,
) {
    val visible = listState.layoutInfo.visibleItemsInfo
    val first = visible.firstOrNull()?.index ?: return
    val last = visible.last().index
    when {
        row < first -> scope.launch { listState.scrollToItem(row) }
        row > last -> scope.launch { listState.scrollToItem(row - (last - first)) }
    }
}
