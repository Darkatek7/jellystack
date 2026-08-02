@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
)

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import dev.jellystack.players.syncplay.SyncPlayUiState
import kotlinx.coroutines.delay

private enum class TvPlayerPanel { NONE, AUDIO, SUBTITLES, QUALITY, SPEED, STATS, SYNCPLAY }

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
    var panel by remember { mutableStateOf(TvPlayerPanel.NONE) }
    val active = state as? PlaybackState.Active
    LaunchedEffect(active?.positionMs, panel) {
        if (panel == TvPlayerPanel.NONE) {
            delay(5_000)
            controlsVisible = false
        }
    }
    DisposableEffect(engine) {
        onDispose(stopPlayback)
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_MENU,
                        -> {
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            if (panel == TvPlayerPanel.NONE && !controlsVisible) {
                                if (active?.isPaused == false) controller.pause() else controller.resume()
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        -> {
                            if (panel == TvPlayerPanel.NONE && !controlsVisible && active != null) {
                                val baseStep = if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -10_000L else 30_000L
                                val repeatMultiplier = (event.nativeKeyEvent.repeatCount + 1).coerceAtMost(6)
                                val destination = active.positionMs + (baseStep * repeatMultiplier)
                                controller.seekTo(destination.coerceIn(0L, active.durationMs ?: Long.MAX_VALUE))
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            when {
                                panel != TvPlayerPanel.NONE -> panel = TvPlayerPanel.NONE
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
        when (state) {
            is PlaybackState.Preparing -> TvLoading(strings.preparingPlayback)
            is PlaybackState.PlaybackError -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((state as PlaybackState.PlaybackError).message, color = Color.White, fontSize = 24.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvActionButton(strings.retry, controller::retry, primary = true)
                        TvActionButton(strings.close, onClose)
                    }
                }
            }
            else -> Unit
        }
        if (active != null && controlsVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
                    .padding(horizontal = 48.dp, vertical = 34.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(active.metadata?.title ?: strings.playback, color = Color.White, fontSize = 28.sp)
                    TvProgress(active.positionMs, active.durationMs)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            TvActionButton(
                                if (active.isPaused) strings.play else strings.pause,
                                { if (active.isPaused) controller.resume() else controller.pause() },
                            )
                        }
                        item { TvActionButton(strings.audio, { panel = TvPlayerPanel.AUDIO }) }
                        item { TvActionButton(strings.subtitles, { panel = TvPlayerPanel.SUBTITLES }) }
                        item { TvActionButton(strings.quality, { panel = TvPlayerPanel.QUALITY }) }
                        item { TvActionButton(strings.speed, { panel = TvPlayerPanel.SPEED }) }
                        item { TvActionButton(strings.stats, { panel = TvPlayerPanel.STATS }) }
                        item {
                            TvActionButton(strings.syncPlay, {
                                syncPlay.refresh()
                                panel = TvPlayerPanel.SYNCPLAY
                            })
                        }
                        item { TvActionButton(strings.close, onClose) }
                    }
                }
            }
        }
        if (active != null && panel != TvPlayerPanel.NONE) {
            TvPlayerPanel(
                panel = panel,
                state = active,
                syncState = syncState,
                controller = controller,
                syncPlay = syncPlay,
                strings = strings,
                onClose = { panel = TvPlayerPanel.NONE },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun TvProgress(
    position: Long,
    duration: Long?,
) {
    val fraction = if (duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(TvPurple, RoundedCornerShape(50)))
        }
        Text("${position.formatDuration()}  /  ${duration?.formatDuration() ?: "--:--"}", color = TvTextMuted)
    }
}

@Composable
private fun TvPlayerPanel(
    panel: TvPlayerPanel,
    state: PlaybackState.Active,
    syncState: SyncPlayUiState,
    controller: PlaybackController,
    syncPlay: SyncPlayCoordinator,
    strings: TvStrings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(520.dp)
                .fillMaxSize()
                .background(TvBackground.copy(alpha = 0.98f))
                .padding(38.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(panel.title(strings), color = TvText, fontSize = 32.sp)
        when (panel) {
            TvPlayerPanel.AUDIO -> {
                state.stream.audioTracks.forEach { track ->
                    TvActionButton(
                        track.title ?: track.language ?: strings.audioTrack.format(track.streamIndex),
                        { controller.selectAudioTrack(track.id) },
                        primary = state.audioTrack?.id == track.id,
                    )
                }
            }
            TvPlayerPanel.SUBTITLES -> {
                TvActionButton(strings.off, { controller.selectSubtitle(null) }, primary = state.subtitleTrack == null)
                state.stream.subtitleTracks.forEach { track ->
                    TvActionButton(
                        track.title ?: track.language ?: strings.subtitleTrack.format(track.streamIndex),
                        { controller.selectSubtitle(track.id) },
                        primary = state.subtitleTrack?.id == track.id,
                    )
                }
            }
            TvPlayerPanel.QUALITY ->
                state.qualityOptions.forEach { option ->
                    TvActionButton(option.label, { controller.selectQuality(option.id) }, primary = state.selectedQualityId == option.id)
                }
            TvPlayerPanel.SPEED ->
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                    TvActionButton("${speed}x", { controller.setPlaybackSpeed(speed) }, primary = state.playbackSpeed == speed)
                }
            TvPlayerPanel.STATS -> {
                TvActionButton(
                    if (state.statsForNerdsEnabled) strings.hideStats else strings.showStats,
                    { controller.setStatsForNerdsEnabled(!state.statsForNerdsEnabled) },
                    primary = state.statsForNerdsEnabled,
                )
                if (state.statsForNerdsEnabled) {
                    val stats = state.runtimeStats
                    listOfNotNull(
                        stats.playbackMode?.let { "${strings.mode}: $it" },
                        stats.container?.let { "${strings.container}: $it" },
                        stats.videoCodec?.let { "${strings.video}: $it" },
                        stats.audioCodec?.let { "${strings.audio}: $it" },
                        stats.width?.let { width -> stats.height?.let { "${strings.resolution}: $width × $it" } },
                        stats.videoBitrate?.let { "${strings.bitrate}: ${it / 1_000_000f} Mbps" },
                        stats.hdr?.let { "${strings.range}: $it" },
                        stats.droppedFrames?.let { "${strings.droppedFrames}: $it" },
                    ).forEach { Text(it, color = TvTextMuted, fontSize = 18.sp) }
                }
            }
            TvPlayerPanel.SYNCPLAY -> {
                syncState.error?.let { Text(it, color = Color(0xFFFFA59E)) }
                syncState.currentGroup?.let { group ->
                    Text(strings.joinedGroup.format(group.name), color = TvPurple, fontSize = 20.sp)
                    TvActionButton(strings.useCurrentItemAsQueue, syncPlay::setCurrentPlaybackAsQueue)
                    TvActionButton(strings.leaveGroup, syncPlay::leaveGroup)
                } ?: run {
                    TvActionButton(strings.createGroup, { syncPlay.createGroup("Jellystack TV") }, primary = true)
                    syncState.groups.forEach { group ->
                        TvActionButton(strings.joinGroup.format(group.name), { syncPlay.joinGroup(group) })
                    }
                }
            }
            TvPlayerPanel.NONE -> Unit
        }
        Spacer(Modifier.weight(1f))
        TvActionButton(strings.back, onClose)
    }
}

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun TvPlayerPanel.title(strings: TvStrings): String =
    when (this) {
        TvPlayerPanel.AUDIO -> strings.audio
        TvPlayerPanel.SUBTITLES -> strings.subtitles
        TvPlayerPanel.QUALITY -> strings.quality
        TvPlayerPanel.SPEED -> strings.speed
        TvPlayerPanel.STATS -> strings.stats
        TvPlayerPanel.SYNCPLAY -> strings.syncPlay
        TvPlayerPanel.NONE -> ""
    }
