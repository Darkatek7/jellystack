package dev.jellystack.core.profile

import kotlinx.coroutines.flow.Flow

interface ProfileStore {
    fun observeProfiles(): Flow<List<HouseholdProfile>>

    suspend fun listProfiles(): List<HouseholdProfile>

    suspend fun getProfile(profileId: String): HouseholdProfile?

    suspend fun upsertProfile(profile: HouseholdProfile)

    suspend fun createProfileWithBinding(
        profile: HouseholdProfile,
        binding: ProfileConnectionBinding,
    )

    suspend fun deleteProfile(profileId: String)

    suspend fun getBinding(profileId: String): ProfileConnectionBinding?

    suspend fun upsertBinding(binding: ProfileConnectionBinding)

    fun observeSavedMedia(profileId: String): Flow<List<SavedMediaRecord>>

    suspend fun listSavedMedia(profileId: String): List<SavedMediaRecord>

    suspend fun upsertSavedMedia(record: SavedMediaRecord)

    suspend fun deleteSavedMedia(
        profileId: String,
        identity: MediaIdentity,
    )
}
