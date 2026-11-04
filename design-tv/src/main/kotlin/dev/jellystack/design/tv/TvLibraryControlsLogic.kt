package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.LibraryBrowseDirection
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryBrowseSort
import dev.jellystack.core.jellyfin.LibraryPlayedFilter
import kotlin.math.floor

/** The jump targets of the A-Z rail: the first item, then one per letter. */
internal val TV_LIBRARY_JUMP_LETTERS: List<String> = listOf("#") + ('A'..'Z').map(Char::toString)

/** The rail only helps while the list is in name order from A; the server index of a letter is that order's. */
internal fun tvLibraryShowsLetterRail(query: LibraryBrowseQuery): Boolean =
    query.sort == LibraryBrowseSort.TITLE && query.direction == LibraryBrowseDirection.ASCENDING

/** The number of filter groups in use, as the Filter button shows it; the sort order is not a filter. */
internal fun tvLibraryActiveFilterCount(query: LibraryBrowseQuery): Int =
    listOf(
        query.played != LibraryPlayedFilter.ANY,
        query.favoritesOnly,
        query.genres.isNotEmpty(),
        query.years.isNotEmpty(),
        query.mediaTypes.isNotEmpty(),
    ).count { it }

/** The query without any filter; the sort order stays. */
internal fun tvLibraryClearedFilters(query: LibraryBrowseQuery): LibraryBrowseQuery =
    LibraryBrowseQuery(sort = query.sort, direction = query.direction)

/** The starting years of the decades the [years] fall into, newest first. */
internal fun tvLibraryDecades(years: List<Int>): List<Int> {
    val starts = years.map { it / DECADE * DECADE }
    return starts.distinct().sortedDescending()
}

/** The years of a decade the library query accepts. */
internal fun tvLibraryDecadeYears(decade: Int): Set<Int> {
    val years = decade until decade + DECADE
    return years.filter { it in QUERY_YEAR_RANGE }.toSet()
}

internal fun tvLibraryDecadeSelected(
    selectedYears: Set<Int>,
    decade: Int,
): Boolean = selectedYears.containsAll(tvLibraryDecadeYears(decade))

/** Selects or deselects every year of [decade]; the other selected years stay. */
internal fun tvLibraryToggleDecade(
    selectedYears: Set<Int>,
    decade: Int,
): Set<Int> =
    if (tvLibraryDecadeSelected(selectedYears, decade)) {
        selectedYears - tvLibraryDecadeYears(decade)
    } else {
        selectedYears + tvLibraryDecadeYears(decade)
    }

/**
 * Posters per row of the title grid. Titles sit below the posters, so a poster must stay wide enough for a readable
 * title; [reservedEndDp] is the room of the A-Z rail beside the grid.
 */
internal fun tvLibraryPosterColumns(
    widthDp: Float,
    fontScale: Float,
    reservedEndDp: Float = 0f,
): Int {
    val usableWidth = widthDp - (TvLayoutTokens.SafeInsets.horizontal.value * 2f) - reservedEndDp
    val minimumPosterWidth = TV_LIBRARY_MIN_POSTER_WIDTH_DP + ((fontScale.coerceAtLeast(1f) - 1f) * 60f)
    return floor(
        (usableWidth + TvLayoutTokens.CardSpacing.value) / (minimumPosterWidth + TvLayoutTokens.CardSpacing.value),
    ).toInt().coerceIn(1, TV_LIBRARY_MAX_POSTER_COLUMNS)
}

internal const val TV_LIBRARY_MIN_POSTER_WIDTH_DP = 120f
internal const val TV_LIBRARY_MAX_POSTER_COLUMNS = 7
internal const val TV_LIBRARY_RAIL_WIDTH_DP = 32f

private const val DECADE = 10
private val QUERY_YEAR_RANGE = 1874..2200
