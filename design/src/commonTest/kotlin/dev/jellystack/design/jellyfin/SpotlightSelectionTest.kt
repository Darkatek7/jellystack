package dev.jellystack.design.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotlightSelectionTest {
    @Test
    fun selectionSurvivesReorderingByStableId() {
        assertEquals(
            "movie-2",
            reconcileSpotlightSelection(
                selectedId = "movie-2",
                candidateIds = listOf("movie-3", "movie-2", "movie-1"),
            ),
        )
    }

    @Test
    fun missingSelectionFallsBackToFirstCandidate() {
        assertEquals(
            "movie-3",
            reconcileSpotlightSelection(
                selectedId = "removed",
                candidateIds = listOf("movie-3", "movie-2"),
            ),
        )
    }

    @Test
    fun emptyCandidatesClearSelection() {
        assertNull(reconcileSpotlightSelection(selectedId = "removed", candidateIds = emptyList()))
    }
}
