package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackDelayTest {
    @Test
    fun adjustingMovesInStepsInBothDirections() {
        assertEquals(100, PlaybackDelay.adjusted(50, 1))
        assertEquals(-50, PlaybackDelay.adjusted(0, -1))
    }

    @Test
    fun adjustingStopsAtTheLimits() {
        assertEquals(PlaybackDelay.MAX_MS, PlaybackDelay.adjusted(PlaybackDelay.MAX_MS, 1))
        assertEquals(-PlaybackDelay.MAX_MS, PlaybackDelay.adjusted(-PlaybackDelay.MAX_MS, -1))
    }
}
