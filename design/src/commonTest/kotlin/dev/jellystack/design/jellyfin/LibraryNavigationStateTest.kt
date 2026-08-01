package dev.jellystack.design.jellyfin

import dev.jellystack.design.navigation.LibraryDestination
import dev.jellystack.design.navigation.LibrarySection
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryNavigationStateTest {
    @Test
    fun homeLibraryEntryReturnsHomeAtItsTopLevel() {
        val destination = LibraryDestination.Section(LibrarySection.Movies)

        val state = homeLibraryNavigationState(destination)

        assertEquals(destination, state.destination)
        assertEquals(1, state.depth)
        assertEquals("section:movies", state.scrollKey)
        assertEquals(
            LibraryBackTarget.ReturnHome,
            libraryBackTarget(state, LibraryEntryOrigin.Home),
        )
    }

    @Test
    fun libraryTabEntryReturnsToTheLibraryRoot() {
        val state =
            LibraryNavigationState().push(
                LibraryDestination.Section(LibrarySection.Movies),
            )

        assertEquals(
            LibraryBackTarget.PreviousLevel,
            libraryBackTarget(state, LibraryEntryOrigin.LibraryTab),
        )
    }

    @Test
    fun nestedHomeLibraryEntryPopsBeforeReturningHome() {
        val state =
            homeLibraryNavigationState(LibraryDestination.Section(LibrarySection.Movies))
                .push(LibraryDestination.Children("boxset", "Collection"))

        assertEquals(
            LibraryBackTarget.PreviousLevel,
            libraryBackTarget(state, LibraryEntryOrigin.Home),
        )
    }

    @Test
    fun libraryStatePopsOneLevelAtATime() {
        val state =
            LibraryNavigationState(
                destination = LibraryDestination.Children("album", "Album"),
                backStack =
                    listOf(
                        LibraryNavigationSnapshot(
                            destination = LibraryDestination.Root,
                            searchQuery = "Dune",
                            scrollKey = "library-root",
                        ),
                        LibraryNavigationSnapshot(
                            destination = LibraryDestination.Section(LibrarySection.Libraries),
                            searchQuery = "Abbey",
                            scrollKey = "section:libraries",
                        ),
                    ),
            )

        val popped = state.pop()
        assertEquals(LibraryDestination.Section(LibrarySection.Libraries), popped.destination)
        assertEquals("Abbey", popped.searchQuery)
        assertEquals("section:libraries", popped.scrollKey)
        assertEquals(
            LibraryDestination.Library("music-1", "Music"),
            LibraryNavigationState().push(LibraryDestination.Library("music-1", "Music")).destination,
        )
    }

    @Test
    fun nestedPushPopRestoresParentQueryAndListIdentity() {
        val root = LibraryNavigationState(searchQuery = "Dune")
        val section =
            root
                .push(LibraryDestination.Section(LibrarySection.Libraries))
                .copy(searchQuery = "Abbey")
        val child = section.push(LibraryDestination.Children("album-1", "Albums"))

        assertEquals(2, child.depth)
        assertEquals(section, child.pop())
        assertEquals(root, child.pop().pop())
    }

    @Test
    fun refreshTargetMatchesTheVisibleLibraryDestination() {
        assertEquals(LibraryRefreshTarget.CurrentLevel, LibraryDestination.Root.refreshTarget())
        assertEquals(
            LibraryRefreshTarget.CurrentLevel,
            LibraryDestination.Section(LibrarySection.Movies).refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.CurrentLevel,
            LibraryDestination.Section(LibrarySection.Series).refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.Favorites,
            LibraryDestination.Section(LibrarySection.Favorites).refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.Libraries,
            LibraryDestination.Section(LibrarySection.Libraries).refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.None,
            LibraryDestination.Section(LibrarySection.Downloads).refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.CurrentLevel,
            LibraryDestination.Library("music", "Music").refreshTarget(),
        )
        assertEquals(
            LibraryRefreshTarget.CurrentLevel,
            LibraryDestination.Children("album", "Album").refreshTarget(),
        )
    }
}
