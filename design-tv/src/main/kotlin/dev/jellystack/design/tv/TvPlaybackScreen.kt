@file:Suppress("CyclomaticComplexMethod", "FunctionName", "FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun TvPlaybackScreen(
    controller: PlaybackController,
    engine: AndroidPlayerEngine,
    syncPlay: SyncPlayCoordinator,
    strings: TvStrings,
    stopPlayback: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val syncState by syncPlay.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var navigation by remember { mutableStateOf(TvPlayerPanelNavigation.closed()) }
    var interactionGeneration by remember { mutableStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val active = state as? PlaybackState.Active

    LaunchedEffect(controlsVisible, navigation.current, interactionGeneration) {
        if (controlsVisible && navigation.current == TvPlayerPanel.NONE) {
            delay(5_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(active != null, controlsVisible, navigation.current) {
        if (active != null && navigation.current == TvPlayerPanel.NONE) {
            if (controlsVisible) controlsFocusRequester.requestFocus() else playerFocusRequester.requestFocus()
        }
    }
    DisposableEffect(engine) { onDispose(stopPlayback) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    interactionGeneration += 1
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        ->
                            if (navigation.current == TvPlayerPanel.NONE && !controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MENU -> {
                            controlsVisible = true
                            navigation = navigation.openMore()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        ->
                            if (navigation.current == TvPlayerPanel.NONE && !controlsVisible) {
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        ->
                            if (navigation.current == TvPlayerPanel.NONE && !controlsVisible && active != null) {
                                val step = if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -10_000L else 30_000L
                                val multiplier = (event.nativeKeyEvent.repeatCount + 1).coerceAtMost(6)
                                controller.seekTo((active.positionMs + step * multiplier).coerceIn(0L, active.durationMs ?: Long.MAX_VALUE))
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_BACK -> {
                            when {
                                navigation.current != TvPlayerPanel.NONE -> navigation = navigation.back()
                                controlsVisible -> controlsVisible = false
                                else -> onClose()
                            }
                            true
                        }
                        else -> false
                    }
                },
    ) {
        AndroidView(
            factory = { engine.createVideoSurface(it) },
            update = engine::updateVideoSurface,
            onRelease = engine::releaseVideoSurface,
            modifier = Modifier.fillMaxSize(),
        )
        when (val playbackState = state) {
            is PlaybackState.Preparing -> TvLoading(strings.preparingPlayback)
            is PlaybackState.PlaybackError ->
                TvPlaybackError(
                    playbackState,
                    controller,
                    strings,
                    onClose,
                    Modifier.align(Alignment.Center),
                )
            else -> Unit
        }
        if (active != null && controlsVisible) {
            TvPlayerHeader(
                primaryTitle = active.metadata.playerPrimaryTitle(strings),
                secondaryTitle = active.metadata.playerSecondaryTitle(),
                backDescription = strings.back,
                onBack = onClose,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.86f), Color.Black.copy(alpha = 0.42f), Color.Transparent)))
                        .padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 54.dp),
            )
            if (active.statsForNerdsEnabled && navigation.current == TvPlayerPanel.NONE) {
                TvStatsForNerdsOverlay(
                    active,
                    strings,
                    Modifier.align(Alignment.TopEnd).padding(top = 128.dp, end = 36.dp).width(330.dp),
                )
            }
            TvPlayerControls(
                active = active,
                strings = strings,
                controller = controller,
                controlsFocusRequester = controlsFocusRequester,
                onAudio = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.AUDIO) },
                onSubtitles = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.SUBTITLES) },
                onMore = { navigation = navigation.openMore() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (active != null && navigation.current != TvPlayerPanel.NONE) {
            TvPlayerOptionsPanel(
                navigation = navigation,
                state = active,
                syncState = syncState,
                strings = strings,
                onBack = { navigation = navigation.back() },
                onOpenFromMore = { navigation = navigation.openFromMore(it) },
                onAudioSelected = controller::selectAudioTrack,
                onSubtitleSelected = controller::selectSubtitle,
                onQualitySelected = controller::selectQuality,
                onSpeedSelected = controller::setPlaybackSpeed,
                onStatsToggled = controller::setStatsForNerdsEnabled,
                syncPlay = syncPlay,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun TvPlaybackError(
    error: PlaybackState.PlaybackError,
    controller: PlaybackController,
    strings: TvStrings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(720.dp).background(TvSurface.copy(alpha = 0.98f), RoundedCornerShape(28.dp)).padding(horizontal = 48.dp, vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(strings.playbackFailedTitle, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(tvPlaybackErrorMessage(error.message, strings), color = TvTextMuted, fontSize = 21.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvActionButton(strings.retry, controller::retry, modifier = Modifier.width(230.dp), primary = true)
            TvActionButton(strings.close, onClose, modifier = Modifier.width(170.dp))
        }
    }
}

@Composable
private fun TvPlayerControls(
    active: PlaybackState.Active,
    strings: TvStrings,
    controller: PlaybackController,
    controlsFocusRequester: FocusRequester,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)))).padding(start = 42.dp, end = 42.dp, top = 86.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvProgress(active.positionMs, active.durationMs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                TvPlayerIconButton(Icons.AutoMirrored.Filled.VolumeUp, strings.audio, onAudio)
                Spacer(Modifier.width(12.dp))
                TvPlayerIconButton(Icons.Default.Subtitles, strings.subtitles, onSubtitles)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                TvPlayerIconButton(Icons.Default.Replay10, "${strings.seekBack} 10", { controller.seekTo((active.positionMs - 10_000L).coerceAtLeast(0L)) }, size = 64.dp, iconSize = 34.dp)
                TvPlayerIconButton(
                    if (active.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (active.isPaused) strings.play else strings.pause,
                    { if (active.isPaused) controller.resume() else controller.pause() },
                    Modifier.focusRequester(controlsFocusRequester),
                    size = 78.dp,
                    iconSize = 42.dp,
                )
                TvPlayerIconButton(Icons.Default.Forward30, "${strings.seekForward} 30", { controller.seekTo((active.positionMs + 30_000L).coerceAtMost(active.durationMs ?: Long.MAX_VALUE)) }, size = 64.dp, iconSize = 34.dp)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TvPlayerIconButton(Icons.Default.MoreVert, strings.more, onMore)
            }
        }
    }
}

@Composable
private fun TvProgress(position: Long, duration: Long?) {
    val fraction = if (duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(TvPurple, RoundedCornerShape(50)))
        }
        Text("${position.formatDuration()}  /  ${duration?.formatDuration() ?: "--:--"}", color = TvTextMuted)
    }
}

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun dev.jellystack.players.PlaybackMetadata?.playerPrimaryTitle(strings: TvStrings): String =
    this?.seriesName?.takeIf(String::isNotBlank) ?: this?.title?.takeIf(String::isNotBlank) ?: strings.playback

private fun dev.jellystack.players.PlaybackMetadata?.playerSecondaryTitle(): String? {
    val metadata = this ?: return null
    if (metadata.seriesName.isNullOrBlank()) return null
    val episodePrefix = if (metadata.seasonNumber != null && metadata.episodeNumber != null) "S${metadata.seasonNumber} · E${metadata.episodeNumber}" else null
    return listOfNotNull(episodePrefix, (metadata.episodeName ?: metadata.title)?.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { null }
}

@Suppress("UNUSED_PARAMETER")
internal fun tvPlaybackErrorMessage(rawMessage: String, strings: TvStrings): String = strings.playbackFailedMessage
