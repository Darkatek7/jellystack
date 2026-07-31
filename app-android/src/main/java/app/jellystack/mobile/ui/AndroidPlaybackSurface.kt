@file:Suppress(
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "MagicNumber",
    "FunctionName",
    "ktlint:standard:function-naming",
)

package app.jellystack.mobile.ui

import android.content.res.Configuration
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import app.jellystack.mobile.R
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun AndroidPlaybackSurface(
    controller: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    castSessionManager: CastSessionManager,
    castRouteButton: @Composable (CastConnectionState) -> Unit,
    modifier: Modifier = Modifier,
    orientation: Int = Configuration.ORIENTATION_UNDEFINED,
    isDarkTheme: Boolean = true,
    seekBackSeconds: Int = 10,
    seekForwardSeconds: Int = 30,
    subtitleTextSize: SubtitleTextSize = SubtitleTextSize.SYSTEM,
    subtitleBackground: SubtitleBackground = SubtitleBackground.SYSTEM,
) {
    val playbackState by controller.state.collectAsState()
    SideEffect {
        playerEngine.setSubtitleAppearance(subtitleTextSize, subtitleBackground)
    }
    if (playbackState is PlaybackState.Stopped) return

    val castState by castSessionManager.connectionState.collectAsState(initial = CastConnectionState.Idle)
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    val controlFocusStates = remember { mutableStateMapOf<String, Boolean>() }
    val hasControlFocus = controlFocusStates.values.any { it }
    var touchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val active = playbackState as? PlaybackState.Active
    val accessibilityManager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var touchExplorationEnabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(accessibilityManager) {
        val listener =
            AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
                touchExplorationEnabled = enabled
            }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager?.removeTouchExplorationStateChangeListener(listener) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val systemUiController = remember(context) { context.findActivity()?.let(::PlayerSystemUiController) }
    val localVideoVisible =
        active?.mediaKind == PlaybackMediaKind.VIDEO &&
            castState !is CastConnectionState.Connected &&
            castState !is CastConnectionState.Connecting

    DisposableEffect(systemUiController, localVideoVisible, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> if (localVideoVisible) systemUiController?.enterImmersiveVideo()
                    Lifecycle.Event.ON_STOP -> systemUiController?.restoreApplicationBars()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (localVideoVisible) systemUiController?.enterImmersiveVideo() else systemUiController?.restoreApplicationBars()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            systemUiController?.restoreApplicationBars()
        }
    }

    val autoHideControls =
        active?.let {
            shouldAutoHideControls(
                PlayerControlsVisibilityInput(
                    mediaKind = it.mediaKind,
                    phase = it.phase,
                    isPaused = it.isPaused,
                    hasControlFocus = hasControlFocus,
                    modalOpen = optionsVisible,
                    touchActive = touchActive,
                    touchExplorationEnabled = touchExplorationEnabled,
                ),
            )
        } == true
    LaunchedEffect(autoHideControls, controlsVisible, interactionToken) {
        if (autoHideControls && controlsVisible) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    BackHandler {
        when {
            optionsVisible -> optionsVisible = false
            else -> controller.stop()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (optionsVisible) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            when (val state = playbackState) {
                is PlaybackState.Preparing -> PreparingOverlay(state.metadata, controller::stop)
                is PlaybackState.PlaybackError -> ErrorOverlay(state, controller)
                is PlaybackState.Active ->
                    ActivePlayer(
                        state = state,
                        controller = controller,
                        playerEngine = playerEngine,
                        castState = castState,
                        castSessionManager = castSessionManager,
                        castRouteButton = castRouteButton,
                        controlsVisible = controlsVisible,
                        onShowControls = {
                            controlsVisible = true
                            interactionToken++
                        },
                        onInteraction = {
                            controlsVisible = true
                            interactionToken++
                        },
                        onControlFocusChanged = { id, focused -> controlFocusStates[id] = focused },
                        onTouchActiveChanged = { touchActive = it },
                        onShowOptions = {
                            controlsVisible = true
                            optionsVisible = true
                        },
                        seekBackSeconds = seekBackSeconds,
                        seekForwardSeconds = seekForwardSeconds,
                    )
                PlaybackState.Stopped -> Unit
            }
        }

        if (active != null && optionsVisible) {
            PlaybackOptionsSheet(
                state = active,
                controller = controller,
                orientation = orientation,
                onDismiss = { optionsVisible = false },
            )
        }
    }
}

@Composable
private fun PreparingOverlay(
    metadata: PlaybackMetadata?,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerCloseButton(onClose)
        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = playbackPrimaryTitle(metadata),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            playbackSecondaryTitle(metadata)?.let {
                Text(it, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            }
            Text(stringResource(R.string.player_preparing), color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ErrorOverlay(
    state: PlaybackState.PlaybackError,
    controller: PlaybackController,
) {
    val errorDetail = state.message.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_error_default)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.player_error_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = errorDetail,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.canRetry) Button(onClick = controller::retry) { Text(stringResource(R.string.player_retry)) }
                TextButton(onClick = { controller.stop(saveProgress = false) }) {
                    Text(stringResource(R.string.player_close), color = Color.White)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun ActivePlayer(
    state: PlaybackState.Active,
    controller: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    castState: CastConnectionState,
    castSessionManager: CastSessionManager,
    castRouteButton: @Composable (CastConnectionState) -> Unit,
    controlsVisible: Boolean,
    onShowControls: () -> Unit,
    onInteraction: () -> Unit,
    onControlFocusChanged: (String, Boolean) -> Unit,
    onTouchActiveChanged: (Boolean) -> Unit,
    onShowOptions: () -> Unit,
    seekBackSeconds: Int,
    seekForwardSeconds: Int,
) {
    val scope = rememberCoroutineScope()
    val remote = castState is CastConnectionState.Connected || castState is CastConnectionState.Connecting
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onTouchActiveChanged(true)
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            onTouchActiveChanged(false)
                        }
                    }
                },
    ) {
        if (!remote && state.mediaKind == PlaybackMediaKind.VIDEO) {
            AndroidVideoSurface(playerEngine, onShowControls)
        } else {
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onShowControls() } })
        }
        when (castState) {
            is CastConnectionState.Connecting ->
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .testTag(AndroidPlaybackTags.CAST_CONNECTING_SPINNER),
                )
            is CastConnectionState.Error ->
                CastErrorPrompt(
                    message = castState.cause?.message,
                    onReconnect = { scope.launch { castSessionManager.play() } },
                    onDisconnect = { scope.launch { castSessionManager.disconnect() } },
                )
            else -> Unit
        }
        if (controlsVisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusGroup(),
            ) {
                PlaybackControls(
                    state = state,
                    controller = controller,
                    castState = castState,
                    castRouteButton = castRouteButton,
                    onShowOptions = onShowOptions,
                    onInteraction = onInteraction,
                    onControlFocusChanged = onControlFocusChanged,
                    seekBackSeconds = seekBackSeconds,
                    seekForwardSeconds = seekForwardSeconds,
                )
                if (state.statsForNerdsEnabled) {
                    PlaybackStatsPanel(state)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlaybackStatsPanel(state: PlaybackState.Active) {
    val stats = state.runtimeStats
    val modeLabel = stringResource(R.string.player_stats_mode)
    val videoLabel = stringResource(R.string.player_stats_video)
    val audioLabel = stringResource(R.string.player_stats_audio)
    val bufferLabel = stringResource(R.string.player_stats_buffer)
    val droppedLabel = stringResource(R.string.player_stats_dropped)
    val entries =
        buildList {
            stats.playbackMode?.let { add(modeLabel to it.name) }
            val resolution =
                if (stats.width != null && stats.height != null) "${stats.width}x${stats.height}" else null
            listOfNotNull(resolution, stats.videoCodec, stats.hdr, stats.frameRate?.let { "${it.toInt()} fps" })
                .takeIf { it.isNotEmpty() }
                ?.let { add(videoLabel to it.joinToString(" · ")) }
            stats.audioCodec?.let { add(audioLabel to it) }
            stats.bufferedDurationMs?.let {
                add(bufferLabel to "${it / 1_000}s")
            }
            stats.droppedFrames?.let {
                add(droppedLabel to it.toString())
            }
        }
    if (entries.isEmpty()) return
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 16.dp)
                .testTag(AndroidPlaybackTags.STATS_PANEL),
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            entries.forEach { (label, value) ->
                Text(
                    text = "$label: $value",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CastErrorPrompt(
    message: String?,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag(AndroidPlaybackTags.CAST_ERROR_PROMPT),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.player_cast_lost), style = MaterialTheme.typography.titleMedium)
            message?.takeIf { it.isNotBlank() }?.let { Text(it, textAlign = TextAlign.Center) }
            Button(onClick = onReconnect, modifier = Modifier.testTag(AndroidPlaybackTags.CAST_RECONNECT_BUTTON)) {
                Text(stringResource(R.string.player_reconnect))
            }
            TextButton(onClick = onDisconnect, modifier = Modifier.testTag(AndroidPlaybackTags.CAST_DISCONNECT_BUTTON)) {
                Text(stringResource(R.string.player_disconnect))
            }
        }
    }
}

@Composable
private fun BoxScope.PlayerCloseButton(onClose: () -> Unit) {
    val description = stringResource(R.string.player_exit)
    IconButton(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(48.dp)
                .semantics { contentDescription = description },
        onClick = onClose,
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
    }
}

@UnstableApi
@Composable
private fun AndroidVideoSurface(
    playerEngine: AndroidPlayerEngine,
    onShowControls: () -> Unit,
) {
    val description = stringResource(R.string.player_video_surface)
    val showControlsLabel = stringResource(R.string.player_show_controls)
    AndroidView(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onShowControls() } }
                .semantics {
                    contentDescription = description
                    onClick(label = showControlsLabel) {
                        onShowControls()
                        true
                    }
                },
        factory = { context ->
            playerEngine.createVideoSurface(context).apply {
                isClickable = true
                setOnClickListener { onShowControls() }
            }
        },
        update = { surface ->
            playerEngine.updateVideoSurface(surface)
            surface.isClickable = true
            surface.setOnClickListener { onShowControls() }
        },
        onRelease = playerEngine::releaseVideoSurface,
    )
}
