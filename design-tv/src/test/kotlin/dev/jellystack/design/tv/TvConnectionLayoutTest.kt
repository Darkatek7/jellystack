package dev.jellystack.design.tv

import dev.jellystack.core.server.JellyfinSignInMethod
import kotlin.test.Test
import kotlin.test.assertTrue

class TvConnectionLayoutTest {
    @Test
    fun passwordFormUsesCompactMetricsThatFitA720DpTvWindow() {
        val layout = connectionFormLayout(JellyfinSignInMethod.PASSWORD)

        assertTrue(layout.cardVerticalPadding.value <= 24f)
        assertTrue(layout.itemSpacing.value <= 10f)
        assertTrue(layout.textFieldHeight.value <= 56f)
    }
}
