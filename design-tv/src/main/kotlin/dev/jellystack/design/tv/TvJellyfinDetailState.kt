@file:Suppress("MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.DetailTrailerContext
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.withProgressOf

/** What a Jellyfin detail page has loaded so far and which of its actions failed. */
@Stable
internal class TvJellyfinDetailState {
    var item by mutableStateOf<JellyfinItem?>(null)
    var detail by mutableStateOf<JellyfinItemDetail?>(null)

    /** A series: all its episodes. An episode: all episodes of its series, of which its season is shown. */
    var episodes by mutableStateOf<List<JellyfinItem>>(emptyList())
    var similar by mutableStateOf<List<JellyfinItem>>(emptyList())
    var trailer by mutableStateOf<DetailTrailerSource?>(null)
    var trailerError by mutableStateOf(false)
    var actionFailed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/**
 * Loads [itemId] into this state: the item and its detail first, then the episodes of a series (or the season of an
 * episode), similar titles and the trailer. Cached details are refreshed afterwards because watched state and
 * progress may have changed elsewhere.
 */
internal suspend fun TvJellyfinDetailState.load(
    itemId: String,
    initialItem: JellyfinItem?,
    repository: JellyfinBrowseRepository,
    trailerResolver: DetailTrailerResolver,
    strings: TvStrings,
) {
    error = null
    val detailWasCached = runSuspendCatching { repository.hasCachedItemDetail(itemId) }.getOrDefault(false)
    runSuspendCatching {
        val loaded =
            loadTvJellyfinDetailBase(
                itemId = itemId,
                initialItem = initialItem,
                cachedItem = repository::cachedItem,
                loadDetail = repository::getItemDetail,
            )
        val loadedItem = loaded.item
        val loadedDetail = loaded.detail
        item = loadedItem
        detail = loadedDetail
        val seriesId = loadedItem.seriesId
        if (loadedItem.type.equals("Series", true)) {
            episodes = repository.refreshEpisodesForSeries(itemId)
        } else if (loadedItem.type.equals("Episode", true) && seriesId != null) {
            // The rest of the season is a bonus: the episode page works without it.
            episodes = runSuspendCatching { repository.refreshEpisodesForSeries(seriesId) }.getOrDefault(emptyList())
        }
        similar = repository.fetchSimilarItems(itemId, 12)
        trailer =
            trailerResolver.resolve(
                DetailTrailerContext(
                    itemId = loadedItem.id,
                    isEpisode = loadedItem.type.equals("Episode", true),
                    isSeries = loadedItem.type.equals("Series", true),
                    seriesId = loadedItem.seriesId,
                    detail = loadedDetail,
                ),
            )
    }.onFailure { failure -> error = tvDetailErrorMessage(failure, strings) }
    if (detailWasCached && item != null) {
        runSuspendCatching {
            repository.getItemDetail(itemId, forceRefresh = true)?.let { detail = it }
            val synced = repository.cachedItem(itemId)
            item = item?.let { shown -> synced?.let(shown::withProgressOf) ?: shown.copy(isPlayed = detail?.isPlayed ?: shown.isPlayed) }
        }
    }
}

/** The episodes of [current]'s season, in order; empty when the season has no other episode to show. */
internal fun tvSeasonSiblings(
    current: JellyfinItem,
    seriesEpisodes: List<JellyfinItem>,
): List<JellyfinItem> {
    val siblings =
        seriesEpisodes
            .filter { it.parentIndexNumber == current.parentIndexNumber }
            .sortedWith(compareBy<JellyfinItem> { it.indexNumber ?: Int.MAX_VALUE }.thenBy { it.id })
    return siblings.takeIf { list -> list.any { it.id != current.id } }.orEmpty()
}

/** The series page for an episode when the series itself is not cached: the episode carries its series' id and art. */
internal fun tvSeriesStubOf(episode: JellyfinItem): JellyfinItem? {
    val seriesId = episode.seriesId?.takeIf(String::isNotBlank) ?: return null
    return episode.copy(
        id = seriesId,
        name = episode.seriesName ?: episode.name,
        type = "Series",
        overview = null,
        taglines = emptyList(),
        primaryImageTag = episode.seriesPrimaryImageTag,
        thumbImageTag = episode.seriesThumbImageTag,
        backdropImageTag = episode.seriesBackdropImageTag,
        logoImageTag = episode.seriesLogoImageTag,
        seriesId = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        indexNumber = null,
        parentIndexNumber = null,
        seasonId = null,
        episodeTitle = null,
        isPlayed = false,
    )
}
