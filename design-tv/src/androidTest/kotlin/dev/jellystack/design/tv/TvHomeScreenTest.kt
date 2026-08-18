package dev.jellystack.design.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.AndroidPlayerEngine
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
    fun autoCycleContinuesForHeroContainerButPausesForActionsAndRestartsAfterFocusLeaves() {
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
                autoCycle = true,
                intervalSeconds = 6,
                provideEntryFocus = false,
            )
        }

        val hero = composeRule.onNodeWithTag("tv-home-hero-carousel")
        val play = composeRule.onNodeWithContentDescription("Play")
        val row = composeRule.onAllNodes(cardWithDescription("First row item"))[0]
        hero.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(7_000)
        composeRule.onAllNodesWithText("Second hero", useUnmergedTree = true).assertCountEquals(1)
        play.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(7_000)
        composeRule.onAllNodesWithText("Second hero", useUnmergedTree = true).assertCountEquals(1)

        row.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(5_999)
        composeRule.onAllNodesWithText("Second hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.mainClock.advanceTimeBy(241)
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle(engine::release)
    }

    @Test
    fun autoCyclePausesForRailButNotForArmedOrPlayingPreview() {
        lateinit var engine: AndroidPlayerEngine
        val railOpen = androidx.compose.runtime.mutableStateOf(true)
        val previewState = androidx.compose.runtime.mutableStateOf<TvTrailerPreviewState>(TvTrailerPreviewState.Idle)
        val target = TvTrailerPreviewTarget("server", "preview", isEpisode = false, seriesId = null)
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
                autoCycle = true,
                intervalSeconds = 8,
                provideEntryFocus = false,
                railOpen = railOpen.value,
                trailerPreviewState = previewState.value,
            )
        }

        composeRule.mainClock.advanceTimeBy(9_000)
        composeRule.onAllNodesWithText("First hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle {
            railOpen.value = false
            previewState.value = TvTrailerPreviewState.Armed(target)
        }
        composeRule.mainClock.advanceTimeBy(8_240)
        composeRule.onAllNodesWithText("Second hero", useUnmergedTree = true).assertCountEquals(1)
        composeRule.runOnIdle { previewState.value = TvTrailerPreviewState.Playing(target) }
        composeRule.mainClock.advanceTimeBy(8_240)
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
                TvTrailerPreviewTarget("server", "episode", isEpisode = true, seriesId = "series"),
            )
        composeRule.setContent {
            val context = LocalContext.current
            engine = remember(context) { AndroidPlayerEngine(context) }
            TestHomeScreen(
                state = JellyfinHomeState(recentShows = listOf(episode)),
                sections = HomeSectionsState.Unavailable,
                engine = engine,
                trailerPreviewState = previewState,
                onPreviewFocus = { previewTargets += it.id },
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
                onPreviewFocus = { previewEvents += "focus:${it.id}" },
                onCancelPreview = { previewEvents += "clear" },
            )
        }

        composeRule.onNodeWithTag("tv-home-hero-carousel").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Play").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onAllNodes(cardWithDescription("Row item"))[0].assertIsFocused()

        composeRule.runOnIdle {
            assertEquals("focus:row", previewEvents.last())
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
                onCancelPreview = { previewCancellations += 1 },
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
    private fun TestHomeScreen(
        state: JellyfinHomeState,
        sections: HomeSectionsState,
        engine: AndroidPlayerEngine,
        onCancelPreview: () -> Unit = {},
        onPreviewFocus: (JellyfinItem) -> Unit = {},
        onOpenNavigationRail: () -> Unit = {},
        onPlayItem: (JellyfinItem) -> Unit = {},
        onItem: (JellyfinItem) -> Unit = {},
        autoCycle: Boolean = false,
        intervalSeconds: Int = 10,
        provideEntryFocus: Boolean = true,
        railOpen: Boolean = false,
        trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    ) {
        val entryFocusRequester = remember { FocusRequester() }
        CompositionLocalProvider(
            LocalTvScreenEntryFocusRequester provides entryFocusRequester.takeIf { provideEntryFocus },
            LocalTvNavigationRailOpener provides onOpenNavigationRail,
        ) {
            JellystackTvTheme {
                TvHomeScreen(
                    state = state,
                    homeSections = sections,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    autoCycle = autoCycle,
                    intervalSeconds = intervalSeconds,
                    railOpen = railOpen,
                    trailerPreviewState = trailerPreviewState,
                    focusMemory = TvFocusMemory(),
                    onRefresh = {},
                    onPreviewFocus = onPreviewFocus,
                    onPreviewBlur = {},
                    onCancelPreview = onCancelPreview,
                    trailerPreviewEngine = engine,
                    previewSoundEnabled = false,
                    previewProgress = 0f,
                    onPlayItem = onPlayItem,
                    onItem = onItem,
                    onLibrary = {},
                    onSeerrItem = {},
                )
            }
        }
    }
}
