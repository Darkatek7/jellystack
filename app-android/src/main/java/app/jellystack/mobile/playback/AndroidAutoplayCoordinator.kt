package app.jellystack.mobile.playback

import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AndroidAutoplayTarget(
    val mediaId: String,
    val title: String,
    val onPlay: suspend () -> Unit,
)

internal sealed interface AndroidAutoplayState {
    data object Idle : AndroidAutoplayState

    data class Countdown(
        val target: AndroidAutoplayTarget,
        val secondsRemaining: Int,
    ) : AndroidAutoplayState
}

internal class AndroidAutoplayCoordinator(
    private val scope: CoroutineScope,
    private val modeProvider: () -> AutoplayNextMode,
    private val resolveNext: suspend (mediaId: String, seriesId: String?) -> AndroidAutoplayTarget?,
) {
    private val mutableState = MutableStateFlow<AndroidAutoplayState>(AndroidAutoplayState.Idle)
    val state: StateFlow<AndroidAutoplayState> = mutableState.asStateFlow()

    private var handledCompletedMediaId: String? = null
    private var foreground = true
    private var resolutionJob: Job? = null
    private var countdownJob: Job? = null

    fun onPlaybackState(state: PlaybackState) {
        val active = state as? PlaybackState.Active ?: return
        if (active.phase != PlaybackPhase.Ended) return
        onPlaybackCompleted(active.mediaId, active.metadata?.seriesId)
    }

    internal fun onPlaybackCompleted(
        mediaId: String,
        seriesId: String?,
    ) {
        if (mediaId == handledCompletedMediaId) return
        handledCompletedMediaId = mediaId
        resolutionJob?.cancel()
        val mode = modeProvider()
        if (mode == AutoplayNextMode.OFF) return
        resolutionJob =
            scope.launch {
                val target = resolveNext(mediaId, seriesId) ?: return@launch
                if (mode == AutoplayNextMode.IMMEDIATE) {
                    launchTarget(target)
                } else {
                    mutableState.value = AndroidAutoplayState.Countdown(target, DEFAULT_COUNTDOWN_SECONDS)
                    startCountdownIfPossible()
                }
            }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) startCountdownIfPossible() else countdownJob?.cancel()
    }

    fun cancel() {
        countdownJob?.cancel()
        mutableState.value = AndroidAutoplayState.Idle
    }

    fun playNow() {
        val countdown = mutableState.value as? AndroidAutoplayState.Countdown ?: return
        launchTarget(countdown.target)
    }

    fun release() {
        resolutionJob?.cancel()
        countdownJob?.cancel()
    }

    private fun startCountdownIfPossible() {
        if (!foreground || countdownJob?.isActive == true) return
        val initial = mutableState.value as? AndroidAutoplayState.Countdown ?: return
        countdownJob =
            scope.launch {
                var remaining = initial.secondsRemaining
                while (remaining > 0 && foreground) {
                    delay(COUNTDOWN_TICK_MILLIS)
                    if (!foreground) return@launch
                    remaining -= 1
                    val current = mutableState.value as? AndroidAutoplayState.Countdown ?: return@launch
                    mutableState.value = current.copy(secondsRemaining = remaining)
                }
                if (remaining == 0) launchTarget(initial.target)
            }
    }

    private fun launchTarget(target: AndroidAutoplayTarget) {
        countdownJob?.cancel()
        mutableState.value = AndroidAutoplayState.Idle
        scope.launch { target.onPlay() }
    }

    private companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 10
        const val COUNTDOWN_TICK_MILLIS = 1_000L
    }
}
