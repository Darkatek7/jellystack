@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.formatPlaybackTime
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import dev.jellystack.players.syncplay.SyncPlayUiState
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun TvPlaybackScreen(
    controller: PlaybackController,
    engine: AndroidPlayerEngine,
    syncPlay: SyncPlayCoordinator,
    playbackState: PlaybackState,
    syncState: SyncPlayUiState,
    segmentState: PlaybackSegmentState,
    continuationState: PlaybackContinuationState,
    seekBackSeconds: Int = 10,
    seekForwardSeconds: Int = 30,
    onSkipSegment: (PlaybackSegmentAction) -> Unit,
    onPlayNext: () -> Unit,
    strings: TvStrings,
    stopPlayback: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var navigation by remember { mutableStateOf(TvPlayerPanelNavigation.closed()) }
    var interactionGeneration by remember { mutableStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val actionEntryFocusRequester = remember { FocusRequester() }
    val active = playbackState as? PlaybackState.Active
    val promptCoordinator = remember(scope) { TvPlaybackPromptCoordinator(scope) }
    val promptState by promptCoordinator.state.collectAsStateWithLifecycle()
    val playbackActions =
        tvPlaybackActionModels(
            segmentState = segmentState,
            continuationState = continuationState,
            isEpisode = active?.metadata?.seriesId != null,
            playbackPhase = active?.phase ?: dev.jellystack.players.PlaybackPhase.Ready,
            strings = strings,
        )
    val standaloneActions = playbackActions.filter { it.id in promptState.visibleActionIds }
    val subtitleBottomPaddingFraction =
        tvSubtitleBottomPaddingFraction(
            controlsVisible = controlsVisible,
            standaloneActionsVisible = standaloneActions.isNotEmpty(),
            panelOpen = navigation.current != TvPlayerPanel.NONE,
        )

    LaunchedEffect(playbackActions.map { it.id }, controlsVisible) {
        promptCoordinator.onPresentationChanged(
            actionIds = playbackActions.map { it.id },
            controlsVisible = controlsVisible,
        )
    }
    LaunchedEffect(engine, subtitleBottomPaddingFraction) {
        engine.setSubtitleBottomPaddingFraction(subtitleBottomPaddingFraction)
    }

    LaunchedEffect(controlsVisible, navigation.current, interactionGeneration, active?.isPaused) {
        if (!shouldAutoHideTvControls(controlsVisible, navigation.current != TvPlayerPanel.NONE, active?.isPaused == true)) {
            return@LaunchedEffect
        }
        delay(5_000)
        controlsVisible = false
    }
    LaunchedEffect(active != null, controlsVisible, navigation.current) {
        if (active != null && navigation.current == TvPlayerPanel.NONE) {
            if (controlsVisible) controlsFocusRequester.requestFocus() else playerFocusRequester.requestFocus()
        }
    }
    DisposableEffect(engine) {
        onDispose {
            engine.setSubtitleBottomPaddingFraction(TV_SUBTITLE_NORMAL_PADDING_FRACTION)
            stopPlayback()
        }
    }
    DisposableEffect(promptCoordinator) { onDispose(promptCoordinator::release) }

    val activatePlaybackAction: (TvPlaybackActionModel) -> Unit = { action ->
        when (action.kind) {
            TvPlaybackActionKind.SEGMENT_SKIP -> action.segmentAction?.let(onSkipSegment)
            TvPlaybackActionKind.PLAY_NEXT -> onPlayNext()
        }
    }
    val handlePlaybackBack = {
        when {
            navigation.current != TvPlayerPanel.NONE -> navigation = navigation.back()
            controlsVisible -> controlsVisible = false
            else -> onClose()
        }
    }
    TvPlayerBackHandler(handlePlaybackBack)

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
                                val stepMs =
                                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                        -seekBackSeconds * 1_000L
                                    } else {
                                        seekForwardSeconds * 1_000L
                                    }
                                controller.seekTo(
                                    tvScrubTarget(
                                        positionMs = active.positionMs,
                                        durationMs = active.durationMs,
                                        stepMs = stepMs,
                                        repeatCount = event.nativeKeyEvent.repeatCount,
                                    ),
                                )
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_BACK -> {
                            handlePlaybackBack()
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
        when (playbackState) {
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
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.86f), Color.Black.copy(alpha = 0.42f), Color.Transparent),
                            ),
                        ).padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 54.dp),
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
                actionEntryFocusRequester = actionEntryFocusRequester,
                actions = playbackActions,
                seekBackSeconds = seekBackSeconds,
                seekForwardSeconds = seekForwardSeconds,
                onAction = activatePlaybackAction,
                onAudio = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.AUDIO) },
                onSubtitles = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.SUBTITLES) },
                onMore = { navigation = navigation.openMore() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        val controlsHiddenOverlay = active != null && !controlsVisible && navigation.current == TvPlayerPanel.NONE
        if (controlsHiddenOverlay && active.isPaused) {
            Text(
                strings.pausedLabel,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
                        .padding(horizontal = 26.dp, vertical = 12.dp)
                        .testTag("tv-playback-paused-chip"),
            )
        }
        if (active != null && !controlsVisible) {
            TvPlaybackActions(
                actions = standaloneActions,
                fallbackFocusRequester = playerFocusRequester,
                entryFocusRequester = actionEntryFocusRequester,
                onAction = activatePlaybackAction,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .padding(horizontal = 42.dp, vertical = 132.dp)
                        .testTag(TV_PLAYBACK_ACTIONS_STANDALONE_TAG),
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
internal fun TvPlayerBackHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
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
        modifier
            .width(
                720.dp,
            ).background(TvSurface.copy(alpha = 0.98f), RoundedCornerShape(28.dp))
            .padding(horizontal = 48.dp, vertical = 38.dp),
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
    actionEntryFocusRequester: FocusRequester,
    actions: List<TvPlaybackActionModel>,
    seekBackSeconds: Int,
    seekForwardSeconds: Int,
    onAction: (TvPlaybackActionModel) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))),
            ).padding(start = 42.dp, end = 42.dp, top = 86.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvPlaybackActions(
            actions = actions,
            fallbackFocusRequester = controlsFocusRequester,
            entryFocusRequester = actionEntryFocusRequester,
            onAction = onAction,
            modifier = Modifier.fillMaxWidth().testTag(TV_PLAYBACK_ACTIONS_CONTROLS_TAG),
        )
        TvProgress(
            active = active,
            controller = controller,
            seekBackMs = seekBackSeconds * 1_000L,
            seekForwardMs = seekForwardSeconds * 1_000L,
            modifier = Modifier.testTag(TV_PLAYBACK_TIMELINE_TAG),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                TvPlayerIconButton(Icons.AutoMirrored.Filled.VolumeUp, strings.audio, onAudio)
                Spacer(Modifier.width(12.dp))
                TvPlayerIconButton(Icons.Default.Subtitles, strings.subtitles, onSubtitles)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                TvPlayerIconButton(Icons.Default.Replay10, "${strings.seekBack} $seekBackSeconds", {
                    controller.seekTo(
                        tvScrubTarget(
                            positionMs = active.positionMs,
                            durationMs = active.durationMs,
                            stepMs = -seekBackSeconds * 1_000L,
                            repeatCount = 0,
                        ),
                    )
                }, size = 64.dp, iconSize = 34.dp)
                TvPlayerIconButton(
                    if (active.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (active.isPaused) strings.play else strings.pause,
                    { if (active.isPaused) controller.resume() else controller.pause() },
                    Modifier
                        .focusRequester(controlsFocusRequester)
                        .then(
                            if (actions.isNotEmpty()) {
                                Modifier.focusProperties { up = actionEntryFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
                    size = 78.dp,
                    iconSize = 42.dp,
                )
                TvPlayerIconButton(Icons.Default.Forward30, "${strings.seekForward} $seekForwardSeconds", {
                    controller.seekTo(
                        tvScrubTarget(
                            positionMs = active.positionMs,
                            durationMs = active.durationMs,
                            stepMs = seekForwardSeconds * 1_000L,
                            repeatCount = 0,
                        ),
                    )
                }, size = 64.dp, iconSize = 34.dp)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TvPlayerIconButton(Icons.Default.MoreVert, strings.more, onMore)
            }
        }
    }
}

/** Repeat-accelerated D-pad scrub target; the base for every seek path in the TV player. */
internal fun tvScrubTarget(
    positionMs: Long,
    durationMs: Long?,
    stepMs: Long,
    repeatCount: Int,
): Long {
    val multiplier = (repeatCount + 1).coerceAtMost(TV_SCRUB_REPEAT_ACCELERATION_CAP)
    val upperBound = durationMs?.takeIf { it > 0L } ?: Long.MAX_VALUE
    return (positionMs + stepMs * multiplier).coerceIn(0L, upperBound)
}

/**
 * Controls may start their hide countdown only while playing with no panel open.
 * Paused playback keeps controls on screen so the state stays discoverable.
 */
internal fun shouldAutoHideTvControls(
    controlsVisible: Boolean,
    panelOpen: Boolean,
    isPaused: Boolean,
): Boolean = controlsVisible && !panelOpen && !isPaused

private const val TV_SCRUB_REPEAT_ACCELERATION_CAP = 6

/** Minimum spacing between live seeks while the user keeps holding a direction. */
internal const val TV_SCRUB_COMMIT_INTERVAL_MS = 250L

@Composable
private fun TvProgress(
    active: PlaybackState.Active,
    controller: PlaybackController,
    seekBackMs: Long,
    seekForwardMs: Long,
    modifier: Modifier = Modifier,
) {
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    var lastCommitElapsed by remember { mutableStateOf(Long.MIN_VALUE) }
    val duration = active.durationMs
    val displayPosition = previewPosition ?: active.positionMs
    val fraction =
        if (duration != null && duration > 0) (displayPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun commit(
        target: Long,
        force: Boolean,
    ) {
        val elapsed = System.currentTimeMillis()
        if (force || lastCommitElapsed == Long.MIN_VALUE || elapsed - lastCommitElapsed >= TV_SCRUB_COMMIT_INTERVAL_MS) {
            lastCommitElapsed = elapsed
            controller.seekTo(target)
        }
    }

    Column(
        modifier
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (
                    keyCode != KeyEvent.KEYCODE_DPAD_LEFT &&
                    keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val target =
                            tvScrubTarget(
                                positionMs = active.positionMs,
                                durationMs = duration,
                                stepMs =
                                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                        -seekBackMs
                                    } else {
                                        seekForwardMs
                                    },
                                repeatCount = event.nativeKeyEvent.repeatCount,
                            )
                        previewPosition = target
                        commit(target, force = false)
                    }

                    KeyEvent.ACTION_UP -> {
                        previewPosition?.let { commit(it, force = true) }
                        previewPosition = null
                    }
                }
                true
            }.tvFocusable(
                onClick = { if (active.isPaused) controller.resume() else controller.pause() },
                shape = RoundedCornerShape(12.dp),
                scale = 1.01f,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .background(TvPurple, RoundedCornerShape(50)),
            )
        }
        Text(
            "${formatPlaybackTime(displayPosition)}  /  " +
                "${duration?.takeIf { it > 0 }?.let(::formatPlaybackTime) ?: "--:--"}",
            color = TvTextMuted,
        )
    }
}

@Composable
internal fun TvPlaybackCompletionPrompt(
    continuationState: PlaybackContinuationState,
    strings: TvStrings,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    tvAutoplayPromptModel(continuationState)?.let { prompt ->
        TvAutoplayPrompt(
            model = prompt,
            strings = strings,
            onPlayNow = onPlayNow,
            onCancel = onCancel,
        )
    }
}

@Composable
internal fun TvPlaybackActions(
    actions: List<TvPlaybackActionModel>,
    fallbackFocusRequester: FocusRequester,
    entryFocusRequester: FocusRequester,
    onAction: (TvPlaybackActionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocusedActionId by remember { mutableStateOf<String?>(null) }
    val actionIds = actions.map { it.id }
    LaunchedEffect(actionIds) {
        val focusedId = lastFocusedActionId
        if (focusedId != null) {
            withFrameNanos { }
            if (focusedId in actionIds) {
                runCatching { requesters[focusedId]?.requestFocus() }
            } else {
                runCatching { fallbackFocusRequester.requestFocus() }
                lastFocusedActionId = null
            }
        }
        requesters.keys.retainAll(actionIds.toSet())
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            key(action.id) {
                val requester = requesters.getOrPut(action.id) { FocusRequester() }
                TvActionButton(
                    label = action.label,
                    onClick = { onAction(action) },
                    modifier =
                        Modifier
                            .testTag(action.id)
                            .then(if (index == 0) Modifier.focusRequester(entryFocusRequester) else Modifier)
                            .focusProperties { down = fallbackFocusRequester },
                    primary = true,
                    focusTargetId = action.id,
                    focusRequester = requester,
                    onFocusChanged = { focused -> if (focused) lastFocusedActionId = action.id },
                )
            }
        }
    }
}

internal const val TV_PLAYBACK_ACTIONS_CONTROLS_TAG = "tv-playback-actions-controls"
internal const val TV_PLAYBACK_ACTIONS_STANDALONE_TAG = "tv-playback-actions-standalone"
internal const val TV_PLAYBACK_TIMELINE_TAG = "tv-playback-timeline"

private fun dev.jellystack.players.PlaybackMetadata?.playerPrimaryTitle(strings: TvStrings): String =
    this?.seriesName?.takeIf(String::isNotBlank) ?: this?.title?.takeIf(String::isNotBlank) ?: strings.playback

private fun dev.jellystack.players.PlaybackMetadata?.playerSecondaryTitle(): String? {
    val metadata = this
    return if (metadata == null || metadata.seriesName.isNullOrBlank()) {
        null
    } else {
        val episodePrefix =
            if (metadata.seasonNumber != null &&
                metadata.episodeNumber != null
            ) {
                "S${metadata.seasonNumber} · E${metadata.episodeNumber}"
            } else {
                null
            }
        listOfNotNull(
            episodePrefix,
            (metadata.episodeName ?: metadata.title)?.takeIf(String::isNotBlank),
        ).joinToString(" · ").ifBlank {
            null
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun tvPlaybackErrorMessage(
    rawMessage: String,
    strings: TvStrings,
): String = strings.playbackFailedMessage
