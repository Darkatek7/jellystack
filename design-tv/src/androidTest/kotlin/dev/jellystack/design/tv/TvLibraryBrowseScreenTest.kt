package dev.jellystack.design.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvLibraryBrowseScreenTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun browseKeepsLibraryIdentityVisibleWhenAMovieIsFocused() {
        val recent = item("recent", "Recent movie")
        composeRule.setContent {
            JellystackTvTheme {
                TvLibraryScreen(
                    route = TvRoute.Library("library", "Movies"),
                    state =
                        JellyfinHomeState(
                            selectedLibraryId = "library",
                            recentMovies = listOf(recent),
                        ),
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    onSelectLibrary = {},
                    onOpenItem = {},
                    onOpenContainer = {},
                    onLoadMore = {},
                    onRetry = {},
                    homeSections = HomeSectionsState.Unavailable,
                    collectionType = "movies",
                    rememberedQuery = LibraryBrowseQuery.DEFAULT,
                    onModeChanged = {},
                    onQueryChanged = {},
                    cinematicModesEnabled = true,
                )
            }
        }

        composeRule
            .onNodeWithTag("cinematic-card-recent-recent")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.onNodeWithText("Movies").assertIsDisplayed()
    }

    @Test
    fun browseEndsWithAnAllTitlesSegmentThatOpensTheFilterableGrid() {
        val recent = item("recent", "Recent")
        val titles = (0 until 12).map { item("title-$it", "Title $it") }
        var route by mutableStateOf(TvRoute.Library("library", "Movies"))
        var state by
            mutableStateOf(
                JellyfinHomeState(
                    selectedLibraryId = "library",
                    recentMovies = listOf(recent),
                    libraryItems = titles,
                ),
            )
        val opened = mutableListOf<String>()
        composeRule.setContent {
            JellystackTvTheme {
                TvLibraryScreen(
                    route = route,
                    state = state,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    onSelectLibrary = {},
                    onOpenItem = { opened += it.id },
                    onOpenContainer = {},
                    onLoadMore = {},
                    onRetry = {},
                    homeSections = HomeSectionsState.Unavailable,
                    collectionType = "movies",
                    rememberedQuery = LibraryBrowseQuery.DEFAULT,
                    onModeChanged = { route = route.copy(mode = it) },
                    onQueryChanged = { state = state.copy(libraryBrowseQuery = it) },
                    cinematicModesEnabled = true,
                )
            }
        }

        val browseCard = composeRule.onNodeWithTag("cinematic-card-recent-recent")
        browseCard.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        browseCard.performClick()
        composeRule.runOnIdle { assertEquals(listOf("recent"), opened) }

        // The library's last row is the "All titles" segment; Down reaches it and OK opens the grid.
        browseCard.performKeyInput { pressKey(Key.DirectionDown) }
        val allTitlesSegment =
            composeRule.onNodeWithTag("cinematic-card-$TV_LIBRARY_ALL_TITLES_ROW-$TV_LIBRARY_ALL_TITLES_CARD")
        allTitlesSegment.assertIsFocused().performClick()
        composeRule.runOnIdle { assertEquals(TvLibraryMode.ALL_TITLES, route.mode) }

        composeRule.onNodeWithTag("tv-library-browse-title").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-library-query-controls").assertIsDisplayed()
        val allTitlesCard = composeRule.onNodeWithTag("tv-library-all-title-title-0")
        allTitlesCard.assertIsDisplayed().performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        // Sort opens the panel; picking a sort field there changes the query.
        composeRule.onNodeWithTag("tv-library-query-sort").performClick()
        composeRule.onNodeWithTag("tv-library-filter-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-library-panel-sort-DATE_ADDED").performClick()
        composeRule.runOnIdle { assertFalse(state.libraryBrowseQuery.isDefault) }

        composeRule.runOnIdle { route = route.copy(mode = TvLibraryMode.BROWSE) }
        composeRule.onNodeWithTag("cinematic-card-recent-recent").assertIsDisplayed()
    }

    @Test
    fun allTitlesGridFocusesTheFirstCardWhenTheItemsArriveAfterTheFirstFrame() {
        val route = TvRoute.Library("library", "Movies", mode = TvLibraryMode.ALL_TITLES)
        val routeKey = route.focusRouteKey(emptyList())
        var state by mutableStateOf(JellyfinHomeState(selectedLibraryId = "library"))
        composeRule.setContent {
            val coordinator =
                remember { TvFocusCoordinator<FocusRequester>(awaitFocusFrame = { androidx.compose.runtime.withFrameNanos { } }) }
            val entryFocus = remember { FocusRequester() }
            val holder = remember { TvAppStateHolder() }
            val sessions =
                remember(coordinator) {
                    TvRootFocusSessions(
                        content = TvSemanticFocusRestorationSession(null, coordinator.currentInteractionRevision),
                        rail = TvSemanticFocusRestorationSession(null, coordinator.currentInteractionRevision),
                    )
                }
            TvRootFocusRestoration(
                focusCoordinator = coordinator,
                appStateHolder = holder,
                focus =
                    TvRootFocusState(
                        currentRoute = route,
                        routeKey = routeKey,
                        showRail = true,
                        railExpanded = false,
                        isForeground = true,
                        contentAuthoritativelyLoaded = !state.isLibraryLoading && tvLibraryContentDetermined(state),
                    ),
                sessions = sessions,
            )
            CompositionLocalProvider(
                LocalTvScreenEntryFocusRequester provides entryFocus,
                LocalTvFocusContext provides TvFocusContext(coordinator, routeKey, holder.focusMemory),
            ) {
                JellystackTvTheme {
                    TvLibraryAllTitles(
                        route = route,
                        state = state,
                        strings = TvStrings.current(AppLanguage.ENGLISH),
                        focusMemory = holder.focusMemory,
                        collectionType = "movies",
                        onQueryChanged = {},
                        onJumpToLetter = {},
                        onLetterJumpHandled = {},
                        onOpenItem = {},
                        onOpenContainer = {},
                        onLoadMore = {},
                        onRetry = {},
                        modifier = Modifier,
                    )
                }
            }
        }

        // The header is composed first with an empty grid; the first card must still win once the items arrive.
        composeRule.runOnIdle { state = state.copy(libraryItems = listOf(item("title-0", "Title 0"), item("title-1", "Title 1"))) }
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithTag("tv-library-all-title-title-0").assertIsFocused() }.isSuccess
        }
        composeRule.onNodeWithTag("tv-library-query-sort").assertIsNotFocused()
    }

    private fun item(
        id: String,
        name: String,
    ) = JellyfinItem(
        id = id,
        libraryId = "library",
        name = name,
        sortName = name,
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
        productionYear = 2024,
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

private const val TEST_TIMEOUT_MS = 5_000L
