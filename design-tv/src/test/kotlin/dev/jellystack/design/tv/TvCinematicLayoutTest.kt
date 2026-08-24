package dev.jellystack.design.tv

import dev.jellystack.core.preferences.MotionPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCinematicLayoutTest {
    @Test
    fun landscapeGeometryAndActionsMeetTenFootContract() {
        val geometry = tvCinematicGeometry()

        assertEquals(232f, geometry.artworkWidthDp)
        assertEquals(131f, geometry.artworkHeightDp)
        assertEquals(56f, geometry.metadataBandHeightDp)
        assertEquals(16f, geometry.cardSpacingDp)
        assertTrue(geometry.focusHaloPaddingDp >= 6f)
        assertTrue(geometry.metadataBandOpaque)
        assertTrue(geometry.minimumActionSizeDp >= 48f)
    }

    @Test
    fun stableKeysAndSelectionRemainIndependentFromFocus() {
        val selected = TvCinematicCard(id = "movie:1", title = "One", selected = true)
        val unselected = TvCinematicCard(id = "movie:2", title = "Two")
        val row = TvCinematicRow(id = "recent", title = "Recent", cards = listOf(selected, unselected))
        val state =
            TvCinematicBrowseState(
                rows = listOf(row),
                focusedAnchor = TvFocusAnchor("recent", "movie:2", TvFocusDestination.SECTION_ITEM),
            )

        assertTrue(
            state.rows
                .single()
                .cards
                .first()
                .selected,
        )
        assertFalse(
            state.rows
                .single()
                .cards
                .last()
                .selected,
        )
        assertEquals("movie:2", state.focusedCard?.id)
        assertEquals(listOf("recent"), state.rows.map(TvCinematicRow::id))
    }

    @Test
    fun motionPreferenceObeysSystemAndExplicitReducedMotion() {
        assertTrue(tvMotionReduced(MotionPreference.SYSTEM, systemAnimationsEnabled = false))
        assertTrue(tvMotionReduced(MotionPreference.REDUCED, systemAnimationsEnabled = true))
        assertFalse(tvMotionReduced(MotionPreference.FULL, systemAnimationsEnabled = true))

        val highContrast = tvCinematicMotion(reducedMotion = false, highContrastFocus = true)
        assertTrue(highContrast.focusRingWidthDp > tvCinematicMotion(false, false).focusRingWidthDp)
    }
}
