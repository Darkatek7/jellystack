package dev.jellystack.design.tv

import dev.jellystack.players.PlaybackDelay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvPlaybackDelaysTest {
    @Test
    fun audioAndSubtitleDelaysAreIndependentAndResetTogether() {
        val delays = TvPlaybackDelays()

        delays.stepAudio(3)
        delays.stepSubtitle(-2)

        assertEquals(150, delays.audioMs)
        assertEquals(-100, delays.subtitleMs)

        delays.resetAll()

        assertTrue(delays.isDefault)
    }

    @Test
    fun resettingOneDelayKeepsTheOther() {
        val delays = TvPlaybackDelays()
        delays.stepAudio(2)
        delays.stepSubtitle(2)

        delays.resetAudio()

        assertEquals(0, delays.audioMs)
        assertEquals(100, delays.subtitleMs)
    }

    @Test
    fun holdingTheKeyStopsAtTheLimit() {
        val delays = TvPlaybackDelays()

        repeat(PlaybackDelay.MAX_MS) { delays.stepAudio(1) }

        assertEquals(PlaybackDelay.MAX_MS, delays.audioMs)
    }

    @Test
    fun delaysShowTheirSignExceptWhenZero() {
        assertEquals("+150 ms", formatDelayMs(150))
        assertEquals("-50 ms", formatDelayMs(-50))
        assertEquals("0 ms", formatDelayMs(0))
    }
}
