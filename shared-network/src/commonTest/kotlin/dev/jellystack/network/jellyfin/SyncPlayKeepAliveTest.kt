package dev.jellystack.network.jellyfin

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncPlayKeepAliveTest {
    @Test
    fun pingsAtHalfTheAnnouncedTimeoutAndImmediatelyOnTheAnnouncement() =
        runTest {
            var pings = 0
            val keepAlive = SyncPlayKeepAlive(backgroundScope) { pings += 1 }

            keepAlive.onForceKeepAlive(60)
            runCurrent()
            assertEquals(1, pings)

            advanceTimeBy(29_999)
            assertEquals(1, pings)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, pings)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(3, pings)
            keepAlive.stop()
        }

    @Test
    fun repeatedAnnouncementWithTheSameTimeoutRepliesWithoutRestartingTheSchedule() =
        runTest {
            var pings = 0
            val keepAlive = SyncPlayKeepAlive(backgroundScope) { pings += 1 }
            keepAlive.onForceKeepAlive(60)
            runCurrent()

            advanceTimeBy(20_000)
            keepAlive.onForceKeepAlive(60)
            runCurrent()
            assertEquals(2, pings)

            // The original schedule still fires 30 s after the first announcement.
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(3, pings)
            keepAlive.stop()
        }

    @Test
    fun aNewTimeoutReplacesTheSchedule() =
        runTest {
            var pings = 0
            val keepAlive = SyncPlayKeepAlive(backgroundScope) { pings += 1 }
            keepAlive.onForceKeepAlive(60)
            runCurrent()

            keepAlive.onForceKeepAlive(20)
            runCurrent()
            assertEquals(2, pings)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(3, pings)
            keepAlive.stop()
        }

    @Test
    fun stoppingEndsThePings() =
        runTest {
            var pings = 0
            val keepAlive = SyncPlayKeepAlive(backgroundScope) { pings += 1 }
            keepAlive.onForceKeepAlive(60)
            runCurrent()
            keepAlive.stop()

            advanceTimeBy(120_000)
            runCurrent()

            assertEquals(1, pings)
        }

    @Test
    fun intervalIsHalfTheTimeoutWithinBoundsAndDefaultsWhenMissing() {
        assertEquals(30_000L, keepAliveIntervalMillis(60))
        assertEquals(30_000L, keepAliveIntervalMillis(null))
        assertEquals(30_000L, keepAliveIntervalMillis(0))
        assertEquals(5_000L, keepAliveIntervalMillis(2))
        assertEquals(60_000L, keepAliveIntervalMillis(3_600))
    }
}
