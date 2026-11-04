@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MatchingDeclarationName", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import dev.jellystack.core.coroutines.runSuspendCatching
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
    val mediaTypeNames: TvMediaTypeStrings,
    val all: String,
    val clear: String,
    val apply: String,
    val cancel: String,
)

/** The part of the filter panel that takes focus when it opens: the Sort button leads to the sort options. */
internal enum class TvLibraryPanelEntry { SORT, FILTER }

/**
 * The sort and filter options of a library, in a solid panel at the right edge. It sits on top of the grid without
 * dimming it, and keeps focus inside until the owner closes it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TvLibraryFilterPanel(
    query: LibraryBrowseQuery,
    labels: TvLibraryQueryLabels,
    panelStrings: TvLibraryStrings,
    availableYears: List<Int>,
    availableMediaTypes: List<LibraryMediaType>,
    entry: TvLibraryPanelEntry,
    onQueryChanged: (LibraryBrowseQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    var genreDialogVisible by remember { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }
    LaunchedEffect(entry) {
        withFrameNanos { }
        runSuspendCatching { entryFocus.requestFocus() }
    }
    val decades = remember(availableYears) { tvLibraryDecades(availableYears) }
    Column(
        modifier =
            modifier
                .width(TV_LIBRARY_PANEL_WIDTH_DP.dp)
                .fillMaxHeight()
                .background(TvSurfaceRaised)
                // Focus must not escape to the grid underneath; Back is what closes the panel.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .testTag("tv-library-filter-panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(panelStrings.filterPanelTitle, color = TvText, fontSize = TvTextSize.Title, fontWeight = FontWeight.Bold, maxLines = 1)
        PanelSection(panelStrings.sortBy) {
            LibraryBrowseSort.entries.forEach { sort ->
                PanelChip(
                    label = sort.label(labels),
                    selected = query.sort == sort,
                    onClick = { onQueryChanged(query.copy(sort = sort)) },
                    focusRequester = entryFocus.takeIf { entry == TvLibraryPanelEntry.SORT && query.sort == sort },
                    tag = "tv-library-panel-sort-${sort.name}",
                )
            }
        }
        PanelSection(panelStrings.direction) {
            LibraryBrowseDirection.entries.forEach { direction ->
                PanelChip(
                    label = if (direction == LibraryBrowseDirection.ASCENDING) labels.ascending else labels.descending,
                    selected = query.direction == direction,
                    onClick = { onQueryChanged(query.copy(direction = direction)) },
                    tag = "tv-library-panel-direction-${direction.name}",
                )
            }
        }
        PanelSection(panelStrings.watchedState) {
            LibraryPlayedFilter.entries.forEach { played ->
                PanelChip(
                    label = played.label(labels, panelStrings),
                    selected = query.played == played,
                    onClick = { onQueryChanged(query.copy(played = played)) },
                    focusRequester = entryFocus.takeIf { entry == TvLibraryPanelEntry.FILTER && played == LibraryPlayedFilter.ANY },
                    tag = "tv-library-panel-played-${played.name}",
                )
            }
            PanelChip(
                label = labels.favoritesOnly,
                selected = query.favoritesOnly,
                onClick = { onQueryChanged(query.copy(favoritesOnly = !query.favoritesOnly)) },
                tag = "tv-library-panel-favorites",
            )
        }
        PanelSection(labels.genre) {
            PanelChip(
                label = query.genres.joinToString().ifBlank { labels.all },
                selected = query.genres.isNotEmpty(),
                onClick = { genreDialogVisible = true },
                tag = "tv-library-panel-genre",
            )
        }
        if (decades.isNotEmpty()) {
            PanelSection(panelStrings.decade) {
                PanelChip(
                    label = panelStrings.any,
                    selected = query.years.isEmpty(),
                    onClick = { onQueryChanged(query.copy(years = emptySet())) },
                    tag = "tv-library-panel-decade-any",
                )
                decades.forEach { decade ->
                    PanelChip(
                        label = panelStrings.decadeLabel(decade),
                        selected = tvLibraryDecadeSelected(query.years, decade),
                        onClick = { onQueryChanged(query.copy(years = tvLibraryToggleDecade(query.years, decade))) },
                        tag = "tv-library-panel-decade-$decade",
                    )
                }
            }
        }
        if (availableMediaTypes.size > 1) {
            PanelSection(labels.mediaType) {
                PanelChip(
                    label = labels.all,
                    selected = query.mediaTypes.isEmpty(),
                    onClick = { onQueryChanged(query.copy(mediaTypes = emptySet())) },
                    tag = "tv-library-panel-type-any",
                )
                availableMediaTypes.forEach { type ->
                    PanelChip(
                        label = type.label(labels.mediaTypeNames),
                        selected = type in query.mediaTypes,
                        onClick = { onQueryChanged(query.copy(mediaTypes = query.mediaTypes.toggled(type))) },
                        tag = "tv-library-panel-type-${type.name}",
                    )
                }
            }
        }
        if (tvLibraryActiveFilterCount(query) > 0) {
            PanelChip(
                label = labels.clear,
                selected = false,
                onClick = { onQueryChanged(tvLibraryClearedFilters(query)) },
                tag = "tv-library-panel-clear",
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

internal const val TV_LIBRARY_PANEL_WIDTH_DP = 420

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TvTextMuted, fontSize = TvTextSize.BodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvLayoutTokens.FocusHaloPadding / 2),
        ) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    focusRequester: FocusRequester? = null,
) {
    val shape = TvShapes.Pill
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Row(
        modifier =
            Modifier
                .height(TV_LIBRARY_CHIP_HEIGHT_DP.dp)
                .bringIntoViewRequester(bringIntoView)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                }.tvFocusable(
                    onClick = onClick,
                    shape = shape,
                    scale = 1.04f,
                    providedFocusRequester = focusRequester,
                    onFocusChanged = { focused = it },
                ).background(if (selected) TvPurpleStrong else TvSurface, shape)
                .padding(horizontal = 18.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TvText,
            fontSize = TvTextSize.Body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val TV_LIBRARY_CHIP_HEIGHT_DP = 40

@Composable
private fun TvLibraryGenreDialog(
    initialGenres: Set<String>,
    labels: TvLibraryQueryLabels,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var value by remember(initialGenres) { mutableStateOf(initialGenres.joinToString(", ")) }
    val inputFocus = rememberTvDialogInitialFocus()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(620.dp)
                    .background(TvSurfaceRaised, RoundedCornerShape(26.dp))
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(labels.genre, color = TvText)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                colors = tvOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).testTag("tv-library-genre-input"),
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

internal fun LibraryBrowseSort.label(labels: TvLibraryQueryLabels): String =
    when (this) {
        LibraryBrowseSort.TITLE -> labels.title
        LibraryBrowseSort.DATE_ADDED -> labels.dateAdded
        LibraryBrowseSort.RELEASE_YEAR -> labels.releaseYear
    }

private fun LibraryMediaType.label(names: TvMediaTypeStrings): String =
    when (this) {
        LibraryMediaType.MOVIE -> names.movie
        LibraryMediaType.SERIES -> names.series
        LibraryMediaType.EPISODE -> names.episode
        LibraryMediaType.MUSIC_VIDEO -> names.musicVideo
        LibraryMediaType.AUDIO -> names.audio
    }

private fun LibraryPlayedFilter.label(
    labels: TvLibraryQueryLabels,
    panelStrings: TvLibraryStrings,
): String =
    when (this) {
        LibraryPlayedFilter.ANY -> panelStrings.any
        LibraryPlayedFilter.PLAYED -> labels.played
        LibraryPlayedFilter.UNPLAYED -> labels.unplayed
    }

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
