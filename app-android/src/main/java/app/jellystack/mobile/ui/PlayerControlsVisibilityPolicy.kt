@file:Suppress("MatchingDeclarationName")

package app.jellystack.mobile.ui

import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackPhase

internal data class PlayerControlsVisibilityInput(
    val mediaKind: PlaybackMediaKind,
    val phase: PlaybackPhase,
    val isPaused: Boolean,
    val hasControlFocus: Boolean,
    val modalOpen: Boolean,
    val touchActive: Boolean,
    val touchExplorationEnabled: Boolean,
)

internal fun shouldAutoHideControls(input: PlayerControlsVisibilityInput): Boolean =
    input.mediaKind == PlaybackMediaKind.VIDEO &&
        input.phase == PlaybackPhase.Ready &&
        !input.isPaused &&
        !input.hasControlFocus &&
        !input.modalOpen &&
        !input.touchActive &&
        !input.touchExplorationEnabled
