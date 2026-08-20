package dev.jellystack.design.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionButtonSupportsDpadAndTvMinimumTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton("Play", { clicks += 1 })
            }
        }

        composeRule
            .onNodeWithText("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun homeVerticalFocusInterceptsDownButLeavesHorizontalNavigationUntouched() {
        var direction: TvHomeVerticalDirection? = null
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton(
                    label = "Focus target",
                    onClick = {},
                    modifier = Modifier.tvHomeVerticalFocus { direction = it },
                )
            }
        }

        composeRule
            .onNodeWithText("Focus target")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(null, direction) }

        composeRule
            .onNodeWithText("Focus target")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.runOnIdle { assertEquals(TvHomeVerticalDirection.DOWN, direction) }
    }

    @Test
    fun tvThemeProvidesReadableDefaultContentColor() {
        var observedColor = Color.Unspecified

        composeRule.setContent {
            JellystackTvTheme {
                observedColor = LocalContentColor.current
            }
        }

        composeRule.runOnIdle { assertEquals(TvText, observedColor) }
    }

    @Test
    fun sectionTitleRendersWithBrightForegroundPixels() {
        composeRule.setContent {
            JellystackTvTheme {
                TvSectionTitle("Section")
            }
        }

        assertHasBrightPixels(composeRule.onNodeWithText("Section").captureToImage(), "TV section title")
    }

    @Test
    fun playerIconButtonIsDpadClickableAndUsesAnAccessibleDescription() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerIconButton(
                    icon = Icons.Default.PlayArrow,
                    description = "Play",
                    onClick = { clicks += 1 },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun playerHeaderDoesNotExposeADuplicateMoreAction() {
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerHeader(
                    primaryTitle = "Fena: Pirate Princess",
                    secondaryTitle = "S1 · E1 · Memories",
                    backDescription = "Back",
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onAllNodesWithContentDescription("More").assertCountEquals(0)
    }

    @Test
    fun playerOptionRowShowsCurrentValueAndSelectedState() {
        composeRule.setContent {
            JellystackTvTheme {
                TvPlayerOptionRow(
                    icon = Icons.Default.HighQuality,
                    title = "Streaming quality",
                    summary = "Automatic · Adaptive",
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Streaming quality").assertExists()
        composeRule.onNodeWithText("Automatic · Adaptive").assertExists()
        composeRule.onNodeWithContentDescription("Streaming quality, Automatic · Adaptive").assertIsSelected()
    }

    @Test
    fun portraitMediaCardKeepsSameFormatWhenFocused() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            JellystackTvTheme {
                TvMediaCard(
                    title = "Discover item",
                    imageUrl = null,
                    onClick = {},
                    landscape = false,
                )
            }
        }

        val card = composeRule.onNodeWithContentDescription("Discover item")
        val before = card.getUnclippedBoundsInRoot()
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.mainClock.advanceTimeBy(500)
        val focused = card.getUnclippedBoundsInRoot()

        assertEquals((before.right - before.left).value, (focused.right - focused.left).value, 0.1f)
        assertEquals((before.bottom - before.top).value, (focused.bottom - focused.top).value, 0.1f)
    }

    @Test
    fun upcomingDiscoverRailUsesTheStandardPortraitCardFormat() {
        val item =
            JellyseerrSearchItem(
                tmdbId = 42,
                mediaType = JellyseerrMediaType.TV,
                title = "Upcoming item",
                overview = null,
                releaseYear = "2026",
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
                mediaInfoId = null,
                tvdbId = null,
                availability = JellyseerrMediaAvailability(null, null),
                requests = emptyList(),
            )
        val rail =
            JellyseerrRecommendationRailState(
                rail = JellyseerrRecommendationRail.UPCOMING_SHOWS,
                items = listOf(item),
                isLoading = false,
                errorMessage = null,
                canLoadMore = false,
                nextPage = 2,
                lastUpdated = null,
                isStale = false,
            )
        composeRule.setContent {
            JellystackTvTheme {
                TvDiscoverScreen(
                    recommendations =
                        JellyseerrRecommendationsState.Ready(
                            mapOf(JellyseerrRecommendationRail.UPCOMING_SHOWS to rail),
                        ),
                    requests = JellyseerrRequestsState.MissingServer,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    onItem = {},
                    onConnectSeerr = {},
                )
            }
        }

        val bounds = composeRule.onNodeWithContentDescription("Upcoming item, 2026").getUnclippedBoundsInRoot()
        assertTrue(
            "Discover cards should remain portrait",
            bounds.bottom - bounds.top > bounds.right - bounds.left,
        )
    }
}

private fun assertHasBrightPixels(
    image: ImageBitmap,
    label: String,
) {
    val pixels = image.toPixelMap()
    var brightPixelCount = 0
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.alpha > 0.5f && color.red + color.green + color.blue > 2.4f) brightPixelCount++
        }
    }
    assertTrue("Expected $label to contain bright foreground pixels.", brightPixelCount >= 8)
}
