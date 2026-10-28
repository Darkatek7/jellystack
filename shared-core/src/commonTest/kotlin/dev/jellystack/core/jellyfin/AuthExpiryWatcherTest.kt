package dev.jellystack.core.jellyfin

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthExpiryWatcherTest {
    private fun TestScope.startWatcher(
        signal: AuthExpirySignal,
        currentToken: String? = "current",
        onVerify: () -> Unit,
    ) {
        val watcher =
            AuthExpiryWatcher(
                signal = signal,
                currentAccessToken = { currentToken },
                verifySession = { onVerify() },
                burstWindowMillis = 300,
                cooldownMillis = 10_000,
            )
        backgroundScope.launch { watcher.run() }
        runCurrent()
    }

    @Test
    fun parallelRejectionsVerifyTheSessionOnce() =
        runTest {
            val signal = AuthExpirySignal()
            var verifications = 0
            startWatcher(signal) { verifications += 1 }

            repeat(5) { signal.report("current") }
            advanceTimeBy(299)
            assertEquals(0, verifications)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, verifications)

            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, verifications)
        }

    @Test
    fun rejectionOfAnotherSessionsTokenIsIgnored() =
        runTest {
            val signal = AuthExpirySignal()
            var verifications = 0
            startWatcher(signal) { verifications += 1 }

            signal.report("stale-token-of-previous-profile")
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(0, verifications)
        }

    @Test
    fun reportWithoutTokenCountsForTheCurrentSession() =
        runTest {
            val signal = AuthExpirySignal()
            var verifications = 0
            startWatcher(signal) { verifications += 1 }

            signal.report()
            advanceTimeBy(300)
            runCurrent()

            assertEquals(1, verifications)
        }

    @Test
    fun reportDuringCooldownIsVerifiedWhenTheCooldownEnds() =
        runTest {
            val signal = AuthExpirySignal()
            var verifications = 0
            startWatcher(signal) { verifications += 1 }

            signal.report("current")
            advanceTimeBy(300)
            runCurrent()
            assertEquals(1, verifications)

            advanceTimeBy(1_000)
            signal.report("current")
            signal.report("current")
            advanceTimeBy(8_000)
            runCurrent()
            assertEquals(1, verifications)

            // Cooldown ends at 10.3 s, then one burst window.
            advanceTimeBy(1_300)
            runCurrent()
            assertEquals(2, verifications)
        }

    @Test
    fun failingVerificationDoesNotEndTheWatcher() =
        runTest {
            val signal = AuthExpirySignal()
            var verifications = 0
            startWatcher(signal) {
                verifications += 1
                if (verifications == 1) error("offline")
            }

            signal.report("current")
            advanceTimeBy(300)
            runCurrent()
            advanceTimeBy(10_000)
            signal.report("current")
            advanceTimeBy(300)
            runCurrent()

            assertEquals(2, verifications)
        }
}
