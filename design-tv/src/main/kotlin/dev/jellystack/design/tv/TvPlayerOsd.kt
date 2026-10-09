@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.players.PlaybackChapter
import dev.jellystack.players.PlaybackTimelineMarkers
import dev.jellystack.players.chapterAt
import dev.jellystack.players.formatPlaybackTime

/** Everything the player controls show; built from the playback state, free of player objects. */
internal data class TvPlayerOsdModel(
    val positionMs: Long,
    val durationMs: Long?,
    val isPaused: Boolean,
    val markers: PlaybackTimelineMarkers = PlaybackTimelineMarkers(),
    val chapters: List<PlaybackChapter> = emptyList(),
    val audioLabel: String,
    val subtitleLabel: String,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val canShowEpisodes: Boolean = false,
    val canPlayNext: Boolean = false,
    val promptActions: List<TvPlaybackActionModel> = emptyList(),
)

internal data class TvPlayerOsdActions(
    val onSeekTo: (Long) -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onAudio: () -> Unit,
    val onSubtitles: () -> Unit,
    val onMore: () -> Unit,
    val onEpisodes: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    val onPromptAction: (TvPlaybackActionModel) -> Unit = {},
)

/**
 * Focus targets of the controls, the seek bar's scrubbing state, and the optional trickplay preview
 * drawn for a position while scrubbing.
 */
internal class TvPlayerOsdInteraction(
    val controlsFocus: FocusRequester,
    val promptEntryFocus: FocusRequester,
    val scrub: TvTimelineScrub,
    val thumbnail: (@Composable (positionMs: Long) -> Unit)?,
)

@Composable
internal fun TvPlayerControls(
    model: TvPlayerOsdModel,
    actions: TvPlayerOsdActions,
    strings: TvStrings,
    interaction: TvPlayerOsdInteraction,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
            .padding(start = 42.dp, end = 42.dp, top = 86.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvPlaybackActions(
            actions = model.promptActions,
            fallbackFocusRequester = interaction.controlsFocus,
            entryFocusRequester = interaction.promptEntryFocus,
            onAction = actions.onPromptAction,
            modifier = Modifier.fillMaxWidth().testTag(TV_PLAYBACK_ACTIONS_CONTROLS_TAG),
        )
        TvPlayerTimeline(model, actions, strings, interaction, Modifier.testTag(TV_PLAYBACK_TIMELINE_TAG))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Weighted, so the usually short subtitle label is measured first and the audio label yields space.
                TvPlayerTextButton(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    model.audioLabel,
                    strings.audio,
                    actions.onAudio,
                    Modifier.weight(1f, fill = false),
                )
                TvPlayerTextButton(Icons.Default.Subtitles, model.subtitleLabel, strings.subtitles, actions.onSubtitles)
            }
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvPlayerIconButton(
                    tvSeekBackIcon(model.seekBackSeconds),
                    "${strings.seekBack} ${model.seekBackSeconds}",
                    { actions.onSeekTo(model.seekTarget(-model.seekBackSeconds)) },
                    size = 64.dp,
                    iconSize = 34.dp,
                )
                TvPlayerIconButton(
                    if (model.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (model.isPaused) strings.play else strings.pause,
                    actions.onTogglePlayPause,
                    Modifier
                        .focusRequester(interaction.controlsFocus)
                        .then(
                            if (model.promptActions.isNotEmpty()) {
                                Modifier.focusProperties { up = interaction.promptEntryFocus }
                            } else {
                                Modifier
                            },
                        ),
                    size = 78.dp,
                    iconSize = 42.dp,
                )
                TvPlayerIconButton(
                    tvSeekForwardIcon(model.seekForwardSeconds),
                    "${strings.seekForward} ${model.seekForwardSeconds}",
                    { actions.onSeekTo(model.seekTarget(model.seekForwardSeconds)) },
                    size = 64.dp,
                    iconSize = 34.dp,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                if (model.canShowEpisodes) {
                    TvPlayerIconButton(Icons.Default.VideoLibrary, strings.episodes, actions.onEpisodes)
                }
                if (model.canPlayNext) {
                    TvPlayerIconButton(Icons.Default.SkipNext, strings.nextEpisode, actions.onPlayNext)
                }
                TvPlayerIconButton(Icons.Default.MoreVert, strings.more, actions.onMore)
            }
        }
    }
}

/** Focusable seek bar: Left/Right scrub with a preview and live seeks, Center toggles play/pause. */
@Composable
private fun TvPlayerTimeline(
    model: TvPlayerOsdModel,
    actions: TvPlayerOsdActions,
    strings: TvStrings,
    interaction: TvPlayerOsdInteraction,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scrub = interaction.scrub
    val preview = scrub.previewPositionMs
    Column(
        modifier
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val step =
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                -model.seekBackSeconds * 1_000L
                            } else {
                                model.seekForwardSeconds * 1_000L
                            }
                        scrub
                            .press(model.positionMs, model.durationMs, step, event.nativeKeyEvent.repeatCount)
                            ?.let(actions.onSeekTo)
                    }
                    KeyEvent.ACTION_UP -> {
                        scrub.release()?.let(actions.onSeekTo)
                        scrub.clear()
                    }
                }
                true
            }.onFocusChanged { state ->
                focused = state.isFocused
                if (!state.isFocused) scrub.clear()
            }
            // The thicker bar and larger knob show focus; a ring or glow would cover the time labels.
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = actions.onTogglePlayPause,
            ),
    ) {
        TvPlayerTimelineBody(
            model = model,
            displayPositionMs = preview ?: model.positionMs,
            active = focused || preview != null,
            thumbnail = interaction.thumbnail.takeIf { preview != null },
            strings = strings,
        )
    }
}

/** Seek bar with the optional preview above it and elapsed time, chapter, and remaining time below. */
@Composable
internal fun TvPlayerTimelineBody(
    model: TvPlayerOsdModel,
    displayPositionMs: Long,
    active: Boolean,
    thumbnail: (@Composable (positionMs: Long) -> Unit)?,
    strings: TvStrings,
) {
    val duration = model.durationMs?.takeIf { it > 0L }
    val fraction = duration?.let { (displayPositionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (thumbnail != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val half = (TRICKPLAY_WIDTH_DP / 2).dp
                val centre = maxWidth * fraction
                val start = (centre - half).coerceIn(0.dp, (maxWidth - (TRICKPLAY_WIDTH_DP).dp).coerceAtLeast(0.dp))
                Box(Modifier.offset(x = start)) { thumbnail(displayPositionMs) }
            }
        }
        TvSeekBar(fraction = fraction, markers = model.markers, active = active)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatPlaybackTime(displayPositionMs), color = TvText, fontSize = TvTextSize.Body)
            Text(
                tvChapterLabel(model.chapters, displayPositionMs, strings.player.chapterNumber).orEmpty(),
                color = TvTextMuted,
                fontSize = TvTextSize.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                duration?.let { "−${formatPlaybackTime((it - displayPositionMs).coerceAtLeast(0L))}" } ?: "--:--",
                color = TvText,
                fontSize = TvTextSize.Body,
            )
        }
    }
}

/** Pill button with an icon and a short value such as "English 5.1"; [description] names the control. */
@Composable
internal fun TvPlayerTextButton(
    icon: ImageVector,
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = TvShapes.Pill
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            }.background(Color.Black.copy(alpha = 0.68f), shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) TvPurple else Color.White.copy(alpha = 0.18f), shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOfNotNull(description, label).joinToString(": ")
            }.clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(
            label,
            color = Color.White,
            fontSize = TvTextSize.Body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = TV_PLAYER_LABEL_MAX_WIDTH_DP.dp),
        )
    }
}

private fun TvPlayerOsdModel.seekTarget(seconds: Int): Long = tvScrubTarget(positionMs, durationMs, seconds * 1_000L, 0)

/** Name of the chapter at [positionMs]; unnamed chapters are numbered. */
internal fun tvChapterLabel(
    chapters: List<PlaybackChapter>,
    positionMs: Long,
    chapterNumberFormat: String,
): String? {
    val chapter = chapters.chapterAt(positionMs) ?: return null
    return chapter.name ?: chapterNumberFormat.format(chapter.index + 1)
}

internal fun tvSeekBackIcon(seconds: Int): ImageVector =
    when (seconds) {
        5 -> Icons.Default.Replay5
        10 -> Icons.Default.Replay10
        30 -> Icons.Default.Replay30
        else -> Icons.Default.FastRewind
    }

internal fun tvSeekForwardIcon(seconds: Int): ImageVector =
    when (seconds) {
        5 -> Icons.Default.Forward5
        10 -> Icons.Default.Forward10
        30 -> Icons.Default.Forward30
        else -> Icons.Default.FastForward
    }

/** Track labels ellipsize instead of pushing into the transport buttons at large font scales. */
private const val TV_PLAYER_LABEL_MAX_WIDTH_DP = 140
