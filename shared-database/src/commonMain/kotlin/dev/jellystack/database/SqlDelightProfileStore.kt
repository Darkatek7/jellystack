package dev.jellystack.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.MediaIdentity
import dev.jellystack.core.profile.MediaIdentityProvider
import dev.jellystack.core.profile.MediaProviderIds
import dev.jellystack.core.profile.ProfileConnectionBinding
import dev.jellystack.core.profile.ProfileStore
import dev.jellystack.core.profile.SavedMediaRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class SqlDelightProfileStore(
    private val queries: HouseholdProfilesQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProfileStore {
    override fun observeProfiles(): Flow<List<HouseholdProfile>> =
        queries
            .selectProfiles()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(Household_profiles::toDomain) }

    override suspend fun listProfiles(): List<HouseholdProfile> = queries.selectProfiles().executeAsList().map(Household_profiles::toDomain)

    override suspend fun getProfile(profileId: String): HouseholdProfile? =
        queries.selectProfileById(profileId).executeAsOneOrNull()?.toDomain()

    override suspend fun upsertProfile(profile: HouseholdProfile) {
        queries.upsertProfile(
            id = profile.id,
            display_name = profile.displayName,
            avatar_seed = profile.avatarSeed,
            created_at = profile.createdAt.toEpochMilliseconds(),
            updated_at = profile.updatedAt.toEpochMilliseconds(),
            last_active_at = profile.lastActiveAt?.toEpochMilliseconds(),
        )
    }

    override suspend fun createProfileWithBinding(
        profile: HouseholdProfile,
        binding: ProfileConnectionBinding,
    ) {
        require(profile.id == binding.profileId)
        queries.transaction {
            queries.upsertProfile(
                id = profile.id,
                display_name = profile.displayName,
                avatar_seed = profile.avatarSeed,
                created_at = profile.createdAt.toEpochMilliseconds(),
                updated_at = profile.updatedAt.toEpochMilliseconds(),
                last_active_at = profile.lastActiveAt?.toEpochMilliseconds(),
            )
            queries.upsertBinding(
                profile_id = binding.profileId,
                jellyfin_connection_id = binding.jellyfinConnectionId,
                seerr_connection_id = binding.seerrConnectionId,
            )
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        queries.transaction {
            queries.deleteSavedMediaByProfile(profileId)
            queries.deleteBindingByProfile(profileId)
            queries.deleteProfile(profileId)
        }
    }

    override suspend fun getBinding(profileId: String): ProfileConnectionBinding? =
        queries.selectBindingByProfile(profileId).executeAsOneOrNull()?.toDomain()

    override suspend fun upsertBinding(binding: ProfileConnectionBinding) {
        queries.upsertBinding(
            profile_id = binding.profileId,
            jellyfin_connection_id = binding.jellyfinConnectionId,
            seerr_connection_id = binding.seerrConnectionId,
        )
    }

    override fun observeSavedMedia(profileId: String): Flow<List<SavedMediaRecord>> =
        queries
            .selectSavedMediaByProfile(profileId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(Profile_saved_media::toDomain) }

    override suspend fun listSavedMedia(profileId: String): List<SavedMediaRecord> =
        queries.selectSavedMediaByProfile(profileId).executeAsList().map(Profile_saved_media::toDomain)

    override suspend fun upsertSavedMedia(record: SavedMediaRecord) {
        val ids = record.providerIds.normalized()
        val identity = record.identity
        queries.upsertSavedMedia(
            profile_id = record.profileId,
            media_type = identity.mediaType,
            provider = identity.provider.storageValue,
            provider_id = identity.providerId,
            tmdb_id = ids.tmdbId,
            tvdb_id = ids.tvdbId,
            source_local_id = ids.sourceLocalId,
            title = record.title,
            poster_path = record.posterPath,
            backdrop_path = record.backdropPath,
            created_at = record.createdAt.toEpochMilliseconds(),
            updated_at = record.updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteSavedMedia(
        profileId: String,
        identity: MediaIdentity,
    ) {
        queries.deleteSavedMedia(
            profile_id = profileId,
            media_type = identity.mediaType,
            provider = identity.provider.storageValue,
            provider_id = identity.providerId,
        )
    }
}

private fun Household_profiles.toDomain(): HouseholdProfile =
    HouseholdProfile(
        id = id,
        displayName = display_name,
        avatarSeed = avatar_seed,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        lastActiveAt = last_active_at?.let(Instant::fromEpochMilliseconds),
    )

private fun Profile_connection_bindings.toDomain(): ProfileConnectionBinding =
    ProfileConnectionBinding(
        profileId = profile_id,
        jellyfinConnectionId = jellyfin_connection_id,
        seerrConnectionId = seerr_connection_id,
    )

private fun Profile_saved_media.toDomain(): SavedMediaRecord =
    SavedMediaRecord(
        profileId = profile_id,
        mediaType = media_type,
        providerIds =
            MediaProviderIds(
                tmdbId = tmdb_id,
                tvdbId = tvdb_id,
                sourceLocalId = source_local_id,
            ),
        title = title,
        posterPath = poster_path,
        backdropPath = backdrop_path,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    ).also { record ->
        check(record.identity.provider == MediaIdentityProvider.fromStorageValue(provider))
        check(record.identity.providerId == provider_id)
    }
