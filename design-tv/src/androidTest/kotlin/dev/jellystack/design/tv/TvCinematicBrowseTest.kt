package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
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
                    state =
                        TvCinematicBrowseState(
                            rows =
                                listOf(
                                    TvCinematicRow(
                                        id = "recent",
                                        title = "Recently added",
                                        cards = listOf(TvCinematicCard(id = "one", title = "One")),
                                    ),
                                ),
                        ),
                    actionLabels = labels(),
                    onCardFocused = { _, _ -> },
                    onCardClick = {},
                    topHeaderContent = { Text("Movies") },
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
    fun focusUpdatesMetadataWithoutChangingSelectionAndAllActionsAreReachable() {
        var state by
            mutableStateOf(
                TvCinematicBrowseState(
                    hero = TvCinematicHero("Browse"),
                    rows =
                        listOf(
                            TvCinematicRow(
                                id = "recent",
                                title = "Recently added",
                                cards =
                                    listOf(
                                        TvCinematicCard(id = "one", title = "One", selected = true),
                                        TvCinematicCard(id = "two", title = "Two"),
                                    ),
                            ),
                        ),
                    inlineStatus = TvCinematicInlineStatus("Refreshing", TvCinematicStatusKind.LOADING),
                ),
            )
        val invoked = mutableListOf<String>()
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state = state,
                    actionLabels = labels(),
                    onCardFocused = { anchor, _ -> state = state.copy(focusedAnchor = anchor) },
                    onCardClick = { invoked += "card:${it.id}" },
                    selectedItemActions =
                        TvSelectedItemActions(
                            onPlayOrResume = { invoked += "play" },
                            onDetails = { invoked += "details" },
                            onToggleSaved = { invoked += "saved" },
                            onTogglePlayed = { invoked += "played" },
                        ),
                )
            }
        }

        val selected = composeRule.onNodeWithTag("cinematic-card-recent-one")
        val focused = composeRule.onNodeWithTag("cinematic-card-recent-two")
        selected.assertIsSelected().assertIsNotFocused()
        focused.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        selected.assertIsSelected().assertIsNotFocused()

        listOf("play", "details", "saved", "played").forEach { action ->
            composeRule
                .onNodeWithTag("cinematic-action-$action")
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
        }
        composeRule.onNodeWithTag("cinematic-status").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(6)
        composeRule.runOnIdle { assertEquals(listOf("play", "details", "saved", "played"), invoked) }
    }

    @Test
    fun firstCinematicCardStartsBeyondCollapsedNavigationRail() {
        composeRule.setContent {
            JellystackTvTheme {
                TvCinematicBrowse(
                    state =
                        TvCinematicBrowseState(
                            hero = TvCinematicHero("Movies"),
                            rows =
                                listOf(
                                    TvCinematicRow(
                                        id = "recent",
                                        title = "Recently added",
                                        cards = listOf(TvCinematicCard(id = "one", title = "One")),
                                    ),
                                ),
                        ),
                    actionLabels = labels(),
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
        assertTrue(firstCardLeft >= TvLayoutTokens.CollapsedRailWidth.value)
    }

    @Test
    fun actionStripUpAndDownReturnsToTheExactOriginatingCard() {
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
                                                TvCinematicCard(id = "one", title = "One"),
                                                TvCinematicCard(id = "two", title = "Two"),
                                            ),
                                    ),
                                ),
                            focusedAnchor = TvFocusAnchor("recent", "two", TvFocusDestination.SECTION_ITEM),
                        ),
                    actionLabels = labels(),
                    onCardFocused = { _, _ -> },
                    onCardClick = {},
                    selectedItemActions =
                        TvSelectedItemActions(
                            onPlayOrResume = {},
                            onDetails = {},
                            onToggleSaved = null,
                            onTogglePlayed = null,
                        ),
                )
            }
        }

        val origin = composeRule.onNodeWithTag("cinematic-card-recent-two")
        origin.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        origin.performKeyInput { pressKey(Key.DirectionUp) }
        val focusedAction =
            composeRule.onNode(hasAnyAncestor(hasTestTag("cinematic-action-strip")) and isFocused())
        focusedAction.assertExists().performKeyInput { pressKey(Key.DirectionDown) }
        origin.assertIsFocused()
    }

    private fun labels() =
        TvSelectedItemActionLabels(
            play = "Play",
            resume = "Resume",
            details = "Details",
            addToList = "Add",
            removeFromList = "Remove",
            markPlayed = "Played",
            markUnplayed = "Unplayed",
        )
}
