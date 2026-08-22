package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvCarouselAutoAdvanceTest {
    @Test
    fun autoAdvanceDisabledWhenFocusedOrPreviewing() {
        assertNull(tvCarouselAutoAdvanceDelayMs(heroFocused = true, candidateCount = 5, previewActive = false))
        assertNull(tvCarouselAutoAdvanceDelayMs(heroFocused = false, candidateCount = 5, previewActive = true))
    }

    @Test
    fun autoAdvanceDisabledForSingleCandidate() {
        assertNull(tvCarouselAutoAdvanceDelayMs(heroFocused = false, candidateCount = 1, previewActive = false))
        assertNull(tvCarouselAutoAdvanceDelayMs(heroFocused = false, candidateCount = 0, previewActive = false))
    }

    @Test
    fun autoAdvanceUsesFixedDelayWhenUnfocused() {
        val delay =
            tvCarouselAutoAdvanceDelayMs(heroFocused = false, candidateCount = 4, previewActive = false)
        assertEquals(TV_CAROUSEL_AUTO_ADVANCE_MS, delay)
    }

    @Test
    fun advanceWrapsToFirstAfterLast() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", advanceTvHomeCarousel(ids, "a"))
        assertEquals("c", advanceTvHomeCarousel(ids, "b"))
        assertEquals("a", advanceTvHomeCarousel(ids, "c"))
    }

    @Test
    fun advanceHandlesUnknownOrMissingSelection() {
        val ids = listOf("a", "b")
        assertEquals("a", advanceTvHomeCarousel(ids, null))
        assertEquals("a", advanceTvHomeCarousel(ids, "gone"))
        assertNull(advanceTvHomeCarousel(emptyList(), "a"))
    }
}
