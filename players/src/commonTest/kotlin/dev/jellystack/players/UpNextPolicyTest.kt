package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpNextPolicyTest {
    @Test
    fun creditsMarkedByTheServerOfferTheNextEpisodeRightAway() {
        assertTrue(shouldOfferUpNext(positionMs = 40 * MINUTE, durationMs = 49 * MINUTE, creditsActive = true))
        assertTrue(shouldOfferUpNext(positionMs = 0L, durationMs = null, creditsActive = true))
    }

    @Test
    fun withoutMarkersOnlyTheLastThirtySecondsOfLongerTitlesCount() {
        val duration = 45 * MINUTE

        assertFalse(shouldOfferUpNext(positionMs = duration - 31_000L, durationMs = duration, creditsActive = false))
        assertTrue(shouldOfferUpNext(positionMs = duration - 30_000L, durationMs = duration, creditsActive = false))
        assertTrue(shouldOfferUpNext(positionMs = duration - 1L, durationMs = duration, creditsActive = false))
        assertFalse(shouldOfferUpNext(positionMs = duration, durationMs = duration, creditsActive = false), "ended")
    }

    @Test
    fun shortTitlesAndUnknownDurationsGetNoFallback() {
        assertFalse(shouldOfferUpNext(positionMs = 9 * MINUTE + 50_000L, durationMs = 9 * MINUTE + 59_000L, creditsActive = false))
        assertFalse(shouldOfferUpNext(positionMs = 1_000L, durationMs = null, creditsActive = false))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
