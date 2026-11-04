@file:Suppress("FunctionName", "ReturnCount")

package dev.jellystack.design.tv

import androidx.compose.ui.graphics.Color
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail

internal data class TvJellyfinDetailBase(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail,
)

internal data class TvJellyfinHeroTitlePresentation(
    val useGraphicLogo: Boolean,
    val textColor: Color,
)

/** Typed detail-load failures so the UI can show localized copy instead of raw exception text. */
internal enum class TvDetailLoadErrorKind { ITEM_UNAVAILABLE, DETAILS_UNAVAILABLE }

internal class TvDetailLoadException(
    val kind: TvDetailLoadErrorKind,
) : Exception(kind.name)

internal fun tvDetailErrorMessage(
    error: Throwable,
    strings: TvStrings,
): String =
    when ((error as? TvDetailLoadException)?.kind) {
        TvDetailLoadErrorKind.ITEM_UNAVAILABLE -> strings.detailUnavailable
        TvDetailLoadErrorKind.DETAILS_UNAVAILABLE -> strings.detailLoadFailed
        null -> strings.detailLoadFailed
    }

internal fun tvJellyfinHeroTitlePresentation(
    itemType: String,
    logoTag: String?,
): TvJellyfinHeroTitlePresentation =
    TvJellyfinHeroTitlePresentation(
        useGraphicLogo = !itemType.equals("Episode", ignoreCase = true) && logoTag != null,
        textColor = TvText,
    )

internal data class TvSeasonGroup(
    val seasonNumber: Int?,
    val episodes: List<JellyfinItem>,
)

internal fun buildTvSeasonGroups(episodes: List<JellyfinItem>): List<TvSeasonGroup> =
    episodes
        .groupBy { episode -> episode.parentIndexNumber?.takeIf { it > 0 } }
        .map { (seasonNumber, episodesInSeason) ->
            TvSeasonGroup(
                seasonNumber = seasonNumber,
                episodes =
                    episodesInSeason.sortedWith(
                        compareBy<JellyfinItem> { it.indexNumber ?: Int.MAX_VALUE }.thenBy { it.id },
                    ),
            )
        }.sortedWith(
            compareBy<TvSeasonGroup> { group -> group.seasonNumber ?: Int.MAX_VALUE },
        )

/** Prefers the season that holds an in-progress or next-up episode, else the first season. */
internal fun defaultTvSeasonIndex(
    groups: List<TvSeasonGroup>,
    fallbackEpisodeId: String? = null,
): Int {
    val byFallback =
        fallbackEpisodeId
            ?.let { targetId -> groups.indexOfFirst { group -> group.episodes.any { it.id == targetId } } }
            ?.takeIf { it >= 0 }
    if (byFallback != null) return byFallback
    val hasProgress: (JellyfinItem) -> Boolean = { (it.positionTicks ?: 0L) > 0L || it.playedPercentage != null }
    val inProgress = groups.indexOfFirst { group -> group.episodes.any(hasProgress) }
    if (inProgress >= 0) return inProgress
    // Otherwise open where the viewer left off: the first regular season that still has an unwatched episode.
    val firstUnplayed =
        groups.indexOfFirst { group -> (group.seasonNumber ?: 0) > 0 && group.episodes.any { !it.isPlayed } }
    return firstUnplayed.takeIf { it >= 0 } ?: 0
}

internal suspend fun loadTvJellyfinDetailBase(
    itemId: String,
    initialItem: JellyfinItem?,
    cachedItem: suspend (String) -> JellyfinItem?,
    loadDetail: suspend (String) -> JellyfinItemDetail?,
): TvJellyfinDetailBase {
    val item = initialItem ?: cachedItem(itemId) ?: throw TvDetailLoadException(TvDetailLoadErrorKind.ITEM_UNAVAILABLE)
    val detail = loadDetail(itemId) ?: throw TvDetailLoadException(TvDetailLoadErrorKind.DETAILS_UNAVAILABLE)
    return TvJellyfinDetailBase(item, detail)
}
