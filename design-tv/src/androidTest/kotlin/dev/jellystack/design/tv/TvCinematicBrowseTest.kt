package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.players.AndroidPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvCinematicBrowseTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun sharedPreviewStageStaysAboveRowsAndNeverParticipatesInFocus() {
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state = TvCinematicBrowseState(hero = TvCinematicHero("Movies", eyebrow = "Movies"), rows = listOf(recentRow())),
                    onCardFocused = { _, _ -> },
                    onCardClick = {},
                )
            }
        }

        val firstCardTop =
            composeRule
                .onNodeWithTag("cinematic-card-recent-one")
                .getUnclippedBoundsInRoot()
                .top
                .value
        assertTrue(firstCardTop >= 250f)
        val stage = composeRule.onNodeWithTag("cinematic-preview-stage").fetchSemanticsNode()
        assertFalse(stage.config.contains(SemanticsActions.OnClick))
        assertFalse(stage.config.contains(SemanticsActions.RequestFocus))
    }

    @Test
    fun focusUpdatesTheStageWithoutChangingSelection() {
        var state by
            mutableStateOf(
                TvCinematicBrowseState(
                    hero = TvCinematicHero("Movies", eyebrow = "Movies"),
                    rows =
                        listOf(
                            TvCinematicRow(
                                id = "recent",
                                title = "Recently added",
                                cards =
                                    listOf(
                                        TvCinematicCard(id = "one", title = "One", selected = true),
                                        TvCinematicCard(id = "two", title = "Two", overview = "Second overview"),
                                    ),
                            ),
                        ),
                    inlineStatus = TvCinematicInlineStatus("Refreshing", TvCinematicStatusKind.LOADING),
                ),
            )
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state = state,
                    onCardFocused = { anchor, _ -> state = state.copy(focusedAnchor = anchor) },
                    onCardClick = {},
                )
            }
        }

        val selected = composeRule.onNodeWithTag("cinematic-card-recent-one")
        val focused = composeRule.onNodeWithTag("cinematic-card-recent-two")
        focused.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        selected.assertIsSelected().assertIsNotFocused()

        // The eyebrow keeps naming the screen while the stage shows the focused card.
        composeRule.onAllNodesWithText("Movies", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Second overview", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag("cinematic-status").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun firstCinematicCardStartsBeyondCollapsedNavigationRail() {
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state = TvCinematicBrowseState(hero = TvCinematicHero("Movies"), rows = listOf(recentRow())),
                    onCardFocused = { _, _ -> },
                    onCardClick = {},
                )
            }
        }

        val firstCardLeft =
            composeRule
                .onNodeWithTag("cinematic-card-recent-one")
                .getUnclippedBoundsInRoot()
                .left
                .value
        assertTrue(firstCardLeft >= TvLayoutTokens.SafeInsets.horizontal.value)
    }

    @Test
    fun playKeyPlaysTheFocusedCardAndOkOpensIt() {
        val played = mutableListOf<String>()
        val opened = mutableListOf<String>()
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state =
                        TvCinematicBrowseState(
                            rows =
                                listOf(
                                    TvCinematicRow(
                                        id = "recent",
                                        title = "Recently added",
                                        cards =
                                            listOf(
                                                TvCinematicCard(id = "movie", title = "Movie"),
                                                TvCinematicCard(id = "folder", title = "Folder"),
                                            ),
                                    ),
                                ),
                        ),
                    onCardFocused = { _, _ -> },
                    onCardClick = { opened += it.id },
                    playback =
                        TvCinematicCardPlayback(
                            labels = TvCinematicHintLabels(play = "Play", resume = "Resume", details = "Details"),
                            canPlay = { it.id == "movie" },
                            onPlay = { played += it.id },
                        ),
                )
            }
        }

        val movie = composeRule.onNodeWithTag("cinematic-card-recent-movie")
        movie.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        movie.performKeyInput { pressKey(Key.MediaPlay) }
        val folder = composeRule.onNodeWithTag("cinematic-card-recent-folder")
        folder.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        folder.performKeyInput { pressKey(Key.MediaPlayPause) }
        folder.performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("movie"), played)
            assertEquals(listOf("folder"), opened)
        }
    }

    @Test
    fun playingTrailerTakesTheScreenUntilTheNextKeyPress() {
        lateinit var engine: AndroidPlayerEngine
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            engine = rememberTestPlayerEngine(LocalContext.current)
            val progress = remember { mutableFloatStateOf(0f) }
            JellystackTvTheme {
                TvCinematicBrowse(
                    state =
                        TvCinematicBrowseState(
                            rows = listOf(recentRow()),
                            focusedAnchor = TvFocusAnchor("recent", "one", TvFocusDestination.SECTION_ITEM),
                        ),
                    onCardFocused = { _, _ -> },
                    onCardClick = {},
                    trailer = TvCinematicTrailer(engine, "Trailer", soundEnabled = true, progress = progress),
                )
            }
        }
        val card = composeRule.onNodeWithTag("cinematic-card-recent-one")
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(TV_HOME_IMMERSIVE_DELAY_MS - 500)
        // The stage title stands in for the browse UI here.
        composeRule.onAllNodesWithText("One", useUnmergedTree = true).assertCountEquals(2)

        composeRule.mainClock.advanceTimeBy(1_500)
        // Only the card's own caption is left; the rows fade but stay composed, so the card keeps its focus.
        composeRule.onAllNodesWithText("One", useUnmergedTree = true).assertCountEquals(1)
        card.assertIsFocused()

        card.performKeyInput { pressKey(Key.Menu) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onAllNodesWithText("One", useUnmergedTree = true).assertCountEquals(2)
        composeRule.runOnIdle(engine::release)
    }

    private fun recentRow() =
        TvCinematicRow(
            id = "recent",
            title = "Recently added",
            cards = listOf(TvCinematicCard(id = "one", title = "One")),
        )
}
