package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvHomeCarouselSelectionTest {
    @Test
    fun reconcileReturnsNullForAnEmptyCandidateList() {
        assertNull(reconcileTvHomeCarouselSelection(emptyList(), currentId = "current"))
    }

    @Test
    fun moveReturnsNullForAnEmptyCandidateList() {
        assertNull(
            moveTvHomeCarouselSelection(
                candidateIds = emptyList(),
                currentId = "current",
                direction = TvHomeCarouselDirection.NEXT,
            ),
        )
    }

    @Test
    fun reconcileFallsBackToFirstCandidateWhenCurrentIdIsMissing() {
        assertEquals(
            "first",
            reconcileTvHomeCarouselSelection(listOf("first", "second"), currentId = "missing"),
        )
    }

    @Test
    fun reconcileKeepsCurrentIdWhenCandidatesAreReordered() {
        assertEquals(
            "second",
            reconcileTvHomeCarouselSelection(listOf("third", "second", "first"), currentId = "second"),
        )
    }

    @Test
    fun reconcileFallsBackToFirstCandidateWhenCurrentIdWasRemoved() {
        assertEquals(
            "replacement",
            reconcileTvHomeCarouselSelection(listOf("replacement", "other"), currentId = "removed"),
        )
    }

    @Test
    fun nextKeepsTheOnlyCandidateSelected() {
        assertEquals(
            "only",
            moveTvHomeCarouselSelection(
                candidateIds = listOf("only"),
                currentId = "only",
                direction = TvHomeCarouselDirection.NEXT,
            ),
        )
    }

    @Test
    fun nextWrapsFromLastCandidateToFirst() {
        assertEquals(
            "first",
            moveTvHomeCarouselSelection(
                candidateIds = listOf("first", "second", "last"),
                currentId = "last",
                direction = TvHomeCarouselDirection.NEXT,
            ),
        )
    }

    @Test
    fun previousWrapsFromFirstCandidateToLast() {
        assertEquals(
            "last",
            moveTvHomeCarouselSelection(
                candidateIds = listOf("first", "second", "last"),
                currentId = "first",
                direction = TvHomeCarouselDirection.PREVIOUS,
            ),
        )
    }
}
