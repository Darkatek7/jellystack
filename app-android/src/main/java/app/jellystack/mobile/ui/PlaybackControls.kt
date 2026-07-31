@file:Suppress(
    "FunctionName",
    "LongParameterList",
    "LongMethod",
    "MatchingDeclarationName",
    "MaxLineLength",
    "MagicNumber",
    "ktlint:standard:function-naming",
)

package app.jellystack.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jellystack.mobile.R
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.formatPlaybackDuration
import dev.jellystack.players.formatPlaybackTime
import kotlin.math.roundToLong

internal object AndroidPlaybackTags {
    const val CAST_CONNECTING_SPINNER = "cast_connecting_spinner"
    const val CAST_ERROR_PROMPT = "cast_error_prompt"
    const val CAST_RECONNECT_BUTTON = "cast_reconnect_button"
    const val CAST_DISCONNECT_BUTTON = "cast_disconnect_button"
    const val CAST_ROUTE_PICKER = "cast_route_picker_player"
    const val CAST_DEVICE_LABEL = "cast_device_label"
    const val CAST_STATUS_LABEL = "cast_status_label"
    const val CAST_DISCONNECT_TOP = "cast_disconnect_top"
    const val TIMELINE = "player_timeline"
    const val OPTIONS = "player_options_panel"
    const val AUDIO_SELECTOR = "player_audio_selector"
    const val SUBTITLE_SELECTOR = "player_subtitle_selector"
    const val QUALITY_SELECTOR = "player_quality_selector"
    const val SPEED_SELECTOR = "player_speed_selector"
    const val STATS_TOGGLE = "player_stats_toggle"
    const val STATS_PANEL = "player_stats_panel"
}

@Composable
internal fun BoxScope.PlaybackControls(
    state: PlaybackState.Active,
    controller: PlaybackController,
    castState: CastConnectionState,
    castRouteButton: @Composable (CastConnectionState) -> Unit,
    onShowOptions: () -> Unit,
    onInteraction: () -> Unit,
    onControlFocusChanged: (String, Boolean) -> Unit,
    seekBackSeconds: Int = 10,
    seekForwardSeconds: Int = 30,
) {
    PlayerTopBar(
        state = state,
        castState = castState,
        castRouteButton = castRouteButton,
        onClose = { controller.stop() },
        onShowOptions = onShowOptions,
        onControlFocusChanged = onControlFocusChanged,
    )
    PlayerBottomControls(
        state = state,
        controller = controller,
        onInteraction = onInteraction,
        onControlFocusChanged = onControlFocusChanged,
        seekBackSeconds = seekBackSeconds,
        seekForwardSeconds = seekForwardSeconds,
    )
}

@Composable
private fun BoxScope.PlayerTopBar(
    state: PlaybackState.Active,
    castState: CastConnectionState,
    castRouteButton: @Composable (CastConnectionState) -> Unit,
    onClose: () -> Unit,
    onShowOptions: () -> Unit,
    onControlFocusChanged: (String, Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(
            icon = { Icon(Icons.Filled.Close, contentDescription = null) },
            description = stringResource(R.string.player_exit),
            onClick = onClose,
            focusId = "close",
            onFocusChanged = onControlFocusChanged,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = playbackPrimaryTitle(state.metadata),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            playbackSecondaryTitle(state.metadata)?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .testTag(AndroidPlaybackTags.CAST_ROUTE_PICKER)
                    .onFocusChanged { onControlFocusChanged("cast", it.hasFocus) }
                    .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            castRouteButton(castState)
        }
        PlayerIconButton(
            icon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
            description = stringResource(R.string.player_more_options),
            onClick = onShowOptions,
            focusId = "options",
            onFocusChanged = onControlFocusChanged,
        )
    }
}

@Composable
private fun BoxScope.PlayerBottomControls(
    state: PlaybackState.Active,
    controller: PlaybackController,
    onInteraction: () -> Unit,
    onControlFocusChanged: (String, Boolean) -> Unit,
    seekBackSeconds: Int,
    seekForwardSeconds: Int,
) {
    val durationMs = state.durationMs?.takeIf { it > 0L }
    val maxValue = (durationMs ?: state.positionMs.coerceAtLeast(1L)).toFloat()
    var sliderValue by remember(state.mediaId) { mutableStateOf(state.positionMs.toFloat()) }
    var scrubbing by remember(state.mediaId) { mutableStateOf(false) }
    val timelineDescription = stringResource(R.string.player_timeline)

    LaunchedEffect(state.positionMs, durationMs, scrubbing) {
        if (!scrubbing) sliderValue = state.positionMs.coerceAtLeast(0L).toFloat()
    }

    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Slider(
            value = sliderValue.coerceIn(0f, maxValue),
            onValueChange = {
                scrubbing = true
                sliderValue = it
                onInteraction()
            },
            onValueChangeFinished = {
                val target = sliderValue.roundToLong().let { durationMs?.let(it::coerceAtMost) ?: it }
                controller.seekTo(target.coerceAtLeast(0L))
                scrubbing = false
                onInteraction()
            },
            valueRange = 0f..maxValue,
            enabled = durationMs != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp)
                    .testTag(AndroidPlaybackTags.TIMELINE)
                    .onFocusChanged { onControlFocusChanged("timeline", it.isFocused) }
                    .semantics { contentDescription = timelineDescription },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatPlaybackTime(sliderValue.roundToLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(
                formatPlaybackDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                icon = { Icon(Icons.Filled.Replay10, contentDescription = null) },
                description = stringResource(R.string.player_seek_back),
                onClick = {
                    controller.seekTo((state.positionMs - seekBackSeconds * 1_000L).coerceAtLeast(0L))
                    onInteraction()
                },
                focusId = "seek-back",
                onFocusChanged = onControlFocusChanged,
            )
            PlayerIconButton(
                icon = { Icon(if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null) },
                description = stringResource(if (state.isPaused) R.string.player_play else R.string.player_pause),
                onClick = {
                    if (state.isPaused) controller.resume() else controller.pause()
                    onInteraction()
                },
                focusId = "play-pause",
                onFocusChanged = onControlFocusChanged,
            )
            PlayerIconButton(
                icon = { Icon(Icons.Filled.Forward30, contentDescription = null) },
                description = stringResource(R.string.player_seek_forward),
                onClick = {
                    val target = state.positionMs + seekForwardSeconds * 1_000L
                    controller.seekTo(durationMs?.let(target::coerceAtMost) ?: target)
                    onInteraction()
                },
                focusId = "seek-forward",
                onFocusChanged = onControlFocusChanged,
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: @Composable () -> Unit,
    description: String,
    onClick: () -> Unit,
    focusId: String,
    onFocusChanged: (String, Boolean) -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(48.dp)
                .onFocusChanged { onFocusChanged(focusId, it.isFocused) }
                .semantics { contentDescription = description },
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(modifier = Modifier.semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) { icon() }
        }
    }
}

@Composable
internal fun playbackPrimaryTitle(metadata: PlaybackMetadata?): String =
    metadata?.seriesName?.takeIf { it.isNotBlank() }
        ?: metadata?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.player_unknown_title)

@Composable
internal fun playbackSecondaryTitle(metadata: PlaybackMetadata?): String? {
    if (metadata == null || metadata.seriesName.isNullOrBlank()) return null
    val episode = metadata.episodeName ?: metadata.title
    val season = metadata.seasonNumber
    val number = metadata.episodeNumber
    return when {
        season != null && number != null && !episode.isNullOrBlank() ->
            stringResource(R.string.player_episode_heading, season, number, episode)
        season != null && number != null -> stringResource(R.string.player_season_episode, season, number)
        else -> episode
    }
}
