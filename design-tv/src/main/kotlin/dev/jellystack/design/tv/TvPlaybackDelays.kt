package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jellystack.players.PlaybackDelay
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEngine

/**
 * Audio and subtitle delay of the title being played. It is about one specific file, so it lives only as long as
 * the title and is never stored in the settings. Positive values mean later.
 */
@Stable
internal class TvPlaybackDelays {
    var audioMs by mutableIntStateOf(0)
        private set
    var subtitleMs by mutableIntStateOf(0)
        private set

    val isDefault: Boolean get() = audioMs == 0 && subtitleMs == 0

    fun stepAudio(steps: Int) {
        audioMs = PlaybackDelay.adjusted(audioMs, steps)
    }

    fun stepSubtitle(steps: Int) {
        subtitleMs = PlaybackDelay.adjusted(subtitleMs, steps)
    }

    fun resetAudio() {
        audioMs = 0
    }

    fun resetSubtitle() {
        subtitleMs = 0
    }

    fun resetAll() {
        audioMs = 0
        subtitleMs = 0
    }
}

/**
 * Keeps the delays of the current title and pushes them to [engine]. A new title (another media id) starts at 0,
 * and so does the engine when the player screen goes away, because the engine outlives the screen.
 */
@Composable
internal fun rememberTvPlaybackDelays(
    engine: PlayerEngine,
    playbackState: PlaybackState,
): TvPlaybackDelays {
    val titleId =
        when (playbackState) {
            is PlaybackState.Active -> playbackState.mediaId
            is PlaybackState.Preparing -> playbackState.mediaId
            is PlaybackState.PlaybackError -> playbackState.mediaId
            PlaybackState.Stopped -> null
        }
    val delays = remember(titleId) { TvPlaybackDelays() }
    LaunchedEffect(engine, delays, delays.audioMs) { engine.setAudioDelayMs(delays.audioMs) }
    LaunchedEffect(engine, delays, delays.subtitleMs) { engine.setSubtitleDelayMs(delays.subtitleMs) }
    DisposableEffect(engine) {
        onDispose {
            engine.setAudioDelayMs(0)
            engine.setSubtitleDelayMs(0)
        }
    }
    return delays
}

/** `+150 ms`, `-50 ms` or `0 ms`. */
internal fun formatDelayMs(delayMs: Int): String = if (delayMs > 0) "+$delayMs ms" else "$delayMs ms"
