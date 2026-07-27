package dev.jellystack.design.jellyfin

import dev.jellystack.design.navigation.LibraryDestination
import dev.jellystack.design.navigation.LibrarySection

internal enum class LibraryRefreshTarget {
    CurrentLevel,
    Favorites,
    Libraries,
    None,
}

internal fun LibraryDestination.refreshTarget(): LibraryRefreshTarget =
    when (this) {
        LibraryDestination.Root -> LibraryRefreshTarget.CurrentLevel
        is LibraryDestination.Library,
        is LibraryDestination.Children,
        -> LibraryRefreshTarget.CurrentLevel
        is LibraryDestination.Section ->
            when (section) {
                LibrarySection.Movies,
                LibrarySection.Series,
                -> LibraryRefreshTarget.CurrentLevel
                LibrarySection.Favorites -> LibraryRefreshTarget.Favorites
                LibrarySection.Libraries -> LibraryRefreshTarget.Libraries
                LibrarySection.Downloads -> LibraryRefreshTarget.None
            }
    }

internal data class LibraryNavigationSnapshot(
    val destination: LibraryDestination,
    val searchQuery: String,
    val scrollKey: String,
)

internal data class LibraryNavigationState(
    val destination: LibraryDestination = LibraryDestination.Root,
    val backStack: List<LibraryNavigationSnapshot> = emptyList(),
    val searchQuery: String = "",
    val scrollKey: String = "library-root",
) {
    val depth: Int
        get() = backStack.size

    fun push(next: LibraryDestination): LibraryNavigationState =
        copy(
            destination = next,
            backStack =
                backStack +
                    LibraryNavigationSnapshot(
                        destination = destination,
                        searchQuery = searchQuery,
                        scrollKey = scrollKey,
                    ),
            searchQuery = "",
            scrollKey = destinationKey(next),
        )

    fun pop(): LibraryNavigationState {
        val previous =
            backStack.lastOrNull()
                ?: LibraryNavigationSnapshot(LibraryDestination.Root, "", "library-root")
        return copy(
            destination = previous.destination,
            backStack = backStack.dropLast(1),
            searchQuery = previous.searchQuery,
            scrollKey = previous.scrollKey,
        )
    }
}

internal fun destinationKey(destination: LibraryDestination): String =
    when (destination) {
        LibraryDestination.Root -> "library-root"
        is LibraryDestination.Section -> "section:${destination.section.name.lowercase()}"
        is LibraryDestination.Library -> "library:${destination.libraryId}"
        is LibraryDestination.Children -> "children:${destination.parentId}"
    }
