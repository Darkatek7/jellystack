package app.jellystack.tv

import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** How long a paused (or finished) video keeps the screen on before the TV may sleep or dim again. */
internal const val TV_PAUSED_KEEP_SCREEN_ON_MS = 10 * 60_000L

/** What the player is doing, as far as the screen is concerned. */
internal enum class TvScreenActivity {
    /** Nothing playing: the screen follows the system's own timeout. */
    IDLE,

    /** Playing, buffering or preparing. */
    PLAYING,

    /** Paused or ended: a still frame, which burns into OLED panels. */
    PAUSED,
}

internal fun PlaybackState.screenActivity(): TvScreenActivity =
    when (this) {
        is PlaybackState.Preparing -> TvScreenActivity.PLAYING
        is PlaybackState.Active -> screenActivity(isPaused, phase)
        is PlaybackState.PlaybackError,
        PlaybackState.Stopped,
        -> TvScreenActivity.IDLE
    }

internal fun screenActivity(
    isPaused: Boolean,
    phase: PlaybackPhase,
): TvScreenActivity {
    val stillFrame = isPaused || phase == PlaybackPhase.Ended
    return if (stillFrame) TvScreenActivity.PAUSED else TvScreenActivity.PLAYING
}

/**
 * Whether the window should keep the screen on: while playing, and for [pausedTimeoutMs] after a pause
 * begins. A pause that is resumed in time never releases the screen; the next pause starts a new countdown.
 */
internal fun Flow<TvScreenActivity>.keepScreenOn(pausedTimeoutMs: Long = TV_PAUSED_KEEP_SCREEN_ON_MS): Flow<Boolean> =
    transformLatest { activity ->
        when (activity) {
            TvScreenActivity.IDLE -> emit(false)
            TvScreenActivity.PLAYING -> emit(true)
            TvScreenActivity.PAUSED -> {
                emit(true)
                delay(pausedTimeoutMs)
                emit(false)
            }
        }
    }.distinctUntilChanged()
