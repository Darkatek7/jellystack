package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvAppStateHolderTest {
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
