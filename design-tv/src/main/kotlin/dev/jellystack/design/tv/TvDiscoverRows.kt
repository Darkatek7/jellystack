package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

internal const val DISCOVER_REQUESTS_ROW = "discover-requests"

/** The rows of the cinematic Discover screen and the Seerr title behind each card. */
internal class TvDiscoverRows(
    val rows: List<TvCinematicRow>,
    private val itemsByCardId: Map<String, JellyseerrSearchItem>,
) {
    val cardIds: Set<String> get() = itemsByCardId.keys

    fun item(cardId: String): JellyseerrSearchItem? = itemsByCardId[cardId]

    /** The card the screen opens on: the first card of the first row. */
    val initialTargetId: String?
        get() {
            val row = rows.firstOrNull() ?: return null
            val card = row.cards.firstOrNull() ?: return null
            return tvCinematicFocusTargetId(row.id, card.id)
        }
}

/**
 * Builds the Discover rows from what Seerr sent. A title that appears twice in a rail gets one card, and so does a
 * title with several requests (a 4K and an HD request, or one request per season), because the cards of a row need
 * distinct IDs.
 */
internal fun buildTvDiscoverRows(
    state: JellyseerrRecommendationsState.Ready,
    requestItems: List<JellyseerrRequestSummary>,
    strings: TvStrings,
    isSaved: (JellyseerrSearchItem) -> Boolean,
): TvDiscoverRows {
    val untitled = strings.messages.untitled
    val itemsByCardId = linkedMapOf<String, JellyseerrSearchItem>()

    fun cardsOf(
        items: List<JellyseerrSearchItem>,
        cardId: (JellyseerrSearchItem) -> String,
    ): List<TvCinematicCard> =
        items.map { item ->
            val id = cardId(item)
            itemsByCardId.getOrPut(id) { item }
            item.toCinematicCard(id, isSaved(item), untitled)
        }

    val rows =
        buildList {
            JellyseerrRecommendationRail.entries.forEach { rail ->
                val cards = cardsOf(state.rails[rail]?.items.orEmpty(), JellyseerrSearchItem::cinematicKey)
                tvCinematicRowOrNull("discover-${rail.name.lowercase()}", rail.label(strings), cards)?.let(::add)
            }
            val requested =
                requestItems
                    .collapsedByMedia()
                    .mapNotNull { it.toSearchItem(strings.messages::untitledRequest) }
            val requestCards = cardsOf(requested) { "request:${it.cinematicKey()}" }
            tvCinematicRowOrNull(DISCOVER_REQUESTS_ROW, strings.requests, requestCards)?.let(::add)
        }
    return TvDiscoverRows(rows, itemsByCardId)
}

/**
 * One request per requested title, in the order the titles first appear. Seerr lists a request per version and per
 * season, so the same title can come up several times; the request kept is the one still in flight, then the most
 * recently changed. Requests Seerr sent without a TMDB ID cannot be opened and are left out.
 */
internal fun List<JellyseerrRequestSummary>.collapsedByMedia(): List<JellyseerrRequestSummary> =
    filter { it.tmdbId != null }
        .groupBy { it.mediaType to it.tmdbId }
        .values
        .mapNotNull { requestsForMedia -> requestsForMedia.minWithOrNull(requestRelevance) }

private val requestRelevance =
    compareBy<JellyseerrRequestSummary> { it.requestStatus.relevanceRank() }
        .thenByDescending { it.updatedAt ?: it.createdAt }
        .thenByDescending { it.id }

private fun JellyseerrRequestStatus.relevanceRank(): Int =
    when (this) {
        JellyseerrRequestStatus.PENDING -> 0
        JellyseerrRequestStatus.APPROVED -> 1
        JellyseerrRequestStatus.FAILED -> 2
        JellyseerrRequestStatus.DECLINED -> 3
        JellyseerrRequestStatus.COMPLETED -> 4
        JellyseerrRequestStatus.UNKNOWN -> 5
    }

/** The title of the request, or a numbered label when Seerr sent no usable name. */
internal fun JellyseerrRequestSummary.titleOrUntitled(untitled: (Int) -> String): String =
    title?.takeIf(String::isNotBlank) ?: originalTitle?.takeIf(String::isNotBlank) ?: untitled(id)

internal fun JellyseerrRequestSummary.toSearchItem(untitled: (Int) -> String): JellyseerrSearchItem? {
    val id = tmdbId ?: return null
    return JellyseerrSearchItem(
        tmdbId = id,
        mediaType = mediaType,
        title = titleOrUntitled(untitled),
        overview = null,
        releaseYear = null,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = mediaId,
        tvdbId = tvdbId,
        availability = availability,
        requests = listOf(this),
    )
}

private fun JellyseerrSearchItem.toCinematicCard(
    id: String,
    selected: Boolean,
    untitled: String,
): TvCinematicCard =
    TvCinematicCard(
        id = id,
        title = title.ifBlank { untitled },
        subtitle = releaseYear,
        overview = overview,
        artworkUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        backdropUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        selected = selected,
    )

internal fun JellyseerrSearchItem.cinematicKey(): String = "${mediaType.name.lowercase()}:$tmdbId"
