package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvNavigationRailStateTest {
    @Test
    fun selectingContentHidesRailAndLeftEdgeRestoresIt() {
        val state = TvNavigationRailState(initiallyVisible = true)

        assertTrue(state.isVisible)
        state.onDestinationSelected()
        assertFalse(state.isVisible)
        state.onContentLeftEdge()
        assertTrue(state.isVisible)
    }
}
