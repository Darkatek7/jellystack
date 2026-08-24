@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MatchingDeclarationName", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jellystack.core.jellyfin.LibraryBrowseDirection
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryBrowseSort
import dev.jellystack.core.jellyfin.LibraryMediaType
import dev.jellystack.core.jellyfin.LibraryPlayedFilter

@Immutable
internal data class TvLibraryQueryLabels(
    val sort: String,
    val title: String,
    val dateAdded: String,
    val releaseYear: String,
    val ascending: String,
    val descending: String,
    val played: String,
    val unplayed: String,
    val favoritesOnly: String,
    val genre: String,
    val year: String,
    val mediaType: String,
    val all: String,
    val clear: String,
    val apply: String,
    val cancel: String,
)

private data class TvLibraryQueryControl(
    val id: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun TvLibraryQueryControls(
    query: LibraryBrowseQuery,
    labels: TvLibraryQueryLabels,
    availableYears: List<Int>,
    availableMediaTypes: List<LibraryMediaType>,
    onQueryChanged: (LibraryBrowseQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    var genreDialogVisible by remember { mutableStateOf(false) }
    val controls =
        buildList {
            add(
                TvLibraryQueryControl(
                    id = "sort",
                    label = "${labels.sort}: ${query.sort.label(labels)}",
                    selected = query.sort != LibraryBrowseSort.TITLE,
                    onClick = { onQueryChanged(query.copy(sort = query.sort.next())) },
                ),
            )
            add(
                TvLibraryQueryControl(
                    id = "direction",
                    label = if (query.direction == LibraryBrowseDirection.ASCENDING) labels.ascending else labels.descending,
                    selected = query.direction == LibraryBrowseDirection.DESCENDING,
                    onClick = {
                        onQueryChanged(
                            query.copy(
                                direction =
                                    if (query.direction == LibraryBrowseDirection.ASCENDING) {
                                        LibraryBrowseDirection.DESCENDING
                                    } else {
                                        LibraryBrowseDirection.ASCENDING
                                    },
                            ),
                        )
                    },
                ),
            )
            add(
                TvLibraryQueryControl(
                    id = "played",
                    label = query.played.label(labels),
                    selected = query.played != LibraryPlayedFilter.ANY,
                    onClick = { onQueryChanged(query.copy(played = query.played.next())) },
                ),
            )
            add(
                TvLibraryQueryControl(
                    id = "favorites",
                    label = labels.favoritesOnly,
                    selected = query.favoritesOnly,
                    onClick = { onQueryChanged(query.copy(favoritesOnly = !query.favoritesOnly)) },
                ),
            )
            add(
                TvLibraryQueryControl(
                    id = "genre",
                    label = "${labels.genre}: ${query.genres.joinToString().ifBlank { labels.all }}",
                    selected = query.genres.isNotEmpty(),
                    onClick = { genreDialogVisible = true },
                ),
            )
            availableYears.takeIf(List<Int>::isNotEmpty)?.let { years ->
                add(
                    TvLibraryQueryControl(
                        id = "year",
                        label = "${labels.year}: ${query.years.firstOrNull() ?: labels.all}",
                        selected = query.years.isNotEmpty(),
                        onClick = { onQueryChanged(query.copy(years = years.nextSelection(query.years))) },
                    ),
                )
            }
            availableMediaTypes.takeIf { it.size > 1 }?.let { mediaTypes ->
                add(
                    TvLibraryQueryControl(
                        id = "type",
                        label = "${labels.mediaType}: ${query.mediaTypes.firstOrNull()?.name ?: labels.all}",
                        selected = query.mediaTypes.isNotEmpty(),
                        onClick = { onQueryChanged(query.copy(mediaTypes = mediaTypes.nextSelection(query.mediaTypes))) },
                    ),
                )
            }
            if (!query.isDefault) {
                add(
                    TvLibraryQueryControl(
                        id = "clear",
                        label = labels.clear,
                        selected = false,
                        onClick = { onQueryChanged(LibraryBrowseQuery.DEFAULT) },
                    ),
                )
            }
        }
    LazyRow(
        modifier = modifier.testTag("tv-library-query-controls"),
        contentPadding = PaddingValues(horizontal = TvLayoutTokens.FocusHaloPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(controls, key = TvLibraryQueryControl::id) { control ->
            TvActionButton(
                label = control.label,
                onClick = control.onClick,
                selected = control.selected,
                focusTargetId = "library:query:${control.id}",
                modifier = Modifier.testTag("tv-library-query-${control.id}"),
            )
        }
    }
    if (genreDialogVisible) {
        TvLibraryGenreDialog(
            initialGenres = query.genres,
            labels = labels,
            onDismiss = { genreDialogVisible = false },
            onApply = { genres ->
                genreDialogVisible = false
                onQueryChanged(query.copy(genres = genres))
            },
        )
    }
}

@Composable
private fun TvLibraryGenreDialog(
    initialGenres: Set<String>,
    labels: TvLibraryQueryLabels,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var value by remember(initialGenres) { mutableStateOf(initialGenres.joinToString(", ")) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(620.dp)
                    .background(TvSurfaceRaised, RoundedCornerShape(26.dp))
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.tv.material3.Text(labels.genre, color = TvText)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                colors = tvOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth().testTag("tv-library-genre-input"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    labels.apply,
                    {
                        onApply(
                            value
                                .split(',')
                                .map(String::trim)
                                .filter(String::isNotBlank)
                                .toSet(),
                        )
                    },
                    primary = true,
                    modifier = Modifier.width(180.dp),
                )
                TvActionButton(labels.cancel, onDismiss, modifier = Modifier.width(180.dp))
            }
        }
    }
}

private fun LibraryBrowseSort.next(): LibraryBrowseSort = LibraryBrowseSort.entries[(ordinal + 1) % LibraryBrowseSort.entries.size]

private fun LibraryPlayedFilter.next(): LibraryPlayedFilter = LibraryPlayedFilter.entries[(ordinal + 1) % LibraryPlayedFilter.entries.size]

private fun LibraryBrowseSort.label(labels: TvLibraryQueryLabels): String =
    when (this) {
        LibraryBrowseSort.TITLE -> labels.title
        LibraryBrowseSort.DATE_ADDED -> labels.dateAdded
        LibraryBrowseSort.RELEASE_YEAR -> labels.releaseYear
    }

private fun LibraryPlayedFilter.label(labels: TvLibraryQueryLabels): String =
    when (this) {
        LibraryPlayedFilter.ANY -> "${labels.played}: ${labels.all}"
        LibraryPlayedFilter.PLAYED -> labels.played
        LibraryPlayedFilter.UNPLAYED -> labels.unplayed
    }

private fun <T> List<T>.nextSelection(current: Set<T>): Set<T> {
    val currentValue = current.firstOrNull() ?: return firstOrNull()?.let(::setOf) ?: emptySet()
    val nextIndex = indexOf(currentValue) + 1
    return getOrNull(nextIndex)?.let(::setOf) ?: emptySet()
}
