package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppLanguage
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TvDiscoverRowsTest {
    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun severalRequestsForOneTitleShowOneCardInTheRequestsRow() {
        val requests =
            listOf(
                request(id = 1, tmdbId = 10, status = JellyseerrRequestStatus.COMPLETED),
                request(id = 2, tmdbId = 10, status = JellyseerrRequestStatus.PENDING).copy(is4k = true),
                request(id = 3, tmdbId = 20, mediaType = JellyseerrMediaType.TV, updatedAt = "2026-03-01T00:00:00Z"),
                request(id = 4, tmdbId = 20, mediaType = JellyseerrMediaType.TV, updatedAt = "2026-04-01T00:00:00Z"),
                request(id = 5, tmdbId = null),
            )

        val discover = buildTvDiscoverRows(readyState(), requests, strings) { false }

        val row = discover.rows.single()
        assertEquals(DISCOVER_REQUESTS_ROW, row.id)
        assertEquals(listOf("request:movie:10", "request:tv:20"), row.cards.map(TvCinematicCard::id))
        // The request still in flight stands for the movie; the latest season request stands for the show.
        assertEquals(listOf(2), discover.item("request:movie:10")?.requests?.map(JellyseerrRequestSummary::id))
        assertEquals(listOf(4), discover.item("request:tv:20")?.requests?.map(JellyseerrRequestSummary::id))
    }

    @Test
    fun aTitleRepeatedInOneRailShowsOnceAndABlankTitleReadsUntitled() {
        val items =
            listOf(
                searchItem(tmdbId = 1, title = ""),
                searchItem(tmdbId = 2, title = "Repeated"),
                searchItem(tmdbId = 2, title = "Repeated"),
            )

        val trends = readyState(JellyseerrRecommendationRail.TRENDS to items)

        val discover = buildTvDiscoverRows(trends, emptyList(), strings) { false }

        val row = discover.rows.single()
        assertEquals("discover-trends", row.id)
        assertEquals(listOf("movie:1", "movie:2"), row.cards.map(TvCinematicCard::id))
        assertEquals(listOf(strings.messages.untitled, "Repeated"), row.cards.map(TvCinematicCard::title))
    }

    @Test
    fun aRequestWithoutAnyNameIsLabelledWithItsNumber() {
        val unnamed = request(id = 7, tmdbId = 30).copy(title = " ")

        val discover = buildTvDiscoverRows(readyState(), listOf(unnamed), strings) { false }

        assertEquals(
            listOf(strings.messages.untitledRequest(7)),
            discover.rows
                .single()
                .cards
                .map(TvCinematicCard::title),
        )
    }

    @Test
    fun theScreenOpensOnTheFirstCardOfTheFirstRowAndIsEmptyWithoutCards() {
        val items = listOf(searchItem(tmdbId = 1, title = "One"))
        val populated =
            buildTvDiscoverRows(
                readyState(JellyseerrRecommendationRail.POPULAR_MOVIES to items),
                listOf(request(id = 1, tmdbId = 2)),
                strings,
            ) { false }
        val empty = buildTvDiscoverRows(readyState(), listOf(request(id = 2, tmdbId = null)), strings) { false }

        assertEquals(tvCinematicFocusTargetId("discover-popular_movies", "movie:1"), populated.initialTargetId)
        assertNull(empty.initialTargetId)
        assertNotNull(populated.item("movie:1"))
    }

    private fun readyState(vararg rails: Pair<JellyseerrRecommendationRail, List<JellyseerrSearchItem>>) =
        JellyseerrRecommendationsState.Ready(
            rails.associate { (rail, items) ->
                rail to
                    JellyseerrRecommendationRailState(
                        rail = rail,
                        items = items,
                        isLoading = false,
                        errorMessage = null,
                        canLoadMore = false,
                        nextPage = 2,
                        lastUpdated = null,
                        isStale = false,
                    )
            },
        )

    private fun searchItem(
        tmdbId: Int,
        title: String,
    ) = seerrItem(title = title, tmdbId = tmdbId)

    private fun request(
        id: Int,
        tmdbId: Int?,
        mediaType: JellyseerrMediaType = JellyseerrMediaType.MOVIE,
        status: JellyseerrRequestStatus = JellyseerrRequestStatus.APPROVED,
        updatedAt: String? = null,
    ) = JellyseerrRequestSummary(
        id = id,
        mediaId = tmdbId,
        tmdbId = tmdbId,
        tvdbId = null,
        title = "Title $tmdbId",
        originalTitle = null,
        mediaType = mediaType,
        requestStatus = status,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        is4k = false,
        canRemoveFromService = false,
        createdAt = null,
        updatedAt = updatedAt?.let(Instant::parse),
        requestedBy = null,
        profileName = null,
        seasons = emptyList(),
        posterPath = null,
        backdropPath = null,
    )
}
