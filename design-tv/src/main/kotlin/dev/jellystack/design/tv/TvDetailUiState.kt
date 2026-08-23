package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

@Immutable
internal data class TvDetailUiState(
    val routeKey: String,
    val sections: List<TvDetailSection>,
)

@Immutable
internal sealed interface TvDetailSection {
    val id: String
    val itemIds: List<String>
        get() = emptyList()

    @Immutable
    data class Facts(
        val values: List<String>,
        override val id: String = "facts",
    ) : TvDetailSection

    @Immutable
    data class Overview(
        val text: String?,
        val tagline: String?,
        override val id: String = "overview",
    ) : TvDetailSection

    @Immutable
    data class Seasons(
        val groups: List<TvSeasonGroup>,
        val selectedIndex: Int,
        override val id: String = "seasons",
    ) : TvDetailSection {
        override val itemIds: List<String> = groups.map { "season-${it.seasonNumber ?: Int.MAX_VALUE}" }
    }

    @Immutable
    data class Episodes(
        val items: List<JellyfinItem>,
        override val id: String = "episodes",
    ) : TvDetailSection {
        override val itemIds: List<String> = items.map(JellyfinItem::id)
    }

    @Immutable
    data class Cast(
        val items: List<TvDetailCastItem>,
        override val id: String = "cast",
    ) : TvDetailSection {
        override val itemIds: List<String> = items.map(TvDetailCastItem::id)
    }

    @Immutable
    data class Ratings(
        val values: JellyseerrMediaRatings,
        override val id: String = "ratings",
    ) : TvDetailSection

    @Immutable
    data class Similar(
        val items: List<TvDetailSimilarItem>,
        override val id: String = "similar",
    ) : TvDetailSection {
        override val itemIds: List<String> = items.map(TvDetailSimilarItem::id)
    }
}

@Immutable
internal sealed interface TvDetailCastItem {
    val id: String

    @Immutable
    data class Jellyfin(
        val person: JellyfinPerson,
    ) : TvDetailCastItem {
        override val id: String = person.id
    }

    @Immutable
    data class Seerr(
        val person: JellyseerrPerson,
    ) : TvDetailCastItem {
        override val id: String = "person-${person.id}"
    }
}

@Immutable
internal sealed interface TvDetailSimilarItem {
    val id: String

    @Immutable
    data class Jellyfin(
        val item: JellyfinItem,
    ) : TvDetailSimilarItem {
        override val id: String = item.id
    }

    @Immutable
    data class Seerr(
        val item: JellyseerrSearchItem,
    ) : TvDetailSimilarItem {
        override val id: String = "${item.mediaType.name.lowercase()}:${item.tmdbId}"
    }
}

@Immutable
internal data class TvFocusAnchor(
    val sectionId: String?,
    val itemId: String?,
    val destination: TvFocusDestination,
)

internal enum class TvFocusDestination { HERO, PRIMARY_ACTION, BODY, SECTION_ITEM }

@Immutable
internal data class TvResolvedFocusAnchor(
    val sectionIndex: Int,
    val itemIndex: Int,
)

internal fun TvDetailUiState.section(id: String): TvDetailSection? = sections.firstOrNull { it.id == id }

internal fun TvDetailUiState.resolve(anchor: TvFocusAnchor): TvResolvedFocusAnchor? {
    if (anchor.destination != TvFocusDestination.SECTION_ITEM) return null
    val sectionIndex = sections.indexOfFirst { it.id == anchor.sectionId }
    if (sectionIndex < 0) return null
    val itemIndex = sections[sectionIndex].itemIds.indexOf(anchor.itemId)
    return itemIndex.takeIf { it >= 0 }?.let { TvResolvedFocusAnchor(sectionIndex, it) }
}

internal fun buildTvJellyfinDetailUiState(
    routeKey: String,
    facts: List<String>,
    overview: String?,
    tagline: String?,
    seasonGroups: List<TvSeasonGroup>,
    selectedSeasonIndex: Int,
    episodes: List<JellyfinItem>,
    cast: List<JellyfinPerson>,
    similar: List<JellyfinItem>,
): TvDetailUiState {
    val episodeSnapshot = episodes.distinctBy(JellyfinItem::id)
    val castSnapshot = cast.distinctBy(JellyfinPerson::id).take(16)
    val similarSnapshot = similar.distinctBy(JellyfinItem::id)
    return TvDetailUiState(
        routeKey = routeKey,
        sections =
            buildList {
                add(TvDetailSection.Facts(facts.toList()))
                add(TvDetailSection.Overview(overview, tagline))
                if (episodeSnapshot.isNotEmpty() && seasonGroups.size > 1) {
                    add(
                        TvDetailSection.Seasons(
                            groups = seasonGroups.map { it.copy(episodes = it.episodes.toList()) },
                            selectedIndex = selectedSeasonIndex,
                        ),
                    )
                }
                if (episodeSnapshot.isNotEmpty()) add(TvDetailSection.Episodes(episodeSnapshot))
                if (castSnapshot.isNotEmpty()) add(TvDetailSection.Cast(castSnapshot.map(TvDetailCastItem::Jellyfin)))
                if (similarSnapshot.isNotEmpty()) add(TvDetailSection.Similar(similarSnapshot.map(TvDetailSimilarItem::Jellyfin)))
            },
    )
}

internal fun buildTvSeerrDetailUiState(
    routeKey: String,
    overview: String?,
    tagline: String?,
    ratings: JellyseerrMediaRatings?,
    cast: List<JellyseerrPerson>,
    similar: List<JellyseerrSearchItem>,
): TvDetailUiState =
    TvDetailUiState(
        routeKey = routeKey,
        sections =
            buildList {
                add(TvDetailSection.Overview(overview, tagline))
                ratings?.let { add(TvDetailSection.Ratings(it)) }
                val castSnapshot = cast.distinctBy(JellyseerrPerson::id).take(16)
                val similarSnapshot = similar.distinctBy { "${it.mediaType.name.lowercase()}:${it.tmdbId}" }
                if (castSnapshot.isNotEmpty()) add(TvDetailSection.Cast(castSnapshot.map(TvDetailCastItem::Seerr)))
                if (similarSnapshot.isNotEmpty()) add(TvDetailSection.Similar(similarSnapshot.map(TvDetailSimilarItem::Seerr)))
            },
    )
