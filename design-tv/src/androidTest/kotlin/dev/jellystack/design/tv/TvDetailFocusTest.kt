package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    fun focusChainOverviewCastSimilarScrollsAndCastCenterIsNoOp() {
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
                    lowerContentTargets =
                        listOf(
                            TvDetailFocusTarget("cast", 2),
                            TvDetailFocusTarget("similar", 3),
                        ),
                ) { bodyFocusModifier, lowerContentFocusModifiers ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    item("cast") {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .then(lowerContentFocusModifiers[0].navigationModifier),
                        ) {
                            itemsIndexed(listOf("Actor 1", "Actor 2")) { index, actor ->
                                TvMediaCard(
                                    title = actor,
                                    imageUrl = null,
                                    onClick = null,
                                    modifier =
                                        if (index == 0) {
                                            lowerContentFocusModifiers[0]
                                                .firstTargetModifier
                                                .width(180.dp)
                                                .testTag("cast-person-$index")
                                        } else {
                                            Modifier.width(180.dp).testTag("cast-person-$index")
                                        },
                                )
                            }
                        }
                    }
                    item("similar") {
                        TvMediaCard(
                            title = "Similar",
                            imageUrl = null,
                            onClick = {},
                            modifier =
                                lowerContentFocusModifiers[1]
                                    .firstTargetModifier
                                    .then(lowerContentFocusModifiers[1].navigationModifier)
                                    .testTag("similar-item-0"),
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
        val body = composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        assertTrue(hero.getUnclippedBoundsInRoot().top.value < 0f)

        body.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        val firstCast = composeRule.onNodeWithContentDescription("Actor 1").assertIsFocused()

        firstCast.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        firstCast.assertIsFocused()

        firstCast.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        val secondCast = composeRule.onNodeWithContentDescription("Actor 2").assertIsFocused()

        secondCast.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        val similar = composeRule.onNodeWithContentDescription("Similar").assertIsFocused()

        similar.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        firstCast.assertIsFocused()
    }

    @Test
    fun disappearingLowerSectionRestoresOverviewFocus() {
        var showCast by mutableStateOf(true)
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    routeKey = "episode-changing",
                    heroContentDescription = "Changing episode details",
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
                    bodyFocusItemIndex = 1,
                    lowerContentTargets =
                        if (showCast) listOf(TvDetailFocusTarget("cast", 2)) else emptyList(),
                ) { bodyFocusModifier, lowerContentFocusModifiers ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    if (showCast) {
                        item("cast") {
                            LazyRow(
                                Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .then(lowerContentFocusModifiers[0].navigationModifier),
                            ) {
                                item("actor") {
                                    TvMediaCard(
                                        title = "Temporary actor",
                                        imageUrl = null,
                                        onClick = null,
                                        modifier =
                                            lowerContentFocusModifiers[0]
                                                .firstTargetModifier
                                                .width(180.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionDown) }
        val overview = composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        overview.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Temporary actor").assertIsFocused()

        composeRule.runOnIdle { showCast = false }
        composeRule.waitForIdle()

        overview.assertIsFocused()
        composeRule.onNodeWithContentDescription("Temporary actor").assertDoesNotExist()
    }

    @Test
    fun appendingLowerSectionKeepsExistingCastFocus() {
        var showSimilar by mutableStateOf(false)
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    routeKey = "episode-appending",
                    heroContentDescription = "Appending episode details",
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
                    bodyFocusItemIndex = 1,
                    lowerContentTargets =
                        buildList {
                            add(TvDetailFocusTarget("cast", 2))
                            if (showSimilar) add(TvDetailFocusTarget("similar", 3))
                        },
                ) { bodyFocusModifier, lowerContentFocusModifiers ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    item("cast") {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .then(lowerContentFocusModifiers[0].navigationModifier),
                        ) {
                            item("actor") {
                                TvMediaCard(
                                    title = "Persistent actor",
                                    imageUrl = null,
                                    onClick = null,
                                    modifier =
                                        lowerContentFocusModifiers[0]
                                            .firstTargetModifier
                                            .width(180.dp),
                                )
                            }
                        }
                    }
                    if (showSimilar) {
                        item("similar") {
                            TvMediaCard(
                                title = "New similar item",
                                imageUrl = null,
                                onClick = {},
                                modifier =
                                    lowerContentFocusModifiers[1]
                                        .firstTargetModifier
                                        .then(lowerContentFocusModifiers[1].navigationModifier),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-body-focus").performKeyInput { pressKey(Key.DirectionDown) }
        val cast = composeRule.onNodeWithContentDescription("Persistent actor").assertIsFocused()

        composeRule.runOnIdle { showSimilar = true }
        composeRule.waitForIdle()

        cast.assertIsFocused()
    }
}
