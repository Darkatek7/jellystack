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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackExtrasState
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackSegmentType
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.shouldOfferUpNext
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
    subtitleTextSize: SubtitleTextSize = SubtitleTextSize.SYSTEM,
    subtitleBackground: SubtitleBackground = SubtitleBackground.SYSTEM,
    extrasState: PlaybackExtrasState = PlaybackExtrasState(),
    timelineSegments: List<PlaybackSegment> = emptyList(),
    imageBaseUrl: String? = null,
    imageAccessToken: String? = null,
    onSeekTo: (Long) -> Unit = controller::seekTo,
    loadEpisodes: (suspend (seriesId: String) -> List<JellyfinItem>)? = null,
    onPlayEpisode: (JellyfinItem) -> Unit = {},
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
    var upNextDismissed by remember(active?.mediaId) { mutableStateOf(false) }
    val playbackActions =
        tvPlaybackActionModels(
            segmentState = segmentState,
            continuationState = continuationState,
            context =
                TvPlaybackActionContext(
                    isEpisode = active?.metadata?.seriesId != null,
                    phase = active?.phase ?: dev.jellystack.players.PlaybackPhase.Ready,
                    upNextDue =
                        !upNextDismissed &&
                            active != null &&
                            shouldOfferUpNext(
                                positionMs = active.positionMs,
                                durationMs = active.durationMs,
                                creditsActive = segmentState.activeSegments.any { it.type == PlaybackSegmentType.OUTRO },
                            ),
                ),
            strings = strings,
        )
    val standaloneActions = playbackActions.filter { it.id in promptState.visibleActionIds }
    val timelineScrub = remember { TvTimelineScrub(liveSeeks = true, nowMs = System::currentTimeMillis) }
    val hiddenScrub = remember { TvTimelineScrub(liveSeeks = false, nowMs = System::currentTimeMillis) }
    var hiddenScrubGeneration by remember { mutableStateOf(0) }
    var hiddenScrubVisible by remember { mutableStateOf(false) }
    LaunchedEffect(hiddenScrubGeneration) {
        if (!hiddenScrubVisible) return@LaunchedEffect
        delay(TV_HIDDEN_SCRUB_LINGER_MS)
        // A new press during the wait keeps the bar; its release restarts the wait.
        if (hiddenScrub.previewPositionMs == null) hiddenScrubVisible = false
    }
    val nowMs = rememberTvClockMs()
    val seriesId = active?.metadata?.seriesId
    val seasonEpisodes by produceState(emptyList<JellyfinItem>(), seriesId, loadEpisodes) {
        val load = loadEpisodes
        value =
            if (seriesId == null || load == null) {
                emptyList()
            } else {
                runSuspendCatching { load(seriesId) }.getOrDefault(emptyList())
            }
    }
    val osdModel =
        active?.let {
            it
                .toTvPlayerOsdModel(strings, extrasState, timelineSegments, playbackActions)
                .copy(
                    seekBackSeconds = seekBackSeconds,
                    seekForwardSeconds = seekForwardSeconds,
                    canPlayNext = continuationState.nextTarget != null,
                    canShowEpisodes = seasonEpisodes.isNotEmpty() && syncState.currentGroup == null,
                )
        }
    val trickplay = extrasState.trickplay?.takeIf { it.itemId == active?.mediaId }
    val thumbnail: (@Composable (Long) -> Unit)? =
        if (trickplay != null && imageBaseUrl != null) {
            { position -> TvTrickplayThumbnail(trickplay, position, imageBaseUrl, imageAccessToken) }
        } else {
            null
        }
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
            persistentActionIds =
                playbackActions
                    .filter { it.kind != TvPlaybackActionKind.SEGMENT_SKIP }
                    .mapTo(mutableSetOf(), TvPlaybackActionModel::id),
        )
    }
    LaunchedEffect(engine, subtitleTextSize, subtitleBackground) {
        engine.setSubtitleAppearance(subtitleTextSize, subtitleBackground)
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
            TvPlaybackActionKind.WATCH_CREDITS -> upNextDismissed = true
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
                    val horizontal =
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP && horizontal && hiddenScrub.previewPositionMs != null) {
                        hiddenScrub.release()?.let(onSeekTo)
                        hiddenScrub.clear()
                        hiddenScrubGeneration += 1
                        return@onPreviewKeyEvent true
                    }
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
                                if (active == null) {
                                    controlsVisible = true
                                } else {
                                    when (tvHiddenControlsCenterAction(active.isPaused, standaloneActions.isNotEmpty())) {
                                        TvHiddenControlsCenterAction.ACTIVATE_PROMPT -> activatePlaybackAction(standaloneActions.first())
                                        TvHiddenControlsCenterAction.PAUSE_AND_SHOW_CONTROLS -> {
                                            controller.pause()
                                            controlsVisible = true
                                        }
                                        TvHiddenControlsCenterAction.RESUME -> controller.resume()
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        ->
                            if (navigation.current == TvPlayerPanel.NONE && !controlsVisible && active != null) {
                                // Hidden controls: preview the target with the seek bar and seek on release.
                                val stepMs =
                                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                        -seekBackSeconds * 1_000L
                                    } else {
                                        seekForwardSeconds * 1_000L
                                    }
                                hiddenScrubVisible = true
                                hiddenScrub.press(active.positionMs, active.durationMs, stepMs, event.nativeKeyEvent.repeatCount)
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
                clock = tvPlayerClockLabels(nowMs, active, strings),
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
                model = requireNotNull(osdModel),
                actions =
                    TvPlayerOsdActions(
                        onSeekTo = onSeekTo,
                        onTogglePlayPause = { if (active.isPaused) controller.resume() else controller.pause() },
                        onAudio = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.AUDIO) },
                        onSubtitles = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.SUBTITLES) },
                        onMore = { navigation = navigation.openMore() },
                        onPlayNext = onPlayNext,
                        onEpisodes = { navigation = TvPlayerPanelNavigation.closed().openQuick(TvPlayerPanel.EPISODES) },
                        onPromptAction = activatePlaybackAction,
                    ),
                strings = strings,
                interaction = TvPlayerOsdInteraction(controlsFocusRequester, actionEntryFocusRequester, timelineScrub, thumbnail),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        val showHiddenScrub = !controlsVisible && hiddenScrubVisible
        if (active != null && osdModel != null && showHiddenScrub) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 42.dp, end = 42.dp, top = 48.dp, bottom = 40.dp)
                    .testTag(TV_PLAYBACK_SCRUB_OVERLAY_TAG),
            ) {
                TvPlayerTimelineBody(
                    model = osdModel,
                    displayPositionMs = hiddenScrub.previewPositionMs ?: active.positionMs,
                    active = true,
                    thumbnail = thumbnail.takeIf { hiddenScrub.previewPositionMs != null },
                    strings = strings,
                )
            }
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
        val mediaSources =
            TvPlayerMediaSources(
                chapters = osdModel?.chapters.orEmpty(),
                episodes = seasonEpisodes,
                images = TvPlayerImages(imageBaseUrl, imageAccessToken),
            )
        val mediaList = active?.let { tvPlayerMediaList(navigation.current, it, strings, mediaSources) }
        if (active != null && mediaList != null) {
            TvPlayerMediaListPanel(
                list = mediaList,
                strings = strings,
                onSelect = { key ->
                    mediaSources.select(navigation.current, key, active.mediaId, onSeekTo, onPlayEpisode)
                    navigation = TvPlayerPanelNavigation.closed()
                },
                onBack = { navigation = navigation.back() },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        } else if (active != null && navigation.current != TvPlayerPanel.NONE) {
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
                chapterSummary =
                    osdModel
                        ?.takeIf { it.chapters.isNotEmpty() }
                        ?.let { tvChapterLabel(it.chapters, active.positionMs, strings.player.chapterNumber) },
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

internal enum class TvHiddenControlsCenterAction {
    ACTIVATE_PROMPT,
    PAUSE_AND_SHOW_CONTROLS,
    RESUME,
}

/**
 * Center/Enter while the controls are hidden acts immediately instead of only revealing the controls:
 * paused playback resumes, a visible skip/next prompt is activated, otherwise playback pauses and the
 * controls appear so the paused state stays discoverable.
 */
internal fun tvHiddenControlsCenterAction(
    isPaused: Boolean,
    promptVisible: Boolean,
): TvHiddenControlsCenterAction =
    when {
        isPaused -> TvHiddenControlsCenterAction.RESUME
        promptVisible -> TvHiddenControlsCenterAction.ACTIVATE_PROMPT
        else -> TvHiddenControlsCenterAction.PAUSE_AND_SHOW_CONTROLS
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
                if (action.kind == TvPlaybackActionKind.PLAY_NEXT && action.detail != null) {
                    TvUpNextCard(
                        action = action,
                        onClick = { onAction(action) },
                        focusRequester = requester,
                        modifier =
                            Modifier
                                .testTag(action.id)
                                .then(if (index == 0) Modifier.focusRequester(entryFocusRequester) else Modifier)
                                .focusProperties { down = fallbackFocusRequester }
                                .onFocusChanged { if (it.isFocused) lastFocusedActionId = action.id },
                    )
                } else {
                    TvActionButton(
                        label = action.label,
                        onClick = { onAction(action) },
                        modifier =
                            Modifier
                                .testTag(action.id)
                                .then(if (index == 0) Modifier.focusRequester(entryFocusRequester) else Modifier)
                                .focusProperties { down = fallbackFocusRequester },
                        // "Watch credits" only dismisses the card, so the card stays the visual primary.
                        primary = action.kind != TvPlaybackActionKind.WATCH_CREDITS,
                        focusTargetId = action.id,
                        focusRequester = requester,
                        onFocusChanged = { focused -> if (focused) lastFocusedActionId = action.id },
                    )
                }
            }
        }
    }
}

internal const val TV_PLAYBACK_ACTIONS_CONTROLS_TAG = "tv-playback-actions-controls"
internal const val TV_PLAYBACK_ACTIONS_STANDALONE_TAG = "tv-playback-actions-standalone"
internal const val TV_PLAYBACK_TIMELINE_TAG = "tv-playback-timeline"
internal const val TV_PLAYBACK_SCRUB_OVERLAY_TAG = "tv-playback-scrub-overlay"

/** How long the seek bar stays after a scrub with hidden controls, so the new position can be read. */
private const val TV_HIDDEN_SCRUB_LINGER_MS = 1_500L

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
