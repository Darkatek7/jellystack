package dev.jellystack.core.profile

import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.randomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class HouseholdProfileRepository(
    private val store: ProfileStore,
    private val activeServerPreferences: ActiveServerPreferenceRepository,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { randomId(16) },
) {
    private val mutex = Mutex()

    fun observeProfiles(): Flow<List<HouseholdProfile>> = store.observeProfiles()

    suspend fun listProfiles(): List<HouseholdProfile> = store.listProfiles()

    suspend fun getProfile(profileId: String): HouseholdProfile? = store.getProfile(profileId)

    suspend fun binding(profileId: String): ProfileConnectionBinding? = store.getBinding(profileId)

    suspend fun ensureLegacyDefaultProfile(): HouseholdProfile? =
        mutex.withLock {
            store.listProfiles().firstOrNull()?.let { return@withLock it }
            val jellyfinConnectionId =
                activeServerPreferences
                    .activeServerId(ServerType.JELLYFIN)
                    ?.takeIf(String::isNotBlank)
                    ?: return@withLock null
            val now = clock.now()
            val profileId = idGenerator()
            val profile =
                HouseholdProfile(
                    id = profileId,
                    displayName = "Default",
                    avatarSeed = profileId,
                    createdAt = now,
                    updatedAt = now,
                    lastActiveAt = now,
                )
            val binding =
                ProfileConnectionBinding(
                    profileId = profileId,
                    jellyfinConnectionId = jellyfinConnectionId,
                    seerrConnectionId =
                        activeServerPreferences
                            .activeServerId(ServerType.JELLYSEERR)
                            ?.takeIf(String::isNotBlank),
                )
            store.createProfileWithBinding(profile, binding)
            profile
        }

    suspend fun createProfile(
        displayName: String,
        jellyfinConnectionId: String,
        seerrConnectionId: String? = null,
        avatarSeed: String? = null,
    ): HouseholdProfile =
        mutex.withLock {
            require(displayName.isNotBlank())
            require(jellyfinConnectionId.isNotBlank())
            val now = clock.now()
            val id = idGenerator()
            val profile =
                HouseholdProfile(
                    id = id,
                    displayName = displayName.trim(),
                    avatarSeed = avatarSeed?.trim()?.takeIf(String::isNotEmpty) ?: id,
                    createdAt = now,
                    updatedAt = now,
                )
            store.createProfileWithBinding(
                profile,
                ProfileConnectionBinding(
                    profileId = id,
                    jellyfinConnectionId = jellyfinConnectionId,
                    seerrConnectionId = seerrConnectionId?.takeIf(String::isNotBlank),
                ),
            )
            profile
        }

    suspend fun updateProfile(profile: HouseholdProfile) {
        require(profile.displayName.isNotBlank())
        store.upsertProfile(profile.copy(displayName = profile.displayName.trim(), updatedAt = clock.now()))
    }

    suspend fun bindConnections(binding: ProfileConnectionBinding) {
        requireNotNull(store.getProfile(binding.profileId))
        require(binding.jellyfinConnectionId.isNotBlank())
        store.upsertBinding(binding)
    }

    suspend fun removeProfile(profileId: String) {
        store.deleteProfile(profileId)
    }
}
