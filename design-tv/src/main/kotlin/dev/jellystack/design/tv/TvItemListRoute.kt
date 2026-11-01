@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.jellystack.core.jellyfin.JellyfinItem

/** What the screen of an item list route reads: the coordinator that loads lists, and what the cards are drawn with. */
internal class TvItemListRouteData(
    val routeKey: String,
    val coordinator: TvItemListCoordinator,
    val states: Map<String, TvItemListState>,
    val images: TvLibraryGridImages,
    val favorites: Set<String>,
)

/** What the screen of an item list route can do: open a title, and start playback. */
internal class TvItemListRoutePlayback(
    val launcher: TvPlaybackLauncher,
    val onOpenItem: (JellyfinItem) -> Unit,
)

/**
 * The screen of a person, collection, playlist, album, or artist route: the cards of its titles, or for an album and a
 * playlist of music the rows of its tracks.
 */
@Composable
internal fun TvItemListRoute(
    route: TvRoute,
    data: TvItemListRouteData,
    playback: TvItemListRoutePlayback,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
) {
    val source = requireNotNull(route.itemListSource())
    LaunchedEffect(source) { data.coordinator.show(source) }
    val content =
        TvItemListContent(
            source = source,
            state = data.states[source.key()] ?: TvItemListState.Loading,
            images = data.images,
            favorites = data.favorites,
            routeKey = data.routeKey,
        )
    val actions = tvItemListActions(content, data.coordinator, playback)
    if (content.showsTrackList) {
        TvTrackListScreen(content, actions, strings, focusMemory)
    } else {
        TvItemListScreen(content, actions, strings, focusMemory)
    }
}

internal fun tvItemListActions(
    content: TvItemListContent,
    coordinator: TvItemListCoordinator,
    playback: TvItemListRoutePlayback,
): TvItemListActions {
    val source = content.source
    val launcher = playback.launcher
    // Tracks are already loaded, so playing them does not ask the server for the list again.
    val tracksLoaded = content.showsTrackList
    return TvItemListActions(
        onOpenItem = playback.onOpenItem,
        onPlayAll = {
            if (tracksLoaded) {
                launcher.playTracks(content.items, startIndex = 0)
            } else {
                source.playItem()?.let(launcher::playAll)
            }
        },
        onShuffle = {
            if (tracksLoaded) {
                launcher.playTracks(content.items, startIndex = null, shuffle = true)
            } else {
                source.playItem()?.let { launcher.playAll(it, shuffle = true) }
            }
        },
        onPlayTrack = { index -> launcher.playTracks(content.items, startIndex = index) },
        onRetry = { coordinator.retry(source) },
    )
}
