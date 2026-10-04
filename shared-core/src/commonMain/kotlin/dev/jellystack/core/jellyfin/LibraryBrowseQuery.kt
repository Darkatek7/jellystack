package dev.jellystack.core.jellyfin

import kotlinx.serialization.Serializable

@Serializable
enum class LibraryBrowseSort {
    TITLE,
    DATE_ADDED,
    RELEASE_YEAR,
}

@Serializable
enum class LibraryBrowseDirection {
    ASCENDING,
    DESCENDING,
}

@Serializable
enum class LibraryPlayedFilter {
    ANY,
    PLAYED,
    UNPLAYED,
}

@Serializable
enum class LibraryMediaType(
    val jellyfinValue: String,
) {
    MOVIE("Movie"),
    SERIES("Series"),
    EPISODE("Episode"),
    MUSIC_VIDEO("MusicVideo"),
    AUDIO("Audio"),
}

@Serializable
data class LibraryBrowseQuery(
    val sort: LibraryBrowseSort = LibraryBrowseSort.TITLE,
    val direction: LibraryBrowseDirection = LibraryBrowseDirection.ASCENDING,
    val played: LibraryPlayedFilter = LibraryPlayedFilter.ANY,
    val favoritesOnly: Boolean = false,
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
    val mediaTypes: Set<LibraryMediaType> = emptySet(),
) {
    init {
        require(genres.all { it.isNotBlank() && it == it.trim() })
        require(years.all { it in MIN_RELEASE_YEAR..MAX_RELEASE_YEAR })
    }

    val isDefault: Boolean
        get() = this == DEFAULT

    internal fun networkSortBy(): String =
        when (sort) {
            LibraryBrowseSort.TITLE -> "SortName"
            LibraryBrowseSort.DATE_ADDED -> "DateCreated"
            LibraryBrowseSort.RELEASE_YEAR -> "ProductionYear"
        }

    internal fun networkSortOrder(): String =
        when (direction) {
            LibraryBrowseDirection.ASCENDING -> "Ascending"
            LibraryBrowseDirection.DESCENDING -> "Descending"
        }

    internal fun networkPlayed(): Boolean? =
        when (played) {
            LibraryPlayedFilter.ANY -> null
            LibraryPlayedFilter.PLAYED -> true
            LibraryPlayedFilter.UNPLAYED -> false
        }

    internal fun networkGenres(): List<String> = genres.sortedBy(String::lowercase)

    internal fun networkYears(): List<Int> = years.sorted()

    internal fun networkMediaTypes(): String? =
        mediaTypes
            .map(LibraryMediaType::jellyfinValue)
            .sorted()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(",")

    companion object {
        val DEFAULT = LibraryBrowseQuery()
        private const val MIN_RELEASE_YEAR = 1874
        private const val MAX_RELEASE_YEAR = 2200
    }
}

enum class LibraryCachePolicy {
    CANONICAL_DEFAULT,
    SESSION_ONLY,
}
