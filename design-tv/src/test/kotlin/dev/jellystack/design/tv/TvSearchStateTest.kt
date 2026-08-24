package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMessage
import dev.jellystack.core.jellyseerr.JellyseerrMessageCode
import dev.jellystack.core.jellyseerr.JellyseerrMessageKind
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TvSearchStateTest {
    @Test
    fun searchSessionStartsEditingAndBrowseModePreservesQueryAndSource() =
        runTest {
            val coordinator = TvJellyfinSearchCoordinator(this, debounceMillis = 0L) { emptyList() }

            assertEquals(TvSearchMode.EDIT, coordinator.session.value.mode)
            coordinator.search("dune")
            coordinator.selectSource(TvSearchSource.SEERR)
            coordinator.enterBrowseMode()

            assertEquals(
                TvSearchSessionState(
                    query = "dune",
                    source = TvSearchSource.SEERR,
                    mode = TvSearchMode.BROWSE,
                    queryGeneration = 1L,
                ),
                coordinator.session.value,
            )

            coordinator.enterEditMode()
            assertEquals(TvSearchMode.EDIT, coordinator.session.value.mode)
        }

    @Test
    fun restoredQueryIsReissuedExactlyOnceToBothSources() =
        runTest {
            val jellyfinQueries = mutableListOf<String>()
            val seerrQueries = mutableListOf<String>()
            val coordinator =
                TvJellyfinSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    initialSession =
                        TvSearchSessionState(
                            query = "restored",
                            source = TvSearchSource.ALL,
                            mode = TvSearchMode.BROWSE,
                            queryGeneration = 7L,
                        ),
                    submitSeerrSearch = seerrQueries::add,
                    searchItems = { query ->
                        jellyfinQueries += query
                        listOf(item(query))
                    },
                )

            advanceUntilIdle()
            coordinator.restoreQuery("restored")
            advanceUntilIdle()

            assertEquals(listOf("restored"), jellyfinQueries)
            assertEquals(listOf("restored"), seerrQueries)
            assertEquals(TvSearchMode.BROWSE, coordinator.session.value.mode)
            assertEquals(8L, coordinator.session.value.queryGeneration)
        }

    @Test
    fun jellyfinCoordinatorPublishesEverySemanticState() =
        runTest {
            var result: Result<List<JellyfinItem>> = Result.success(listOf(item("one")))
            val coordinator = TvJellyfinSearchCoordinator(this, debounceMillis = 10L) { result.getOrThrow() }

            assertIs<TvJellyfinSearchState.Idle>(coordinator.state.value)
            coordinator.search("  dune  ")
            assertEquals(TvJellyfinSearchState.Loading("dune"), coordinator.state.value)
            advanceUntilIdle()
            val results = assertIs<TvJellyfinSearchState.Results>(coordinator.state.value)
            assertEquals(listOf("one"), results.items.map { it.id })

            result = Result.success(emptyList())
            coordinator.search("empty")
            advanceUntilIdle()
            assertEquals(TvJellyfinSearchState.Empty("empty"), coordinator.state.value)

            result = Result.failure(IllegalStateException("offline"))
            coordinator.search("broken")
            advanceUntilIdle()
            val failure = assertIs<TvJellyfinSearchState.Failure>(coordinator.state.value)
            assertEquals("broken", failure.query)
            assertEquals("offline", failure.message)

            coordinator.search(" ")
            assertIs<TvJellyfinSearchState.Idle>(coordinator.state.value)
        }

    @Test
    fun cancelledOlderSearchCannotOverwriteLatestQuery() =
        runTest {
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val coordinator =
                TvJellyfinSearchCoordinator(this, debounceMillis = 10L) { query ->
                    if (query == "old") {
                        oldStarted.complete(Unit)
                        withContext(NonCancellable) { releaseOld.await() }
                    }
                    listOf(item(query))
                }

            coordinator.search("old")
            advanceTimeBy(10L)
            oldStarted.await()
            coordinator.search("new")
            advanceUntilIdle()
            assertEquals("new", assertIs<TvJellyfinSearchState.Results>(coordinator.state.value).query)

            releaseOld.complete(Unit)
            advanceUntilIdle()
            val final = assertIs<TvJellyfinSearchState.Results>(coordinator.state.value)
            assertEquals("new", final.query)
            assertEquals("new", final.items.single().id)
        }

    @Test
    fun retryKeepsQuerySourceAndModeWhileAdvancingGeneration() =
        runTest {
            val coordinator = TvJellyfinSearchCoordinator(this, debounceMillis = 0L) { emptyList() }
            coordinator.search("arrival")
            coordinator.selectSource(TvSearchSource.JELLYFIN)
            coordinator.enterBrowseMode()
            advanceUntilIdle()

            coordinator.retry()

            assertEquals("arrival", coordinator.session.value.query)
            assertEquals(TvSearchSource.JELLYFIN, coordinator.session.value.source)
            assertEquals(TvSearchMode.BROWSE, coordinator.session.value.mode)
            assertEquals(2L, coordinator.session.value.queryGeneration)
        }

    @Test
    fun partialResultsAndSourceFailuresRemainIndependent() {
        val jellyfinItem = item("jellyfin")
        val seerrItem = seerrItem("Seerr")

        val jellyfinSuccess = TvJellyfinSearchState.Results("query", listOf(jellyfinItem))
        val seerrFailure = ready(query = "query", message = searchFailure("query"))
        val first = tvSearchPresentation("query", TvSearchSource.ALL, jellyfinSuccess, seerrFailure)
        assertEquals(listOf(jellyfinItem), first.jellyfinItems)
        assertTrue(first.showSeerrFailure)
        assertFalse(first.showNoResults)

        val jellyfinFailure = TvJellyfinSearchState.Failure("query", "offline")
        val seerrSuccess = ready(query = "query", results = listOf(seerrItem))
        val second = tvSearchPresentation("query", TvSearchSource.ALL, jellyfinFailure, seerrSuccess)
        assertTrue(second.showJellyfinFailure)
        assertEquals(listOf(seerrItem), second.seerrItems)
        assertFalse(second.showNoResults)
    }

    @Test
    fun emptyLoadingFailureAndSourceFiltersAreDistinct() {
        val empty = TvJellyfinSearchState.Empty("query")
        val seerrEmpty = ready(query = "query")
        assertTrue(tvSearchPresentation("query", TvSearchSource.ALL, empty, seerrEmpty).showNoResults)
        assertFalse(tvSearchPresentation("", TvSearchSource.ALL, TvJellyfinSearchState.Idle, seerrEmpty).showNoResults)

        val loadingState = TvJellyfinSearchState.Loading("query")
        val loading = tvSearchPresentation("query", TvSearchSource.ALL, loadingState, seerrEmpty)
        assertTrue(loading.showSearching)
        assertFalse(loading.showNoResults)

        val jellyfinOnly =
            tvSearchPresentation(
                "query",
                TvSearchSource.JELLYFIN,
                TvJellyfinSearchState.Results("query", listOf(item("jellyfin"))),
                ready(query = "query", results = listOf(seerrItem("Seerr"))),
            )
        assertEquals(1, jellyfinOnly.jellyfinItems.size)
        assertTrue(jellyfinOnly.seerrItems.isEmpty())
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
    fun discoverTreatsRailFailuresAsFailureWithoutDiscardingPartialContent() {
        val failedRail = recommendationRail(JellyseerrRecommendationRail.TRENDS, errorMessage = "offline")
        val failed =
            JellyseerrRecommendationsState.Ready(
                mapOf(JellyseerrRecommendationRail.TRENDS to failedRail),
            )
        assertIs<TvDiscoverAvailability.Failure>(tvDiscoverAvailability(failed))

        val successfulRail =
            recommendationRail(
                JellyseerrRecommendationRail.POPULAR_MOVIES,
                items = listOf(seerrItem("Available movie")),
            )
        val partial =
            JellyseerrRecommendationsState.Ready(
                mapOf(
                    JellyseerrRecommendationRail.TRENDS to failedRail,
                    JellyseerrRecommendationRail.POPULAR_MOVIES to successfulRail,
                ),
            )
        val content = assertIs<TvDiscoverAvailability.Content>(tvDiscoverAvailability(partial))
        assertTrue(content.hasRailFailures)
        assertEquals(partial, content.state)
    }

    @Test
    fun discoverShowsLoadingWhenRetryHasClearedErrorsButNoItemsAreVisible() {
        val loading =
            JellyseerrRecommendationsState.Ready(
                mapOf(
                    JellyseerrRecommendationRail.TRENDS to
                        recommendationRail(JellyseerrRecommendationRail.TRENDS, isLoading = true),
                ),
            )

        assertIs<TvDiscoverAvailability.Loading>(tvDiscoverAvailability(loading))
    }

    private fun recommendationRail(
        rail: JellyseerrRecommendationRail,
        items: List<JellyseerrSearchItem> = emptyList(),
        isLoading: Boolean = false,
        errorMessage: String? = null,
    ) = JellyseerrRecommendationRailState(
        rail = rail,
        items = items,
        isLoading = isLoading,
        errorMessage = errorMessage,
        canLoadMore = false,
        nextPage = 2,
        lastUpdated = null,
        isStale = false,
    )

    private fun ready(
        query: String,
        results: List<JellyseerrSearchItem> = emptyList(),
        isSearching: Boolean = false,
        message: JellyseerrMessage? = null,
    ) = JellyseerrRequestsState.Ready(
        filter = JellyseerrRequestFilter.ALL,
        requests = emptyList(),
        counts = null,
        query = query,
        searchResults = results,
        isSearching = isSearching,
        isRefreshing = false,
        isPerformingAction = false,
        message = message,
        isAdmin = false,
        lastUpdated = null,
        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
        capabilities = JellyseerrRequestCapabilities.NONE,
    )

    private fun searchFailure(query: String) =
        JellyseerrMessage(
            id = 1L,
            kind = JellyseerrMessageKind.ERROR,
            code = JellyseerrMessageCode.SearchFailed,
            subject = query,
        )

    private fun seerrItem(title: String) =
        JellyseerrSearchItem(
            tmdbId = 1,
            mediaType = JellyseerrMediaType.MOVIE,
            title = title,
            overview = null,
            releaseYear = null,
            posterPath = null,
            backdropPath = null,
            mediaInfoId = null,
            tvdbId = null,
            availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
            requests = emptyList(),
        )

    private fun item(id: String) =
        JellyfinItem(
            id = id,
            libraryId = "library",
            name = id,
            sortName = null,
            overview = null,
            type = "Movie",
            mediaType = "Video",
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
}
