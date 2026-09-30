package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryBrowseQueryTest {
    @Test
    fun defaultQueryMatchesCanonicalTitleAscendingRequest() {
        val query = LibraryBrowseQuery()

        assertTrue(query.isDefault)
        assertEquals("SortName", query.networkSortBy())
        assertEquals("Ascending", query.networkSortOrder())
        assertNull(query.networkPlayed())
        assertEquals(emptyList(), query.networkGenres())
        assertEquals(emptyList(), query.networkYears())
        assertNull(query.networkMediaTypes())
    }

    @Test
    fun nonDefaultQueryProducesStableNetworkValues() {
        val query =
            LibraryBrowseQuery(
                sort = LibraryBrowseSort.RELEASE_YEAR,
                direction = LibraryBrowseDirection.DESCENDING,
                played = LibraryPlayedFilter.UNPLAYED,
                favoritesOnly = true,
                genres = setOf("Science Fiction", "Action"),
                years = setOf(2025, 1999),
                mediaTypes = setOf(LibraryMediaType.SERIES, LibraryMediaType.MOVIE),
            )

        assertFalse(query.isDefault)
        assertEquals("ProductionYear", query.networkSortBy())
        assertEquals("Descending", query.networkSortOrder())
        assertEquals(false, query.networkPlayed())
        assertEquals(listOf("Action", "Science Fiction"), query.networkGenres())
        assertEquals(listOf(1999, 2025), query.networkYears())
        assertEquals("Movie,Series", query.networkMediaTypes())
    }

    @Test
    fun playedAndDateAddedMappingsAreExplicit() {
        val query =
            LibraryBrowseQuery(
                sort = LibraryBrowseSort.DATE_ADDED,
                played = LibraryPlayedFilter.PLAYED,
            )

        assertEquals("DateCreated", query.networkSortBy())
        assertEquals(true, query.networkPlayed())
    }

    @Test
    fun invalidYearsAndUnnormalizedGenresAreRejectedBeforeNetwork() {
        assertFailsWith<IllegalArgumentException> { LibraryBrowseQuery(years = setOf(1800)) }
        assertFailsWith<IllegalArgumentException> { LibraryBrowseQuery(genres = setOf(" Action ")) }
        assertFailsWith<IllegalArgumentException> { LibraryBrowseQuery(genres = setOf("")) }
    }
}
