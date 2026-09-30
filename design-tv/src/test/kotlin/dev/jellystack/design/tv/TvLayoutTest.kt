package dev.jellystack.design.tv

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvLayoutTest {
    @Test
    fun referenceViewportUsesTvSafeInsets() {
        val bounds = tvSafeBounds(widthDp = 960f, heightDp = 540f)

        assertEquals(48f, bounds.left)
        assertEquals(27f, bounds.top)
        assertEquals(912f, bounds.right)
        assertEquals(513f, bounds.bottom)
    }

    @Test
    fun expandedRailIsAStableOverlay() {
        assertEquals(228f, TvLayoutTokens.ExpandedRailWidth.value)
        assertEquals(0f, tvContentOffsetForRail(expanded = false))
        assertEquals(0f, tvContentOffsetForRail(expanded = true))
    }

    @Test
    fun focusAndActionTokensMeetTvContract() {
        assertTrue(TvLayoutTokens.FOCUS_SCALE in 1.05f..1.06f)
        assertTrue(TvLayoutTokens.MinimumActionSize.value >= 48f)
        assertTrue(tvContrastRatio(TvLayoutTokens.FocusLightRing, Color.Black) >= 3f)
        assertTrue(tvContrastRatio(TvLayoutTokens.FocusDarkRing, Color.White) >= 3f)
    }

    @Test
    fun settingsColumnsAdaptToLargeText() {
        assertEquals(3, tvSettingsColumnCount(availableWidthDp = 864f, fontScale = 1f))
        assertEquals(2, tvSettingsColumnCount(availableWidthDp = 864f, fontScale = 1.5f))
    }
}
