package dev.jellystack.design.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvCinematicSearchDiscoverTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun searchResultCardOpensItsDetails() {
        val item = jellyfinItem("dune", "Dune")
        var details = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvSearchScreen(
                    searchState = TvSearchUiState.completed("dune", jellyfin = listOf(item)),
                    homeState = JellyfinHomeState(),
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    actions =
                        testSearchActions(
                            onQueryChanged = {},
                            onRetryJellyfin = {},
                            onRetrySeerr = {},
                            onJellyfinItem = { details += 1 },
                            onSeerrItem = {},
                        ),
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Dune")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, details) }
    }

    @Test
    fun populatedDiscoverUsesCinematicRowsAndKeepsPartialFailureInline() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val item = seerrItem("Dune")
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations =
                        JellyseerrRecommendationsState.Ready(
                            mapOf(
                                JellyseerrRecommendationRail.TRENDS to rail(items = listOf(item)),
                                JellyseerrRecommendationRail.POPULAR_MOVIES to rail(error = "offline"),
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

        composeRule.onNodeWithTag("cinematic-card-discover-trends-movie:1").assertExists()
        composeRule.onNodeWithTag("cinematic-action-strip").assertExists()
        composeRule.onNodeWithTag("cinematic-action-play").assertExists()
        composeRule.onNodeWithTag("cinematic-action-details").assertExists()
        composeRule.onNodeWithContentDescription(strings.discoverLoadFailed).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).assertDoesNotExist()
    }

    @Test
    fun discoverVerticalMoveFromSecondCardTargetsFirstCardInNextRow() {
        val firstRow = listOf(seerrItem("First", 1), seerrItem("Second", 2))
        val secondRow = listOf(seerrItem("Third", 3), seerrItem("Fourth", 4))
        composeRule.setContent {
            JellystackTvTheme {
                val focusCoordinator = remember { TvFocusCoordinator<FocusRequester>() }
                val focusMemory = remember { TvFocusMemory() }
                TvRouteFocusScope(focusCoordinator, "discover", focusMemory) {
                    TvDiscoverScreen(
                        recommendations =
                            JellyseerrRecommendationsState.Ready(
                                mapOf(
                                    JellyseerrRecommendationRail.TRENDS to
                                        rail(JellyseerrRecommendationRail.TRENDS, firstRow),
                                    JellyseerrRecommendationRail.POPULAR_MOVIES to
                                        rail(JellyseerrRecommendationRail.POPULAR_MOVIES, secondRow),
                                ),
                            ),
                        requests = JellyseerrRequestsState.MissingServer,
                        strings = TvStrings.current(AppLanguage.ENGLISH),
                        focusMemory = focusMemory,
                        onItem = {},
                        onConnectSeerr = {},
                        onRetry = {},
                    )
                }
            }
        }

        val second = composeRule.onNodeWithTag("cinematic-card-discover-trends-movie:2")
        second.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        second.performKeyInput { pressKey(Key.DirectionDown) }

        composeRule
            .onNodeWithTag("cinematic-card-discover-popular_movies-movie:3")
            .assertIsFocused()
    }

    @Test
    fun leftFromFirstDiscoverCardRequestsNavigationRail() {
        var railOpenRequests = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalTvNavigationRailOpener provides { railOpenRequests += 1 }) {
                JellystackTvTheme {
                    TvDiscoverScreen(
                        recommendations =
                            JellyseerrRecommendationsState.Ready(
                                mapOf(
                                    JellyseerrRecommendationRail.TRENDS to
                                        rail(items = listOf(seerrItem("First", 1), seerrItem("Second", 2))),
                                ),
                            ),
                        requests = JellyseerrRequestsState.MissingServer,
                        strings = TvStrings.current(AppLanguage.ENGLISH),
                        focusMemory = remember { TvFocusMemory() },
                        onItem = {},
                        onConnectSeerr = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("cinematic-card-discover-trends-movie:1")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(1, railOpenRequests) }
    }

    @Test
    fun oneHundredDirectionalActionsNeverActivateOrLoseFocus() {
        val items = (1..8).map { jellyfinItem("item-$it", "Item $it") }
        var activations = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvSearchScreen(
                    searchState = TvSearchUiState.completed("items", jellyfin = items),
                    homeState = JellyfinHomeState(),
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    actions =
                        testSearchActions(
                            onQueryChanged = {},
                            onRetryJellyfin = {},
                            onRetrySeerr = {},
                            onJellyfinItem = { activations += 1 },
                            onSeerrItem = {},
                        ),
                )
            }
        }
        val first = composeRule.onNodeWithContentDescription("Item 1")
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        first.performKeyInput {
            repeat(50) {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionLeft)
            }
        }

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(0, activations) }
    }

    private fun rail(
        rail: JellyseerrRecommendationRail = JellyseerrRecommendationRail.TRENDS,
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

    private fun seerrItem(
        title: String,
        tmdbId: Int = 1,
    ) = JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = JellyseerrMediaType.MOVIE,
        title = title,
        overview = null,
        releaseYear = "2021",
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
