package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryBrowseDirection
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryBrowseSort
import dev.jellystack.core.jellyfin.LibraryMediaType
import dev.jellystack.core.jellyfin.LibraryPlayedFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLibraryControlsLogicTest {
    @Test
    fun filterCountCountsGroupsNotValuesAndIgnoresSorting() {
        assertEquals(0, tvLibraryActiveFilterCount(LibraryBrowseQuery(sort = LibraryBrowseSort.DATE_ADDED)))
        val query =
            LibraryBrowseQuery(
                played = LibraryPlayedFilter.UNPLAYED,
                genres = setOf("Drama", "Comedy"),
                years = (1990..1999).toSet(),
                mediaTypes = setOf(LibraryMediaType.MOVIE),
            )
        assertEquals(4, tvLibraryActiveFilterCount(query))
        assertEquals(5, tvLibraryActiveFilterCount(query.copy(favoritesOnly = true)))
        assertEquals("Filter (4)", tvLibraryFilterLabel("Filter", 4))
        assertEquals("Filter", tvLibraryFilterLabel("Filter", 0))
    }

    @Test
    fun clearingFiltersKeepsTheSortOrder() {
        val query =
            LibraryBrowseQuery(
                sort = LibraryBrowseSort.RELEASE_YEAR,
                direction = LibraryBrowseDirection.DESCENDING,
                favoritesOnly = true,
                genres = setOf("Drama"),
            )

        assertEquals(
            LibraryBrowseQuery(sort = LibraryBrowseSort.RELEASE_YEAR, direction = LibraryBrowseDirection.DESCENDING),
            tvLibraryClearedFilters(query),
        )
    }

    @Test
    fun letterRailOnlyShowsForNameOrderFromA() {
        assertTrue(tvLibraryShowsLetterRail(LibraryBrowseQuery.DEFAULT))
        assertTrue(tvLibraryShowsLetterRail(LibraryBrowseQuery(genres = setOf("Drama"))))
        assertFalse(tvLibraryShowsLetterRail(LibraryBrowseQuery(sort = LibraryBrowseSort.DATE_ADDED)))
        assertFalse(tvLibraryShowsLetterRail(LibraryBrowseQuery(direction = LibraryBrowseDirection.DESCENDING)))
        assertEquals(27, TV_LIBRARY_JUMP_LETTERS.size)
        assertEquals(listOf("#", "A", "B"), TV_LIBRARY_JUMP_LETTERS.take(3))
        assertEquals("Z", TV_LIBRARY_JUMP_LETTERS.last())
    }

    @Test
    fun decadesAreDerivedFromTheYearsAndToggleWholeDecades() {
        assertEquals(listOf(2020, 2010, 1990), tvLibraryDecades(listOf(2024, 2021, 2015, 1999, 1994)))

        val selected = tvLibraryToggleDecade(setOf(1985), 2010)
        assertEquals(setOf(1985) + (2010..2019).toSet(), selected)
        assertTrue(tvLibraryDecadeSelected(selected, 2010))
        assertFalse(tvLibraryDecadeSelected(selected, 2020))
        assertEquals(setOf(1985), tvLibraryToggleDecade(selected, 2010))
    }

    @Test
    fun aDecadeBeforeTheQueryRangeOnlyKeepsAcceptedYears() {
        val years = tvLibraryDecadeYears(1870)

        assertEquals((1874..1879).toSet(), years)
        // Must not throw: LibraryBrowseQuery rejects years below its range.
        LibraryBrowseQuery(years = years)
    }

    @Test
    fun posterColumnsFitTheScreenAndShrinkWithLargeText() {
        assertEquals(6, tvLibraryPosterColumns(960f, fontScale = 1f))
        assertEquals(6, tvLibraryPosterColumns(960f, fontScale = 1f, reservedEndDp = TV_LIBRARY_RAIL_WIDTH_DP + 8f))
        assertEquals(5, tvLibraryPosterColumns(960f, fontScale = 1.5f))
        assertEquals(1, tvLibraryPosterColumns(100f, fontScale = 1f))
    }

    @Test
    fun titleGridFocusLocationsFollowTheListOrder() {
        val locations = tvLibraryTitleGridFocusLocations(listOf("a", "b"), TV_LIBRARY_EMPTY_TARGET)

        assertEquals(mapOf("a" to 0, "b" to 1, TV_LIBRARY_EMPTY_TARGET to 2), locations)
    }

    @Test
    fun landingRowsFollowLibraryOrderAndSkipLibrariesWithoutItems() {
        val libraries =
            listOf(
                JellyfinLibrary("movies", "Movies", "movies", 10, null),
                JellyfinLibrary("books", "Books", "books", 4, null),
                JellyfinLibrary("music", "Music", "music", 4, null),
                JellyfinLibrary("shows", "Shows", "tvshows", 7, null),
            )
        val latest =
            mapOf(
                "shows" to listOf(goldenItem("Series", "Harbor Lights")),
                "music" to listOf(goldenItem("MusicAlbum", "Glass Harbor")),
                "books" to emptyList(),
                "movies" to
                    listOf("Paper Moon", "Paper Moon", "Late Tide").map { goldenItem("Movie", it) },
            )

        val rows = tvLibraryLandingRows(libraries, latest)

        assertEquals(listOf("movies", "music", "shows"), rows.map { it.library.id })
        assertEquals(listOf("Paper Moon", "Late Tide"), rows[0].items.map { it.name })
    }
}
