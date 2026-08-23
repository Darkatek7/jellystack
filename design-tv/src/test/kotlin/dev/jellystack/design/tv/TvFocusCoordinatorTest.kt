package dev.jellystack.design.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TvFocusCoordinatorTest {
    @Test
    fun semanticRequesterRegistrationSurvivesCoordinatorDisposalAndReorder() =
        runTest {
            val memory = TvFocusMemory()
            memory.remember(
                "home",
                TvFocusAnchor("continue", "episode-2", TvFocusDestination.SECTION_ITEM),
                horizontalCenter = 400f,
                horizontalIndex = 1,
            )
            val recreated = TvFocusCoordinator<String>()
            recreated.register(
                "home",
                tvHomeCardTargetId("continue", "episode-3"),
                "requester-3",
                focusTarget =
                    TvFocusTarget(
                        tvHomeCardTargetId("continue", "episode-3"),
                        TvFocusAnchor("continue", "episode-3", TvFocusDestination.SECTION_ITEM),
                        430f,
                        2,
                    ),
            )
            recreated.register(
                "home",
                tvHomeCardTargetId("continue", "episode-1"),
                "requester-1",
                focusTarget =
                    TvFocusTarget(
                        tvHomeCardTargetId("continue", "episode-1"),
                        TvFocusAnchor("continue", "episode-1", TvFocusDestination.SECTION_ITEM),
                        100f,
                        0,
                    ),
            )
            val preferred = memory.resolve("home", recreated.focusTargets("home"))?.targetId

            assertEquals(
                TvFocusRestoration.Focused("requester-3"),
                recreated.restoreFocus("home", preferredTargetId = preferred) { true },
            )
        }

    @Test
    fun semanticResolutionIncludesOffscreenMaterializerTargets() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            coordinator.registerMaterializer(
                routeKey = "home",
                ownerId = "continue-row",
                targetIds =
                    linkedSetOf(
                        tvHomeCardTargetId("continue", "episode-1"),
                        tvHomeCardTargetId("continue", "episode-2"),
                        tvHomeCardTargetId("continue", "episode-3"),
                    ),
                materialize = { false },
            )

            assertEquals(
                listOf("episode-1", "episode-2", "episode-3"),
                coordinator.focusTargets("home").map { it.anchor.itemId },
            )
        }

    @Test
    fun materializerOrderDefinesFirstActionableWhenOffscreenDescriptorsHaveNoCoordinates() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val first = tvHomeCardTargetId("continue", "episode-1")
            val second = tvHomeCardTargetId("continue", "episode-2")
            val third = tvHomeCardTargetId("continue", "episode-3")
            coordinator.register(
                routeKey = "home",
                targetId = first,
                target = "requester-1",
                focusTarget = tvFocusTarget(first, horizontalCenter = 200f),
            )
            coordinator.registerMaterializer(
                routeKey = "home",
                ownerId = "continue-row",
                targetIds = linkedSetOf(first, second, third),
                materialize = { false },
            )

            val targets = coordinator.focusTargets("home")
            assertEquals(listOf(first, second, third), targets.map { it.targetId })
            assertEquals(listOf(0f, 1f, 2f), targets.map { it.horizontalCenter })
            assertEquals(listOf(0, 1, 2), targets.map { it.horizontalIndex })
        }

    @Test
    fun secondQueuedRestorationCancelsWhenUserMovesWhileItWaitsForLock() =
        runTest {
            val firstFrame = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            var frames = 0
            val coordinator =
                TvFocusCoordinator<String>(
                    awaitFocusFrame = {
                        frames += 1
                        if (frames == 1) {
                            firstFrame.complete(Unit)
                            releaseFirst.await()
                        }
                    },
                )
            coordinator.register("home", "card", "requester")
            val requested = mutableListOf<String>()
            val first = async { coordinator.restoreFocus("home", preferredTargetId = "card") { false } }
            firstFrame.await()
            val second =
                async {
                    coordinator.restoreFocus("home", preferredTargetId = "card") {
                        requested += it
                        true
                    }
                }
            launch { coordinator.onUserMovement() }.join()
            releaseFirst.complete(Unit)

            first.await()
            assertEquals(TvFocusRestoration.Cancelled, second.await())
            assertTrue(requested.isEmpty())
        }

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
    fun settingsLandingAndCategoriesRestoreTheirExactTargetsIndependently() =
        runTest {
            val coordinator = TvFocusCoordinator<String>()
            val landingRoute = TvRoute.Settings().focusRouteKey()
            val playbackRoute = tvSettingsRoute(TvSettingsCategory.PLAYBACK).focusRouteKey()
            val audioRoute = tvSettingsRoute(TvSettingsCategory.AUDIO_SUBTITLES).focusRouteKey()
            val landingTarget = tvSettingsCategoryTargetId(TvSettingsCategory.CONNECTIONS)
            val playbackTarget = tvSettingsControlTargetId("playback-speed")
            val audioTarget = tvSettingsControlTargetId("subtitle-background")

            coordinator.register(landingRoute, landingTarget)
            coordinator.register(playbackRoute, playbackTarget)
            coordinator.register(audioRoute, audioTarget)
            coordinator.rememberFocused(landingRoute, landingTarget)
            coordinator.rememberFocused(playbackRoute, playbackTarget)
            coordinator.rememberFocused(audioRoute, audioTarget)

            assertEquals(TvFocusRestoration.Focused(landingTarget), coordinator.restoreFocus(landingRoute) { true })
            assertEquals(TvFocusRestoration.Focused(playbackTarget), coordinator.restoreFocus(playbackRoute) { true })
            assertEquals(TvFocusRestoration.Focused(audioTarget), coordinator.restoreFocus(audioRoute) { true })
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
            assertEquals(listOf("remembered", "remembered", "remembered", "first"), attempted)

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
            assertEquals(3, requests)
        }

    @Test
    fun transientFocusRejectionWaitsForFramesAndRetriesTheExactTarget() =
        runTest {
            val events = mutableListOf<String>()
            val coordinator =
                TvFocusCoordinator<String>(
                    focusRequestAttempts = 3,
                    awaitFocusFrame = { events += "frame" },
                )
            coordinator.register("home", targetId = "card:20", target = "card-requester")
            coordinator.register("home", targetId = "hero", target = "hero-requester", fallback = true)
            var exactAttempts = 0

            val focused =
                coordinator.restoreFocus("home", preferredTargetId = "card:20") { requester ->
                    events += "focus:$requester"
                    if (requester == "card-requester") ++exactAttempts == 2 else true
                }

            assertEquals(TvFocusRestoration.Focused("card-requester"), focused)
            assertEquals(
                listOf("frame", "focus:card-requester", "frame", "focus:card-requester"),
                events,
            )
        }

    @Test
    fun exhaustedExactTargetRetriesUseFallbackOnlyAfterTheBoundedAttempts() =
        runTest {
            val attempted = mutableListOf<String>()
            var frames = 0
            val coordinator =
                TvFocusCoordinator<String>(
                    focusRequestAttempts = 3,
                    awaitFocusFrame = { frames += 1 },
                )
            coordinator.register("library:movies", targetId = "item:20", target = "item-requester")
            coordinator.register("library:movies", targetId = "first", target = "first-requester", fallback = true)

            val focused =
                coordinator.restoreFocus("library:movies", preferredTargetId = "item:20") { requester ->
                    attempted += requester
                    requester == "first-requester"
                }

            assertEquals(TvFocusRestoration.Focused("first-requester"), focused)
            assertEquals(
                listOf("item-requester", "item-requester", "item-requester", "first-requester"),
                attempted,
            )
            assertEquals(4, frames)
        }

    @Test
    fun routeKeysSeparateLibraryListIdsPathsSettingsAndOtherRoots() {
        assertEquals("home", TvRoute.Home.focusRouteKey())
        assertEquals("library:list", TvRoute.Library().focusRouteKey())
        assertEquals("library:movies", TvRoute.Library("movies").focusRouteKey())
        assertEquals(
            "library:movies/path:folder/season",
            TvRoute.Library("movies").focusRouteKey(listOf("folder", "season")),
        )
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
            coordinator.onUserMovement()
            assertEquals(TvFocusRestoration.Focused("retry"), coordinator.restoreFocus("library:error") { true })
        }
}
