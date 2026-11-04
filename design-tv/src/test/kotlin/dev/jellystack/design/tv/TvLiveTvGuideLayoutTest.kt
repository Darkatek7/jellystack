package dev.jellystack.design.tv

import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TvLiveTvGuideLayoutTest {
    private val origin = Instant.parse("2026-11-01T18:00:00Z")
    private val window = GuideWindow(origin)

    private fun program(
        id: String,
        from: Int,
        to: Int,
    ) = LiveTvProgram(id, "c", id, null, null, origin + from.minutes, origin + to.minutes)

    private fun focus(
        row: Int,
        key: String,
        anchor: Float,
    ) = GuideFocus(row, key, anchor)

    @Test
    fun theOriginIsTheHalfHourOfTheClockNotOfUtc() {
        val now = Instant.parse("2026-11-01T18:20:00Z")

        assertEquals(Instant.parse("2026-11-01T18:00:00Z"), guideOrigin(now, utcOffsetSeconds = 3_600))
        assertEquals(Instant.parse("2026-11-01T18:30:00Z"), guideOrigin(Instant.parse("2026-11-01T18:45:00Z"), 0))
        // Nepal is +05:45: its half hours fall a quarter past those of UTC.
        assertEquals(Instant.parse("2026-11-01T18:15:00Z"), guideOrigin(now, utcOffsetSeconds = 20_700))
    }

    @Test
    fun programsLoadThreeHoursBeyondTheWindowButNeverPastTwelveHours() {
        assertEquals(origin + 360.minutes, window.loadUntil)
        assertEquals(origin + 720.minutes, window.withOffset(480).loadUntil)
        assertEquals(origin + 720.minutes, window.withOffset(540).loadUntil)
    }

    @Test
    fun theWindowStaysBetweenTheOriginAndTheTwelfthHour() {
        assertEquals(0, window.shiftedBySteps(-1).offsetMinutes)
        assertEquals(30, window.shiftedBySteps(1).offsetMinutes)
        assertEquals(540, window.withOffset(10_000).offsetMinutes)
        assertEquals(origin + 720.minutes, window.horizon)
    }

    @Test
    fun cellsAreSizedByDuration() {
        val cells = guideRowCells(listOf(program("a", 0, 30), program("b", 30, 120)), window)

        assertEquals(listOf(30f, 90f), cells.map { it.minutes })
        assertEquals(60f, guideCellWidth(cells[0].minutes, minuteWidth = 2f))
    }

    @Test
    fun programsAreClippedAtBothEdgesOfTheWindow() {
        val cells =
            guideRowCells(
                listOf(
                    program("before", -60, 20),
                    program("inside", 20, 100),
                    program("after", 100, 400),
                    program("later", 400, 460),
                ),
                window,
            )

        assertEquals(listOf("before", "inside", "after"), cells.map { it.key })
        assertEquals(0f to 20f, cells[0].startMinute to cells[0].endMinute)
        assertTrue(cells[0].cutsStart)
        assertFalse(cells[0].cutsEnd)
        assertEquals(100f to 180f, cells[2].startMinute to cells[2].endMinute)
        assertTrue(cells[2].cutsEnd)
    }

    @Test
    fun aSliverCutOffByTheEdgeIsDroppedButAShortProgramInsideIsKept() {
        val cells =
            guideRowCells(
                listOf(program("sliver", -30, 3), program("short", 10, 13), program("edge", 178, 240)),
                window,
            )

        assertEquals(listOf("short"), cells.map { it.key })
    }

    @Test
    fun overlappingProgramsAreResolvedInFavourOfTheOneThatStartedFirst() {
        val cells = guideRowCells(listOf(program("a", 0, 60), program("b", 40, 120), program("c", 50, 55)), window)

        assertEquals(listOf("a", "b"), cells.map { it.key })
        assertEquals(60f, cells[1].startMinute)
        assertTrue(cells[1].cutsStart)
    }

    @Test
    fun aRowWithoutProgramsInTheWindowHasOnePlaceholderOverTheWholeWindow() {
        val cells = guideRowCells(listOf(program("later", 400, 460)), window)

        assertEquals(1, cells.size)
        assertNull(cells[0].program)
        assertEquals(GUIDE_EMPTY_KEY, cells[0].key)
        assertEquals(180f, cells[0].minutes)
        assertEquals(1, guideRowCells(emptyList(), window).size)
    }

    @Test
    fun theCellsFollowTheWindowWhenItMoves() {
        val moved = window.withOffset(60)
        val cells = guideRowCells(listOf(program("a", 0, 90), program("b", 90, 150)), moved)

        assertEquals(listOf("a", "b"), cells.map { it.key })
        assertEquals(0f to 30f, cells[0].startMinute to cells[0].endMinute)
        assertEquals(30f to 90f, cells[1].startMinute to cells[1].endMinute)
    }

    @Test
    fun theFocusTargetIsTheCellAtTheTimeElseTheNearestOne() {
        val cells = guideRowCells(listOf(program("a", 0, 30), program("b", 30, 60), program("c", 100, 160)), window)

        assertEquals(1, guideFocusTarget(cells, 30f))
        assertEquals(0, guideFocusTarget(cells, 29.9f))
        // 70 is in the gap, closer to b (ends at 60) than to c (starts at 100).
        assertEquals(1, guideFocusTarget(cells, 70f))
        assertEquals(2, guideFocusTarget(cells, 90f))
        assertNull(guideFocusTarget(emptyList(), 10f))
    }

    @Test
    fun upAndDownLandOnTheProgramThatAirsAtTheSameTimeAndNeverMoveTheWindow() {
        val rows =
            listOf(
                listOf(program("a0", 0, 60), program("a1", 60, 120)),
                listOf(program("b0", 0, 30), program("b1", 30, 150)),
                listOf(program("c0", 0, 90), program("c1", 90, 120)),
            )

        val down = guideMove(GuideDirection.DOWN, focus(0, "a1", 75f), window, 3) { rows[it] } as GuideMove.To
        assertEquals(focus(1, "b1", 75f), down.focus)
        assertEquals(window, down.window)

        val downAgain = guideMove(GuideDirection.DOWN, down.focus, window, 3) { rows[it] } as GuideMove.To
        assertEquals("c0", downAgain.focus.key)
        // The time is kept, so going back up lands where it came from.
        val up = guideMove(GuideDirection.UP, downAgain.focus, window, 3) { rows[it] } as GuideMove.To
        assertEquals("b1", up.focus.key)
    }

    @Test
    fun upFromTheTopLeavesForTheHeaderAndDownFromTheBottomStays() {
        val rows = listOf(listOf(program("a", 0, 60)), listOf(program("b", 0, 60)))

        assertEquals(GuideMove.LeaveUp, guideMove(GuideDirection.UP, focus(0, "a", 0f), window, 2) { rows[it] })
        assertEquals(GuideMove.Stay, guideMove(GuideDirection.DOWN, focus(1, "b", 0f), window, 2) { rows[it] })
    }

    @Test
    fun aRowWithoutProgramsStillTakesTheFocus() {
        val rows = listOf(listOf(program("a", 0, 60)), emptyList())

        val move = guideMove(GuideDirection.DOWN, focus(0, "a", 10f), window, 2) { rows[it] } as GuideMove.To

        assertEquals(GUIDE_EMPTY_KEY, move.focus.key)
    }

    @Test
    fun rightGoesToTheNextProgramAndShowsItWhenItIsBeyondTheWindow() {
        val row = listOf(program("a", 100, 170), program("b", 170, 200), program("c", 200, 260))

        val inside = guideMove(GuideDirection.RIGHT, focus(0, "a", 100f), window, 1) { row } as GuideMove.To
        assertEquals("b", inside.focus.key)
        assertEquals(170f, inside.focus.anchorMinutes)
        // Only 10 of its 30 minutes would show in 0..180, too little to read: the window steps to 150.
        assertEquals(150, inside.window.offsetMinutes)

        // c starts at 200, outside the window 0..180: the window steps to its half hour.
        val beyond = guideMove(GuideDirection.RIGHT, focus(0, "b", 170f), window, 1) { row } as GuideMove.To
        assertEquals("c", beyond.focus.key)
        assertEquals(180, beyond.window.offsetMinutes)
        assertEquals(200f, beyond.focus.anchorMinutes)
    }

    @Test
    fun leftToAProgramThatEndedBeforeTheWindowMovesTheWindowBack() {
        val row = listOf(program("a", 100, 150), program("b", 150, 300))
        val moved = window.withOffset(150)

        val move = guideMove(GuideDirection.LEFT, focus(0, "b", 150f), moved, 1) { row } as GuideMove.To

        assertEquals("a", move.focus.key)
        assertEquals(90, move.window.offsetMinutes)
    }

    @Test
    fun leftFromTheFirstProgramAtTheOriginOpensTheRail() {
        val row = listOf(program("a", 0, 60), program("b", 60, 120))

        assertEquals(GuideMove.LeaveLeft, guideMove(GuideDirection.LEFT, focus(0, "a", 0f), window, 1) { row })
    }

    @Test
    fun rightWithoutAnotherProgramStepsTheWindowOnUntilTwelveHours() {
        val row = listOf(program("a", 0, 60))
        var current = focus(0, "a", 10f)
        var shown = window

        repeat(40) {
            val move = guideMove(GuideDirection.RIGHT, current, shown, 1) { row }
            if (move is GuideMove.To) {
                current = move.focus
                shown = move.window
            }
        }

        assertEquals(540, shown.offsetMinutes)
        assertEquals(GuideMove.Stay, guideMove(GuideDirection.RIGHT, current, shown, 1) { row })
        // The focus stays inside the window it landed in.
        assertTrue(current.anchorMinutes in 540f..720f)
    }

    @Test
    fun aStepOfTheWindowKeepsFocusOnACellThatIsShown() {
        val row = listOf(program("a", 0, 400))

        val move = guideMove(GuideDirection.RIGHT, focus(0, "a", 10f), window, 1) { row } as GuideMove.To

        assertEquals("a", move.focus.key)
        assertEquals(30, move.window.offsetMinutes)
        assertEquals(30f, move.focus.anchorMinutes)
    }
}
