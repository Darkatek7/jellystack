package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryLoadErrorKind
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class TvHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recreatedHomeRestoresExactOffscreenCard() {
        lateinit var engine: AndroidPlayerEngine
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val preferred = androidx.compose.runtime.mutableStateOf<String?>(tvHomeCardTargetId("plugin:row-4", "row-4-item-10"))
        val sections =
            HomeSectionsState.Ready(
                sections =
                    (0..4).map { row ->
                        HomeSection(
                            id = "row-$row",
                            title = "Row $row",
                            viewMode = HomeSectionViewMode.LANDSCAPE,
                            displayTitle = true,
                            showDetailsMenu = false,
                            items = (0..11).map { column -> homeSectionItem(item("row-$row-item-$column", "Row $row item $column")) },
                        )
                    },
                imageBaseUrl = "",
                imageAccessToken = "",
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            RestorableHomeHost(shown.value, preferred.value, sections, engine)
        }

        composeRule.onNodeWithContentDescription("Row 4 item 10").assertIsFocused()
        composeRule.runOnIdle {
            preferred.value = null
            shown.value = false
        }
        composeRule.runOnIdle { shown.value = true }
        composeRule.onNodeWithContentDescription("Row 4 item 10").assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun verticalMoveFromOffscreenColumnTargetsFirstItemInNextRow() {
        lateinit var engine: AndroidPlayerEngine
        val preferred = androidx.compose.runtime.mutableStateOf<String?>(tvHomeCardTargetId("plugin:row-0", "row-0-item-10"))
        val sections =
            HomeSectionsState.Ready(
                sections =
                    (0..1).map { row ->
                        HomeSection(
                            id = "row-$row",
                            title = "Row $row",
                            viewMode = HomeSectionViewMode.LANDSCAPE,
                            displayTitle = true,
                            showDetailsMenu = false,
                            items = (0..11).map { column -> homeSectionItem(item("row-$row-item-$column", "Row $row item $column")) },
                        )
                    },
                imageBaseUrl = "",
                imageAccessToken = "",
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            RestorableHomeHost(true, preferred.value, sections, engine)
        }

        composeRule
            .onNodeWithContentDescription("Row 0 item 10")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Row 1 item 0").assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun recreatedLibraryRestoresExactOffscreenGridCard() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val preferred = androidx.compose.runtime.mutableStateOf<String?>(tvLibraryTargetId("library-27"))
        val libraries =
            (0..31).map { index ->
                JellyfinLibrary("library-$index", "Library $index", null, index.toLong(), null)
            }
        composeRule.setContent { RestorableLibraryHost(shown.value, preferred.value, libraries) }

        composeRule.onNodeWithContentDescription("Library 27, 27 items").assertIsFocused()
        composeRule.runOnIdle {
            preferred.value = null
            shown.value = false
        }
        composeRule.runOnIdle { shown.value = true }
        composeRule.onNodeWithContentDescription("Library 27, 27 items").assertIsFocused()
    }

    @Test
    fun recreatedPagedLibraryErrorRestoresOffscreenRetryAndLeftOpensRail() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        val preferred = androidx.compose.runtime.mutableStateOf<String?>(TV_LIBRARY_RETRY_TARGET)
        var railOpenRequests = 0
        var refreshRequests = 0
        var nextPageRequests = 0
        val items = (0..31).map { index -> item("library-item-$index", "Library item $index") }
        composeRule.setContent {
            RestorablePagedLibraryErrorHost(
                shown = shown.value,
                preferredTargetId = preferred.value,
                items = items,
                onOpenRail = { railOpenRequests += 1 },
                errorKind = LibraryLoadErrorKind.NEXT_PAGE,
                onRetry = { refreshRequests += 1 },
                onLoadMore = { nextPageRequests += 1 },
            )
        }

        composeRule.waitUntil(2_000) {
            composeRule.onAllNodes(hasContentDescription("Try again")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithContentDescription("Try again")
            .assertIsFocused()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(0, refreshRequests)
            assertEquals(1, nextPageRequests)
        }
        composeRule.runOnIdle {
            preferred.value = null
            shown.value = false
        }
        composeRule.runOnIdle { shown.value = true }
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodes(hasContentDescription("Try again")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithContentDescription("Try again")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(1, railOpenRequests) }
    }

    @Test
    fun retainedItemsFirstPageErrorRetryDispatchesRefresh() {
        var refreshRequests = 0
        var nextPageRequests = 0
        val items = (0..3).map { index -> item("library-item-$index", "Library item $index") }
        composeRule.setContent {
            RestorablePagedLibraryErrorHost(
                shown = true,
                preferredTargetId = TV_LIBRARY_RETRY_TARGET,
                items = items,
                onOpenRail = {},
                errorKind = LibraryLoadErrorKind.FIRST_PAGE,
                onRetry = { refreshRequests += 1 },
                onLoadMore = { nextPageRequests += 1 },
            )
        }

        composeRule.waitUntil(2_000) {
            composeRule.onAllNodes(hasContentDescription("Try again")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNodeWithContentDescription("Try again")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, refreshRequests)
            assertEquals(0, nextPageRequests)
        }
    }

    @Test
    fun delayedRailCompositionCompletesOnRequestedItem() {
        composeRule.setContent { RailRestorationHarness(expanded = true, selectedAvailable = true, delayedSelected = true) }

        composeRule.waitUntil(2_000) {
            composeRule.onAllNodes(hasContentDescription("Rail selected")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Rail selected").assertIsFocused()
    }

    @Test
    fun unavailableRailSelectionUsesDeterministicHomeFallback() {
        composeRule.setContent { RailRestorationHarness(expanded = true, selectedAvailable = false, delayedSelected = false) }

        composeRule.onNodeWithContentDescription("Rail home").assertIsFocused()
    }

    @Test
    fun closingRailRestoresExactContentTarget() {
        val expanded = androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            RailRestorationHarness(expanded = expanded.value, selectedAvailable = true, delayedSelected = false)
        }

        composeRule.onNodeWithContentDescription("Content exact").assertIsFocused()
        composeRule.runOnIdle { expanded.value = true }
        composeRule.onNodeWithContentDescription("Rail selected").assertIsFocused()
        composeRule.runOnIdle { expanded.value = false }
        composeRule.onNodeWithContentDescription("Content exact").assertIsFocused()
    }

    @Test
    fun loadingHomeStillRendersBrandedHeroAsFirstSlot() {
        lateinit var engine: AndroidPlayerEngine
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(JellyfinHomeState(isHomeLoading = true), HomeSectionsState.Loading, engine)
        }

        composeRule.onAllNodesWithText("Jellystack", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Try again").assertExists()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun recentHeroDoesNotDuplicateCandidatesInASeparateRow() {
        lateinit var engine: AndroidPlayerEngine
        val recent = item("recent", "Recent", dateCreated = (Clock.System.now() - 1.days).toString())
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(recent)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
            )
        }

        composeRule.onAllNodesWithText("New in the last 30 days").assertCountEquals(0)
        composeRule.onAllNodes(cardWithDescription("Recent")).assertCountEquals(0)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun heroAndFirstMediaCardUseRenderedHomeGeometry() {
        lateinit var engine: AndroidPlayerEngine
        val recent = item("recent", "Recent", dateCreated = (Clock.System.now() - 1.days).toString())
        val firstCard = item("first-card", "First media card")
        val section =
            HomeSection(
                id = "first-row",
                title = "First row",
                viewMode = HomeSectionViewMode.LANDSCAPE,
                displayTitle = true,
                showDetailsMenu = false,
                items = listOf(homeSectionItem(firstCard)),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(recent)),
                sections = HomeSectionsState.Ready(listOf(section), "", ""),
                engine = engine,
            )
        }

        val heroBounds = composeRule.onNodeWithTag("tv-home-hero-carousel").getUnclippedBoundsInRoot()
        val firstCardBounds = composeRule.onAllNodes(cardWithDescription("First media card"))[0].getUnclippedBoundsInRoot()
        assertEquals(360f, (heroBounds.bottom - heroBounds.top).value, 0.01f)
        assertEquals(452f, firstCardBounds.top.value, 0.51f)
        assertEquals(tvHomeFirstCardTopDp().toFloat(), firstCardBounds.top.value, 0.51f)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun latestFallbackShowsCarouselPositionWithoutRecentWindowLabel() {
        lateinit var engine: AndroidPlayerEngine
        val first = item("old-first", "Old first", dateCreated = (Clock.System.now() - 40.days).toString())
        val second = item("old-second", "Old second", dateCreated = (Clock.System.now() - 50.days).toString())
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
            )
        }

        composeRule.onAllNodesWithText("01 | 02", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Last 30 days", useUnmergedTree = true).assertCountEquals(0)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun heroReceivesEntryFocusAndRightChangesSlideWithoutLosingFocus() {
        lateinit var engine: AndroidPlayerEngine
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
            )
        }

        val hero = composeRule.onNodeWithTag("tv-home-hero-carousel")
        hero.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Second hero", useUnmergedTree = true).assertCountEquals(1)
        hero.assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun heroActionsAndFirstServerRowFollowTheCompleteVerticalFocusPath() {
        lateinit var engine: AndroidPlayerEngine
        val recent = item("recent", "Recent", dateCreated = (Clock.System.now() - 1.days).toString())
        val rowItem = item("row-item", "First row item")
        val section =
            HomeSection(
                id = "first-row",
                title = "First row",
                viewMode = HomeSectionViewMode.LANDSCAPE,
                displayTitle = true,
                showDetailsMenu = false,
                items = listOf(homeSectionItem(rowItem)),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(recent)),
                sections = HomeSectionsState.Ready(listOf(section), "", ""),
                engine = engine,
            )
        }

        val play = composeRule.onNodeWithContentDescription("Play")
        val details = composeRule.onNodeWithContentDescription("Details")
        val hero = composeRule.onNodeWithTag("tv-home-hero-carousel").assertIsFocused()
        val card = composeRule.onAllNodes(cardWithDescription("First row item"))[0]
        hero.performKeyInput { pressKey(Key.DirectionDown) }
        play.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        details.assertIsFocused()
        assertTrue(play.getUnclippedBoundsInRoot().top < card.getUnclippedBoundsInRoot().top)

        details.performKeyInput { pressKey(Key.DirectionDown) }
        card.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        play.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        hero.assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun unavailableHomeSectionsRoutesHeroActionsToTheFirstDefaultRow() {
        lateinit var engine: AndroidPlayerEngine
        val recent = item("recent", "Recent", dateCreated = (Clock.System.now() - 1.days).toString())
        val continueItem = item("continue", "Continue row item")
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(recent), continueWatching = listOf(continueItem)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-carousel").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Play").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onAllNodes(cardWithDescription("Continue row item"))[0].assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun leftBoundaryOpensNavigationRailAndKeepsFirstCandidateActive() {
        lateinit var engine: AndroidPlayerEngine
        var railOpenRequests = 0
        val playedIds = mutableListOf<String>()
        val detailIds = mutableListOf<String>()
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                onOpenNavigationRail = { railOpenRequests += 1 },
                onPlayItem = { playedIds += it.id },
                onItem = { detailIds += it.id },
            )
        }

        val hero = composeRule.onNodeWithTag("tv-home-hero-carousel").assertIsFocused()
        hero.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        hero.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        hero.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Play").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Details").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle {
            assertEquals(1, railOpenRequests)
            assertEquals(listOf("first"), playedIds)
            assertEquals(listOf("first", "first"), detailIds)
            engine.release()
        }
    }

    @Test
    fun homeSectionRoutesMediaWithItsSourceItemAndCollectionFoldersToLibrary() {
        lateinit var engine: AndroidPlayerEngine
        val media = item("movie", "Home movie")
        val library = item("movies", "Movies").copy(type = "CollectionFolder")
        val openedItems = mutableListOf<JellyfinItem>()
        val openedLibraries = mutableListOf<Pair<String, String>>()
        val section =
            HomeSection(
                id = "home-row",
                title = "Home row",
                viewMode = HomeSectionViewMode.PORTRAIT,
                displayTitle = true,
                showDetailsMenu = false,
                items = listOf(homeSectionItem(media), homeSectionItem(library)),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(),
                sections = HomeSectionsState.Ready(listOf(section), "", ""),
                engine = engine,
                onItem = { openedItems += it },
                onHomeLibrary = { id, title -> openedLibraries += id to title },
            )
        }

        composeRule.onAllNodes(cardWithDescription("Home movie"))[0].performClick()
        composeRule.onAllNodes(cardWithDescription("Movies"))[0].performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(media), openedItems)
            assertEquals(listOf("movies" to "Movies"), openedLibraries)
            engine.release()
        }
    }

    @Test
    fun legacyAutoCycleSettingNeverAdvancesTvSpotlight() {
        lateinit var engine: AndroidPlayerEngine
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        val rowItem = item("row-item", "First row item")
        val section =
            HomeSection(
                id = "row",
                title = "Row",
                viewMode = HomeSectionViewMode.LANDSCAPE,
                displayTitle = true,
                showDetailsMenu = false,
                items = listOf(homeSectionItem(rowItem)),
            )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Ready(listOf(section), "", ""),
                engine = engine,
                provideEntryFocus = false,
            )
        }

        val hero = composeRule.onNodeWithTag("tv-home-hero-carousel")
        hero.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(30_000)
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun playingHeroPreviewIsNotInterruptedByLegacyAutoCycleInterval() {
        lateinit var engine: AndroidPlayerEngine
        val target = TvTrailerPreviewTarget("server", "preview", isEpisode = false, seriesId = null)
        val request = TvTrailerPreviewRequest(TvTrailerPreviewOwner.HERO, target)
        val previewState = androidx.compose.runtime.mutableStateOf<TvTrailerPreviewState>(TvTrailerPreviewState.Playing(request))
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                provideEntryFocus = false,
                trailerPreviewState = previewState.value,
            )
        }

        composeRule.mainClock.advanceTimeBy(30_000)
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun heroRetargetsPreviewToActionItemAndRendersPlayerSurfaceWhilePlaying() {
        lateinit var engine: AndroidPlayerEngine
        val previewTargets = mutableListOf<String>()
        val episode =
            item("episode", "Episode", dateCreated = (Clock.System.now() - 1.days).toString()).copy(
                type = "Episode",
                seriesId = "series",
                seasonId = "season",
                seriesName = "Series",
                parentIndexNumber = 1,
            )
        val previewState =
            TvTrailerPreviewState.Playing(
                TvTrailerPreviewRequest(
                    TvTrailerPreviewOwner.HERO,
                    TvTrailerPreviewTarget("server", "episode", isEpisode = true, seriesId = "series"),
                ),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentShows = listOf(episode)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                trailerPreviewState = previewState,
                onPreviewFocus = { owner, item, _ ->
                    assertEquals(TvTrailerPreviewOwner.HERO, owner)
                    previewTargets += item.id
                },
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-preview-surface", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf("episode"), previewTargets)
            engine.release()
        }
    }

    @Test
    fun leavingHeroClearsItsPreviewBeforeTheFocusedRowCardArmsItsPreview() {
        lateinit var engine: AndroidPlayerEngine
        val previewEvents = mutableListOf<String>()
        val hero = item("hero", "Hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val rowItem = item("row", "Row item")
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(hero), continueWatching = listOf(rowItem)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                onPreviewFocus = { owner, item, _ -> previewEvents += "focus:$owner:${item.id}" },
                onCancelPreview = { owner -> previewEvents += "clear:$owner" },
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-carousel").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onAllNodes(cardWithDescription("Row item"))[0].assertIsFocused()

        composeRule.runOnIdle {
            assertEquals("focus:CARD:row", previewEvents.last())
            engine.release()
        }
    }

    @Test
    fun focusedRowCardPreviewSurvivesHeroTimerExpiry() {
        lateinit var engine: AndroidPlayerEngine
        val previewState = androidx.compose.runtime.mutableStateOf<TvTrailerPreviewState>(TvTrailerPreviewState.Idle)
        val clearedOwners = mutableListOf<TvTrailerPreviewOwner>()
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        val rowItem = item("row", "Row item")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second), continueWatching = listOf(rowItem)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                trailerPreviewState = previewState.value,
                onPreviewFocus = { owner, item, presentationId ->
                    previewState.value =
                        TvTrailerPreviewState.Playing(
                            TvTrailerPreviewRequest(
                                owner,
                                TvTrailerPreviewTarget("server", item.id, isEpisode = false, seriesId = null),
                                presentationId,
                            ),
                        )
                },
                onCancelPreview = { owner ->
                    clearedOwners += owner
                    val current = previewState.value as? TvTrailerPreviewState.Playing
                    if (current?.request?.owner == owner) previewState.value = TvTrailerPreviewState.Idle
                },
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-carousel").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onAllNodes(cardWithDescription("Row item"))[0].assertIsFocused()
        composeRule.runOnIdle { clearedOwners.clear() }

        composeRule.mainClock.advanceTimeBy(6_240)
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle {
            val playing = previewState.value as TvTrailerPreviewState.Playing
            assertEquals(TvTrailerPreviewOwner.CARD, playing.request.owner)
            assertEquals("row", playing.request.target.itemId)
            assertEquals(emptyList<TvTrailerPreviewOwner>(), clearedOwners)
            engine.release()
        }
    }

    @Test
    fun sameItemInHeroAndRowRendersOnlyTheOwnedSurface() {
        lateinit var engine: AndroidPlayerEngine
        val same = item("same", "Same item", dateCreated = (Clock.System.now() - 1.days).toString())
        val target = TvTrailerPreviewTarget("server", same.id, isEpisode = false, seriesId = null)
        val previewState =
            androidx.compose.runtime.mutableStateOf<TvTrailerPreviewState>(
                TvTrailerPreviewState.Playing(
                    TvTrailerPreviewRequest(
                        TvTrailerPreviewOwner.CARD,
                        target,
                        tvHomeCardTargetId("continue", same.id),
                    ),
                ),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(same), continueWatching = listOf(same)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                provideEntryFocus = false,
                trailerPreviewState = previewState.value,
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-preview-surface", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("tv-media-card-preview-surface-same", useUnmergedTree = true).assertExists()

        composeRule.runOnIdle {
            previewState.value =
                TvTrailerPreviewState.Playing(TvTrailerPreviewRequest(TvTrailerPreviewOwner.HERO, target))
        }
        composeRule.onNodeWithTag("tv-home-hero-carousel").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("tv-home-hero-preview-surface", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("tv-media-card-preview-surface-same", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun duplicateCardsRenderPreviewOnlyInOwnedCardInstance() {
        lateinit var engine: AndroidPlayerEngine
        val same = item("same", "Same item", dateCreated = (Clock.System.now() - 1.days).toString())
        val target = TvTrailerPreviewTarget("server", same.id, isEpisode = false, seriesId = null)
        val previewState =
            TvTrailerPreviewState.Playing(
                TvTrailerPreviewRequest(
                    owner = TvTrailerPreviewOwner.CARD,
                    target = target,
                    presentationId = tvHomeCardTargetId("next", same.id),
                ),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            JellystackTvTheme {
                androidx.compose.foundation.layout.Row {
                    listOf("continue", "next").forEach { rowId ->
                        val presentationId = tvHomeCardTargetId(rowId, same.id)
                        TvMediaCard(
                            title = "$rowId ${same.name}",
                            imageUrl = null,
                            onClick = {},
                            previewing = previewState.showsTvMediaCardPreview(same.id, presentationId),
                            previewEngine = engine,
                            previewSurfaceTestTag = "tv-media-card-preview-surface-same",
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(hasTestTag("tv-media-card-preview-surface-same"), useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun manualHeroSlideChangeClearsAndRearmsHeroOwner() {
        lateinit var engine: AndroidPlayerEngine
        val events = mutableListOf<String>()
        val first = item("first", "First hero", dateCreated = (Clock.System.now() - 1.days).toString())
        val second = item("second", "Second hero", dateCreated = (Clock.System.now() - 2.days).toString())
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentMovies = listOf(first, second)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                onPreviewFocus = { owner, item, _ -> events += "focus:$owner:${item.id}" },
                onCancelPreview = { owner -> events += "clear:$owner" },
            )
        }
        composeRule.runOnIdle { events.clear() }

        composeRule.onNodeWithTag("tv-home-hero-carousel").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.runOnIdle {
            assertEquals(listOf("clear:HERO", "focus:HERO:second"), events)
            engine.release()
        }
    }

    @Test
    fun verticalMoveScrollsOffscreenRowBeforeFocusingItsFirstCard() {
        lateinit var engine: AndroidPlayerEngine
        val sections =
            (1..6).map { index ->
                val media = item("local-$index", "Local $index")
                HomeSection(
                    id = "section-$index",
                    title = "Section $index",
                    viewMode = if (index % 2 == 0) HomeSectionViewMode.PORTRAIT else HomeSectionViewMode.LANDSCAPE,
                    displayTitle = true,
                    showDetailsMenu = false,
                    items = listOf(homeSectionItem(media)),
                )
            }
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(),
                sections = HomeSectionsState.Ready(sections, "", ""),
                engine = engine,
            )
        }

        composeRule
            .onNodeWithContentDescription("Play")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        for (index in 1 until 6) {
            composeRule
                .onAllNodes(cardWithDescription("Local $index"))[0]
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        composeRule.onAllNodes(cardWithDescription("Local 6"))[0].assertIsFocused()
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun nonJellyfinMoveCancelsPreviewAndTargetsFirstCardOfNextRow() {
        lateinit var engine: AndroidPlayerEngine
        var previewCancellations = 0
        val externalRow =
            HomeSection(
                id = "external",
                title = "External",
                viewMode = HomeSectionViewMode.LANDSCAPE,
                displayTitle = true,
                showDetailsMenu = false,
                items =
                    listOf(
                        externalHomeSectionItem("external-1", "External 1"),
                        externalHomeSectionItem("external-2", "External 2"),
                    ),
            )
        val localRow =
            HomeSection(
                id = "local",
                title = "Local",
                viewMode = HomeSectionViewMode.PORTRAIT,
                displayTitle = true,
                showDetailsMenu = false,
                items =
                    listOf(
                        homeSectionItem(item("local-1", "Local first")),
                        homeSectionItem(item("local-2", "Local second")),
                    ),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(),
                sections = HomeSectionsState.Ready(listOf(externalRow, localRow), "", ""),
                engine = engine,
                onCancelPreview = { _ -> previewCancellations += 1 },
            )
        }

        composeRule
            .onNodeWithContentDescription("Play")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule
            .onAllNodes(cardWithDescription("External 1"))[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule
            .onAllNodes(cardWithDescription("External 2"))[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule
            .onAllNodes(cardWithDescription("Local first"))[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.runOnIdle {
            assertTrue(previewCancellations >= 2)
            engine.release()
        }
    }

    private fun homeSectionItem(item: JellyfinItem): HomeSectionItem =
        HomeSectionItem(
            id = item.id,
            name = item.name,
            overview = null,
            productionYear = null,
            communityRating = null,
            imageUrl = null,
            jellyfinItem = item,
            action = HomeSectionAction.JELLYFIN,
        )

    private fun cardWithDescription(name: String): SemanticsMatcher =
        hasContentDescription(name) and hasClickAction() and hasTestTag("tv-home-hero-carousel").not()

    private fun externalHomeSectionItem(
        id: String,
        name: String,
    ): HomeSectionItem =
        HomeSectionItem(
            id = id,
            name = name,
            overview = null,
            productionYear = null,
            communityRating = null,
            imageUrl = null,
            jellyfinItem = null,
            action = HomeSectionAction.SEERR,
            seerrTmdbId = id.substringAfterLast('-').toInt(),
            seerrMediaType = "movie",
        )

    private fun item(
        id: String,
        name: String,
        dateCreated: String? = null,
    ): JellyfinItem =
        JellyfinItem(
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
            dateCreated = dateCreated,
        )

    @androidx.compose.runtime.Composable
    private fun RestorableHomeHost(
        shown: Boolean,
        preferredTargetId: String?,
        sections: HomeSectionsState,
        engine: AndroidPlayerEngine,
    ) {
        val coordinator = remember { TvFocusCoordinator<FocusRequester>() }
        val entryFocusRequester = remember { FocusRequester() }
        if (shown) {
            CompositionLocalProvider(
                LocalTvScreenEntryFocusRequester provides entryFocusRequester,
                LocalTvFocusContext provides TvFocusContext(coordinator, "home"),
            ) {
                JellystackTvTheme {
                    TvHomeScreen(
                        state =
                            JellyfinHomeState(
                                recentMovies =
                                    listOf(
                                        item(
                                            "hero",
                                            "Hero",
                                            dateCreated = (Clock.System.now() - 1.days).toString(),
                                        ),
                                    ),
                            ),
                        homeSections = sections,
                        strings = TvStrings.current(AppLanguage.ENGLISH),
                        trailerPreviewState = TvTrailerPreviewState.Idle,
                        focusMemory = remember { TvFocusMemory() },
                        onRefresh = {},
                        onPreviewFocus = { _, _, _ -> },
                        onPreviewBlur = { _, _, _ -> },
                        onCancelPreview = {},
                        trailerPreviewEngine = engine,
                        previewSoundEnabled = false,
                        previewProgress = remember { mutableStateOf(0f) },
                        onPlayItem = {},
                        onItem = {},
                        onHomeLibrary = { _, _ -> },
                        onLibrary = {},
                        onSeerrItem = {},
                    )
                }
            }
            androidx.compose.runtime.LaunchedEffect(shown, preferredTargetId) {
                coordinator.restoreFocus(
                    routeKey = "home",
                    preferredTargetId = preferredTargetId,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RestorableLibraryHost(
        shown: Boolean,
        preferredTargetId: String?,
        libraries: List<JellyfinLibrary>,
    ) {
        val route = TvRoute.Library()
        val routeKey = route.focusRouteKey(emptyList())
        val coordinator = remember { TvFocusCoordinator<FocusRequester>() }
        val entryFocusRequester = remember { FocusRequester() }
        if (shown) {
            CompositionLocalProvider(
                LocalTvScreenEntryFocusRequester provides entryFocusRequester,
                LocalTvFocusContext provides TvFocusContext(coordinator, routeKey),
            ) {
                JellystackTvTheme {
                    TvLibraryScreen(
                        route = route,
                        state = JellyfinHomeState(libraries = libraries),
                        strings = TvStrings.current(AppLanguage.ENGLISH),
                        focusMemory = remember { TvFocusMemory() },
                        onSelectLibrary = {},
                        onOpenItem = {},
                        onOpenContainer = {},
                        onLoadMore = {},
                        onRetry = {},
                    )
                }
            }
            androidx.compose.runtime.LaunchedEffect(shown, preferredTargetId) {
                coordinator.restoreFocus(
                    routeKey = routeKey,
                    preferredTargetId = preferredTargetId,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RestorablePagedLibraryErrorHost(
        shown: Boolean,
        preferredTargetId: String?,
        items: List<JellyfinItem>,
        onOpenRail: () -> Unit,
        errorKind: LibraryLoadErrorKind = LibraryLoadErrorKind.NEXT_PAGE,
        onRetry: () -> Unit = {},
        onLoadMore: () -> Unit = {},
    ) {
        val route = TvRoute.Library("library", "Library")
        val routeKey = route.focusRouteKey(emptyList())
        val coordinator =
            remember {
                TvFocusCoordinator<FocusRequester>(
                    awaitFocusFrame = { withFrameNanos { } },
                )
            }
        if (shown) {
            CompositionLocalProvider(LocalTvNavigationRailOpener provides onOpenRail) {
                TvRouteFocusScope(coordinator, routeKey) {
                    JellystackTvTheme {
                        TvLibraryScreen(
                            route = route,
                            state =
                                JellyfinHomeState(
                                    selectedLibraryId = "library",
                                    libraryItems = items,
                                    libraryErrorMessage = "Paging failed",
                                    libraryErrorKind = errorKind,
                                ),
                            strings = TvStrings.current(AppLanguage.ENGLISH),
                            focusMemory = remember { TvFocusMemory() },
                            onSelectLibrary = {},
                            onOpenItem = {},
                            onOpenContainer = {},
                            onLoadMore = onLoadMore,
                            onRetry = onRetry,
                        )
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(shown, preferredTargetId) {
                coordinator.restoreFocus(
                    routeKey = routeKey,
                    preferredTargetId = preferredTargetId,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RailRestorationHarness(
        expanded: Boolean,
        selectedAvailable: Boolean,
        delayedSelected: Boolean,
    ) {
        val coordinator = remember { TvFocusCoordinator<FocusRequester>(attachmentTimeoutMillis = 500) }
        val contentRoute = "test-content"
        val contentTarget = "content:exact"
        val selectedTarget = tvRailTargetId(TvRoute.Settings())
        val homeTarget = tvRailTargetId(TvRoute.Home)
        var showSelected by remember { mutableStateOf(expanded && selectedAvailable && !delayedSelected) }
        Column {
            CompositionLocalProvider(LocalTvFocusContext provides TvFocusContext(coordinator, contentRoute)) {
                TvRouteFocusMaterializer(
                    ownerId = "test-content",
                    targetIds = setOf(contentTarget),
                    fallbackTargetIds = setOf(contentTarget),
                ) { true }
                TvActionButton("Content exact", {}, focusTargetId = contentTarget)
            }
            if (expanded) {
                CompositionLocalProvider(LocalTvFocusContext provides TvFocusContext(coordinator, TV_FOCUS_RAIL_ROUTE)) {
                    TvRouteFocusMaterializer(
                        ownerId = "test-rail",
                        targetIds = setOf(selectedTarget, homeTarget),
                        fallbackTargetIds = setOf(homeTarget),
                    ) { targetId ->
                        when (targetId) {
                            selectedTarget -> {
                                if (!selectedAvailable) return@TvRouteFocusMaterializer false
                                if (delayedSelected) delay(100)
                                showSelected = true
                                true
                            }
                            homeTarget -> true
                            else -> false
                        }
                    }
                    TvActionButton("Rail home", {}, focusTargetId = homeTarget)
                    if (showSelected) TvActionButton("Rail selected", {}, focusTargetId = selectedTarget)
                }
            }
        }
        androidx.compose.runtime.LaunchedEffect(expanded, selectedAvailable, delayedSelected) {
            coordinator.restoreFocus(
                routeKey = if (expanded) TV_FOCUS_RAIL_ROUTE else contentRoute,
                preferredTargetId = if (expanded) selectedTarget else null,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestHomeScreen(
        state: JellyfinHomeState,
        sections: HomeSectionsState,
        engine: AndroidPlayerEngine,
        onCancelPreview: (TvTrailerPreviewOwner) -> Unit = {},
        onPreviewFocus: (TvTrailerPreviewOwner, JellyfinItem, String?) -> Unit = { _, _, _ -> },
        onPreviewBlur: (TvTrailerPreviewOwner, JellyfinItem, String?) -> Unit = { _, _, _ -> },
        onOpenNavigationRail: () -> Unit = {},
        onPlayItem: (JellyfinItem) -> Unit = {},
        onItem: (JellyfinItem) -> Unit = {},
        onHomeLibrary: (String, String) -> Unit = { _, _ -> },
        provideEntryFocus: Boolean = true,
        trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    ) {
        val entryFocusRequester = remember { FocusRequester() }
        val focusCoordinator = remember { TvFocusCoordinator<FocusRequester>() }
        androidx.compose.runtime.LaunchedEffect(provideEntryFocus) {
            if (provideEntryFocus) {
                focusCoordinator.restoreFocus(
                    routeKey = "home",
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        }
        CompositionLocalProvider(
            LocalTvScreenEntryFocusRequester provides entryFocusRequester.takeIf { provideEntryFocus },
            LocalTvNavigationRailOpener provides onOpenNavigationRail,
            LocalTvFocusContext provides TvFocusContext(focusCoordinator, "home"),
        ) {
            JellystackTvTheme {
                TvHomeScreen(
                    state = state,
                    homeSections = sections,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    trailerPreviewState = trailerPreviewState,
                    focusMemory = TvFocusMemory(),
                    onRefresh = {},
                    onPreviewFocus = onPreviewFocus,
                    onPreviewBlur = onPreviewBlur,
                    onCancelPreview = onCancelPreview,
                    trailerPreviewEngine = engine,
                    previewSoundEnabled = false,
                    previewProgress = remember { mutableStateOf(0f) },
                    onPlayItem = onPlayItem,
                    onItem = onItem,
                    onHomeLibrary = onHomeLibrary,
                    onLibrary = {},
                    onSeerrItem = {},
                )
            }
        }
    }
}
