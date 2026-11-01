package dev.jellystack.design.tv

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.core.jellyfin.isEpisodeParent
import dev.jellystack.core.jellyfin.isMusicAlbum
import dev.jellystack.core.jellyfin.isMusicArtist
import dev.jellystack.core.jellyfin.isPlayableAudio
import dev.jellystack.core.jellyfin.isPlayableVideo
import dev.jellystack.core.jellyfin.isPlaylist

/**
 * The videos and music tracks "Play all" plays, in order. A series or season among the [entries] plays as its
 * episodes; anything that cannot be played directly (a folder, a nested collection, a missing file) is left out. An
 * entry whose episodes cannot be loaded is left out rather than failing the whole list.
 */
internal suspend fun buildPlayAllQueue(
    entries: List<JellyfinItem>,
    loadEpisodes: suspend (JellyfinItem) -> List<JellyfinItem>,
): List<JellyfinItem> {
    val resolver = SeriesPlaybackTargetResolver()
    return buildList {
        entries.forEach { entry ->
            when {
                entry.isEpisodeParent() ->
                    runSuspendCatching { loadEpisodes(entry) }
                        .getOrNull()
                        ?.let { episodes -> addAll(resolver.playOrder(episodes, completedDownloadIds = emptySet())) }
                entry.isPlayableVideo() || entry.isPlayableAudio() -> add(entry)
            }
        }
    }.distinctBy(JellyfinItem::id)
}

/**
 * What "Play all" plays for [container]: a collection's entries (in release order), a playlist's (in its own order),
 * an album's tracks in track order, or all the tracks of an artist, album by album.
 */
internal suspend fun JellyfinBrowseRepository.listEntriesOf(container: JellyfinItem): List<JellyfinItem> =
    when {
        container.isMusicAlbum() -> albumTracks(container.id)
        container.isMusicArtist() -> artistTracks(container.id)
        container.isPlaylist() -> playlistItems(container.id)
        else -> collectionItems(container.id)
    }
