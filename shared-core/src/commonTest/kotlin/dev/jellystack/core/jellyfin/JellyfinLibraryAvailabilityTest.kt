package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JellyfinLibraryAvailabilityTest {
    @Test
    fun liveTvLibrariesAreRemovedAndOthersKeepTheirOrder() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        library("tv", "livetv"),
                        library("movies", "movies"),
                        library("guide", "LiveTv"),
                        library("mixed", null),
                    ),
            )

        assertEquals(listOf("movies", "mixed"), state.withBrowsableLibraries().libraries.map { it.id })
    }

    @Test
    fun stateWithoutLiveTvIsReturnedUnchanged() {
        val state = JellyfinHomeState(libraries = listOf(library("movies", "movies")))

        assertSame(state, state.withBrowsableLibraries())
    }

    private fun library(
        id: String,
        collectionType: String?,
    ) = JellyfinLibrary(id = id, name = id, collectionType = collectionType, itemCount = null, primaryImageTag = null)
}
