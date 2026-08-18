package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvNavigationRailStateTest {
    @Test
    fun startsCollapsedForContentFirstNavigation() {
        assertFalse(TvFocusCoordinator<String>().isRailVisible)
    }

    @Test
    fun compactRailIsDisplayOnlyUntilOpened() {
        assertFalse(tvNavigationRailItemsFocusable(expanded = false))
        assertTrue(tvNavigationRailItemsFocusable(expanded = true))
    }

    @Test
    fun homeRequestsContentFocusWhenAsyncHeroArrives() {
        assertFalse(shouldRequestHomeEntryFocus(hasRecentContent = false, railOpen = false))
        assertFalse(shouldRequestHomeEntryFocus(hasRecentContent = true, railOpen = true))
        assertTrue(shouldRequestHomeEntryFocus(hasRecentContent = true, railOpen = false))
    }

    @Test
    fun homeEntryFocusIsConsumedOnlyOnce() {
        val gate = TvHomeEntryFocusGate()

        assertTrue(gate.consume(hasRecentContent = true, railOpen = false))
        assertFalse(gate.consume(hasRecentContent = true, railOpen = true))
        assertFalse(gate.consume(hasRecentContent = true, railOpen = false))
    }

    @Test
    fun selectingContentHidesRailAndLeftEdgeRestoresIt() {
        val state = TvFocusCoordinator<String>(initiallyRailVisible = true)

        assertTrue(state.isRailVisible)
        state.closeRail()
        assertFalse(state.isRailVisible)
        state.openRail()
        assertTrue(state.isRailVisible)
    }
}
