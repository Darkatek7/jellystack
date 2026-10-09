package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTimelineScrubTest {
    private var now = 0L

    @Test
    fun pressesAccumulateFromThePreviewAndSeekOnlyOnRelease() {
        val scrub = TvTimelineScrub(liveSeeks = false, nowMs = { now })

        assertNull(scrub.press(basePositionMs = 100_000L, durationMs = 600_000L, stepMs = 10_000L, repeatCount = 0))
        // The player has not moved yet, so the next press builds on the preview, not on the stale position.
        assertNull(scrub.press(basePositionMs = 100_000L, durationMs = 600_000L, stepMs = 10_000L, repeatCount = 0))

        assertEquals(120_000L, scrub.previewPositionMs)
        assertEquals(120_000L, scrub.release())
        scrub.clear()
        assertNull(scrub.previewPositionMs)
    }

    @Test
    fun liveSeeksAreThrottledWhileHeld() {
        val scrub = TvTimelineScrub(liveSeeks = true, nowMs = { now })

        assertEquals(110_000L, scrub.press(100_000L, 600_000L, 10_000L, repeatCount = 0))
        now = 100L
        assertNull(scrub.press(100_000L, 600_000L, 10_000L, repeatCount = 1))
        now = TV_SCRUB_COMMIT_INTERVAL_MS + 1L
        assertEquals(130_000L, scrub.press(100_000L, 600_000L, 10_000L, repeatCount = 2))
        assertEquals(130_000L, scrub.release())
    }

    @Test
    fun previewStaysInsideTheTimeline() {
        val scrub = TvTimelineScrub(liveSeeks = false, nowMs = { now })

        scrub.press(basePositionMs = 5_000L, durationMs = 600_000L, stepMs = -30_000L, repeatCount = 0)
        assertEquals(0L, scrub.previewPositionMs)
        scrub.clear()
        scrub.press(basePositionMs = 595_000L, durationMs = 600_000L, stepMs = 30_000L, repeatCount = 0)
        assertEquals(600_000L, scrub.previewPositionMs)
    }

    @Test
    fun holdingADirectionSpeedsUpStepByStep() {
        assertEquals(listOf(1, 1, 2, 4, 8), listOf(0, 4, 5, 15, 30).map(::tvScrubAcceleration))
    }
}
