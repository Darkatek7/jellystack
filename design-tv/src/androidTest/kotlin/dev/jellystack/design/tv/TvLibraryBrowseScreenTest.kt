package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvLibraryBrowseScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseAllTitlesQueryAndVisibleActionsAreReachable() {
        val recent = item("recent", "Recent")
        val titles = (0 until 12).map { item("title-$it", "Title $it") }
        var route by mutableStateOf(TvRoute.Library("library", "Movies"))
        var state by
            mutableStateOf(
                JellyfinHomeState(
                    selectedLibraryId = "library",
                    recentMovies = listOf(recent),
                    libraryItems = titles,
                ),
            )
        val opened = mutableListOf<String>()
        composeRule.setContent {
            JellystackTvTheme {
                TvLibraryScreen(
                    route = route,
                    state = state,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    focusMemory = remember { TvFocusMemory() },
                    onSelectLibrary = {},
                    onOpenItem = { opened += it.id },
                    onOpenContainer = {},
                    onLoadMore = {},
                    onRetry = {},
                    homeSections = HomeSectionsState.Unavailable,
                    collectionType = "movies",
                    rememberedQuery = LibraryBrowseQuery.DEFAULT,
                    onModeChanged = { route = route.copy(mode = it) },
                    onQueryChanged = { state = state.copy(libraryBrowseQuery = it) },
                    onPlayItem = { opened += "play:${it.id}" },
                    cinematicModesEnabled = true,
                )
            }
        }

        composeRule.onNodeWithTag("tv-library-mode-controls").assertIsDisplayed()
        composeRule
            .onNodeWithTag("cinematic-card-recent-recent")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.onNodeWithTag("cinematic-action-details").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(listOf("recent"), opened) }

        composeRule.runOnIdle { route = route.copy(mode = TvLibraryMode.ALL_TITLES) }

        composeRule.onNodeWithTag("tv-library-query-controls").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-library-all-title-title-0").assertIsDisplayed()
        composeRule.onNodeWithTag("tv-library-query-sort").performClick()
        composeRule.runOnIdle { assertFalse(state.libraryBrowseQuery.isDefault) }

        composeRule.runOnIdle { route = route.copy(mode = TvLibraryMode.BROWSE) }
        composeRule.onNodeWithTag("cinematic-card-recent-recent").assertIsDisplayed()
    }

    private fun item(
        id: String,
        name: String,
    ) = JellyfinItem(
        id = id,
        libraryId = "library",
        name = name,
        sortName = name,
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
        productionYear = 2024,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )
}
