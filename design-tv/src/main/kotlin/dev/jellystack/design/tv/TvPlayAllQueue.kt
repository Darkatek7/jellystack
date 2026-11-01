package dev.jellystack.design.tv

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.core.jellyfin.isEpisodeParent
import dev.jellystack.core.jellyfin.isPlayableVideo
import dev.jellystack.core.jellyfin.isPlaylist

/**
 * The videos "Play all" plays, in order. A series or season among the [entries] plays as its episodes; anything that is
 * not a video (a folder, a nested collection, music, a missing file) is left out. An entry whose episodes cannot be
 * loaded is left out rather than failing the whole list.
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
                entry.isPlayableVideo() -> add(entry)
            }
        }
    }.distinctBy(JellyfinItem::id)
}

/** The entries of a collection (in release order) or playlist (in its own order) as the server lists them. */
internal suspend fun JellyfinBrowseRepository.listEntriesOf(container: JellyfinItem): List<JellyfinItem> =
    if (container.isPlaylist()) playlistItems(container.id) else collectionItems(container.id)
