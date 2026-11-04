@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryPlayedFilter

private val GOLDEN_LIBRARY_TITLES =
    listOf(
        "The Last Horizon",
        "Northern Lights",
        "Paper Moon",
        "Harbor Lights",
        "Quiet Signal",
        "Northbound",
        "Glass Harbor",
        "Late Tide",
        "A Remarkably Long Title That Cannot Fit",
        "Copper Fields",
        "Midnight Express",
        "Silver Lining",
        "Blue Meridian",
        "Open Water",
        "Winter Garden",
        "Slow Fire",
        "Second Sunrise",
        "Iron Orchard",
    )

private val GOLDEN_LIBRARIES =
    listOf(
        JellyfinLibrary("movies", "Movies", "movies", 412, null),
        JellyfinLibrary("shows", "Shows", "tvshows", 96, null),
        JellyfinLibrary("music", "Music", "music", 1280, null),
        JellyfinLibrary("live", "Live TV", "livetv", null, null),
        JellyfinLibrary("books", "Books", "books", 37, null),
        JellyfinLibrary("collections", "Collections", "boxsets", 12, null),
    )

private fun goldenLibraryItems(): List<JellyfinItem> =
    GOLDEN_LIBRARY_TITLES.mapIndexed { index, title ->
        goldenItem("Movie", title).copy(
            libraryId = "movies",
            productionYear = 2008 + index,
            primaryImageTag = "golden",
            isPlayed = index == 2,
            positionTicks = if (index == 4) 40L * 600_000_000L else null,
        )
    }

private fun goldenLibraryState(query: LibraryBrowseQuery = LibraryBrowseQuery.DEFAULT) =
    JellyfinHomeState(
        selectedLibraryId = "movies",
        libraries = GOLDEN_LIBRARIES,
        libraryItems = goldenLibraryItems(),
        totalLibraryItemCount = 412,
        favorites = setOf("Northern Lights"),
        libraryBrowseQuery = query,
    )

/** The library title grid with its header and A-Z rail; the panel variant shows the open filter panel. */
@Composable
internal fun GoldenLibraryGrid(
    strings: TvStrings,
    panelOpen: Boolean,
) {
    val query =
        if (panelOpen) {
            LibraryBrowseQuery(played = LibraryPlayedFilter.UNPLAYED, years = tvLibraryDecadeYears(2010))
        } else {
            LibraryBrowseQuery.DEFAULT
        }
    TvLibraryAllTitles(
        route = TvRoute.Library("movies", "Movies", TvLibraryMode.ALL_TITLES),
        state = goldenLibraryState(query),
        strings = strings,
        focusMemory = remember { TvFocusMemory() },
        collectionType = "movies",
        onQueryChanged = {},
        onJumpToLetter = {},
        onLetterJumpHandled = {},
        onOpenItem = {},
        onOpenContainer = {},
        onLoadMore = {},
        onRetry = {},
        modifier = Modifier,
        initialPanel = if (panelOpen) TvLibraryPanelEntry.FILTER else null,
    )
}

/** The library landing: tiles for every kind of library, then the recently added row of each. */
@Composable
internal fun GoldenLibraryLanding(strings: TvStrings) {
    val items = goldenLibraryItems()
    TvLibraryLandingScreen(
        state =
            JellyfinHomeState(
                libraries = GOLDEN_LIBRARIES,
                libraryLatest =
                    mapOf(
                        "movies" to items.take(8),
                        "shows" to
                            listOf(
                                goldenItem("Series", "Harbor Lights").copy(productionYear = 2021),
                                goldenItem("Series", "Quiet Signal").copy(productionYear = 2019),
                            ),
                        "music" to listOf(goldenItem("MusicAlbum", "Glass Harbor").copy(productionYear = 2023)),
                    ),
            ),
        strings = strings,
        focusMemory = remember { TvFocusMemory() },
        routeKey = "golden-library-landing",
        onSelectLibrary = {},
        onOpenItem = {},
        onRetry = {},
    )
}
