package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvPremiumLayoutTest {
    @Test
    fun detailActionsFitNarrowTvViewport() {
        val availableWidth = 960 - 92 - 36

        assertTrue(tvDetailActionRowRequiredWidthDp() <= availableWidth)
    }

    @Test
    fun compactActionContentFitsAtTwoHundredPercentFontScale() {
        assertTrue(tvCompactActionRequiredHeightDp(fontScale = 2f) <= 72f)
    }

    @Test
    fun compactActionLabelsFitAtTwoHundredPercentFontScale() {
        assertTrue(
            tvCompactActionRequiredWidthDp(characterCount = "Favorite".length, fontScale = 2f) <=
                TV_DETAIL_COMPACT_ACTION_WIDTH_DP,
        )
    }

    @Test
    fun firstHomeRowStartsInsideAStandardTvViewport() {
        assertEquals(360, tvHomeHeroHeightDp())
        assertEquals(446, tvHomeFirstCardTopDp())
        assertTrue(tvHomeFirstCardTopDp() < 540)
    }
}
