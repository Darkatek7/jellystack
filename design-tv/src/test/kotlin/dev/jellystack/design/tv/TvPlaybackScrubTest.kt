package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaybackScrubTest {
    @Test
    fun scrubAcceleratesWithRepeatCountUpToCap() {
        assertEquals(11_000L, tvScrubTarget(10_000L, 100_000L, stepMs = 1_000L, repeatCount = 0))
        assertEquals(13_000L, tvScrubTarget(10_000L, 100_000L, stepMs = 1_000L, repeatCount = 2))
        // repeatCount beyond the cap stops accelerating.
        assertEquals(70_000L, tvScrubTarget(10_000L, 100_000L, stepMs = 10_000L, repeatCount = 5))
        assertEquals(70_000L, tvScrubTarget(10_000L, 100_000L, stepMs = 10_000L, repeatCount = 40))
    }

    @Test
    fun scrubClampsToDurationAndZero() {
        assertEquals(100_000L, tvScrubTarget(99_000L, 100_000L, stepMs = 30_000L, repeatCount = 0))
        assertEquals(0L, tvScrubTarget(5_000L, 100_000L, stepMs = -30_000L, repeatCount = 3))
    }

    @Test
    fun scrubWithoutKnownDurationUsesUnboundedForward() {
        assertEquals(40_000L, tvScrubTarget(10_000L, null, stepMs = 30_000L, repeatCount = 0))
        assertEquals(0L, tvScrubTarget(10_000L, null, stepMs = -30_000L, repeatCount = 4))
    }

    @Test
    fun nonPositiveDurationsDoNotClamp() {
        assertEquals(50_000L, tvScrubTarget(20_000L, 0L, stepMs = 30_000L, repeatCount = 0))
        assertEquals(50_000L, tvScrubTarget(20_000L, -1L, stepMs = 30_000L, repeatCount = 0))
    }

    @Test
    fun controlsAutoHideOnlyWhilePlayingWithoutPanels() {
        assertTrue(shouldAutoHideTvControls(controlsVisible = true, panelOpen = false, isPaused = false))
        assertFalse(
            "hidden stays hidden",
            shouldAutoHideTvControls(controlsVisible = false, panelOpen = false, isPaused = false),
        )
        assertFalse(
            "panels block hiding",
            shouldAutoHideTvControls(controlsVisible = true, panelOpen = true, isPaused = false),
        )
        assertFalse(
            "paused playback keeps controls visible",
            shouldAutoHideTvControls(controlsVisible = true, panelOpen = false, isPaused = true),
        )
    }

    @Test
    fun configuredSeekSecondsFeedScrubTargets() {
        val backStep = -15 * 1_000L
        val forwardStep = 60 * 1_000L
        assertEquals(85_000L, tvScrubTarget(100_000L, 600_000L, backStep, repeatCount = 0))
        assertEquals(160_000L, tvScrubTarget(100_000L, 600_000L, forwardStep, repeatCount = 0))
    }
}
