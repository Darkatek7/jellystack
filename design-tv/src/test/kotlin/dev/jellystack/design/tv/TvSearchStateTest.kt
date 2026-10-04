package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TvSearchStateTest {
    @Test
    fun sourceAndEditModeArePartOfTheUnifiedState() =
        runTest {
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    sources = TvSearchSources(jellyfin = { emptyList() }, seerr = { emptyList() }),
                )

            coordinator.search("dune")
            coordinator.selectSource(TvSearchSource.SEERR)
            coordinator.enterBrowseMode()

            assertEquals("dune", coordinator.state.value.session.query)
            assertEquals(TvSearchSource.SEERR, coordinator.state.value.session.source)
            assertEquals(TvSearchMode.BROWSE, coordinator.state.value.session.mode)
        }

    @Test
    fun discoverDistinguishesMissingLoadingFailureAndContent() {
        assertIs<TvDiscoverAvailability.MissingConnection>(
            tvDiscoverAvailability(JellyseerrRecommendationsState.MissingServer),
        )
        assertIs<TvDiscoverAvailability.Loading>(
            tvDiscoverAvailability(JellyseerrRecommendationsState.Loading),
        )
        assertIs<TvDiscoverAvailability.Failure>(
            tvDiscoverAvailability(JellyseerrRecommendationsState.Error("offline")),
        )
        val ready = JellyseerrRecommendationsState.Ready(emptyMap())
        val content = assertIs<TvDiscoverAvailability.Content>(tvDiscoverAvailability(ready))
        assertEquals(ready, content.state)
        assertFalse(content.hasRailFailures)
    }

    @Test
    fun discoverKeepsPartialContentAndReportsRailFailures() {
        val failedRail = recommendationRail(errorMessage = "offline")
        val failed =
            JellyseerrRecommendationsState.Ready(
                mapOf(JellyseerrRecommendationRail.TRENDS to failedRail),
            )
        assertIs<TvDiscoverAvailability.Failure>(tvDiscoverAvailability(failed))

        val partial =
            JellyseerrRecommendationsState.Ready(
                mapOf(
                    JellyseerrRecommendationRail.TRENDS to failedRail,
                    JellyseerrRecommendationRail.POPULAR_MOVIES to recommendationRail(hasItems = true),
                ),
            )
        val content = assertIs<TvDiscoverAvailability.Content>(tvDiscoverAvailability(partial))
        assertTrue(content.hasRailFailures)
    }

    private fun recommendationRail(
        hasItems: Boolean = false,
        errorMessage: String? = null,
    ) = JellyseerrRecommendationRailState(
        rail = JellyseerrRecommendationRail.TRENDS,
        items = if (hasItems) listOf(seerrItem("available")) else emptyList(),
        isLoading = false,
        errorMessage = errorMessage,
        canLoadMore = false,
        nextPage = 2,
        lastUpdated = null,
        isStale = false,
    )
}
