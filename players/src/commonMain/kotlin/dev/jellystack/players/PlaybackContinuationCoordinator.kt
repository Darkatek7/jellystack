package dev.jellystack.players

import dev.jellystack.core.preferences.AutoplayNextMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackContinuationTarget(
    val mediaId: String,
    val title: String,
    val play: suspend () -> Unit,
)

data class PlaybackContinuationState(
    val mediaId: String? = null,
    val nextTarget: PlaybackContinuationTarget? = null,
    val countdownSecondsRemaining: Int? = null,
)

class PlaybackContinuationCoordinator(
    private val scope: CoroutineScope,
    private val modeProvider: () -> AutoplayNextMode,
    private val resolveNext: suspend (mediaId: String, seriesId: String) -> PlaybackContinuationTarget?,
) {
    private val mutableState = MutableStateFlow(PlaybackContinuationState())
    val state: StateFlow<PlaybackContinuationState> = mutableState.asStateFlow()

    private var currentMediaId: String? = null
    private var currentSeriesId: String? = null
    private var resolutionStarted = false
    private var completionHandled = false
    private var completionMode: AutoplayNextMode? = null
    private var foreground = true
    private var generation = 0L
    private var resolutionJob: Job? = null
    private var countdownJob: Job? = null
    private var playJob: Job? = null

    fun onPlaybackState(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Active -> observeActive(playbackState)
            is PlaybackState.Preparing -> prepareMedia(playbackState.mediaId)
            PlaybackState.Stopped -> reset()
            is PlaybackState.PlaybackError -> reset()
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            startCountdownIfPossible()
        } else {
            countdownJob?.cancel()
            countdownJob = null
        }
    }

    fun cancelAutoplay() {
        countdownJob?.cancel()
        countdownJob = null
        mutableState.value = mutableState.value.copy(countdownSecondsRemaining = null)
    }

    fun playNext() {
        val target = mutableState.value.nextTarget ?: return
        completionHandled = true
        launchTarget(target)
    }

    fun release() {
        reset()
        playJob?.cancel()
        playJob = null
    }

    private fun observeActive(active: PlaybackState.Active) {
        val seriesId = active.metadata?.seriesId
        if (active.mediaKind != PlaybackMediaKind.VIDEO || seriesId == null) {
            prepareMedia(active.mediaId)
            return
        }
        if (currentMediaId != active.mediaId || currentSeriesId != seriesId) {
            beginEpisode(active.mediaId, seriesId)
        } else if (!resolutionStarted) {
            startResolution(active.mediaId, seriesId)
        }
        if (active.phase == PlaybackPhase.Ended) onPlaybackCompleted(active.mediaId)
    }

    private fun prepareMedia(mediaId: String) {
        if (currentMediaId == mediaId && currentSeriesId == null) return
        clearCurrentWork()
        currentMediaId = mediaId
        mutableState.value = PlaybackContinuationState(mediaId = mediaId)
    }

    private fun beginEpisode(
        mediaId: String,
        seriesId: String,
    ) {
        clearCurrentWork()
        currentMediaId = mediaId
        currentSeriesId = seriesId
        mutableState.value = PlaybackContinuationState(mediaId = mediaId)
        startResolution(mediaId, seriesId)
    }

    private fun startResolution(
        mediaId: String,
        seriesId: String,
    ) {
        resolutionStarted = true
        val resolutionGeneration = generation
        resolutionJob =
            scope.launch {
                val target =
                    try {
                        resolveNext(mediaId, seriesId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                if (resolutionGeneration != generation || currentMediaId != mediaId) return@launch
                mutableState.value = mutableState.value.copy(nextTarget = target)
                if (completionHandled) applyCompletionMode()
            }
    }

    private fun onPlaybackCompleted(mediaId: String) {
        if (mediaId != currentMediaId || completionHandled) return
        completionHandled = true
        completionMode = modeProvider()
        applyCompletionMode()
    }

    private fun applyCompletionMode() {
        val target = mutableState.value.nextTarget ?: return
        when (completionMode) {
            AutoplayNextMode.OFF -> Unit
            AutoplayNextMode.IMMEDIATE -> launchTarget(target)
            AutoplayNextMode.COUNTDOWN -> {
                if (mutableState.value.countdownSecondsRemaining == null) {
                    mutableState.value = mutableState.value.copy(countdownSecondsRemaining = COUNTDOWN_SECONDS)
                }
                startCountdownIfPossible()
            }
            null -> Unit
        }
    }

    private fun startCountdownIfPossible() {
        if (!foreground || countdownJob?.isActive == true) return
        val initial = mutableState.value.countdownSecondsRemaining ?: return
        if (mutableState.value.nextTarget == null) return
        countdownJob =
            scope.launch {
                var remaining = initial
                while (remaining > 0 && foreground) {
                    delay(COUNTDOWN_TICK_MILLIS)
                    if (!foreground) return@launch
                    remaining -= 1
                    if (mutableState.value.countdownSecondsRemaining == null) return@launch
                    mutableState.value = mutableState.value.copy(countdownSecondsRemaining = remaining)
                }
                if (remaining == 0) {
                    mutableState.value.nextTarget?.let(::launchTarget)
                }
            }
    }

    private fun launchTarget(target: PlaybackContinuationTarget) {
        countdownJob?.cancel()
        countdownJob = null
        mutableState.value = mutableState.value.copy(nextTarget = null, countdownSecondsRemaining = null)
        playJob?.cancel()
        playJob = scope.launch { target.play() }
    }

    private fun clearCurrentWork() {
        generation += 1
        resolutionJob?.cancel()
        resolutionJob = null
        countdownJob?.cancel()
        countdownJob = null
        currentMediaId = null
        currentSeriesId = null
        resolutionStarted = false
        completionHandled = false
        completionMode = null
    }

    private fun reset() {
        clearCurrentWork()
        mutableState.value = PlaybackContinuationState()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 10
        const val COUNTDOWN_TICK_MILLIS = 1_000L
    }
}
