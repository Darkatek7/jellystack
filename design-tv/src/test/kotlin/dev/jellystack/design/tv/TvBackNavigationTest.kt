package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvBackNavigationTest {
    @Test
    fun allTitlesBackReturnsToBrowseBeforePoppingFolderPath() {
        assertEquals(
            TvBackAction.POP_ROUTE,
            tvBackAction(
                currentRoute = TvRoute.Library("library", "Movies", TvLibraryMode.ALL_TITLES),
                backStackSize = 3,
                libraryPathDepth = 2,
                railVisible = false,
                selectedLibraryId = "library",
            ),
        )
    }

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
    fun onlyHomeDelegatesCollapsedTopLevelBackToExit() {
        assertEquals(
            TvBackAction.SYSTEM_EXIT,
            tvBackAction(
                TvRoute.Home,
                backStackSize = 1,
                libraryPathDepth = 0,
                railVisible = false,
                selectedLibraryId = null,
            ),
        )
        assertEquals(
            TvBackAction.RETURN_HOME,
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
    fun topLevelDispatcherOwnsBackSoTheAppCanConfirmExit() {
        val holder = TvAppStateHolder()
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                libraryPathDepth = { 0 },
                selectedLibraryId = { null },
                popLibraryPath = {},
                cancelFocusRestoration = {},
            )

        assertTrue(dispatcher.rootHandlerEnabled)
        assertFalse(dispatcher.dispatch())
    }

    @Test
    fun nonHomeTopLevelBackReturnsHomeBeforeExit() {
        val holder = TvAppStateHolder().apply { selectTopLevel(TvRoute.Discover) }
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                libraryPathDepth = { 0 },
                selectedLibraryId = { null },
                popLibraryPath = {},
                cancelFocusRestoration = {},
            )

        assertTrue(dispatcher.dispatch())
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
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
    fun settingsCategoryPopsToLandingBeforeTheRailCanOpen() {
        assertEquals(
            TvBackAction.POP_ROUTE,
            tvBackAction(
                tvSettingsRoute(TvSettingsCategory.AUDIO_SUBTITLES),
                backStackSize = 2,
                libraryPathDepth = 0,
                railVisible = false,
                selectedLibraryId = null,
            ),
        )
        assertEquals(
            TvBackAction.RETURN_HOME,
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
    fun topLevelLibraryListIgnoresAStaleNestedBrowsePath() {
        assertEquals(
            TvBackAction.RETURN_HOME,
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

    @Test
    fun noBackStateOpensTheRail() {
        val routes = listOf(TvRoute.Home, TvRoute.Library(), TvRoute.Search, TvRoute.Discover, TvRoute.Settings())

        routes.forEach { route ->
            listOf(false, true).forEach { railExpanded ->
                val action =
                    tvBackAction(
                        currentRoute = route,
                        backStackSize = 1,
                        libraryPathDepth = 0,
                        railVisible = railExpanded,
                        selectedLibraryId = null,
                    )
                assertEquals(
                    when {
                        railExpanded -> TvBackAction.CLOSE_RAIL
                        route == TvRoute.Home -> TvBackAction.SYSTEM_EXIT
                        else -> TvBackAction.RETURN_HOME
                    },
                    action,
                )
            }
        }
    }

    @Test
    fun productionDispatcherKeepsFolderRouteRailAndSystemExitLayered() {
        val holder = TvAppStateHolder()
        holder.push(TvRoute.Library("shows"))
        holder.openRail()
        var pathDepth = 2
        var cancellations = 0
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                libraryPathDepth = { pathDepth },
                selectedLibraryId = { "shows" },
                popLibraryPath = { pathDepth -= 1 },
                cancelFocusRestoration = { cancellations += 1 },
            )

        assertFalse(dispatcher.rootHandlerEnabled)
        assertTrue(dispatcher.dispatch())
        assertEquals(1, pathDepth)
        assertEquals(2, holder.state.backStack.size)
        assertTrue(dispatcher.dispatch())
        assertEquals(0, pathDepth)
        holder.openRail()
        assertTrue(dispatcher.dispatch())
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
        assertFalse(holder.state.railExpanded)
        holder.openRail()
        assertTrue(dispatcher.rootHandlerEnabled)
        assertTrue(dispatcher.dispatch())
        assertFalse(holder.state.railExpanded)
        assertFalse(dispatcher.dispatch())
        assertEquals(4, cancellations)
    }
}
