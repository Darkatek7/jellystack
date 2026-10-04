package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvMetadataPresentationTest {
    @Test
    fun itemCountsAreLocalizedAndPluralized() {
        val english = TvStrings.current(AppLanguage.ENGLISH)
        val german = TvStrings.current(AppLanguage.GERMAN)

        assertEquals("1 item", english.itemCount(1))
        assertEquals("2 items", english.itemCount(2))
        assertEquals("1 Element", german.itemCount(1))
        assertEquals("2 Elemente", german.itemCount(2))
    }

    @Test
    fun invalidRatingsAreOmitted() {
        assertNull(tvRatingLabel(null))
        assertNull(tvRatingLabel(0.0))
        assertNull(tvRatingLabel(-1.0))
        assertNull(tvRatingLabel(Double.NaN))
        assertEquals("★ 8.4", tvRatingLabel(8.36))
    }
}
