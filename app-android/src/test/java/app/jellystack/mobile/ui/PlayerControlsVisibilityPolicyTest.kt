package app.jellystack.mobile.ui

import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackPhase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerControlsVisibilityPolicyTest {
    @Test
    fun autoHideRequiresReadyPlayingAndNoAccessibilityOrInteractionBlocker() {
        val baseline =
            PlayerControlsVisibilityInput(
                mediaKind = PlaybackMediaKind.VIDEO,
                phase = PlaybackPhase.Ready,
                isPaused = false,
                hasControlFocus = false,
                modalOpen = false,
                touchActive = false,
                touchExplorationEnabled = false,
            )
        assertTrue(shouldAutoHideControls(baseline))
        assertFalse(shouldAutoHideControls(baseline.copy(isPaused = true)))
        assertFalse(shouldAutoHideControls(baseline.copy(phase = PlaybackPhase.Buffering)))
        assertFalse(shouldAutoHideControls(baseline.copy(phase = PlaybackPhase.Ended)))
        assertFalse(shouldAutoHideControls(baseline.copy(hasControlFocus = true)))
        assertFalse(shouldAutoHideControls(baseline.copy(modalOpen = true)))
        assertFalse(shouldAutoHideControls(baseline.copy(touchActive = true)))
        assertFalse(shouldAutoHideControls(baseline.copy(touchExplorationEnabled = true)))
        assertFalse(shouldAutoHideControls(baseline.copy(mediaKind = PlaybackMediaKind.AUDIO)))
    }
}
