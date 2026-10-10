package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvProfileGateTest {
    @Test
    fun severalProfilesPickAgainAfterTheGracePeriod() {
        assertTrue(tvProfileGateOnReturn(profileCount = 2, backgroundedAtMillis = 0L, nowMillis = GRACE))
    }

    @Test
    fun shortBreakKeepsTheActiveProfile() {
        assertFalse(tvProfileGateOnReturn(profileCount = 2, backgroundedAtMillis = 0L, nowMillis = GRACE - 1))
    }

    @Test
    fun singleProfileNeverAsks() {
        assertFalse(tvProfileGateOnReturn(profileCount = 1, backgroundedAtMillis = 0L, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun startWithoutAPrecedingStopNeverAsks() {
        assertFalse(tvProfileGateOnReturn(profileCount = 3, backgroundedAtMillis = null, nowMillis = Long.MAX_VALUE))
    }

    private companion object {
        const val GRACE = TV_PROFILE_GATE_GRACE_MILLIS
    }
}
