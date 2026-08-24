package dev.jellystack.design.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvSearchInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backLeavesEditingWithoutClearingQueryAndCenterReopensIt() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        var session by mutableStateOf(TvSearchSessionState(query = "dune"))
        composeRule.setContent {
            JellystackTvTheme {
                TvSearchScreen(
                    sessionState = session,
                    jellyfinState = TvJellyfinSearchState.Empty("dune"),
                    requestsState = JellyseerrRequestsState.MissingServer,
                    homeState = JellyfinHomeState(),
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onQueryChanged = { session = session.copy(query = it) },
                    onSourceChanged = { session = session.copy(source = it) },
                    onEnterEditMode = { session = session.copy(mode = TvSearchMode.EDIT) },
                    onEnterBrowseMode = { session = session.copy(mode = TvSearchMode.BROWSE) },
                    onRetryJellyfin = {},
                    onRetrySeerr = {},
                    onJellyfinItem = {},
                    onSeerrItem = {},
                )
            }
        }

        composeRule.waitUntil {
            runCatching {
                composeRule.onNodeWithTag("tv-search-query").assertIsFocused()
                true
            }.getOrDefault(false)
        }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil { session.mode == TvSearchMode.BROWSE }
        composeRule.onNodeWithTag("tv-search-source-all").assertIsFocused()
        assertEquals("dune", session.query)

        composeRule.onNodeWithTag("tv-search-source-all").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("tv-search-query").assertIsFocused()
        composeRule.onNodeWithTag("tv-search-query").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil { session.mode == TvSearchMode.EDIT }

        assertEquals("dune", session.query)
    }

    @Test
    fun firstSearchResultKeepsItsFocusBoundsInsideTheSafeInset() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val item = jellyfinItem("dune", "Dune")
        composeRule.setContent {
            JellystackTvTheme {
                TvSearchScreen(
                    sessionState =
                        TvSearchSessionState(
                            query = "dune",
                            source = TvSearchSource.JELLYFIN,
                            mode = TvSearchMode.BROWSE,
                        ),
                    jellyfinState = TvJellyfinSearchState.Results("dune", listOf(item)),
                    requestsState = JellyseerrRequestsState.MissingServer,
                    homeState = JellyfinHomeState(),
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onQueryChanged = {},
                    onRetryJellyfin = {},
                    onRetrySeerr = {},
                    onJellyfinItem = {},
                    onSeerrItem = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val left =
            composeRule
                .onNodeWithContentDescription("Dune")
                .getUnclippedBoundsInRoot()
                .left.value
        assertTrue("First result must leave at least 48dp for its focus halo; left=$left", left >= 48f)
    }

    @Test
    fun partialDiscoverFailureIsInlineAndCannotOwnRetryFocus() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val failed = recommendationRail(JellyseerrRecommendationRail.TRENDS, error = "offline")
        val populated =
            recommendationRail(
                JellyseerrRecommendationRail.POPULAR_MOVIES,
                items = listOf(seerrItem("Available movie")),
            )
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations =
                        JellyseerrRecommendationsState.Ready(
                            mapOf(
                                JellyseerrRecommendationRail.TRENDS to failed,
                                JellyseerrRecommendationRail.POPULAR_MOVIES to populated,
                            ),
                        ),
                    requests = JellyseerrRequestsState.MissingServer,
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onItem = {},
                    onConnectSeerr = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(strings.discoverLoadFailed).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Available movie").assertExists()
    }

    private fun recommendationRail(
        rail: JellyseerrRecommendationRail,
        items: List<JellyseerrSearchItem> = emptyList(),
        error: String? = null,
    ) = JellyseerrRecommendationRailState(
        rail = rail,
        items = items,
        isLoading = false,
        errorMessage = error,
        canLoadMore = false,
        nextPage = 2,
        lastUpdated = null,
        isStale = false,
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

    private fun jellyfinItem(
        id: String,
        name: String,
    ) = JellyfinItem(
        id = id,
        libraryId = "library",
        name = name,
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
