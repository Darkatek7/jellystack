package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.JellyfinHomeState
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
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionButtonSupportsDpadAndTvMinimumTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton("Play", { clicks += 1 })
            }
        }

        composeRule
            .onNodeWithText("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun primaryActionAndSelectedChoiceExposeDifferentSemantics() {
        composeRule.setContent {
            JellystackTvTheme {
                Column {
                    TvActionButton("Primary", {}, primary = true)
                    TvActionButton("Choice", {}, primary = true, selected = true)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Primary").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Choice").assertIsSelected()
    }

    @Test
    fun headingsTogglesAndStatusExposeTvAccessibilitySemantics() {
        composeRule.setContent {
            JellystackTvTheme {
                Column {
                    TvSectionTitle("Heading")
                    TvPlayerOptionRow(
                        icon = Icons.Default.HighQuality,
                        title = "Stats",
                        summary = "On",
                        selected = true,
                        checked = true,
                        onClick = {},
                    )
                    TvStatusAnchor("Loading")
                }
            }
        }

        composeRule.onNodeWithText("Heading").assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithContentDescription("Stats, On").assertIsOn()
        composeRule
            .onNodeWithContentDescription("Loading")
            .assert(SemanticsMatcher.expectValue(TvStatusKey, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun homeVerticalFocusInterceptsDownButLeavesHorizontalNavigationUntouched() {
        var direction: TvHomeVerticalDirection? = null
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton(
                    label = "Focus target",
                    onClick = {},
                    modifier = Modifier.tvHomeVerticalFocus { direction = it },
                )
            }
        }

        composeRule
            .onNodeWithText("Focus target")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(null, direction) }

        composeRule
            .onNodeWithText("Focus target")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.runOnIdle { assertEquals(TvHomeVerticalDirection.DOWN, direction) }
    }

    @Test
    fun tvThemeProvidesReadableDefaultContentColor() {
        var observedColor = Color.Unspecified

        composeRule.setContent {
            JellystackTvTheme {
                observedColor = LocalContentColor.current
            }
        }

        composeRule.runOnIdle { assertEquals(TvText, observedColor) }
    }

    @Test
    fun sectionTitleUsesReadableForegroundColor() {
        val layoutResults = mutableListOf<TextLayoutResult>()
        composeRule.setContent {
            JellystackTvTheme {
                TvSectionTitle("Section")
            }
        }

        composeRule.onNodeWithText("Section").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
            getResults(layoutResults)
        }
        assertEquals(
            TvText,
            layoutResults
                .single()
                .layoutInput.style.color,
        )
    }

    @Test
    fun playerIconButtonIsDpadClickableAndUsesAnAccessibleDescription() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerIconButton(
                    icon = Icons.Default.PlayArrow,
                    description = "Play",
                    onClick = { clicks += 1 },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun playerHeaderDoesNotExposeADuplicateMoreAction() {
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerHeader(
                    primaryTitle = "Fena: Pirate Princess",
                    secondaryTitle = "S1 · E1 · Memories",
                    backDescription = "Back",
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onAllNodesWithContentDescription("More").assertCountEquals(0)
    }

    @Test
    fun playerOptionRowShowsCurrentValueAndSelectedState() {
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerOptionRow(
                    icon = Icons.Default.HighQuality,
                    title = "Streaming quality",
                    summary = "Automatic · Adaptive",
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Streaming quality").assertExists()
        composeRule.onNodeWithText("Automatic · Adaptive").assertExists()
        composeRule.onNodeWithContentDescription("Streaming quality, Automatic · Adaptive").assertIsSelected()
    }

    @Test
    fun landscapeMediaCardIsSixteenByNineAndKeepsBoundsWhenFocused() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            JellystackTvTheme {
                TvMediaCard(
                    title = "Discover item",
                    imageUrl = null,
                    onClick = {},
                    format = TvMediaCardFormat.LANDSCAPE,
                )
            }
        }

        val card = composeRule.onNodeWithContentDescription("Discover item")
        val before = card.getUnclippedBoundsInRoot()
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(500)
        val focused = card.getUnclippedBoundsInRoot()

        assertEquals(232f, (before.right - before.left).value, 1f)
        assertEquals(187f, (before.bottom - before.top).value, 1f)
        assertEquals((before.right - before.left).value, (focused.right - focused.left).value, 0.1f)
        assertEquals((before.bottom - before.top).value, (focused.bottom - focused.top).value, 0.1f)
    }

    @Test
    fun castPortraitMediaCardIsTwoByThreeAndKeepsBoundsWhenFocused() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            JellystackTvTheme {
                TvMediaCard(
                    title = "Cast person",
                    imageUrl = null,
                    onClick = null,
                    format = TvMediaCardFormat.CAST_PORTRAIT,
                )
            }
        }

        val card = composeRule.onNodeWithContentDescription("Cast person")
        val before = card.getUnclippedBoundsInRoot()
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(500)
        val focused = card.getUnclippedBoundsInRoot()

        assertEquals(2f / 3f, (before.right - before.left).value / (before.bottom - before.top).value, 0.05f)
        assertEquals((before.right - before.left).value, (focused.right - focused.left).value, 0.1f)
        assertEquals((before.bottom - before.top).value, (focused.bottom - focused.top).value, 0.1f)
    }

    @Test
    fun upcomingDiscoverRailUsesArtworkPlusOpaqueMetadataBand() {
        val item =
            JellyseerrSearchItem(
                tmdbId = 42,
                mediaType = JellyseerrMediaType.TV,
                title = "Upcoming item",
                overview = null,
                releaseYear = "2026",
                posterPath = null,
                backdropPath = null,
                mediaInfoId = null,
                tvdbId = null,
                availability = JellyseerrMediaAvailability(null, null),
                requests = emptyList(),
            )
        val rail =
            JellyseerrRecommendationRailState(
                rail = JellyseerrRecommendationRail.UPCOMING_SHOWS,
                items = listOf(item),
                isLoading = false,
                errorMessage = null,
                canLoadMore = false,
                nextPage = 2,
                lastUpdated = null,
                isStale = false,
            )
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations =
                        JellyseerrRecommendationsState.Ready(
                            mapOf(JellyseerrRecommendationRail.UPCOMING_SHOWS to rail),
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

        val bounds = composeRule.onNodeWithContentDescription("Upcoming item, 2026").getUnclippedBoundsInRoot()
        val width = (bounds.right - bounds.left).value
        val height = (bounds.bottom - bounds.top).value
        assertTrue("Discover cards should remain landscape", width > height)
        assertEquals(232f / 187f, width / height, 0.05f)
    }

    @Test
    fun searchKeepsJellyfinResultsVisibleWhenSeerrFailsAndRetriesOnlySeerr() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        var seerrRetries = 0
        var session by mutableStateOf(TvSearchSessionState())
        val item = jellyfinItem("jellyfin-result", "Jellyfin result")
        composeRule.setContent {
            JellystackTvTheme {
                TvSearchScreen(
                    sessionState = session,
                    jellyfinState = TvJellyfinSearchState.Results("query", listOf(item)),
                    requestsState = seerrSearchFailure("query"),
                    homeState = JellyfinHomeState(),
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onQueryChanged = { session = session.copy(query = it) },
                    onRetryJellyfin = {},
                    onRetrySeerr = { seerrRetries += 1 },
                    onJellyfinItem = {},
                    onSeerrItem = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("query")
        composeRule.onNodeWithContentDescription("Jellyfin result").assertExists()
        composeRule.onNodeWithContentDescription(strings.seerrSearchFailed).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).performClick()
        composeRule.runOnIdle { assertEquals(1, seerrRetries) }
    }

    @Test
    fun searchRetryRestoresFocusToTheQueryAfterTheRetryActionDisappears() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        var requestsState by mutableStateOf<JellyseerrRequestsState>(seerrSearchFailure("query"))
        var session by mutableStateOf(TvSearchSessionState())
        val focusCoordinator = TvFocusCoordinator<androidx.compose.ui.focus.FocusRequester>()
        composeRule.setContent {
            JellystackTvTheme {
                TvRouteFocusScope(focusCoordinator, "search") {
                    TvSearchScreen(
                        sessionState = session,
                        jellyfinState = TvJellyfinSearchState.Empty("query"),
                        requestsState = requestsState,
                        homeState = JellyfinHomeState(),
                        strings = strings,
                        focusMemory = remember { TvFocusMemory() },
                        onQueryChanged = { session = session.copy(query = it) },
                        onRetryJellyfin = {},
                        onRetrySeerr = {
                            requestsState = seerrReady(query = "query", isSearching = true)
                        },
                        onJellyfinItem = {},
                        onSeerrItem = {},
                    )
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("query")
        composeRule
            .onNodeWithContentDescription(strings.retry)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun discoverOnlyOffersLocalizedConnectActionWhenServerIsMissing() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations = JellyseerrRecommendationsState.MissingServer,
                    requests = JellyseerrRequestsState.MissingServer,
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onItem = {},
                    onConnectSeerr = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(strings.connectSeerrPrompt).assertExists()
        composeRule.onNodeWithContentDescription(strings.connectSeerr).assertExists()
        composeRule.onNodeWithText("${strings.settings}: Seerr").assertDoesNotExist()
    }

    @Test
    fun discoverFailureShowsRetryInsteadOfConnect() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        var retries = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations = JellyseerrRecommendationsState.Error("offline"),
                    requests = JellyseerrRequestsState.MissingServer,
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onItem = {},
                    onConnectSeerr = {},
                    onRetry = { retries += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription(strings.discoverLoadFailed).assertExists()
        composeRule.onNodeWithContentDescription(strings.connectSeerr).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(strings.retry).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun discoverKeepsSuccessfulRailsVisibleAlongsideInlineRailFailure() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val item = seerrItem(42, "Available movie")
        val recommendations =
            JellyseerrRecommendationsState.Ready(
                mapOf(
                    JellyseerrRecommendationRail.TRENDS to
                        recommendationRail(JellyseerrRecommendationRail.TRENDS, errorMessage = "offline"),
                    JellyseerrRecommendationRail.POPULAR_MOVIES to
                        recommendationRail(JellyseerrRecommendationRail.POPULAR_MOVIES, items = listOf(item)),
                ),
            )
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations = recommendations,
                    requests = JellyseerrRequestsState.MissingServer,
                    strings = strings,
                    focusMemory = remember { TvFocusMemory() },
                    onItem = {},
                    onConnectSeerr = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Available movie").assertExists()
        composeRule.onNodeWithContentDescription(strings.discoverLoadFailed).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).assertDoesNotExist()
        composeRule.onNodeWithText(strings.noResults).assertDoesNotExist()
    }

    @Test
    fun discoverPartialFailureRefreshDoesNotStealItemFocus() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val oldItem = seerrItem(42, "Available movie")
        var recommendations by
            mutableStateOf<JellyseerrRecommendationsState>(
                JellyseerrRecommendationsState.Ready(
                    mapOf(
                        JellyseerrRecommendationRail.TRENDS to
                            recommendationRail(JellyseerrRecommendationRail.TRENDS, errorMessage = "offline"),
                        JellyseerrRecommendationRail.POPULAR_MOVIES to
                            recommendationRail(JellyseerrRecommendationRail.POPULAR_MOVIES, items = listOf(oldItem)),
                    ),
                ),
            )
        val focusCoordinator = TvFocusCoordinator<androidx.compose.ui.focus.FocusRequester>()
        composeRule.setContent {
            JellystackTvTheme {
                TvRouteFocusScope(focusCoordinator, "discover") {
                    TvDiscoverScreen(
                        recommendations = recommendations,
                        requests = JellyseerrRequestsState.MissingServer,
                        strings = strings,
                        focusMemory = remember { TvFocusMemory() },
                        onItem = {},
                        onConnectSeerr = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Available movie")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.runOnIdle {
            recommendations =
                JellyseerrRecommendationsState.Ready(
                    mapOf(
                        JellyseerrRecommendationRail.TRENDS to
                            recommendationRail(JellyseerrRecommendationRail.TRENDS, errorMessage = "still offline"),
                        JellyseerrRecommendationRail.POPULAR_MOVIES to
                            recommendationRail(
                                JellyseerrRecommendationRail.POPULAR_MOVIES,
                                items = listOf(oldItem),
                            ),
                    ),
                )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Available movie").assertIsFocused()
    }

    @Test
    fun discoverRetryRestoresFocusFromLoadingToContent() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val item = seerrItem(42, "Recovered movie")
        var recommendations by
            mutableStateOf<JellyseerrRecommendationsState>(JellyseerrRecommendationsState.Error("offline"))
        val focusCoordinator = TvFocusCoordinator<androidx.compose.ui.focus.FocusRequester>()
        composeRule.setContent {
            JellystackTvTheme {
                TvRouteFocusScope(focusCoordinator, "discover") {
                    TvDiscoverScreen(
                        recommendations = recommendations,
                        requests = JellyseerrRequestsState.MissingServer,
                        strings = strings,
                        focusMemory = remember { TvFocusMemory() },
                        onItem = {},
                        onConnectSeerr = {},
                        onRetry = { recommendations = JellyseerrRecommendationsState.Loading },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(strings.retry)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(strings.loading).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).assertDoesNotExist()
        composeRule.runOnIdle {
            recommendations =
                JellyseerrRecommendationsState.Ready(
                    mapOf(
                        JellyseerrRecommendationRail.POPULAR_MOVIES to
                            recommendationRail(
                                JellyseerrRecommendationRail.POPULAR_MOVIES,
                                items = listOf(item),
                            ),
                    ),
                )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Recovered movie").assertIsFocused()
    }

    @Test
    fun discoverRetryRestoresFocusFromLoadingToFailure() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        var recommendations by
            mutableStateOf<JellyseerrRecommendationsState>(JellyseerrRecommendationsState.Error("offline"))
        val focusCoordinator = TvFocusCoordinator<androidx.compose.ui.focus.FocusRequester>()
        composeRule.setContent {
            JellystackTvTheme {
                TvRouteFocusScope(focusCoordinator, "discover") {
                    TvDiscoverScreen(
                        recommendations = recommendations,
                        requests = JellyseerrRequestsState.MissingServer,
                        strings = strings,
                        focusMemory = remember { TvFocusMemory() },
                        onItem = {},
                        onConnectSeerr = {},
                        onRetry = { recommendations = JellyseerrRecommendationsState.Loading },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(strings.retry)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(strings.loading).assertExists()
        composeRule.onNodeWithContentDescription(strings.retry).assertDoesNotExist()

        composeRule.runOnIdle {
            recommendations = JellyseerrRecommendationsState.Error("still offline")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(strings.retry).assertIsFocused()
    }
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

private fun seerrReady(
    query: String,
    isSearching: Boolean,
) = JellyseerrRequestsState.Ready(
    filter = JellyseerrRequestFilter.ALL,
    requests = emptyList(),
    counts = null,
    query = query,
    searchResults = emptyList(),
    isSearching = isSearching,
    isRefreshing = false,
    isPerformingAction = false,
    message = null,
    isAdmin = false,
    lastUpdated = null,
    languageProfiles = JellyseerrLanguageProfiles.EMPTY,
    capabilities = JellyseerrRequestCapabilities.NONE,
)

private fun seerrItem(
    id: Int,
    title: String,
) = JellyseerrSearchItem(
    tmdbId = id,
    mediaType = JellyseerrMediaType.MOVIE,
    title = title,
    overview = null,
    releaseYear = null,
    posterPath = null,
    backdropPath = null,
    mediaInfoId = null,
    tvdbId = null,
    availability = JellyseerrMediaAvailability(null, null),
    requests = emptyList(),
)

private fun seerrSearchFailure(query: String) =
    JellyseerrRequestsState.Ready(
        filter = JellyseerrRequestFilter.ALL,
        requests = emptyList(),
        counts = null,
        query = query,
        searchResults = emptyList(),
        isSearching = false,
        isRefreshing = false,
        isPerformingAction = false,
        message =
            JellyseerrMessage(
                id = 1L,
                kind = JellyseerrMessageKind.ERROR,
                code = JellyseerrMessageCode.SearchFailed,
                subject = query,
            ),
        isAdmin = false,
        lastUpdated = null,
        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
        capabilities = JellyseerrRequestCapabilities.NONE,
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
