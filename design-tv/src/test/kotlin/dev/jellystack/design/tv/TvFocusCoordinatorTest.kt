package dev.jellystack.design.tv

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TvFocusCoordinatorTest {
    @Test
    fun restorationWaitsForLateMaterializerRegistration() =
        runTest {
            val coordinator = TvFocusCoordinator<String>(attachmentTimeoutMillis = 1_000)
            val restoration = async { coordinator.restoreFocus("rail", preferredTargetId = "rail:settings") { true } }
            runCurrent()
            assertFalse(restoration.isCompleted)

            coordinator.registerMaterializer(
                routeKey = "rail",
                ownerId = "rail-items",
                targetIds = setOf("rail:settings", "rail:home"),
                fallbackTargetIds = setOf("rail:home"),
            ) { targetId ->
                coordinator.register("rail", targetId = targetId, target = "$targetId-requester")
                true
            }

            assertEquals(TvFocusRestoration.Focused("rail:settings-requester"), restoration.await())
        }

    @Test
    fun rememberedOffscreenTargetIsMaterializedBeforeFallbackFocus() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val events = mutableListOf<String>()
            coordinator.register("home", targetId = "hero", target = "hero-requester", fallback = true)
            coordinator.register("home", targetId = "card:20", target = "old-card-requester")
            coordinator.rememberFocused("home", targetId = "card:20", target = "old-card-requester")
            coordinator.unregister("home", targetId = "card:20", target = "old-card-requester")
            coordinator.registerMaterializer(
                routeKey = "home",
                ownerId = "home-lists",
                targetIds = setOf("hero", "card:20"),
                fallbackTargetIds = setOf("hero"),
            ) { targetId ->
                events += "materialize:$targetId"
                coordinator.register("home", targetId = targetId, target = "new-card-requester")
                true
            }

            val result =
                coordinator.restoreFocus("home") { requester ->
                    events += "focus:$requester"
                    true
                }

            assertEquals(TvFocusRestoration.Focused("new-card-requester"), result)
            assertEquals(listOf("materialize:card:20", "focus:new-card-requester"), events)
        }

    @Test
    fun materializationWaitsForTargetRegistrationWithoutFocusingFallback() =
        runTest {
            val coordinator = TvFocusCoordinator<String>(attachmentTimeoutMillis = 1_000)
            val focused = mutableListOf<String>()
            coordinator.register("home", targetId = "hero", target = "hero-requester", fallback = true)
            coordinator.register("home", targetId = "card:20", target = "old-card-requester")
            coordinator.rememberFocused("home", targetId = "card:20", target = "old-card-requester")
            coordinator.unregister("home", targetId = "card:20", target = "old-card-requester")
            coordinator.registerMaterializer("home", "home-lists", setOf("hero", "card:20"), setOf("hero")) { true }

            val restoration =
                async {
                    coordinator.restoreFocus("home") { requester ->
                        focused += requester
                        true
                    }
                }
            runCurrent()

            assertFalse(restoration.isCompleted)
            assertTrue(focused.isEmpty())
            coordinator.register("home", targetId = "card:20", target = "attached-card-requester")

            assertEquals(TvFocusRestoration.Focused("attached-card-requester"), restoration.await())
            assertEquals(listOf("attached-card-requester"), focused)
        }

    @Test
    fun initialFocusWaitsForLateTargetAttachment() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val events = mutableListOf<String>()
            val restoration =
                async {
                    coordinator.restoreFocus("home") {
                        events += "focus:$it"
                        true
                    }
                }
            runCurrent()
            coordinator.register("home", "hero", fallback = true)

            assertEquals(TvFocusRestoration.Focused("hero"), restoration.await())
            assertEquals(listOf("focus:hero"), events)
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

            assertEquals(TvFocusRestoration.Focused("continue-3"), coordinator.restoreFocus("home") { true })
            assertEquals(TvFocusRestoration.Focused("audio"), coordinator.restoreFocus("settings") { true })
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

            assertEquals(TvFocusRestoration.Focused("new-audio"), coordinator.restoreFocus("settings") { true })
        }

    @Test
    fun settingsServerActionMemorySurvivesMultiServerReorder() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val serverA = tvSettingsServerActionTargetId("server-a", "remove")
            val serverB = tvSettingsServerActionTargetId("server-b", "remove")
            coordinator.register("settings:root", serverA, "old-a")
            coordinator.register("settings:root", serverB, "old-b")
            coordinator.rememberFocused("settings:root", serverB, "old-b")
            coordinator.unregister("settings:root", serverA, "old-a")
            coordinator.unregister("settings:root", serverB, "old-b")

            listOf(serverB to "new-b", serverA to "new-a").forEach { (targetId, requester) ->
                coordinator.register("settings:root", targetId, requester)
            }

            assertEquals(TvFocusRestoration.Focused("new-b"), coordinator.restoreFocus("settings:root") { true })
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
                coordinator.restoreFocus("library:movies") {
                    attempted += it
                    it == "first"
                }

            assertEquals(TvFocusRestoration.Focused("first"), focused)
            assertEquals(listOf("remembered", "first"), attempted)

            coordinator.unregister("library:movies", "remembered")
            assertEquals(TvFocusRestoration.Focused("first"), coordinator.restoreFocus("library:movies") { true })
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
    fun failedAttachedTargetReturnsBoundedFailure() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.register("discover", "connect", fallback = true)
            var requests = 0

            val focused =
                coordinator.restoreFocus("discover") {
                    requests += 1
                    false
                }

            assertEquals(TvFocusRestoration.Failed, focused)
            assertEquals(1, requests)
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
        assertEquals(tvRailTargetId(TvRoute.Library()), tvRailTargetId(TvRoute.Library("movies")))
        assertEquals(tvRailTargetId(TvRoute.Settings()), tvRailTargetId(TvRoute.Settings("playback")))
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

            assertEquals(
                TvFocusRestoration.Focused("empty-placeholder"),
                coordinator.restoreFocus("library:empty") { true },
            )
            coordinator.openRail()
            coordinator.closeRail()
            assertEquals(TvFocusRestoration.Focused("retry"), coordinator.restoreFocus("library:error") { true })
        }
}
