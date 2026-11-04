@file:Suppress("MaxLineLength")

package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TvEndsAtTest {
    private fun format(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    // 2026-11-04 20:00:00 UTC
    private val eightPm = 1_793_822_400_000L

    @Test
    fun endsAtAddsTheRemainingMinutesToNow() {
        assertEquals("22:46", tvEndsAtClock(eightPm, 166, format("HH:mm")))
    }

    @Test
    fun endsAtFollowsTheTwelveHourFormat() {
        assertEquals("10:46 PM", tvEndsAtClock(eightPm, 166, format("h:mm a")))
    }

    @Test
    fun endsAtCrossesMidnightAndIgnoresSecondsOfNow() {
        assertEquals("00:15", tvEndsAtClock(eightPm + 59_000L, 255, format("HH:mm")))
    }

    @Test
    fun remainingMinutesSubtractsTheResumePositionAndNeverGoesNegative() {
        val runtime = 100 * TV_RUNTIME_TICKS_PER_MINUTE
        assertEquals(100L, tvRemainingMinutes(runtime, null))
        assertEquals(62L, tvRemainingMinutes(runtime, 38 * TV_RUNTIME_TICKS_PER_MINUTE))
        assertEquals(0L, tvRemainingMinutes(runtime, 140 * TV_RUNTIME_TICKS_PER_MINUTE))
    }

    @Test
    fun remainingMinutesIsUnknownWithoutARuntime() {
        assertNull(tvRemainingMinutes(null, 5L))
        assertNull(tvRemainingMinutes(0L, null))
    }
}
