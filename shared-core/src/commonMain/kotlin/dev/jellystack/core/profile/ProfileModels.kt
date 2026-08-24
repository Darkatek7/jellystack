package dev.jellystack.core.profile

import kotlinx.datetime.Instant

data class HouseholdProfile(
    val id: String,
    val displayName: String,
    val avatarSeed: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastActiveAt: Instant? = null,
)

data class ProfileConnectionBinding(
    val profileId: String,
    val jellyfinConnectionId: String,
    val seerrConnectionId: String? = null,
)

enum class MediaIdentityProvider(
    val storageValue: String,
) {
    TMDB("tmdb"),
    TVDB("tvdb"),
    SOURCE_LOCAL("source_local"),
    ;

    companion object {
        fun fromStorageValue(value: String): MediaIdentityProvider =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown media identity provider")
    }
}

data class MediaIdentity(
    val mediaType: String,
    val provider: MediaIdentityProvider,
    val providerId: String,
) {
    init {
        require(mediaType.isNotBlank())
        require(providerId.isNotBlank())
    }
}

data class MediaProviderIds(
    val tmdbId: String? = null,
    val tvdbId: String? = null,
    val sourceLocalId: String? = null,
) {
    fun normalized(): MediaProviderIds =
        MediaProviderIds(
            tmdbId = tmdbId.normalizedId(),
            tvdbId = tvdbId.normalizedId(),
            sourceLocalId = sourceLocalId.normalizedId(),
        )

    fun identityFor(mediaType: String): MediaIdentity? {
        val normalizedIds = normalized()
        val normalizedType = mediaType.trim().lowercase().takeIf(String::isNotEmpty) ?: return null
        return when {
            normalizedIds.tmdbId != null ->
                MediaIdentity(normalizedType, MediaIdentityProvider.TMDB, normalizedIds.tmdbId)
            normalizedIds.tvdbId != null ->
                MediaIdentity(normalizedType, MediaIdentityProvider.TVDB, normalizedIds.tvdbId)
            normalizedIds.sourceLocalId != null ->
                MediaIdentity(normalizedType, MediaIdentityProvider.SOURCE_LOCAL, normalizedIds.sourceLocalId)
            else -> null
        }
    }
}

data class SavedMediaRecord(
    val profileId: String,
    val mediaType: String,
    val providerIds: MediaProviderIds,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val identity: MediaIdentity =
        requireNotNull(providerIds.identityFor(mediaType)) {
            "Saved media requires an exact provider or source-local identity"
        }
}

private fun String?.normalizedId(): String? = this?.trim()?.takeIf(String::isNotEmpty)
