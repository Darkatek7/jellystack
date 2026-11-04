@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.delay

/** Which surface the player route draws; it keeps the last one while nothing says which (stopped, about to leave). */
@Composable
internal fun rememberTvPlayerSurface(
    playback: PlaybackState,
    start: TvPlaybackStartState,
): TvPlayerSurface {
    var last by remember { mutableStateOf(TvPlayerSurface.VIDEO) }
    val surface = tvPlayerSurface(playback, start) ?: last
    SideEffect { last = surface }
    return surface
}

/** What the now-playing view reads. */
internal class TvAudioPlayerState(
    val playback: PlaybackState,
    val start: TvPlaybackStartState,
    val images: TvPlayerImageSource,
    val hasNext: Boolean,
)

/** The transport controls of the now-playing view. */
internal class TvAudioTransport(
    val onSetPaused: (Boolean) -> Unit,
    val onPreviousTrack: () -> Unit,
    val onNextTrack: () -> Unit,
)

/** The queue behind the now-playing view: whether a track came before [mediaId] in it, and playing that track. */
internal class TvAudioQueueControls(
    val hasPrevious: (mediaId: String) -> Boolean = { false },
    val playPrevious: (mediaId: String) -> Unit = {},
)

/**
 * The transport for the track playing now. Previous restarts a track that has played a while and goes back to the
 * track before it early on (see [tvAudioPreviousAction]).
 */
internal fun tvAudioTransport(
    playback: PlaybackState,
    queue: TvAudioQueueControls,
    onSetPaused: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onNextTrack: () -> Unit,
): TvAudioTransport {
    val active = playback as? PlaybackState.Active
    val hasPrevious = active != null && queue.hasPrevious(active.mediaId)
    return TvAudioTransport(
        onSetPaused = onSetPaused,
        onPreviousTrack = {
            when (tvAudioPreviousAction(active?.positionMs ?: 0L, hasPrevious)) {
                TvAudioPreviousAction.PREVIOUS_TRACK -> active?.let { queue.playPrevious(it.mediaId) }
                TvAudioPreviousAction.RESTART_TRACK -> onSeekTo(0L)
            }
        },
        onNextTrack = onNextTrack,
    )
}

/** What the error panels of the now-playing view do. */
internal class TvAudioErrorActions(
    val onRetryPlayback: () -> Unit,
    val onRetryStart: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * The player route for music: the cover, the title with artist and album, how far into the track it is, and
 * previous, play/pause, next. There is no video, no on-screen display to show or hide, and no up-next prompt; the next
 * track of a list starts by itself. Focus starts on play/pause, Left and Right move between the controls, and Back
 * leaves the route (which stops playback).
 *
 * The content dims after a minute without a key press, so a long album does not leave a bright still picture on the
 * panel.
 */
@Composable
internal fun TvAudioPlayerScreen(
    state: TvAudioPlayerState,
    transport: TvAudioTransport,
    errors: TvAudioErrorActions,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val active = state.playback as? PlaybackState.Active
    val overlay = tvPlayerOverlay(state.playback, state.start)
    val playPauseFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    var interactions by remember { mutableIntStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    val dim by animateFloatAsState(if (idle) TV_AUDIO_IDLE_ALPHA else 1f, tween(TV_AUDIO_DIM_MS), label = "audio-dim")
    LaunchedEffect(interactions) {
        idle = false
        delay(TV_AUDIO_IDLE_DIM_AFTER_MS)
        idle = true
    }
    // Back needs a focused view even before there is a track to show (it also cancels a start that is still running).
    LaunchedEffect(active != null, overlay) {
        val trackShown = active != null && overlay == TvPlayerOverlay.NONE
        if (trackShown) playPauseFocus.requestFocus() else rootFocus.requestFocus()
    }
    TvPlayerBackHandler(errors.onClose)
    Box(
        modifier
            .fillMaxSize()
            .background(TvBackground)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                interactions += 1
                val errorPanelShown =
                    overlay == TvPlayerOverlay.PLAYBACK_ERROR || overlay == TvPlayerOverlay.START_ERROR
                handleAudioKey(event.nativeKeyEvent, active, transport, errorPanelShown, errors.onClose)
            }.testTag("tv-audio-player"),
    ) {
        if (active != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }) {
                TvAudioNowPlayingContent(
                    nowPlaying = active.toAudioNowPlaying(state.images, strings.messages.untitled),
                    hasNext = state.hasNext,
                    transport = transport,
                    strings = strings,
                    playPauseFocus = playPauseFocus,
                )
            }
        }
        TvAudioPlayerOverlay(overlay, state, errors, strings)
    }
}

/** Media keys and Back, which the controls do not take themselves. Returns whether the key was used. */
private fun handleAudioKey(
    event: KeyEvent,
    active: PlaybackState.Active?,
    transport: TvAudioTransport,
    errorPanelShown: Boolean,
    onClose: () -> Unit,
): Boolean {
    val pausedTarget = active?.let { tvMediaKeyPausedTarget(event.keyCode, it.isPaused) }
    val firstPress = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
    val back = event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN
    return when {
        back -> {
            onClose()
            true
        }
        // The error panels own the rest of the remote: their buttons need Center and the D-pad.
        errorPanelShown -> false
        pausedTarget != null -> {
            if (firstPress) transport.onSetPaused(pausedTarget)
            true
        }
        event.action != KeyEvent.ACTION_DOWN -> false
        event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT -> {
            transport.onNextTrack()
            true
        }
        event.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            transport.onPreviousTrack()
            true
        }
        else -> false
    }
}

@Composable
private fun TvAudioPlayerOverlay(
    overlay: TvPlayerOverlay,
    state: TvAudioPlayerState,
    errors: TvAudioErrorActions,
    strings: TvStrings,
) {
    when (overlay) {
        TvPlayerOverlay.PREPARING ->
            TvPlayerPreparing(
                model = tvPlayerPreparingModel(state.playback, state.start, state.images),
                label = strings.playbackUi.preparingPlayback,
            )
        TvPlayerOverlay.PLAYBACK_ERROR ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TvPlaybackError(
                    title = strings.playbackUi.playbackFailedTitle,
                    message = tvPlaybackErrorMessage("", strings),
                    onRetry = errors.onRetryPlayback,
                    strings = strings,
                    onClose = errors.onClose,
                )
            }
        TvPlayerOverlay.START_ERROR ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TvPlaybackError(
                    title = strings.player.startFailedTitle,
                    message = tvStartFailureMessage(state.start, strings),
                    onRetry = errors.onRetryStart,
                    strings = strings,
                    onClose = errors.onClose,
                )
            }
        TvPlayerOverlay.NONE -> Unit
    }
}

/** Previous, play/pause and next. Focus starts on play/pause; Left and Right move between the three. */
@Composable
internal fun TvAudioPlayerControls(
    isPaused: Boolean,
    hasNext: Boolean,
    transport: TvAudioTransport,
    strings: TvStrings,
    playPauseFocus: FocusRequester,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        TvPlayerIconButton(
            Icons.Default.SkipPrevious,
            strings.messages.music.previousTrack,
            transport.onPreviousTrack,
            Modifier.testTag("tv-audio-previous"),
            size = 64.dp,
            iconSize = 34.dp,
        )
        TvPlayerIconButton(
            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            if (isPaused) strings.play else strings.playbackUi.pause,
            { transport.onSetPaused(!isPaused) },
            Modifier.focusRequester(playPauseFocus).testTag("tv-audio-play-pause"),
            size = 78.dp,
            iconSize = 42.dp,
        )
        // The last track has no next one; the button stays so Left and Right keep the same three stops.
        TvPlayerIconButton(
            Icons.Default.SkipNext,
            strings.messages.music.nextTrack,
            { if (hasNext) transport.onNextTrack() },
            Modifier.alpha(if (hasNext) 1f else TV_AUDIO_DISABLED_ALPHA).testTag("tv-audio-next"),
            size = 64.dp,
            iconSize = 34.dp,
        )
    }
}

/** After this long without a key press the content dims. */
internal const val TV_AUDIO_IDLE_DIM_AFTER_MS = 60_000L
private const val TV_AUDIO_DIM_MS = 2_000
private const val TV_AUDIO_IDLE_ALPHA = 0.45f
private const val TV_AUDIO_DISABLED_ALPHA = 0.4f
