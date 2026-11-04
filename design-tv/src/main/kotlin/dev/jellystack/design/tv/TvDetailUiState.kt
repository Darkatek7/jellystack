@file:Suppress("LongParameterList", "MaxLineLength", "ReturnCount")

package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.serialization.Serializable

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

    /**
     * The full overview and tagline with the credits the item has ([rows]); the hero only shows three lines of the
     * overview. This is also the body focus target at the bottom of the page.
     */
    @Immutable
    data class Details(
        val text: String?,
        val tagline: String?,
        val rows: List<TvDetailRow> = emptyList(),
        override val id: String = "details",
    ) : TvDetailSection

    @Immutable
    data class Seasons(
        val groups: List<TvSeasonGroup>,
        val selectedIndex: Int,
        override val id: String = "seasons",
    ) : TvDetailSection {
        override val itemIds: List<String> = groups.map { "season-${it.seasonNumber ?: Int.MAX_VALUE}" }
    }

    /** The episodes of the shown season; on an episode page they are the others of its season ([moreFromSeason]). */
    @Immutable
    data class Episodes(
        val items: List<JellyfinItem>,
        val moreFromSeason: Int? = null,
        val currentEpisodeId: String? = null,
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

/** One credit line of the Details section, such as "Director" and the names. */
@Immutable
internal data class TvDetailRow(
    val label: String,
    val value: String,
)

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
@Serializable
internal data class TvFocusAnchor(
    val sectionId: String?,
    val itemId: String?,
    val destination: TvFocusDestination,
)

@Serializable
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

/** Blank text counts as missing, so the section shows its fallback text instead of an empty header. */
private fun tvDetailsSection(
    overview: String?,
    tagline: String?,
    rows: List<TvDetailRow>,
): TvDetailSection.Details =
    TvDetailSection.Details(
        text = overview?.takeIf(String::isNotBlank),
        tagline = tagline?.takeIf(String::isNotBlank),
        rows = rows,
    )

/**
 * Sections of a Jellyfin detail page in the order they are shown: Seasons, Episodes (or More from season on an
 * episode), Cast, Similar, Details.
 */
internal fun buildTvJellyfinDetailUiState(
    routeKey: String,
    overview: String?,
    tagline: String?,
    detailRows: List<TvDetailRow>,
    seasonGroups: List<TvSeasonGroup>,
    selectedSeasonIndex: Int,
    episodes: List<JellyfinItem>,
    cast: List<JellyfinPerson>,
    similar: List<JellyfinItem>,
    moreFromSeason: Int? = null,
    currentEpisodeId: String? = null,
): TvDetailUiState {
    val episodeSnapshot = episodes.distinctBy(JellyfinItem::id)
    val castSnapshot = cast.distinctBy(JellyfinPerson::id).take(16)
    val similarSnapshot = similar.distinctBy(JellyfinItem::id)
    return TvDetailUiState(
        routeKey = routeKey,
        sections =
            buildList {
                if (episodeSnapshot.isNotEmpty() && seasonGroups.size > 1 && moreFromSeason == null) {
                    add(
                        TvDetailSection.Seasons(
                            groups = seasonGroups.map { it.copy(episodes = it.episodes.toList()) },
                            selectedIndex = selectedSeasonIndex,
                        ),
                    )
                }
                if (episodeSnapshot.isNotEmpty()) {
                    add(TvDetailSection.Episodes(episodeSnapshot, moreFromSeason, currentEpisodeId))
                }
                if (castSnapshot.isNotEmpty()) add(TvDetailSection.Cast(castSnapshot.map(TvDetailCastItem::Jellyfin)))
                if (similarSnapshot.isNotEmpty()) add(TvDetailSection.Similar(similarSnapshot.map(TvDetailSimilarItem::Jellyfin)))
                add(tvDetailsSection(overview, tagline, detailRows))
            },
    )
}

/** Seerr pages show Cast, Similar, Details and, last, the ratings. */
internal fun buildTvSeerrDetailUiState(
    routeKey: String,
    overview: String?,
    tagline: String?,
    ratings: JellyseerrMediaRatings?,
    cast: List<JellyseerrPerson>,
    similar: List<JellyseerrSearchItem>,
    detailRows: List<TvDetailRow> = emptyList(),
): TvDetailUiState =
    TvDetailUiState(
        routeKey = routeKey,
        sections =
            buildList {
                val castSnapshot = cast.distinctBy(JellyseerrPerson::id).take(16)
                val similarSnapshot = similar.distinctBy { "${it.mediaType.name.lowercase()}:${it.tmdbId}" }
                if (castSnapshot.isNotEmpty()) add(TvDetailSection.Cast(castSnapshot.map(TvDetailCastItem::Seerr)))
                if (similarSnapshot.isNotEmpty()) add(TvDetailSection.Similar(similarSnapshot.map(TvDetailSimilarItem::Seerr)))
                add(tvDetailsSection(overview, tagline, detailRows))
                ratings?.let { add(TvDetailSection.Ratings(it)) }
            },
    )
