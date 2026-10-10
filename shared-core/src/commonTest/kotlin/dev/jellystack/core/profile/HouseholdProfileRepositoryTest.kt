package dev.jellystack.core.profile

import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.testing.InMemorySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HouseholdProfileRepositoryTest {
    @Test
    fun legacyBootstrapUsesExactActiveConnectionsAndIsIdempotent() =
        runTest {
            val store = InMemoryProfileStore()
            val active = ActiveServerPreferenceRepository(InMemorySettings())
            active.setActiveServerId(ServerType.JELLYFIN, "jellyfin-active")
            active.setActiveServerId(ServerType.JELLYSEERR, "seerr-active")
            val migratedProfiles = mutableListOf<String>()
            val repository =
                HouseholdProfileRepository(
                    store = store,
                    activeServerPreferences = active,
                    clock = FixedProfileClock,
                    legacyProfileMigration = migratedProfiles::add,
                    idGenerator = { "legacy-default" },
                )

            val first = repository.ensureLegacyDefaultProfile()
            val second = repository.ensureLegacyDefaultProfile()

            assertEquals(first, second)
            assertEquals(listOf("legacy-default"), store.listProfiles().map { it.id })
            assertEquals(
                ProfileConnectionBinding("legacy-default", "jellyfin-active", "seerr-active"),
                store.getBinding("legacy-default"),
            )
            assertEquals(1, store.atomicCreations)
            assertEquals(listOf("legacy-default", "legacy-default"), migratedProfiles)
        }

    @Test
    fun legacyBootstrapDoesNotGuessWhenActiveJellyfinIdIsMissing() =
        runTest {
            val store = InMemoryProfileStore()
            val active = ActiveServerPreferenceRepository(InMemorySettings())
            active.setActiveServerId(ServerType.JELLYSEERR, "seerr-active")
            val repository = HouseholdProfileRepository(store, active, FixedProfileClock) { "unused" }

            assertNull(repository.ensureLegacyDefaultProfile())
            assertEquals(emptyList(), store.listProfiles())
        }

    @Test
    fun legacyRepairBindsSoleSeerrConnectionOnlyForOneUnboundProfile() =
        runTest {
            val store = InMemoryProfileStore()
            val repository = HouseholdProfileRepository(store, ActiveServerPreferenceRepository(InMemorySettings()))
            val profile = repository.createProfile("Default", "jellyfin")

            assertTrue(repository.repairLegacySingleProfileSeerrBinding(listOf("seerr")))
            assertEquals(
                ProfileConnectionBinding(profile.id, "jellyfin", "seerr"),
                store.getBinding(profile.id),
            )
            assertFalse(repository.repairLegacySingleProfileSeerrBinding(listOf("seerr")))

            val ambiguousStore = InMemoryProfileStore()
            val ambiguous = HouseholdProfileRepository(ambiguousStore, ActiveServerPreferenceRepository(InMemorySettings()))
            ambiguous.createProfile("Default", "jellyfin")
            assertFalse(ambiguous.repairLegacySingleProfileSeerrBinding(listOf("seerr-a", "seerr-b")))
        }

    @Test
    fun bindSeerrReturnsThePreviousConnectionOnlyWhenNoProfileUsesItAnymore() =
        runTest {
            val store = InMemoryProfileStore()
            var nextId = 0
            val repository =
                HouseholdProfileRepository(
                    store = store,
                    activeServerPreferences = ActiveServerPreferenceRepository(InMemorySettings()),
                    idGenerator = { "profile-${nextId++}" },
                )
            val first = repository.createProfile("First", "jellyfin-a", seerrConnectionId = "seerr-shared")
            val second = repository.createProfile("Second", "jellyfin-b", seerrConnectionId = "seerr-shared")

            assertNull(repository.bindSeerr(first.id, "seerr-first"))
            assertEquals("seerr-first", store.getBinding(first.id)?.seerrConnectionId)

            assertEquals("seerr-shared", repository.bindSeerr(second.id, null))
            assertNull(store.getBinding(second.id)?.seerrConnectionId)
            assertEquals("jellyfin-b", store.getBinding(second.id)?.jellyfinConnectionId)

            assertNull(repository.bindSeerr(first.id, "seerr-first"))
        }

    @Test
    fun legacyRepairReplacesOnlyUntouchedDefaultIdentity() =
        runTest {
            val store = InMemoryProfileStore()
            val active = ActiveServerPreferenceRepository(InMemorySettings())
            active.setActiveServerId(ServerType.JELLYFIN, "jellyfin")
            val repository =
                HouseholdProfileRepository(
                    store = store,
                    activeServerPreferences = active,
                    clock = FixedProfileClock,
                    idGenerator = { "profile" },
                )
            repository.ensureLegacyDefaultProfile()

            assertTrue(repository.repairLegacySingleProfileIdentity("jellyfin", "Alice", "user-id"))
            assertEquals("Alice", store.getProfile("profile")?.displayName)
            assertEquals("user-id", store.getProfile("profile")?.avatarSeed)
            assertFalse(repository.repairLegacySingleProfileIdentity("jellyfin", "Other", "other-id"))
        }
}

internal object FixedProfileClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
}

internal class InMemoryProfileStore : ProfileStore {
    private val profiles = linkedMapOf<String, HouseholdProfile>()
    private val bindings = linkedMapOf<String, ProfileConnectionBinding>()
    private val profileFlow = MutableStateFlow<List<HouseholdProfile>>(emptyList())
    private val bindingFlow = MutableStateFlow<List<ProfileConnectionBinding>>(emptyList())
    private val saves = mutableMapOf<String, MutableList<SavedMediaRecord>>()
    var atomicCreations = 0

    override fun observeProfiles(): Flow<List<HouseholdProfile>> = profileFlow

    override suspend fun listProfiles(): List<HouseholdProfile> = profiles.values.toList()

    override suspend fun getProfile(profileId: String): HouseholdProfile? = profiles[profileId]

    override suspend fun upsertProfile(profile: HouseholdProfile) {
        profiles[profile.id] = profile
        profileFlow.value = profiles.values.toList()
    }

    override suspend fun createProfileWithBinding(
        profile: HouseholdProfile,
        binding: ProfileConnectionBinding,
    ) {
        atomicCreations += 1
        profiles[profile.id] = profile
        bindings[profile.id] = binding
        profileFlow.value = profiles.values.toList()
        bindingFlow.value = bindings.values.toList()
    }

    override suspend fun deleteProfile(profileId: String) {
        profiles.remove(profileId)
        bindings.remove(profileId)
        saves.remove(profileId)
        profileFlow.value = profiles.values.toList()
        bindingFlow.value = bindings.values.toList()
    }

    override suspend fun getBinding(profileId: String): ProfileConnectionBinding? = bindings[profileId]

    override fun observeBindings(): Flow<List<ProfileConnectionBinding>> = bindingFlow

    override suspend fun upsertBinding(binding: ProfileConnectionBinding) {
        bindings[binding.profileId] = binding
        bindingFlow.value = bindings.values.toList()
    }

    override fun observeSavedMedia(profileId: String): Flow<List<SavedMediaRecord>> = MutableStateFlow(saves[profileId].orEmpty())

    override suspend fun listSavedMedia(profileId: String): List<SavedMediaRecord> = saves[profileId].orEmpty()

    override suspend fun upsertSavedMedia(record: SavedMediaRecord) {
        saves.getOrPut(record.profileId, ::mutableListOf).add(record)
    }

    override suspend fun deleteSavedMedia(
        profileId: String,
        identity: MediaIdentity,
    ) {
        saves[profileId]?.removeAll { it.identity == identity }
    }
}
