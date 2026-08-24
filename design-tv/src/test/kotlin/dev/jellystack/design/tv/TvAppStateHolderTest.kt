@file:Suppress("MaxLineLength")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvAppStateHolderTest {
    @Test
    fun closingAccountGenerationCancelsOldPrivatePublishersBeforeNextAccountStarts() =
        runTest {
            val accountA =
                TvAccountGeneration(
                    TvAuthenticatedEnvironmentIdentity("same-url-a", "principal-a"),
                    backgroundScope,
                )
            val oldLoadStarted = CompletableDeferred<Unit>()
            val releaseOldLoad = CompletableDeferred<Unit>()
            val accountAShelves = mutableListOf<String>()
            accountA.scope.launch {
                oldLoadStarted.complete(Unit)
                releaseOldLoad.await()
                accountAShelves += "account-a-art-token-progress-detail-request"
            }
            oldLoadStarted.await()

            accountA.close()
            val accountB =
                TvAccountGeneration(
                    TvAuthenticatedEnvironmentIdentity("same-url-b", "principal-b"),
                    backgroundScope,
                )
            val accountBShelves = mutableListOf<String>()
            releaseOldLoad.complete(Unit)
            runCurrent()

            assertTrue(accountAShelves.isEmpty())
            assertTrue(accountBShelves.isEmpty())
            accountB.close()
        }

    @Test
    fun navigationActionsProduceImmutableDeterministicState() {
        val holder = TvAppStateHolder()
        val initial = holder.state

        holder.push(TvRoute.Library("movies", "Movies"))
        holder.push(TvRoute.JellyfinDetail("movie-1"))

        assertEquals(TvRoute.Home, initial.currentRoute)
        assertEquals(
            listOf(TvRoute.Home, TvRoute.Library("movies", "Movies"), TvRoute.JellyfinDetail("movie-1")),
            holder.state.backStack,
        )
        assertEquals(TvRoute.JellyfinDetail("movie-1"), holder.state.currentRoute)
        assertTrue(holder.popRoute())
        assertEquals(TvRoute.Library("movies", "Movies"), holder.state.currentRoute)

        holder.selectTopLevel(TvRoute.Search)

        assertEquals(listOf(TvRoute.Search), holder.state.backStack)
        assertFalse(holder.popRoute())
    }

    @Test
    fun connectionsOpenedFromDiscoverReturnsToDiscover() {
        val holder = TvAppStateHolder()

        holder.selectTopLevel(TvRoute.Discover)
        holder.push(tvConnectionsSettingsRoute())

        assertEquals(tvConnectionsSettingsRoute(), holder.state.currentRoute)
        assertTrue(holder.popRoute())
        assertEquals(TvRoute.Discover, holder.state.currentRoute)
    }

    @Test
    fun lifecycleAndRailActionsAreExplicit() {
        val holder = TvAppStateHolder()

        assertTrue(holder.state.isForeground)
        holder.openRail()
        assertTrue(holder.state.railExpanded)
        holder.onBackgrounded()
        assertFalse(holder.state.isForeground)
        assertFalse(holder.state.railExpanded)

        holder.onForegrounded()
        assertTrue(holder.state.isForeground)
        assertFalse(holder.state.railExpanded)
        holder.closeRail()
        assertFalse(holder.state.railExpanded)
    }

    @Test
    fun recreationSerializesRoutesAndSemanticFocusAnchor() {
        val holder = TvAppStateHolder()
        holder.push(TvRoute.Library("shows", "Shows"))
        holder.push(TvRoute.JellyfinDetail("episode-9"))
        holder.focusMemory.remember(
            routeKey = "jellyfin-detail:episode-9",
            anchor = TvFocusAnchor("episodes", "episode-9", TvFocusDestination.SECTION_ITEM),
            horizontalCenter = 640f,
            horizontalIndex = 4,
        )

        val encoded = TvAppStatePersistence.encode(holder.snapshot())
        val restored = TvAppStateHolder(requireNotNull(TvAppStatePersistence.decode(encoded)))

        assertEquals(holder.state.backStack, restored.state.backStack)
        assertEquals(holder.state.currentRoute, restored.state.currentRoute)
        assertEquals(
            TvFocusAnchor("episodes", "episode-9", TvFocusDestination.SECTION_ITEM),
            restored.focusMemory.restore("jellyfin-detail:episode-9")?.anchor,
        )
        assertEquals(640f, restored.focusMemory.restore("jellyfin-detail:episode-9")?.horizontalCenter)
    }

    @Test
    fun wakePreservesRouteAndFocusWithoutReopeningRail() {
        val holder = TvAppStateHolder()
        holder.selectTopLevel(TvRoute.Discover)
        holder.focusMemory.remember(
            "discover",
            TvFocusAnchor("trending", "movie-4", TvFocusDestination.SECTION_ITEM),
            horizontalCenter = 420f,
            horizontalIndex = 2,
        )
        holder.openRail()

        holder.onBackgrounded()
        holder.onForegrounded()

        assertEquals(TvRoute.Discover, holder.state.currentRoute)
        assertEquals(
            "movie-4",
            holder.focusMemory
                .restore("discover")
                ?.anchor
                ?.itemId,
        )
        assertFalse(holder.state.railExpanded)
    }

    @Test
    fun generationResetDropsPriorProfileNavigationFocusAndDetailIdentity() {
        val holder = TvAppStateHolder()
        val detail = item("private-item")
        holder.push(TvRoute.JellyfinDetail(detail.id))
        holder.openRail()
        holder.rememberDetailSource(detail)
        holder.focusMemory.remember("jellyfin-detail:private-item", "cast", "person-1")

        holder.resetForGeneration(8L)

        assertEquals(8L, holder.state.activeProfileGeneration)
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
        assertFalse(holder.state.railExpanded)
        assertNull(holder.focusMemory.restore("jellyfin-detail:private-item"))
        assertNull(holder.detailSource("private-item"))
    }

    @Test
    fun duplicatePushAndGenerationResetAreIdempotent() {
        val holder = TvAppStateHolder()
        holder.push(TvRoute.Search)
        val pushed = holder.state

        holder.push(TvRoute.Search)
        assertSame(pushed, holder.state)

        holder.resetForGeneration(3L)
        val reset = holder.state
        holder.resetForGeneration(3L)
        assertSame(reset, holder.state)
    }

    @Test
    fun sameAuthenticatedEnvironmentSurvivesProcessRecreation() {
        val identity = TvAuthenticatedEnvironmentIdentity("connection-1", "user-1")
        val holder = TvAppStateHolder()
        assertTrue(holder.activateEnvironment(identity))
        holder.push(TvRoute.Search)
        holder.focusMemory.remember("search", "results", "movie-1")

        val restored =
            TvAppStateHolder(
                requireNotNull(TvAppStatePersistence.decode(TvAppStatePersistence.encode(holder.snapshot()))),
            )

        assertFalse(restored.activateEnvironment(identity))
        assertEquals(TvRoute.Search, restored.state.currentRoute)
        assertEquals("movie-1", restored.focusMemory.restore("search")?.itemId)
        assertEquals(identity, restored.state.environmentIdentity)
    }

    @Test
    fun authenticatedPrincipalOrConnectionChangeStartsCleanGeneration() {
        val holder = TvAppStateHolder()
        holder.activateEnvironment(TvAuthenticatedEnvironmentIdentity("connection-1", "user-1"))
        holder.push(TvRoute.Search)
        holder.focusMemory.remember("search", "results", "private")
        val firstGeneration = holder.state.activeProfileGeneration

        assertTrue(holder.activateEnvironment(TvAuthenticatedEnvironmentIdentity("connection-1", "user-2")))
        assertEquals(firstGeneration + 1, holder.state.activeProfileGeneration)
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
        assertNull(holder.focusMemory.restore("search"))

        holder.push(TvRoute.Discover)
        assertTrue(holder.activateEnvironment(TvAuthenticatedEnvironmentIdentity("connection-2", "user-2")))
        assertEquals(firstGeneration + 2, holder.state.activeProfileGeneration)
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
    }

    @Test
    fun persistenceMigratesLegacyRoutesAndToleratesUnknownFields() {
        val legacyRoutes = listOf(TvRoute.Home, TvRoute.Library("movies", "Movies"))
        assertEquals(legacyRoutes, TvAppStatePersistence.decode(TvRouteBackStack.encode(legacyRoutes))?.backStack)

        val identity = TvAuthenticatedEnvironmentIdentity("connection-1", "user-1")
        val encoded =
            TvAppStatePersistence.encode(
                TvAppStateSnapshot(
                    backStack = listOf(TvRoute.Search),
                    environmentIdentity = identity,
                ),
            )
        assertTrue("\"version\":1" in encoded)
        val unversionedSnapshot = encoded.substringAfter("\"snapshot\":").dropLast(1)
        assertEquals(listOf(TvRoute.Search), TvAppStatePersistence.decode(unversionedSnapshot)?.backStack)
        val futureCompatible =
            encoded
                .replace("\"version\":1", "\"version\":99")
                .dropLast(1) + ",\"futureField\":{\"value\":42}}"
        assertEquals(listOf(TvRoute.Search), TvAppStatePersistence.decode(futureCompatible)?.backStack)
        assertEquals(identity, TvAppStatePersistence.decode(futureCompatible)?.environmentIdentity)
        assertNull(TvAppStatePersistence.decode("{definitely-not-json"))
    }

    @Test
    fun legacyRoutesBindToFirstIdentityButCredentialLossClearsPrivateState() {
        val legacyRoutes = listOf(TvRoute.Home, TvRoute.Library("movies"))
        val holder = TvAppStateHolder(requireNotNull(TvAppStatePersistence.decode(TvRouteBackStack.encode(legacyRoutes))))
        holder.focusMemory.remember("library:movies", "items", "private")

        assertTrue(holder.activateEnvironment(TvAuthenticatedEnvironmentIdentity("connection", "user")))
        assertEquals(legacyRoutes, holder.state.backStack)
        assertNull(holder.focusMemory.restore("library:movies"))

        holder.rememberDetailSource(item("private-detail"))
        holder.push(TvRoute.JellyfinDetail("private-detail"))
        assertTrue(holder.deactivateEnvironment())
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
        assertNull(holder.detailSource("private-detail"))
        assertNull(holder.state.environmentIdentity)
    }

    @Test
    fun legacyRoutesAreClearedWhenNoAuthenticatedIdentityIsActive() {
        val legacyRoutes = listOf(TvRoute.Home, TvRoute.JellyfinDetail("private-detail"))
        val holder = TvAppStateHolder(requireNotNull(TvAppStatePersistence.decode(TvRouteBackStack.encode(legacyRoutes))))
        val initialGeneration = holder.state.activeProfileGeneration

        assertTrue(holder.deactivateEnvironment())
        assertEquals(listOf(TvRoute.Home), holder.state.backStack)
        assertEquals(initialGeneration + 1L, holder.state.activeProfileGeneration)
        assertFalse(holder.deactivateEnvironment())
    }

    @Test
    fun persistenceEnvelopeRoundTripsAllDurableState() {
        val snapshot =
            TvAppStateSnapshot(
                backStack = listOf(TvRoute.Discover),
                railExpanded = true,
                activeProfileGeneration = 7,
                isForeground = false,
                environmentIdentity = TvAuthenticatedEnvironmentIdentity("connection", "user"),
                focusSnapshots =
                    mapOf(
                        "discover" to
                            TvFocusSnapshot(
                                TvFocusAnchor("trending", "movie", TvFocusDestination.SECTION_ITEM),
                                2,
                                3,
                                640f,
                            ),
                    ),
            )

        assertEquals(snapshot, TvAppStatePersistence.decode(TvAppStatePersistence.encode(snapshot)))
    }

    @Test
    fun userRailMovementCancelsAnInFlightRestoration() =
        runTest {
            val restorationStarted = CompletableDeferred<Unit>()
            val releaseRestoration = CompletableDeferred<Unit>()
            val coordinator =
                TvFocusCoordinator<String>(
                    awaitFocusFrame = {
                        restorationStarted.complete(Unit)
                        releaseRestoration.await()
                    },
                )
            coordinator.register("home", targetId = "home:card", target = "content")
            val requested = mutableListOf<String>()

            val restoration =
                async {
                    coordinator.restoreFocus("home", preferredTargetId = "home:card") {
                        requested += it
                        true
                    }
                }
            restorationStarted.await()

            coordinator.onUserMovement()
            releaseRestoration.complete(Unit)

            assertEquals(TvFocusRestoration.Cancelled, restoration.await())
            assertTrue(requested.isEmpty())
        }

    private fun item(id: String) =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = "Private",
            sortName = null,
            overview = null,
            type = "Movie",
            mediaType = null,
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = null,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = null,
            seriesName = null,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
        )
}
