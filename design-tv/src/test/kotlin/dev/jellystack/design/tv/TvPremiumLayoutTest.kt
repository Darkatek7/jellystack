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
        assertEquals(300, tvHomeHeroHeightDp())
        assertEquals(377, tvHomeFirstCardTopDp())
        assertTrue(tvHomeFirstCardTopDp() < 540)
    }

    @Test
    fun browseRowsStartBelowThePreviewStageAndFitTheViewport() {
        val heroBottom = TvLayoutTokens.SafeInsets.vertical.value + tvHomeHeroHeightDp()
        val cardHeight = TvLayoutTokens.LandscapeArtworkHeight.value + TvLayoutTokens.LandscapeMetadataBandHeight.value
        val focusedCardBottom =
            tvHomeFirstCardTopDp() + cardHeight * TvLayoutTokens.FOCUS_SCALE + TvLayoutTokens.FocusHaloPadding.value

        assertTrue(TV_CINEMATIC_ROWS_TOP.value >= heroBottom)
        assertEquals(TV_CINEMATIC_ROWS_TOP, TV_CINEMATIC_STAGE_HEIGHT)
        assertTrue(focusedCardBottom <= 540f, "first card bottom $focusedCardBottom exceeds a 540 dp viewport")
    }
}
