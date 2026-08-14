package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvHomeCarouselSelectionTest {
    @Test
    fun autoCycleOnlyRunsWithMultipleCandidatesAndNoPauseReason() {
        assertTrue(
            shouldAutoCycleTvHomeCarousel(
                enabled = true,
                candidateCount = 2,
                railOpen = false,
                previewPlaying = false,
                heroFocused = false,
            ),
        )
        assertFalse(shouldAutoCycleTvHomeCarousel(true, 1, false, false, false))
        assertFalse(shouldAutoCycleTvHomeCarousel(true, 2, true, false, false))
        assertFalse(shouldAutoCycleTvHomeCarousel(true, 2, false, true, false))
        assertFalse(shouldAutoCycleTvHomeCarousel(true, 2, false, false, true))
    }

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
