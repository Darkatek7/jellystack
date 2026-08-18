package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class TvTrailerPreviewTarget(
    val serverKey: String,
    val itemId: String,
    val isEpisode: Boolean,
    val seriesId: String?,
)

internal enum class TvTrailerPreviewOwner { HERO, CARD }

internal data class TvTrailerPreviewRequest(
    val owner: TvTrailerPreviewOwner,
    val target: TvTrailerPreviewTarget,
)

internal sealed interface TvTrailerPreviewState {
    data object Idle : TvTrailerPreviewState

    data class Armed(
        val request: TvTrailerPreviewRequest,
    ) : TvTrailerPreviewState

    data class Playing(
        val request: TvTrailerPreviewRequest,
    ) : TvTrailerPreviewState

    data class Unavailable(
        val request: TvTrailerPreviewRequest,
    ) : TvTrailerPreviewState
}

internal enum class TvTrailerPreviewPlayerEvent {
    Completed,
    Failed,
}

internal interface TvTrailerPreviewPlayer {
    val events: Flow<TvTrailerPreviewPlayerEvent>

    suspend fun play(source: DetailTrailerSource.Local): Boolean

    fun stop()

    fun setSoundEnabled(enabled: Boolean)
}

internal class TvTrailerPreviewController(
    private val scope: CoroutineScope,
    private val resolve: suspend (TvTrailerPreviewTarget) -> DetailTrailerSource.Local?,
    private val player: TvTrailerPreviewPlayer,
    private val focusDelayMillis: Long = DEFAULT_FOCUS_DELAY_MILLIS,
) {
    private val mutableState = MutableStateFlow<TvTrailerPreviewState>(TvTrailerPreviewState.Idle)
    val state: StateFlow<TvTrailerPreviewState> = mutableState.asStateFlow()

    private val cache = mutableMapOf<TvTrailerPreviewTarget, DetailTrailerSource.Local?>()
    private var focusedRequest: TvTrailerPreviewRequest? = null
    private var focusJob: Job? = null
    private var enabled = true
    private var soundEnabled = true
    private val eventJob =
        scope.launch {
            player.events.collect { event ->
                if (mutableState.value !is TvTrailerPreviewState.Playing) return@collect
                when (event) {
                    TvTrailerPreviewPlayerEvent.Completed,
                    TvTrailerPreviewPlayerEvent.Failed,
                    -> stopPlaying()
                }
            }
        }

    fun focus(request: TvTrailerPreviewRequest) {
        if (!enabled) return
        if (focusedRequest == request && mutableState.value !is TvTrailerPreviewState.Idle) return
        cancelPendingAndStopPlaying()
        focusedRequest = request
        mutableState.value = TvTrailerPreviewState.Armed(request)
        focusJob =
            scope.launch {
                val resolution = async { cachedOrResolve(request.target) }
                delay(focusDelayMillis)
                val source = resolution.await()
                currentCoroutineContext().ensureActive()
                if (!enabled || focusedRequest != request) return@launch
                if (source == null) {
                    mutableState.value = TvTrailerPreviewState.Unavailable(request)
                    return@launch
                }
                player.setSoundEnabled(soundEnabled)
                if (player.play(source)) {
                    mutableState.value = TvTrailerPreviewState.Playing(request)
                } else {
                    mutableState.value = TvTrailerPreviewState.Idle
                }
            }
    }

    fun clearFocus() {
        focusedRequest = null
        cancelPendingAndStopPlaying()
        mutableState.value = TvTrailerPreviewState.Idle
    }

    fun clearFocus(request: TvTrailerPreviewRequest) {
        if (focusedRequest == request) clearFocus()
    }

    fun clearFocus(owner: TvTrailerPreviewOwner) {
        if (focusedRequest?.owner == owner) clearFocus()
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) clearFocus()
    }

    fun setSoundEnabled(value: Boolean) {
        soundEnabled = value
        player.setSoundEnabled(value)
    }

    fun invalidateCache() {
        cache.clear()
        clearFocus()
    }

    fun release() {
        clearFocus()
        eventJob.cancel()
    }

    private suspend fun cachedOrResolve(target: TvTrailerPreviewTarget): DetailTrailerSource.Local? {
        if (cache.containsKey(target)) return cache[target]
        val source = resolve(target)
        currentCoroutineContext().ensureActive()
        cache[target] = source
        return source
    }

    private fun cancelPendingAndStopPlaying() {
        focusJob?.cancel()
        focusJob = null
        if (mutableState.value is TvTrailerPreviewState.Playing) player.stop()
    }

    private fun stopPlaying() {
        player.stop()
        mutableState.value = TvTrailerPreviewState.Idle
    }

    private companion object {
        const val DEFAULT_FOCUS_DELAY_MILLIS = 3_000L
    }
}
