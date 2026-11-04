@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

private const val TV_PICKER_LIST_HEIGHT_DP = 300

/**
 * Picks audio, subtitles (or off) and version before playing. Every choice applies at once through the callbacks;
 * OK on [TvStrings.applyLabel] or Back closes the dialog. The version column only appears for items with several
 * files. Focus starts on the chosen audio track.
 */
@Composable
internal fun TvDetailTrackPickerDialog(
    options: TvDetailPlaybackOptions,
    strings: TvStrings,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
    onVersion: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = rememberTvDialogInitialFocus()
    val copy = strings.detailScreen
    val hasAudio = options.audio.isNotEmpty()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .background(TvSurfaceRaised, TvShapes.Dialog)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                copy.playbackOptions,
                modifier = Modifier.tvHeading(),
                color = TvText,
                fontSize = TvTextSize.Title,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (options.versions.isNotEmpty()) {
                    TvPickerColumn(
                        title = copy.versionLabel,
                        rows = options.versions.map { TvPickerRow(it.title, it.detail, it.sourceId == options.selectedSourceId) },
                        onPick = { index -> onVersion(options.versions[index].sourceId) },
                        initialFocus = firstFocus.takeIf { !hasAudio },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (hasAudio) {
                    TvPickerColumn(
                        title = copy.audioLabel,
                        rows = options.audio.map { TvPickerRow(it.title, it.detail, it.streamIndex == options.audioStreamIndex) },
                        onPick = { index -> onAudio(options.audio[index].streamIndex) },
                        initialFocus = firstFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (options.subtitles.isNotEmpty()) {
                    TvPickerColumn(
                        title = copy.subtitlesLabel,
                        rows =
                            listOf(TvPickerRow(strings.off, null, options.subtitleStreamIndex == null)) +
                                options.subtitles.map { TvPickerRow(it.title, it.detail, it.streamIndex == options.subtitleStreamIndex) },
                        // Row 0 is "Off", the tracks follow it.
                        onPick = { index -> onSubtitle(options.subtitles.getOrNull(index - 1)?.streamIndex) },
                        initialFocus = firstFocus.takeIf { !hasAudio && options.versions.isEmpty() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TvActionButton(strings.applyLabel, onClick = onDismiss, primary = true, modifier = Modifier.width(230.dp))
        }
    }
}

private data class TvPickerRow(
    val title: String,
    val detail: String?,
    val selected: Boolean,
)

/** One column of choices with a heading; the chosen row takes [initialFocus] and starts in view. */
@Composable
private fun TvPickerColumn(
    title: String,
    rows: List<TvPickerRow>,
    onPick: (Int) -> Unit,
    initialFocus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = rows.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = TV_PICKER_LIST_HEIGHT_DP.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                TvPlayerSelectionRow(
                    title = row.title,
                    metadata = row.detail,
                    selected = row.selected,
                    onClick = { onPick(index) },
                    modifier = if (initialFocus != null && index == selectedIndex) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }
        }
    }
}

/** What the "More" button offers; Go to series only exists for episodes and Version only with several files. */
internal enum class TvDetailMoreEntry { GO_TO_SERIES, VERSION }

internal fun tvDetailMoreEntries(
    isEpisode: Boolean,
    hasSeries: Boolean,
    versionCount: Int,
): List<TvDetailMoreEntry> =
    buildList {
        if (isEpisode && hasSeries) add(TvDetailMoreEntry.GO_TO_SERIES)
        if (versionCount > 1) add(TvDetailMoreEntry.VERSION)
    }

/** The "More" menu: one pill per entry; the first has the focus. */
@Composable
internal fun TvDetailMoreDialog(
    entries: List<TvDetailMoreEntry>,
    strings: TvStrings,
    onEntry: (TvDetailMoreEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = rememberTvDialogInitialFocus()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .background(TvSurfaceRaised, TvShapes.Dialog)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                strings.more,
                modifier = Modifier.tvHeading(),
                color = TvText,
                fontSize = TvTextSize.Title,
                fontWeight = FontWeight.Bold,
            )
            entries.forEachIndexed { index, entry ->
                TvActionButton(
                    label =
                        when (entry) {
                            TvDetailMoreEntry.GO_TO_SERIES -> strings.detailScreen.goToSeries
                            TvDetailMoreEntry.VERSION -> strings.detailScreen.versionLabel
                        },
                    onClick = { onEntry(entry) },
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = firstFocus.takeIf { index == 0 },
                )
            }
        }
    }
}
