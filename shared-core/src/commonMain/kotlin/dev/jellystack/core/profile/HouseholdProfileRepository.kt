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
    private val legacyProfileMigration: (profileId: String) -> Unit = {},
    private val idGenerator: () -> String = { randomId(16) },
) {
    private val mutex = Mutex()

    fun observeProfiles(): Flow<List<HouseholdProfile>> = store.observeProfiles()

    suspend fun listProfiles(): List<HouseholdProfile> = store.listProfiles()

    suspend fun getProfile(profileId: String): HouseholdProfile? = store.getProfile(profileId)

    suspend fun binding(profileId: String): ProfileConnectionBinding? = store.getBinding(profileId)

    fun observeBindings(): Flow<List<ProfileConnectionBinding>> = store.observeBindings()

    /**
     * Points the profile at [seerrConnectionId], or at no Seerr when null. Returns the previous Seerr
     * connection when no profile uses it anymore, so the caller can delete that local record.
     */
    suspend fun bindSeerr(
        profileId: String,
        seerrConnectionId: String?,
    ): String? =
        mutex.withLock {
            val binding = requireNotNull(store.getBinding(profileId))
            val next = seerrConnectionId?.takeIf(String::isNotBlank)
            store.upsertBinding(binding.copy(seerrConnectionId = next))
            val previous = binding.seerrConnectionId?.takeIf { it != next } ?: return@withLock null
            val stillUsed =
                store.listProfiles().any { profile ->
                    store.getBinding(profile.id)?.seerrConnectionId == previous
                }
            previous.takeUnless { stillUsed }
        }

    suspend fun ensureLegacyDefaultProfile(): HouseholdProfile? =
        mutex.withLock {
            store.listProfiles().firstOrNull()?.let {
                legacyProfileMigration(it.id)
                return@withLock it
            }
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
            legacyProfileMigration(profileId)
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

    /** Repairs only the unambiguous one-profile/one-Seerr upgrade case. */
    suspend fun repairLegacySingleProfileSeerrBinding(seerrConnectionIds: List<String>): Boolean =
        mutex.withLock {
            val profile = store.listProfiles().singleOrNull() ?: return@withLock false
            val binding = store.getBinding(profile.id) ?: return@withLock false
            if (binding.seerrConnectionId != null) return@withLock false
            val seerrConnectionId = seerrConnectionIds.filter(String::isNotBlank).distinct().singleOrNull() ?: return@withLock false
            store.upsertBinding(binding.copy(seerrConnectionId = seerrConnectionId))
            true
        }

    /** Replaces only the untouched synthetic identity created by the legacy migration. */
    suspend fun repairLegacySingleProfileIdentity(
        jellyfinConnectionId: String,
        displayName: String,
        avatarSeed: String,
    ): Boolean =
        mutex.withLock {
            val profile = store.listProfiles().singleOrNull() ?: return@withLock false
            val binding = store.getBinding(profile.id) ?: return@withLock false
            if (binding.jellyfinConnectionId != jellyfinConnectionId) return@withLock false
            if (profile.displayName != "Default" || profile.avatarSeed != profile.id) return@withLock false
            if (displayName.isBlank() || avatarSeed.isBlank()) return@withLock false
            store.upsertProfile(
                profile.copy(
                    displayName = displayName.trim(),
                    avatarSeed = avatarSeed.trim(),
                    updatedAt = clock.now(),
                ),
            )
            true
        }

    suspend fun removeProfile(profileId: String) {
        store.deleteProfile(profileId)
    }
}
