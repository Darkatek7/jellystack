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
    fun libraryOpenedOnItsGridStepsOutOfFoldersBeforeLeaving() {
        assertEquals(
            TvBackAction.POP_LIBRARY_PATH,
            tvBackAction(
                currentRoute = TvRoute.Library("playlists", "Playlists", TvLibraryMode.TITLES_ONLY),
                backStackSize = 2,
                libraryPathDepth = 1,
                railVisible = false,
                selectedLibraryId = "playlists",
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
    fun homeBackWalksFromRowsToTopToRailBeforeExit() {
        assertEquals(TvBackAction.FOCUS_HOME_TOP, tvHomeBackAction(railVisible = false, homeAtTop = false))
        assertEquals(TvBackAction.OPEN_RAIL, tvHomeBackAction(railVisible = false, homeAtTop = true))
        assertEquals(TvBackAction.SYSTEM_EXIT, tvHomeBackAction(railVisible = true, homeAtTop = true))
        assertEquals(TvBackAction.CLOSE_RAIL, tvHomeBackAction(railVisible = true, homeAtTop = false))
    }

    @Test
    fun homeBackStepsThroughTheDispatcherBeforeExit() {
        val holder = TvAppStateHolder()
        val homeBack = TvHomeBackState()
        var focusedTop = 0
        homeBack.bind {
            focusedTop += 1
            homeBack.atTop = true
        }
        homeBack.atTop = false
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                library =
                    TvLibraryBackAccess(
                        pathDepth = { 0 },
                        selectedLibraryId = { null },
                        popPath = {},
                    ),
                cancelFocusRestoration = {},
                homeBack = homeBack,
            )

        assertTrue(dispatcher.dispatch())
        assertEquals(1, focusedTop)
        assertFalse(holder.state.railExpanded)
        assertTrue(dispatcher.dispatch())
        assertTrue(holder.state.railExpanded)
        assertFalse(dispatcher.dispatch())
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
                library =
                    TvLibraryBackAccess(
                        pathDepth = { 0 },
                        selectedLibraryId = { null },
                        popPath = {},
                    ),
                cancelFocusRestoration = {},
            )

        assertTrue(dispatcher.rootHandlerEnabled)
        assertTrue(dispatcher.dispatch())
        assertTrue(holder.state.railExpanded)
        assertFalse(dispatcher.dispatch())
    }

    @Test
    fun nonHomeTopLevelBackReturnsHomeBeforeExit() {
        val holder = TvAppStateHolder().apply { selectTopLevel(TvRoute.Discover) }
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                library =
                    TvLibraryBackAccess(
                        pathDepth = { 0 },
                        selectedLibraryId = { null },
                        popPath = {},
                    ),
                cancelFocusRestoration = {},
            )

        assertTrue(dispatcher.dispatch())
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
    }

    @Test
    fun backClosesRailOnTopLevelRoutes() {
        assertEquals(
            TvBackAction.CLOSE_RAIL,
            tvBackAction(
                TvRoute.Discover,
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
    fun noBackStateWalksTheTopLevelRoutes() {
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
                library =
                    TvLibraryBackAccess(
                        pathDepth = { pathDepth },
                        selectedLibraryId = { "shows" },
                        popPath = { pathDepth -= 1 },
                    ),
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
        assertFalse(dispatcher.dispatch())
        assertTrue(holder.state.railExpanded)
        assertEquals(3, cancellations)
    }
}
