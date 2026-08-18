package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvBackNavigationTest {
    @Test
    fun nestedLibraryPathPopsBeforeRouteOrRail() {
        assertEquals(
            TvBackAction.POP_LIBRARY_PATH,
            tvBackAction(
                TvRoute.Library("shows"),
                backStackSize = 2,
                libraryPathDepth = 1,
                railVisible = true,
                selectedLibraryId = "shows",
            ),
        )
    }

    @Test
    fun nestedLibraryRoutePopsBeforeRail() {
        assertEquals(
            TvBackAction.POP_ROUTE,
            tvBackAction(
                TvRoute.Library("shows"),
                backStackSize = 2,
                libraryPathDepth = 0,
                railVisible = true,
                selectedLibraryId = "shows",
            ),
        )
    }

    @Test
    fun railOpensOnlyAtTopLevelRoot() {
        assertEquals(
            TvBackAction.OPEN_RAIL,
            tvBackAction(
                TvRoute.Home,
                backStackSize = 1,
                libraryPathDepth = 0,
                railVisible = false,
                selectedLibraryId = null,
            ),
        )
        assertEquals(
            TvBackAction.OPEN_RAIL,
            tvBackAction(
                TvRoute.Settings(),
                backStackSize = 1,
                libraryPathDepth = 0,
                railVisible = false,
                selectedLibraryId = null,
            ),
        )
    }

    @Test
    fun backClosesRailAtTopLevelRoot() {
        assertEquals(
            TvBackAction.CLOSE_RAIL,
            tvBackAction(
                TvRoute.Home,
                backStackSize = 1,
                libraryPathDepth = 0,
                railVisible = true,
                selectedLibraryId = null,
            ),
        )
    }

    @Test
    fun topLevelLibraryListIgnoresAStaleNestedBrowsePath() {
        assertEquals(
            TvBackAction.OPEN_RAIL,
            tvBackAction(
                TvRoute.Library(),
                backStackSize = 1,
                libraryPathDepth = 2,
                railVisible = false,
                selectedLibraryId = "shows",
            ),
        )
    }

    @Test
    fun libraryRouteDoesNotPopAPathOwnedByAnotherLibrary() {
        assertEquals(
            TvBackAction.POP_ROUTE,
            tvBackAction(
                TvRoute.Library("shows"),
                backStackSize = 2,
                libraryPathDepth = 2,
                railVisible = false,
                selectedLibraryId = "movies",
            ),
        )
    }
}
