package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvFocusMemoryTest {
    @Test
    fun restoresRememberedItemWhenItStillExists() {
        val memory = TvFocusMemory()
        memory.remember("home", "continue", "episode-2", verticalIndex = 3, horizontalIndex = 1)

        assertEquals("episode-2", memory.resolveItem("home", listOf("episode-1", "episode-2")))
        assertEquals(3, memory.restore("home")?.verticalIndex)
    }

    @Test
    fun removedItemFallsBackToNearestRememberedIndex() {
        val memory = TvFocusMemory()
        memory.remember("library:movies", "grid", "removed", horizontalIndex = 2)

        assertEquals("movie-3", memory.resolveItem("library:movies", listOf("movie-1", "movie-2", "movie-3")))
        assertNull(memory.resolveItem("missing", emptyList()))
    }

    @Test
    fun libraryPathsKeepIndependentGridSnapshots() {
        val memory = TvFocusMemory()
        val seasonOne = TvRoute.Library("shows").focusRouteKey(listOf("show", "season-1"))
        val seasonTwo = TvRoute.Library("shows").focusRouteKey(listOf("show", "season-2"))
        memory.remember(seasonOne, "grid", "episode-2", horizontalIndex = 1)
        memory.remember(seasonTwo, "grid", "episode-8", horizontalIndex = 7)

        assertEquals("episode-2", memory.restore(seasonOne)?.itemId)
        assertEquals("episode-8", memory.restore(seasonTwo)?.itemId)
    }
}
