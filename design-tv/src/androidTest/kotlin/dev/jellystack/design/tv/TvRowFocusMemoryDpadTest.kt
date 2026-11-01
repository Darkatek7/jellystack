package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Up and back Down returns to the card a row last had focus on, with the row still scrolled to it. */
@RunWith(AndroidJUnit4::class)
class TvRowFocusMemoryDpadTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    private var bottomIds by mutableStateOf(emptyList<String>())

    @Test
    fun downReturnsToTheCardUpLeft() {
        showBrowse((1..12).map { "b$it" })
        val card = moveToCardNine()

        card.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("cinematic-card-top-t1").assertIsFocused()
        composeRule.onNodeWithTag("cinematic-card-top-t1").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("cinematic-card-bottom-b9").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun downFallsBackToTheFirstCardWhenTheRememberedOneIsGone() {
        showBrowse((1..12).map { "b$it" })
        moveToCardNine().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("cinematic-card-top-t1").assertIsFocused()

        composeRule.runOnIdle { bottomIds = (1..12).filter { it != 9 }.map { "b$it" } }
        composeRule.onNodeWithTag("cinematic-card-top-t1").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("cinematic-card-bottom-b1").assertIsFocused()
    }

    /** Card 9 starts outside the viewport, so the row scrolls there the way it does with a remote. */
    private fun moveToCardNine(): SemanticsNodeInteraction {
        composeRule.onNodeWithTag("cinematic-card-bottom-b1").performSemanticsAction(SemanticsActions.RequestFocus)
        (1..8).forEach { index ->
            composeRule.onNodeWithTag("cinematic-card-bottom-b$index").performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }
        return composeRule.onNodeWithTag("cinematic-card-bottom-b9").assertIsFocused()
    }

    private fun showBrowse(initialBottom: List<String>) {
        bottomIds = initialBottom
        composeRule.setContent {
            JellystackTvTheme {
                val focusCoordinator = remember { TvFocusCoordinator<FocusRequester>() }
                val focusMemory = remember { TvFocusMemory() }
                TvRouteFocusScope(focusCoordinator, "test-browse", focusMemory) {
                    TvCinematicBrowse(
                        state =
                            TvCinematicBrowseState(
                                hero = TvCinematicHero("Movies", eyebrow = "Movies"),
                                rows =
                                    listOf(
                                        row("top", listOf("t1", "t2")),
                                        row("bottom", bottomIds),
                                    ),
                            ),
                        onCardFocused = { _, _ -> },
                        onCardClick = {},
                        rememberRowFocusOnVerticalMove = true,
                    )
                }
            }
        }
    }

    private fun row(
        id: String,
        cardIds: List<String>,
    ) = TvCinematicRow(id, id, cardIds.map { TvCinematicCard(id = it, title = it) })
}
