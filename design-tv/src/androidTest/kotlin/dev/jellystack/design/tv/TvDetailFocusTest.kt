package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvDetailFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailOpensAtHeroAndUpFromPrimaryActionRestoresTop() {
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    routeKey = "movie-1",
                    heroContentDescription = "Movie details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        TvActionButton(
                            label = "Play",
                            onClick = {},
                            primary = true,
                            modifier =
                                primaryActionModifier
                                    .then(actionRowModifier)
                                    .align(Alignment.BottomStart)
                                    .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                        )
                    },
                ) { _, _ ->
                    item("body") {
                        Box(Modifier.height(900.dp).testTag("tv-detail-body"))
                    }
                }
            }
        }

        val hero = composeRule.onNodeWithTag("tv-detail-hero").assertIsFocused()
        composeRule.onNodeWithContentDescription("Movie details").assertIsFocused()
        assertEquals(0f, hero.getUnclippedBoundsInRoot().top.value, 0.01f)

        hero.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").assertIsFocused()

        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        hero.assertIsFocused()
        assertEquals(0f, hero.getUnclippedBoundsInRoot().top.value, 0.01f)
    }

    @Test
    fun downFromActionRowFocusesAndScrollsToDetailBody() {
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    routeKey = "episode-1",
                    heroContentDescription = "Episode details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        Box(Modifier.fillMaxSize().then(actionRowModifier)) {
                            TvActionButton(
                                label = "Play",
                                onClick = {},
                                primary = true,
                                modifier =
                                    primaryActionModifier
                                        .then(actionRowModifier)
                                        .align(Alignment.BottomStart)
                                        .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                            )
                        }
                    },
                    bodyFocusItemIndex = 1,
                    nextBodyItemIndex = 2,
                ) { bodyFocusModifier, lowerContentFocusModifier ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    item("cast") {
                        TvActionButton(
                            label = "Cast member",
                            onClick = {},
                            modifier = lowerContentFocusModifier.width(220.dp),
                        )
                    }
                }
            }
        }

        val hero = composeRule.onNodeWithTag("tv-detail-hero").assertIsFocused()
        hero.performKeyInput { pressKey(Key.DirectionDown) }
        val primary = composeRule.onNodeWithTag("tv-detail-primary-action").assertIsFocused()

        primary.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(2_000) {
            runCatching {
                composeRule.onNodeWithTag("tv-detail-body-focus").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        assertTrue(hero.getUnclippedBoundsInRoot().top.value < 0f)

        composeRule.onNodeWithTag("tv-detail-body-focus").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Cast member").assertIsFocused()

        composeRule.onNodeWithContentDescription("Cast member").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
    }
}
