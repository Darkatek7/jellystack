package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlayerPanelNavigationTest {
    @Test
    fun childOpenedFromMoreReturnsToMoreAndRestoresItsRow() {
        val state =
            TvPlayerPanelNavigation
                .closed()
                .openMore()
                .openFromMore(TvPlayerPanel.QUALITY)
                .back()

        assertEquals(TvPlayerPanel.MORE, state.current)
        assertEquals(TvPlayerPanel.QUALITY, state.restoreFocusTo)
    }

    @Test
    fun quickAudioPanelClosesDirectlyBackToPlayer() {
        val state = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.AUDIO).back()

        assertEquals(TvPlayerPanel.NONE, state.current)
    }

    @Test
    fun backFromMoreClosesTheSheet() {
        val state = TvPlayerPanelNavigation.closed().openMore().back()

        assertEquals(TvPlayerPanel.NONE, state.current)
    }
}
