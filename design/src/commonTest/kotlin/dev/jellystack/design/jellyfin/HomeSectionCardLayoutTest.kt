package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.HomeSectionViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSectionCardLayoutTest {
    @Test
    fun portraitCardsReserveAStableMetadataRegion() {
        val layout = homeSectionCardLayout(HomeSectionViewMode.PORTRAIT, compact = true)

        assertEquals(78, layout.metadataHeightDp)
        assertFalse(layout.usesOverlay)
        assertEquals(291f, layout.totalHeightDp)
    }

    @Test
    fun landscapeSquareAndSmallCardsUseFixedArtworkOverlays() {
        listOf(
            HomeSectionViewMode.LANDSCAPE,
            HomeSectionViewMode.SQUARE,
            HomeSectionViewMode.SMALL,
        ).forEach { mode ->
            val layout = homeSectionCardLayout(mode, compact = true)
            assertTrue(layout.usesOverlay)
            assertEquals(0, layout.metadataHeightDp)
        }
    }

    @Test
    fun expandedLayoutsRemainDeterministicPerViewMode() {
        HomeSectionViewMode.entries.forEach { mode ->
            assertEquals(
                homeSectionCardLayout(mode, compact = false),
                homeSectionCardLayout(mode, compact = false),
            )
        }
    }
}
