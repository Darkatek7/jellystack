package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvLibraryBrowsePresentationTest {
    @Test
    fun browseRowsHaveRequiredOrderAndOmitEmptyOrUnsupportedContent() {
        val continueItem = item("continue", "Continue", libraryId = "library")
        val recentItem = item("recent", "Recent", libraryId = "library")
        val favorite = item("favorite", "Favorite", libraryId = "library")
        val serverItem = item("server", "Server", libraryId = "library")
        val state =
            JellyfinHomeState(
                selectedLibraryId = "library",
                continueWatching = listOf(continueItem),
                nextUp = emptyList(),
                recentMovies = listOf(recentItem),
                favorites = setOf(favorite.id),
            )
        val sections =
            HomeSectionsState.Ready(
                sections =
                    listOf(
                        HomeSection("empty", "Empty", HomeSectionViewMode.LANDSCAPE, true, false, emptyList()),
                        HomeSection(
                            "server",
                            "Server row",
                            HomeSectionViewMode.LANDSCAPE,
                            true,
                            false,
                            listOf(
                                HomeSectionItem(
                                    id = serverItem.id,
                                    name = serverItem.name,
                                    overview = null,
                                    productionYear = null,
                                    communityRating = null,
                                    imageUrl = null,
                                    jellyfinItem = serverItem,
                                    action = HomeSectionAction.JELLYFIN,
                                ),
                            ),
                        ),
                    ),
                imageBaseUrl = "",
                imageAccessToken = "",
            )

        val rows =
            buildTvLibraryBrowseRows(
                state = state,
                homeSections = sections,
                myListItems = listOf(favorite),
                labels = labels(),
            )

        assertEquals(listOf("continue", "recent", "my-list", "server:server"), rows.map(TvCinematicRow::id))
        assertEquals(listOf("continue"), rows[0].cards.map(TvCinematicCard::id))
        assertFalse(rows.any { it.cards.isEmpty() })
    }

    @Test
    fun nextUpAppearsOnlyForSeriesCapableLibrary() {
        val next = item("next", "Next", libraryId = "shows")
        val state = JellyfinHomeState(selectedLibraryId = "shows", nextUp = listOf(next))

        assertEquals(
            listOf("next-up"),
            buildTvLibraryBrowseRows(state, HomeSectionsState.Unavailable, emptyList(), labels(), "tvshows")
                .map(TvCinematicRow::id),
        )
        assertEquals(
            emptyList(),
            buildTvLibraryBrowseRows(state, HomeSectionsState.Unavailable, emptyList(), labels(), "movies"),
        )
    }

    @Test
    fun adaptiveGridUsesFiveColumnsAndLargeTextUsesFour() {
        assertEquals(5, tvLibraryAllTitlesColumnCount(960f, fontScale = 1f))
        assertEquals(4, tvLibraryAllTitlesColumnCount(960f, fontScale = 1.5f))
        assertEquals(TvLibraryCardShape.PORTRAIT, tvLibraryCardShape("movies", reliablePosters = true))
        assertEquals(TvLibraryCardShape.LANDSCAPE, tvLibraryCardShape("music", reliablePosters = false))
    }

    @Test
    fun browseAndAllTitlesKeepIndependentFocusSnapshots() {
        val memory = TvFocusMemory()
        val browseRoute = TvRoute.Library("library", "Movies", TvLibraryMode.BROWSE)
        val allTitlesRoute = browseRoute.copy(mode = TvLibraryMode.ALL_TITLES)
        memory.remember(browseRoute.focusRouteKey(), "recent", "recent-8", horizontalIndex = 8)
        memory.remember(allTitlesRoute.focusRouteKey(), "items", "item-20", horizontalIndex = 0)

        assertEquals("recent-8", memory.restore(browseRoute.focusRouteKey())?.itemId)
        assertEquals("item-20", memory.restore(allTitlesRoute.focusRouteKey())?.itemId)
    }

    private fun labels() =
        TvLibraryBrowseLabels(
            continueWatching = "Continue",
            nextUp = "Next up",
            recentlyAdded = "Recently added",
            myList = "My List",
        )

    private fun item(
        id: String,
        name: String,
        libraryId: String?,
    ) = JellyfinItem(
        id = id,
        libraryId = libraryId,
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
        productionYear = null,
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
