package dev.jellystack.core.profile

import dev.jellystack.core.security.FakeSecureStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfilePinRepositoryTest {
    @Test
    fun acceptsOnlyExactlyFourNumericDigits() =
        runTest {
            val repository = repository()

            listOf("", "123", "12345", "12a4", "１２３４").forEach { invalid ->
                assertFailsWith<IllegalArgumentException> { repository.configure(PROFILE_A, invalid) }
            }

            repository.configure(PROFILE_A, "0427")
            assertEquals(ProfilePinState.Ready, repository.state(PROFILE_A))
        }

    @Test
    fun storesOnlyVersionedSaltedVerifierAndEqualPinsUseDifferentSalts() =
        runTest {
            val secureStore = FakeSecureStore()
            val salts = ArrayDeque(listOf("salt-one", "salt-two"))
            val repository = repository(secureStore = secureStore, saltGenerator = { salts.removeFirst() })

            repository.configure(PROFILE_A, "1234")
            repository.configure(PROFILE_B, "1234")

            val stored = secureStore.snapshot()
            assertTrue(stored.keys.all { it.startsWith("profile.pin.") })
            assertTrue(stored.values.none { it.contains("1234") })
            assertTrue(stored.values.filter { it.startsWith("v1:") }.all { it.split(':').size == 4 })
            assertNotEquals(stored["profile.pin.$PROFILE_A.verifier"], stored["profile.pin.$PROFILE_B.verifier"])
        }

    @Test
    fun verifierUsesSha256Derivation() =
        runTest {
            val secureStore = FakeSecureStore()
            val repository =
                repository(
                    secureStore = secureStore,
                    saltGenerator = { "salt" },
                    workFactor = 1,
                )

            repository.configure(PROFILE_A, "0427")

            assertEquals(
                "v1:1:salt:d0a3c53fdb9855549f6f7ceed143f30d8257ffcf5d57210bb2eadf32e01735ac",
                secureStore.snapshot()["profile.pin.$PROFILE_A.verifier"],
            )
        }

    @Test
    fun rejectsFourAttemptsThenLocksTheFifthForThirtySeconds() =
        runTest {
            val clock = MutablePinClock(1_000)
            val repository = repository(clock = clock)
            repository.configure(PROFILE_A, "2468")

            assertEquals(ProfilePinResult.Rejected(4), repository.verify(PROFILE_A, "0000"))
            assertEquals(ProfilePinResult.Rejected(3), repository.verify(PROFILE_A, "0000"))
            assertEquals(ProfilePinResult.Rejected(2), repository.verify(PROFILE_A, "0000"))
            assertEquals(ProfilePinResult.Rejected(1), repository.verify(PROFILE_A, "0000"))
            val fifth = assertIs<ProfilePinResult.Locked>(repository.verify(PROFILE_A, "0000"))
            assertEquals(31_000, fifth.until.toEpochMilliseconds())
            assertEquals(ProfilePinState.Locked(fifth.until), repository.state(PROFILE_A))

            clock.epochMillis = 30_999
            assertEquals(fifth, repository.verify(PROFILE_A, "2468"))
            clock.epochMillis = 31_000
            assertEquals(ProfilePinResult.Unlocked, repository.verify(PROFILE_A, "2468"))
            assertEquals(ProfilePinState.Ready, repository.state(PROFILE_A))
        }

    @Test
    fun successfulVerificationResetsFailures() =
        runTest {
            val repository = repository()
            repository.configure(PROFILE_A, "2468")
            assertEquals(ProfilePinResult.Rejected(4), repository.verify(PROFILE_A, "0000"))
            assertEquals(ProfilePinResult.Unlocked, repository.verify(PROFILE_A, "2468"))
            assertEquals(ProfilePinResult.Rejected(4), repository.verify(PROFILE_A, "0000"))
        }

    @Test
    fun processRecreationRetainsLockout() =
        runTest {
            val secureStore = FakeSecureStore()
            val clock = MutablePinClock(5_000)
            repository(secureStore, clock).also { first ->
                first.configure(PROFILE_A, "2468")
                repeat(5) { first.verify(PROFILE_A, "0000") }
            }

            val recreated = repository(secureStore, clock)
            val state = assertIs<ProfilePinState.Locked>(recreated.state(PROFILE_A))
            assertEquals(35_000, state.until.toEpochMilliseconds())
        }

    @Test
    fun recoveryClearsPinOnlyAfterExactProfileReauthenticationSucceeds() =
        runTest {
            val secureStore = FakeSecureStore()
            val repository = repository(secureStore)
            repository.configure(PROFILE_A, "2468")
            repeat(5) { repository.verify(PROFILE_A, "0000") }

            assertFalse(repository.recoverAfterReauthentication(PROFILE_A) { profileId -> profileId == PROFILE_B })
            assertIs<ProfilePinState.Locked>(repository.state(PROFILE_A))
            assertTrue(repository.recoverAfterReauthentication(PROFILE_A) { profileId -> profileId == PROFILE_A })
            assertEquals(ProfilePinState.NotConfigured, repository.state(PROFILE_A))
            assertTrue(secureStore.snapshot().keys.none { it.contains(PROFILE_A) })
        }

    @Test
    fun removeClearsVerifierAndAttemptMetadata() =
        runTest {
            val secureStore = FakeSecureStore()
            val repository = repository(secureStore)
            repository.configure(PROFILE_A, "2468")
            repository.verify(PROFILE_A, "0000")

            repository.remove(PROFILE_A)

            assertEquals(ProfilePinState.NotConfigured, repository.state(PROFILE_A))
            assertTrue(secureStore.snapshot().keys.none { it.contains(PROFILE_A) })
        }

    private fun repository(
        secureStore: FakeSecureStore = FakeSecureStore(),
        clock: Clock = MutablePinClock(1_000),
        saltGenerator: () -> String = { "deterministic-salt" },
        workFactor: Int = 8,
    ) = ProfilePinRepository(
        secureStore = secureStore,
        clock = clock,
        saltGenerator = saltGenerator,
        workFactor = workFactor,
    )

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}

private class MutablePinClock(
    var epochMillis: Long,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
}
