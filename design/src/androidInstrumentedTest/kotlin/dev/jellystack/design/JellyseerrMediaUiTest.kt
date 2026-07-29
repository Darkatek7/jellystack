package dev.jellystack.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.design.jellyseerr.JellyseerrRecommendationsScreen
import dev.jellystack.design.theme.JellystackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyseerrMediaUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun recommendationTapRoutesSelectionToDiscoverOwner() {
        val selected = mutableListOf<JellyseerrSearchItem>()
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                recommendationsScreen(
                    onOpenDetails = { _, item, _ -> selected += item },
                )
            }
        }

        composeRule.onNodeWithText("Sample Movie").performClick()

        composeRule.runOnIdle { assertEquals(listOf(seededItem()), selected) }
        composeRule.onNodeWithTag(TestTags.RECOMMENDATION_BACKDROP).assertDoesNotExist()
    }

    @Test
    fun recommendationLongPressRoutesRequestToDiscoverOwner() {
        val selected = mutableListOf<JellyseerrSearchItem>()
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                recommendationsScreen(
                    onRequestOpen = { _, item, _ -> selected += item },
                )
            }
        }

        composeRule.onNodeWithText("Sample Movie").performTouchInput { longClick() }

        composeRule.runOnIdle { assertEquals(listOf(seededItem()), selected) }
    }
}

@androidx.compose.runtime.Composable
private fun recommendationsScreen(
    onOpenDetails: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit = { _, _, _ -> },
    onRequestOpen: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit = { _, _, _ -> },
) {
    JellyseerrRecommendationsScreen(
        state = readyState(),
        detailStates = emptyMap(),
        onRefresh = {},
        onRetryRail = {},
        onLoadMore = {},
        onOpenDetails = onOpenDetails,
        onLoadDetail = {},
        onRequestOpen = onRequestOpen,
        onTrailer = { _, _, _, _ -> },
        onImpression = { _, _, _ -> },
        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
        onAddServer = {},
        contentPadding = PaddingValues(),
        onShellModalChange = {},
    )
}

private fun readyState(): JellyseerrRecommendationsState =
    JellyseerrRecommendationsState.Ready(
        mapOf(
            JellyseerrRecommendationRail.TRENDS to
                JellyseerrRecommendationRailState(
                    rail = JellyseerrRecommendationRail.TRENDS,
                    items = listOf(seededItem()),
                    isLoading = false,
                    errorMessage = null,
                    canLoadMore = false,
                    nextPage = 1,
                    lastUpdated = null,
                    isStale = false,
                ),
        ),
    )

private fun seededItem(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = 550,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Sample Movie",
        overview = null,
        releaseYear = "1999",
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )
