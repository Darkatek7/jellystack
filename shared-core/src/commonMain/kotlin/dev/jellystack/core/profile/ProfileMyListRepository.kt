package dev.jellystack.core.profile

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

data class MyListEntry(
    val identity: MediaIdentity,
    val jellyfinItem: JellyfinItem?,
    val savedMedia: SavedMediaRecord?,
) {
    val available: Boolean
        get() = jellyfinItem != null

    val title: String
        get() = jellyfinItem?.name ?: requireNotNull(savedMedia).title
}

class ProfileMyListRepository(
    private val store: ProfileStore,
    private val clock: Clock = Clock.System,
) {
    fun observeSavedMedia(profileId: String): Flow<List<SavedMediaRecord>> = store.observeSavedMedia(profileId)

    suspend fun saveSeerr(
        profileId: String,
        item: JellyseerrSearchItem,
    ) {
        require(profileId.isNotBlank())
        val now = clock.now()
        val existing = store.listSavedMedia(profileId).firstOrNull { it.identity == item.mediaIdentity() }
        store.upsertSavedMedia(
            SavedMediaRecord(
                profileId = profileId,
                mediaType = item.mediaType.storageMediaType(),
                providerIds =
                    MediaProviderIds(
                        tmdbId = item.tmdbId.toString(),
                        tvdbId = item.tvdbId?.toString(),
                    ),
                title = item.title,
                posterPath = item.posterPath,
                backdropPath = item.backdropPath,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun removeSeerr(
        profileId: String,
        item: JellyseerrSearchItem,
    ) {
        store.deleteSavedMedia(profileId, item.mediaIdentity())
    }

    suspend fun reconcile(
        profileId: String,
        jellyfinFavoriteIds: Set<String>,
        resolveFavorite: suspend (itemId: String) -> JellyfinItem?,
        resolveIdentity: suspend (identity: MediaIdentity) -> JellyfinItem?,
    ): List<MyListEntry> {
        val entries = linkedMapOf<MediaIdentity, MyListEntry>()
        jellyfinFavoriteIds.forEach { itemId ->
            val item = resolveFavorite(itemId) ?: return@forEach
            val identity = item.mediaIdentity()
            entries[identity] = MyListEntry(identity, item, savedMedia = null)
        }
        store
            .listSavedMedia(profileId)
            .sortedByDescending(SavedMediaRecord::updatedAt)
            .forEach { saved ->
                val existing = entries[saved.identity]
                val available = existing?.jellyfinItem ?: resolveIdentity(saved.identity)
                entries[saved.identity] = MyListEntry(saved.identity, available, saved)
            }
        return entries.values.toList()
    }
}

fun JellyfinItem.mediaIdentity(): MediaIdentity =
    requireNotNull(providerIds.identityFor(type.storageMediaType())) {
        "Jellyfin item requires an exact provider or source-local identity"
    }

fun JellyseerrSearchItem.mediaIdentity(): MediaIdentity =
    requireNotNull(
        MediaProviderIds(
            tmdbId = tmdbId.toString(),
            tvdbId = tvdbId?.toString(),
        ).identityFor(mediaType.storageMediaType()),
    )

fun String.storageMediaType(): String =
    when (trim().lowercase()) {
        "series", "tv" -> "tv"
        "movie" -> "movie"
        else -> trim().lowercase()
    }

private fun JellyseerrMediaType.storageMediaType(): String =
    when (this) {
        JellyseerrMediaType.MOVIE -> "movie"
        JellyseerrMediaType.TV -> "tv"
        JellyseerrMediaType.PERSON -> "person"
        JellyseerrMediaType.COLLECTION -> "collection"
        JellyseerrMediaType.UNKNOWN -> "unknown"
    }
