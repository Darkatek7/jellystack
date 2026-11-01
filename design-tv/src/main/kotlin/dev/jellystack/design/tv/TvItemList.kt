package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.isPlaylist
import dev.jellystack.core.jellyfin.opensAsItemList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a list of titles belongs to: a person's filmography, or the entries of a collection or playlist. */
internal sealed interface TvItemListSource {
    val id: String
    val title: String

    data class Person(
        override val id: String,
        override val title: String,
    ) : TvItemListSource

    data class Container(
        override val id: String,
        override val title: String,
        val isPlaylist: Boolean,
    ) : TvItemListSource
}

/** The list of titles a route shows, or null for routes that are not title lists. */
internal fun TvRoute.itemListSource(): TvItemListSource? =
    when (this) {
        is TvRoute.Person -> TvItemListSource.Person(id, name)
        is TvRoute.Collection -> TvItemListSource.Container(id, name, isPlaylist)
        else -> null
    }

/** The route a collection or playlist card opens; null for any other title. */
internal fun JellyfinItem.itemListRoute(): TvRoute.Collection? =
    takeIf(JellyfinItem::opensAsItemList)?.let { TvRoute.Collection(it.id, it.name, isPlaylist = it.isPlaylist()) }

/** The collection or playlist as a title, for the play action, which only needs its id, kind, and name. */
internal fun TvItemListSource.Container.toItem(): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = null,
        name = title,
        sortName = null,
        overview = null,
        type = if (isPlaylist) "Playlist" else "BoxSet",
        mediaType = null,
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

@Immutable
internal sealed interface TvItemListState {
    data object Loading : TvItemListState

    data class Ready(
        val items: List<JellyfinItem>,
    ) : TvItemListState

    data object Failed : TvItemListState
}

/** Loads the titles of an item list from the server. */
internal fun interface TvItemListLoader {
    suspend fun load(source: TvItemListSource): List<JellyfinItem>
}

internal fun JellyfinBrowseRepository.itemListLoader() =
    TvItemListLoader { source ->
        when (source) {
            is TvItemListSource.Person -> personItems(source.id)
            is TvItemListSource.Container ->
                if (source.isPlaylist) playlistItems(source.id) else collectionItems(source.id)
        }
    }

/**
 * Holds the loaded lists outside the screens. A list a screen showed stays visible when the user returns from one of
 * its titles, while it refreshes; that keeps the remembered card in place for focus restoration, not a spinner.
 */
internal class TvItemListCoordinator(
    private val scope: CoroutineScope,
    private val loader: TvItemListLoader,
) {
    private val mutableStates = MutableStateFlow<Map<String, TvItemListState>>(emptyMap())
    val states: StateFlow<Map<String, TvItemListState>> = mutableStates.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    /** Shows [source] now (the loaded list, else a loading state) and refreshes it from the server. */
    fun show(source: TvItemListSource) {
        start(source, showLoading = states.value[source.key()] !is TvItemListState.Ready)
    }

    /** Tries a failed list again. */
    fun retry(source: TvItemListSource) {
        start(source, showLoading = true)
    }

    private fun start(
        source: TvItemListSource,
        showLoading: Boolean,
    ) {
        val key = source.key()
        jobs.remove(key)?.cancel()
        if (showLoading) mutableStates.update { it + (key to TvItemListState.Loading) }
        jobs[key] =
            scope.launch {
                val result = runSuspendCatching { loader.load(source) }
                mutableStates.update { current ->
                    result
                        .fold(
                            onSuccess = { items -> TvItemListState.Ready(items.distinctBy(JellyfinItem::id)) },
                            // A refresh that fails keeps what the user already sees.
                            onFailure = { current[key] as? TvItemListState.Ready ?: TvItemListState.Failed },
                        ).let { current + (key to it) }
                }
            }
    }
}

internal fun TvItemListSource.key(): String =
    when (this) {
        is TvItemListSource.Person -> "person:$id"
        is TvItemListSource.Container -> "collection:$id"
    }
