package app.jellystack.mobile.playback

import dev.jellystack.players.PlaybackContinuationState

internal data class AndroidAutoplayPromptModel(
    val title: String,
    val secondsRemaining: Int,
)

internal fun androidAutoplayPromptModel(state: PlaybackContinuationState): AndroidAutoplayPromptModel? =
    state.nextTarget
        ?.let { target ->
            state.countdownSecondsRemaining?.let { seconds -> AndroidAutoplayPromptModel(target.title, seconds) }
        }
