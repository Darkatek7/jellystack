package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSeerrPresentationTest {
    @Test
    fun backdropWinsOverPosterForLandscapeMediaCards() {
        val artwork = tvSeerrCardArtwork(posterPath = "/poster.jpg", backdropPath = "/backdrop.jpg")

        assertEquals("/backdrop.jpg", artwork.path)
        assertTrue(artwork.isBackdrop)
        assertEquals(TvMediaCardArtworkFit.CROP, artwork.fit)
    }

    @Test
    fun posterIsContainedOnlyWhenBackdropIsUnavailable() {
        val artwork = tvSeerrCardArtwork(posterPath = "/poster.jpg", backdropPath = null)

        assertEquals("/poster.jpg", artwork.path)
        assertFalse(artwork.isBackdrop)
        assertEquals(TvMediaCardArtworkFit.CONTAIN_PORTRAIT, artwork.fit)
    }
}
