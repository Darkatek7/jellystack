@file:Suppress("FunctionName")

package dev.jellystack.design.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jellystack.design.components.ModalFocusScope
import dev.jellystack.design.navigation.ShellModal
import dev.jellystack.design.navigation.ShellModalOwner
import dev.jellystack.players.SubtitleTrack

internal object TrackPickerTestTags {
    const val AUDIO = "audio_track_picker"
    const val SUBTITLES = "subtitle_track_picker"
    const val DIALOG = "track_picker_dialog"
}

internal data class TrackPickerOption<T>(
    val value: T,
    val fullLabel: String,
    val selected: Boolean,
)

internal sealed interface SubtitleSelection {
    data object Off : SubtitleSelection

    data class Track(
        val track: SubtitleTrack,
    ) : SubtitleSelection
}

@Composable
internal fun <T> CompactTrackPicker(
    label: String,
    selectedSummary: String,
    options: List<TrackPickerOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = options.isNotEmpty(),
    onShellModalChange: (ShellModalOwner?) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val returnFocusRequester = remember { FocusRequester() }
    val closePicker = {
        open = false
        onShellModalChange(null)
    }
    OutlinedButton(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .focusRequester(returnFocusRequester)
                .focusProperties { canFocus = enabled }
                .semantics { contentDescription = "$label: $selectedSummary" },
        enabled = enabled,
        onClick = {
            open = true
            onShellModalChange(
                ShellModalOwner(
                    modal = ShellModal.PlayerOptions,
                    dismiss = closePicker,
                ),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label · $selectedSummary",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("⌄")
        }
    }

    if (open) {
        TrackPickerDialog(
            label = label,
            options = options,
            onSelect = onSelect,
            onDismissRequest = closePicker,
            returnFocusRequester = returnFocusRequester,
        )
    }
}

@Composable
internal fun <T> TrackPickerDialog(
    label: String,
    options: List<TrackPickerOption<T>>,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    returnFocusRequester: FocusRequester? = null,
) {
    if (options.isEmpty()) return
    ModalFocusScope(
        onDismissRequest = onDismissRequest,
        returnFocusRequester = returnFocusRequester,
    ) { initialFocusModifier ->
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .heightIn(max = 560.dp)
                    .testTag(TrackPickerTestTags.DIALOG),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn {
                    itemsIndexed(options) { index, option ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = option.fullLabel,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = option.selected,
                                    onClick = null,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) initialFocusModifier else Modifier)
                                    .clickable {
                                        onSelect(option.value)
                                        onDismissRequest()
                                    },
                        )
                    }
                }
            }
        }
    }
}
