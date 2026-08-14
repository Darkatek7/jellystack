package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvHomeCarouselSelectionTest {
    @Test
    fun autoCycleIntervalUsesConfiguredValueAboveMinimum() {
        assertEquals(11_000L, tvHomeCarouselIntervalMillis(intervalSeconds = 11))
    }

    @Test
    fun autoCycleIntervalClampsConfiguredValueToSixSecondMinimum() {
        assertEquals(6_000L, tvHomeCarouselIntervalMillis(intervalSeconds = 2))
    }

    @Test
    fun previewPauseIncludesArmedAndPlayingButNotTerminalStates() {
        val target = TvTrailerPreviewTarget("server", "item", isEpisode = false, seriesId = null)

        assertTrue(TvTrailerPreviewState.Armed(target).blocksTvHomeCarouselAutoCycle())
        assertTrue(TvTrailerPreviewState.Playing(target).blocksTvHomeCarouselAutoCycle())
        assertFalse(TvTrailerPreviewState.Idle.blocksTvHomeCarouselAutoCycle())
        assertFalse(TvTrailerPreviewState.Unavailable(target).blocksTvHomeCarouselAutoCycle())
    }

    @Test
    fun autoCycleOnlyRunsWithMultipleCandidatesAndNoPauseReason() {
        assertTrue(
            shouldAutoCycleTvHomeCarousel(
                enabled = true,
                candidateCount = 2,
                railOpen = false,
                previewActive = false,
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
