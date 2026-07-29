package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTimeFormatterTest {
    @Test
    fun formatsMinutesAndHoursAndClampsNegativeValues() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("1:01:01", formatPlaybackTime(3_661_000L))
        assertEquals("0:00", formatPlaybackTime(-1L))
    }

    @Test
    fun unknownDurationUsesPlaceholder() {
        assertEquals("--:--", formatPlaybackDuration(null))
        assertEquals("2:00", formatPlaybackDuration(120_000L))
    }
}
