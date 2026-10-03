package dev.jellystack.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertTrue

class JellystackVisualTokensTest {
    @Test
    fun themeTextPairsMeetWcagContrast() {
        assertTrue(
            contrastRatio(
                JellystackThemeColors.lightOnBackground,
                JellystackThemeColors.lightBackground,
            ) >= 4.5,
        )
        assertTrue(
            contrastRatio(
                JellystackThemeColors.darkOnBackground,
                JellystackThemeColors.darkBackground,
            ) >= 4.5,
        )
        assertTrue(
            contrastRatio(
                Color.White,
                Color.Black.copy(alpha = 0.82f).compositeOver(Color.White),
            ) >= 4.5,
        )
    }
}
