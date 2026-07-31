@file:Suppress(
    "FunctionName",
    "LongParameterList",
    "LongMethod",
    "MaxLineLength",
    "MagicNumber",
    "ktlint:standard:function-naming",
)

package app.jellystack.mobile.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jellystack.mobile.R
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SubtitleTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackOptionsSheet(
    state: PlaybackState.Active,
    controller: PlaybackController,
    orientation: Int,
    onDismiss: () -> Unit,
) {
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Dialog(
            onDismissRequest = onDismiss,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.44f))
                        .clickable(onClick = onDismiss),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .widthIn(max = 360.dp)
                            .fillMaxWidth(0.45f)
                            .testTag(AndroidPlaybackTags.OPTIONS)
                            .clickable(enabled = false) {},
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                ) {
                    OptionsContent(state, controller, onDismiss)
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(AndroidPlaybackTags.OPTIONS),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            OptionsContent(state, controller, onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsContent(
    state: PlaybackState.Active,
    controller: PlaybackController,
    onDismiss: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.player_more_options),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.focusRequester(initialFocus).focusable().semantics { heading() },
        )
        val unavailableLabel = stringResource(R.string.player_unavailable)
        val selectedAudioTrack = state.audioTrack ?: state.stream.audioTracks.firstOrNull()
        PlaybackSelectorField<AudioTrack>(
            label = stringResource(R.string.player_audio),
            selectedSummary = selectedAudioTrack?.let(::audioTrackLabel) ?: unavailableLabel,
            options =
                state.stream.audioTracks.map { track ->
                    PlaybackSelectorOption<AudioTrack>(
                        value = track,
                        label = audioTrackLabel(track),
                        selected = track.id == selectedAudioTrack?.id,
                    )
                },
            onSelect = { track -> controller.selectAudioTrack(track.id) },
            enabled = state.stream.audioTracks.isNotEmpty(),
            modifier = Modifier.testTag(AndroidPlaybackTags.AUDIO_SELECTOR),
        )

        val offLabel = stringResource(R.string.player_off)
        val subtitleOptions =
            buildList<PlaybackSelectorOption<SubtitleTrack?>> {
                add(
                    PlaybackSelectorOption(
                        value = null,
                        label = offLabel,
                        selected = state.subtitleTrack == null,
                    ),
                )
                state.stream.subtitleTracks.forEach { track ->
                    add(
                        PlaybackSelectorOption(
                            value = track,
                            label = subtitleTrackLabel(track),
                            selected = track.id == state.subtitleTrack?.id,
                        ),
                    )
                }
            }
        PlaybackSelectorField<SubtitleTrack?>(
            label = stringResource(R.string.player_subtitles),
            selectedSummary = state.subtitleTrack?.let(::subtitleTrackLabel) ?: offLabel,
            options = subtitleOptions,
            onSelect = { track -> controller.selectSubtitle(track?.id) },
            modifier = Modifier.testTag(AndroidPlaybackTags.SUBTITLE_SELECTOR),
        )

        val qualityOptions =
            state.qualityOptions.map { option ->
                PlaybackSelectorOption(
                    value = option,
                    label = qualityLabel(option),
                    selected = option.id == state.selectedQualityId,
                )
            }
        PlaybackSelectorField<PlaybackQualityOption>(
            label = stringResource(R.string.player_quality),
            selectedSummary = qualityOptions.firstOrNull { it.selected }?.label ?: unavailableLabel,
            options = qualityOptions,
            onSelect = { option -> controller.selectQuality(option.id) },
            enabled = qualityOptions.isNotEmpty(),
            modifier = Modifier.testTag(AndroidPlaybackTags.QUALITY_SELECTOR),
        )
        val speedOptions =
            PlaybackController.PLAYBACK_SPEEDS.map { speed ->
                PlaybackSelectorOption(
                    value = speed,
                    label = playbackSpeedLabel(speed),
                    selected = speed == state.playbackSpeed,
                )
            }
        PlaybackSelectorField<Float>(
            label = stringResource(R.string.player_speed),
            selectedSummary = playbackSpeedLabel(state.playbackSpeed),
            options = speedOptions,
            onSelect = controller::setPlaybackSpeed,
            enabled = state !is PlaybackState.CastConnecting && state !is PlaybackState.CastPlayback,
            modifier = Modifier.testTag(AndroidPlaybackTags.SPEED_SELECTOR),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { controller.setStatsForNerdsEnabled(!state.statsForNerdsEnabled) }
                    .testTag(AndroidPlaybackTags.STATS_TOGGLE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.player_stats_for_nerds), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.player_stats_for_nerds_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.statsForNerdsEnabled,
                onCheckedChange = controller::setStatsForNerdsEnabled,
            )
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.player_close))
        }
    }
}

private fun playbackSpeedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private data class PlaybackSelectorOption<T>(
    val value: T,
    val label: String,
    val selected: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PlaybackSelectorField(
    label: String,
    selectedSummary: String,
    options: List<PlaybackSelectorOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = options.isNotEmpty(),
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand ->
            if (enabled) expanded = shouldExpand
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedSummary,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                    trailingIcon =
                        if (option.selected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private fun audioTrackLabel(track: AudioTrack): String =
    listOfNotNull(track.title, track.language, track.codec).filter { it.isNotBlank() }.distinct().joinToString(" · ")

private fun subtitleTrackLabel(track: SubtitleTrack): String =
    listOfNotNull(track.title, track.language, track.format.name).filter { it.isNotBlank() }.distinct().joinToString(" · ")

@Composable
private fun qualityLabel(option: PlaybackQualityOption): String {
    val label =
        if (option.isAuto) {
            stringResource(R.string.player_auto)
        } else {
            option.label.ifBlank { stringResource(R.string.player_video) }
        }
    val mode =
        stringResource(
            when (option.mode) {
                PlaybackMode.DIRECT -> R.string.player_mode_direct
                PlaybackMode.HLS -> R.string.player_mode_adaptive
                PlaybackMode.LOCAL -> R.string.player_mode_local
            },
        )
    return "$label · $mode"
}
