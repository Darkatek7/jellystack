package app.jellystack.mobile.playback

import dev.jellystack.players.PlaybackContinuationState

internal data class AndroidAutoplayPromptModel(
    val title: String,
    val secondsRemaining: Int,
)

internal fun androidAutoplayPromptModel(state: PlaybackContinuationState): AndroidAutoplayPromptModel? {
    val target = state.nextTarget ?: return null
    val secondsRemaining = state.countdownSecondsRemaining ?: return null
    return AndroidAutoplayPromptModel(target.title, secondsRemaining)
}
