package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun restoredQueryBrowsesResultsInsteadOfReopeningTheKeyboard() =
        runTest {
            val sources = TvSearchSources(jellyfin = { emptyList() }, seerr = { emptyList() })
            val restored =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    initialSession = TvSearchSessionState(query = " dune ", mode = TvSearchMode.EDIT),
                    sources = sources,
                )
            val fresh =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    initialSession = TvSearchSessionState(query = "  ", mode = TvSearchMode.EDIT),
                    sources = sources,
                )

            assertEquals("dune", restored.session.value.query)
            assertEquals(TvSearchMode.BROWSE, restored.session.value.mode)
            assertEquals(TvSearchMode.EDIT, fresh.session.value.mode)
            advanceUntilIdle()
        }

    @Test
    fun queryFocusLossWhileEditingEndsEditingOnTheSourceChips() {
        val tracker = TvSearchQueryFocusTracker()

        assertEquals(null, tracker.onFocusChanged(focused = false, mode = TvSearchMode.EDIT))
        assertEquals(null, tracker.onFocusChanged(focused = true, mode = TvSearchMode.EDIT))
        assertEquals(TvSearchEditExit.FOCUS_SOURCES, tracker.onFocusChanged(focused = false, mode = TvSearchMode.EDIT))
    }

    @Test
    fun leftTowardsTheRailEndsEditingWithoutTakingFocusBack() {
        val tracker = TvSearchQueryFocusTracker()
        tracker.onFocusChanged(focused = true, mode = TvSearchMode.EDIT)

        tracker.onLeavingForRail()

        assertEquals(TvSearchEditExit.KEEP_FOCUS, tracker.onFocusChanged(focused = false, mode = TvSearchMode.EDIT))
        tracker.onFocusChanged(focused = true, mode = TvSearchMode.EDIT)
        assertEquals(TvSearchEditExit.FOCUS_SOURCES, tracker.onFocusChanged(focused = false, mode = TvSearchMode.EDIT))
    }

    @Test
    fun queryFocusLossWhileBrowsingKeepsBrowsing() {
        val tracker = TvSearchQueryFocusTracker()
        tracker.onFocusChanged(focused = true, mode = TvSearchMode.BROWSE)

        assertEquals(null, tracker.onFocusChanged(focused = false, mode = TvSearchMode.BROWSE))
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
