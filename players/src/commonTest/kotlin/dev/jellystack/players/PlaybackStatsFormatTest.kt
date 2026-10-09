package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackStatsFormatTest {
    @Test
    fun fractionalRatesKeepTheirDecimalsAndWholeRatesDropThem() {
        assertEquals("23.976 fps", formatFrameRate(23.976025f))
        assertEquals("29.97 fps", formatFrameRate(29.97003f))
        assertEquals("59.94 fps", formatFrameRate(59.94006f))
        assertEquals("25 fps", formatFrameRate(25f))
        assertEquals("24 fps", formatFrameRate(23.9999f))
    }

    @Test
    fun unknownRatesHaveNoLabel() {
        assertNull(formatFrameRate(0f))
        assertNull(formatFrameRate(-1f))
        assertNull(formatFrameRate(Float.NaN))
    }
}
