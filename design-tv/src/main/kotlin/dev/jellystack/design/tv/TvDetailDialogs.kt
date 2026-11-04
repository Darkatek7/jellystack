@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable

/** Which dialog a Jellyfin detail page shows on top of itself. */
internal enum class TvDetailDialog { MORE, TRACKS }

/** What the dialogs of a detail page report back. */
internal class TvDetailDialogCallbacks(
    val onMoreEntry: (TvDetailMoreEntry) -> Unit,
    val onSelection: ((TvDetailSelection) -> TvDetailSelection) -> Unit,
    val onDismiss: () -> Unit,
)

/** The "more" menu and the track picker of a Jellyfin detail page. */
@Composable
internal fun TvDetailDialogs(
    dialog: TvDetailDialog?,
    moreEntries: List<TvDetailMoreEntry>,
    options: TvDetailPlaybackOptions?,
    strings: TvStrings,
    callbacks: TvDetailDialogCallbacks,
) {
    when (dialog) {
        TvDetailDialog.MORE ->
            TvDetailMoreDialog(
                entries = moreEntries,
                strings = strings,
                onEntry = callbacks.onMoreEntry,
                onDismiss = callbacks.onDismiss,
            )
        TvDetailDialog.TRACKS ->
            if (options != null) {
                TvDetailTrackPickerDialog(
                    options = options,
                    strings = strings,
                    onAudio = { id -> callbacks.onSelection { it.withAudio(id) } },
                    onSubtitle = { id -> callbacks.onSelection { it.withSubtitle(id) } },
                    onVersion = { id -> callbacks.onSelection { it.withVersion(id) } },
                    onDismiss = callbacks.onDismiss,
                )
            }
        null -> Unit
    }
}
