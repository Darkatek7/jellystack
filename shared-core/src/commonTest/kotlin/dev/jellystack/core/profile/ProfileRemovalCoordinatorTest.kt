package dev.jellystack.core.profile

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.testing.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileRemovalCoordinatorTest {
    @Test
    fun removesLocalProfileDataAndOnlyUnreferencedConnections() =
        runTest {
            val store = InMemoryProfileStore()
            val preferences = ProfilePreferencesRepository(InMemorySettings())
            val pins =
                ProfilePinRepository(
                    secureStore = FakeSecureStore(),
                    saltGenerator = { "salt" },
                    workFactor = 1,
                )
            val removedConnections = mutableListOf<String>()
            val coordinator = ProfileRemovalCoordinator(store, preferences, pins, removedConnections::add)
            store.createProfileWithBinding(profile("a"), ProfileConnectionBinding("a", "shared-jf", "seerr-a"))
            store.createProfileWithBinding(profile("b"), ProfileConnectionBinding("b", "shared-jf", null))
            preferences.setPreferredAudioLanguage("a", "de")
            pins.configure("a", "1234")

            coordinator.remove("a")

            assertNull(store.getProfile("a"))
            assertEquals(ProfilePreferences(), preferences.preferences("a").value)
            assertEquals(ProfilePinState.NotConfigured, pins.state("a"))
            assertEquals(listOf("seerr-a"), removedConnections)
            assertEquals("shared-jf", store.getBinding("b")?.jellyfinConnectionId)
        }

    private fun profile(id: String) =
        HouseholdProfile(
            id = id,
            displayName = id,
            avatarSeed = id,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
}
