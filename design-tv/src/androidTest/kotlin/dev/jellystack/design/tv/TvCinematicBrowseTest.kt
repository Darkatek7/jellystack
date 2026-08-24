package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvCinematicBrowseTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithTag("cinematic-status").assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(6)
        composeRule.runOnIdle { assertEquals(listOf("play", "details", "saved", "played"), invoked) }
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
