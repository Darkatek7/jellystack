package dev.jellystack.design

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.design.jellyseerr.JellyseerrDetailCommandState
import dev.jellystack.design.jellyseerr.JellyseerrDetailSection
import dev.jellystack.design.jellyseerr.JellyseerrMediaDetailPage
import dev.jellystack.design.jellyseerr.SeerrImmersiveDetailTestTags
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.theme.JellystackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyseerrImmersiveDetailUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun compactDetailShowsCinematicHeroCommandAndStateHoistedTabs() {
        var selectedSection by mutableStateOf(JellyseerrDetailSection.Overview)
        var primaryClicks = 0
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                ProvideResponsiveProfile(Modifier.size(width = 390.dp, height = 844.dp)) {
                    JellyseerrMediaDetailPage(
                        item = item(),
                        detailState = JellyseerrMediaDetailState.Loaded(detail()),
                        selectedSection = selectedSection,
                        commandState =
                            JellyseerrDetailCommandState(
                                primaryActionLabel = "Request",
                                statusLabel = "Not requested",
                                showOverflow = true,
                            ),
                        onSectionSelected = { selectedSection = it },
                        onPrimaryAction = { primaryClicks += 1 },
                        onRetry = {},
                        onTrailer = {},
                        onClose = {},
                        actions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.TITLE).assertExists()
        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, primaryClicks) }
        composeRule.onNodeWithContentDescription("Back").assertExists()

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT).performScrollToIndex(2)
        composeRule
            .onNodeWithTag("seerr_immersive_detail_tab_extras")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT).performScrollToIndex(3)
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.VIDEOS).assertExists()
    }

    @Test
    fun expandedDetailUsesThePaneWithoutACompactBackControl() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.size(width = 900.dp, height = 900.dp)) {
                    JellyseerrMediaDetailPage(
                        item = item(),
                        detailState = JellyseerrMediaDetailState.Loaded(detail()),
                        commandState = JellyseerrDetailCommandState(statusLabel = "Available"),
                        onRetry = {},
                        onTrailer = {},
                        onClose = {},
                        actions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.COMMAND_DECK).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun baseFailureIsAnAccessibleRetrySurface() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.size(width = 390.dp, height = 844.dp)) {
                    JellyseerrMediaDetailPage(
                        item = item(),
                        detailState = JellyseerrMediaDetailState.Error("Network unavailable"),
                        onRetry = {},
                        onTrailer = {},
                        onClose = {},
                        actions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun emptyLoadedDetailShowsAVisibleAccessibleEmptyState() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.size(width = 390.dp, height = 844.dp)) {
                    JellyseerrMediaDetailPage(
                        item = item(),
                        detailState = JellyseerrMediaDetailState.Loaded(emptyDetail()),
                        onRetry = {},
                        onTrailer = {},
                        onClose = {},
                        actions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT).performScrollToIndex(1)
        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.EMPTY)
            .assertIsDisplayed()
            .assertTextContains("No details available")
    }

    @Test
    fun expandedBaseFailureSuppressesTheCompactBackControl() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.size(width = 900.dp, height = 900.dp)) {
                    JellyseerrMediaDetailPage(
                        item = item(),
                        detailState = JellyseerrMediaDetailState.Error("Network unavailable"),
                        onRetry = {},
                        onTrailer = {},
                        onClose = {},
                        actions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}

private fun item(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = 550,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Sample Movie",
        overview = "A cinematic overview.",
        releaseYear = "1999",
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )

private fun detail(): JellyseerrMediaDetail =
    JellyseerrMediaDetail(
        tmdbId = 550,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Sample Movie",
        year = "1999",
        overview = "A cinematic overview with enough information to render the overview section.",
        runtimeMinutes = 139,
        genres = listOf("Drama", "Thriller"),
        releaseDate = "1999-10-15",
        revenue = 100_000_000,
        originalLanguage = "en",
        productionCountries = listOf("United States"),
        studios = listOf("Sample Studio"),
        ratings =
            JellyseerrMediaRatings(
                tmdb = 8.4,
                imdb = 8.8,
                rottenTomatoesCritics = 81.0,
                rottenTomatoesAudience = 96.0,
            ),
        trailer = null,
        posterPath = null,
        backdropPath = null,
        jellyseerrUrl = null,
        jellyfinUrl = null,
        imdbId = "tt0137523",
        tvdbId = null,
        cast =
            (1..20).map { index ->
                JellyseerrPerson(
                    id = index,
                    name = "Cast member $index",
                    character = "Character $index",
                    order = index,
                )
            },
        videos =
            listOf(
                JellyseerrMediaVideo(
                    id = "video",
                    name = "Official trailer",
                    site = "YouTube",
                    type = "Trailer",
                    key = "abc",
                    url = null,
                    official = true,
                    publishedAt = null,
                ),
            ),
        enrichment =
            JellyseerrMediaDetailEnrichment(
                ratings =
                    JellyseerrMediaRatings(
                        tmdb = 8.4,
                        imdb = 8.8,
                        rottenTomatoesCritics = 81.0,
                        rottenTomatoesAudience = 96.0,
                    ),
            ),
    )

private fun emptyDetail(): JellyseerrMediaDetail =
    JellyseerrMediaDetail(
        tmdbId = 550,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Sample Movie",
        year = null,
        overview = null,
        runtimeMinutes = null,
        genres = emptyList(),
        releaseDate = null,
        revenue = null,
        originalLanguage = null,
        productionCountries = emptyList(),
        studios = emptyList(),
        ratings = null,
        trailer = null,
        posterPath = null,
        backdropPath = null,
        jellyseerrUrl = null,
        jellyfinUrl = null,
        imdbId = null,
        tvdbId = null,
    )
