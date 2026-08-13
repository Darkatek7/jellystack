package dev.jellystack.design.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
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
import kotlin.time.Duration.Companion.days
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun heroAndFirstRowRouteVerticallyWhileRightStillMovesBetweenActions() {
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

        val play = composeRule.onNodeWithContentDescription("Play")
        val details = composeRule.onNodeWithContentDescription("Details")
        val card = composeRule.onAllNodes(hasContentDescription("Recent") and hasClickAction())[0]
        play.performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        details.assertIsFocused()
        assertTrue(play.getUnclippedBoundsInRoot().top < card.getUnclippedBoundsInRoot().top)

        details.performKeyInput { pressKey(Key.DirectionDown) }
        card.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        play.assertIsFocused()
        composeRule.runOnIdle(engine::release)
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
                .onAllNodes(hasContentDescription("Local $index") and hasClickAction())[0]
                .assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        composeRule.onAllNodes(hasContentDescription("Local 6") and hasClickAction())[0].assertIsFocused()
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
        composeRule.onAllNodes(hasContentDescription("External 1") and hasClickAction())[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onAllNodes(hasContentDescription("External 2") and hasClickAction())[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onAllNodes(hasContentDescription("Local first") and hasClickAction())[0]
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.runOnIdle {
            assertTrue(previewCancellations == 2)
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

    private fun externalHomeSectionItem(id: String, name: String): HomeSectionItem =
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

    private fun item(id: String, name: String, dateCreated: String? = null): JellyfinItem =
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
    ) {
        JellystackTvTheme {
            TvHomeScreen(
                state = state,
                homeSections = sections,
                strings = TvStrings.current(AppLanguage.ENGLISH),
                autoCycle = false,
                intervalSeconds = 10,
                railOpen = false,
                trailerPreviewState = TvTrailerPreviewState.Idle,
                focusMemory = TvFocusMemory(),
                onRefresh = {},
                onPreviewFocus = {},
                onPreviewBlur = {},
                onCancelPreview = onCancelPreview,
                trailerPreviewEngine = engine,
                previewSoundEnabled = false,
                previewProgress = 0f,
                onPlayItem = {},
                onItem = {},
                onLibrary = {},
                onSeerrItem = {},
            )
        }
    }
}
