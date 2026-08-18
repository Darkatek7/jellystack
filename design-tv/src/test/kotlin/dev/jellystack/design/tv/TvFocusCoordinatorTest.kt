package dev.jellystack.design.tv

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvFocusCoordinatorTest {
    @Test
    fun initialFocusWaitsForAFrameAndLateTargetAttachment() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val events = mutableListOf<String>()

            val focused =
                coordinator.restoreContentFocus(
                    routeKey = "home",
                    awaitFrame = {
                        events += "frame"
                        coordinator.register("home", "hero", fallback = true)
                    },
                    requestFocus = {
                        events += "focus:$it"
                        true
                    },
                )

            assertEquals("hero", focused)
            assertEquals(listOf("frame", "focus:hero"), events)
        }

    @Test
    fun remembersExactAttachedTargetIndependentlyPerRoute() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.register("home", "hero", fallback = true)
            coordinator.register("home", "continue-3")
            coordinator.register("settings", "language", fallback = true)
            coordinator.register("settings", "audio")
            coordinator.rememberFocused("home", "continue-3")
            coordinator.rememberFocused("settings", "audio")

            assertEquals("continue-3", coordinator.restoreContentFocus("home", {}, { true }))
            assertEquals("audio", coordinator.restoreContentFocus("settings", {}, { true }))
        }

    @Test
    fun semanticTargetMemorySurvivesRouteDisposalAndRequesterRecreation() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.register("settings", targetId = "audio-control", target = "old-audio")
            coordinator.rememberFocused("settings", targetId = "audio-control", target = "old-audio")
            coordinator.unregister("settings", targetId = "audio-control", target = "old-audio")

            coordinator.register("settings", targetId = "entry", target = "new-entry", fallback = true)
            coordinator.register("settings", targetId = "audio-control", target = "new-audio")

            assertEquals("new-audio", coordinator.restoreContentFocus("settings", {}, { true }))
        }

    @Test
    fun railCloseFallsBackWhenRememberedTargetDisappearsOrRejectsFocus() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.register("library:movies", "first", fallback = true)
            coordinator.register("library:movies", "remembered")
            coordinator.rememberFocused("library:movies", "remembered")
            val attempted = mutableListOf<String>()

            val focused =
                coordinator.restoreContentFocus(
                    routeKey = "library:movies",
                    awaitFrame = {},
                    requestFocus = {
                        attempted += it
                        it == "first"
                    },
                )

            assertEquals("first", focused)
            assertEquals(listOf("remembered", "first"), attempted)

            coordinator.unregister("library:movies", "remembered")
            assertEquals("first", coordinator.restoreContentFocus("library:movies", {}, { true }))
        }

    @Test
    fun targetRegistrationRequestsRestoreOnlyWhenRememberedTargetIsNoLongerAttached() {
        val coordinator = TvFocusCoordinator<String>()

        assertTrue(coordinator.needsContentRestoration("home"))
        coordinator.register("home", "hero", fallback = true)
        assertTrue(coordinator.needsContentRestoration("home"))
        coordinator.rememberFocused("home", "hero")
        assertFalse(coordinator.needsContentRestoration("home"))
        coordinator.register("home", "card")
        assertFalse(coordinator.needsContentRestoration("home"))
        coordinator.unregister("home", "hero")
        assertTrue(coordinator.needsContentRestoration("home"))
    }

    @Test
    fun restoreRetriesOnlyWithinBoundAndNeverRequestsDetachedTargets() =
        runTest {
            val coordinator = TvFocusCoordinator<String>(maxRestoreAttempts = 2)
            coordinator.register("discover", "connect", fallback = true)
            var frames = 0
            var requests = 0

            val focused =
                coordinator.restoreContentFocus(
                    routeKey = "discover",
                    awaitFrame = { frames += 1 },
                    requestFocus = {
                        requests += 1
                        false
                    },
                )

            assertNull(focused)
            assertEquals(2, frames)
            assertEquals(2, requests)
        }

    @Test
    fun rapidRepeatedLeftAndRailCloseAreIdempotent() {
        val coordinator = TvFocusCoordinator<String>()

        assertTrue(coordinator.openRail(repeatCount = 0))
        assertFalse(coordinator.openRail(repeatCount = 1))
        assertFalse(coordinator.openRail(repeatCount = 0))
        assertTrue(coordinator.isRailVisible)
        assertTrue(coordinator.closeRail())
        assertFalse(coordinator.closeRail())
        assertFalse(coordinator.isRailVisible)
    }

    @Test
    fun routeKeysSeparateLibraryListIdsPathsSettingsAndOtherRoots() {
        assertEquals("home", TvRoute.Home.focusRouteKey())
        assertEquals("library:list", TvRoute.Library().focusRouteKey())
        assertEquals("library:movies", TvRoute.Library("movies").focusRouteKey())
        assertEquals("library:movies/path:folder/season", TvRoute.Library("movies").focusRouteKey(listOf("folder", "season")))
        assertEquals("settings:root", TvRoute.Settings().focusRouteKey())
        assertEquals("settings:playback", TvRoute.Settings("playback").focusRouteKey())
        assertEquals("discover", TvRoute.Discover.focusRouteKey())
        assertEquals("search", TvRoute.Search.focusRouteKey())
    }

    @Test
    fun gridAndSettingsLeftEdgesAreDerivedFromColumnCount() {
        assertTrue(isTvGridLeftEdge(itemIndex = 0, columnCount = 4))
        assertTrue(isTvGridLeftEdge(itemIndex = 4, columnCount = 4))
        assertFalse(isTvGridLeftEdge(itemIndex = 1, columnCount = 4))
        assertTrue(isTvGridLeftEdge(itemIndex = 3, columnCount = 3))
        assertFalse(isTvGridLeftEdge(itemIndex = 5, columnCount = 3))
    }

    @Test
    fun emptyAndErrorScreensKeepAFallbackTargetForRailRoundTrips() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.register("library:empty", "empty-placeholder", fallback = true)
            coordinator.register("library:error", "retry", fallback = true)

            assertEquals("empty-placeholder", coordinator.restoreContentFocus("library:empty", {}, { true }))
            coordinator.openRail()
            coordinator.closeRail()
            assertEquals("retry", coordinator.restoreContentFocus("library:error", {}, { true }))
        }
}
