@file:Suppress("FunctionNaming", "LongParameterList", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.JellyfinHomeState

/** What the library header shows: name, size, and the two buttons that open the sort and filter panel. */
internal data class TvLibraryHeaderState(
    val title: String,
    val itemCount: String?,
    val sortLabel: String,
    val filterLabel: String,
    val activeFilterCount: Int,
)

/**
 * One line above the grid: library name, item count, and the Sort and Filter buttons. The buttons share a pill
 * shape with the other TV buttons but are shorter, so the grid keeps its room.
 */
@Composable
internal fun TvLibraryHeader(
    state: TvLibraryHeaderState,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    sortFocus: FocusRequester,
    filterFocus: FocusRequester,
    modifier: Modifier = Modifier,
    entryFocus: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag("tv-library-query-controls"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.title,
            color = TvText,
            fontSize = TvTextSize.Headline,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }.testTag("tv-library-browse-title"),
        )
        state.itemCount?.let { count ->
            Text(
                text = "  ·  $count",
                color = TvTextMuted,
                fontSize = TvTextSize.BodyLarge,
                maxLines = 1,
                modifier = Modifier.testTag("tv-library-item-count"),
            )
        }
        Spacer(Modifier.weight(1f))
        TvLibraryHeaderButton(
            label = state.sortLabel,
            onClick = onSort,
            focusRequester = sortFocus,
            trailingDropDown = true,
            selected = false,
            focusTargetId = TV_LIBRARY_SORT_TARGET,
            modifier = Modifier.tvScreenEntryFocus(entryFocus, TV_LIBRARY_SORT_TARGET).testTag("tv-library-query-sort"),
        )
        Spacer(Modifier.width(12.dp))
        TvLibraryHeaderButton(
            label = state.filterLabel,
            onClick = onFilter,
            focusRequester = filterFocus,
            trailingDropDown = false,
            selected = state.activeFilterCount > 0,
            focusTargetId = TV_LIBRARY_FILTER_TARGET,
            modifier = Modifier.testTag("tv-library-query-filter"),
        )
    }
}

internal const val TV_LIBRARY_SORT_TARGET = "library:query:sort"
internal const val TV_LIBRARY_FILTER_TARGET = "library:query:filter"

/** "Filter" with the number of active filters, as in "Filter (2)". */
internal fun tvLibraryFilterLabel(
    filter: String,
    activeFilterCount: Int,
): String = if (activeFilterCount > 0) "$filter ($activeFilterCount)" else filter

@Composable
private fun TvLibraryHeaderButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    trailingDropDown: Boolean,
    selected: Boolean,
    focusTargetId: String,
    modifier: Modifier = Modifier,
) {
    val shape = TvShapes.Pill
    Row(
        modifier =
            modifier
                .height(TV_LIBRARY_HEADER_BUTTON_HEIGHT_DP.dp)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .tvFocusable(
                    onClick = onClick,
                    shape = shape,
                    providedFocusRequester = focusRequester,
                    focusTargetId = focusTargetId,
                ).background(if (selected) TvPurpleStrong else TvSurfaceRaised, shape)
                .padding(start = 20.dp, end = if (trailingDropDown) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = TvText, fontSize = TvTextSize.Body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (trailingDropDown) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TvText, modifier = Modifier.size(24.dp))
        }
    }
}

private const val TV_LIBRARY_HEADER_BUTTON_HEIGHT_DP = 44

/**
 * Whether the library's first page has been answered: items are there, the load ended or failed, or the library is
 * known to be empty. Before that, an empty item list only means the items have not arrived yet.
 */
internal fun tvLibraryContentDetermined(state: JellyfinHomeState): Boolean {
    val libraryCount = state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.itemCount
    val knownEmpty = (state.totalLibraryItemCount ?: libraryCount) == 0L
    return state.libraryItems.isNotEmpty() || state.endReached || state.libraryErrorMessage != null || knownEmpty
}

/**
 * Whether the sort and filter controls may take a library page's first focus: only when [route]'s own library has
 * answered with no items. While items exist or may still arrive, the first card takes it.
 */
internal fun tvLibraryQueryControlsMayLead(
    route: TvRoute,
    state: JellyfinHomeState,
): Boolean {
    // Any library route: a browse route whose library has too few titles for rows shows the same grid and header.
    if (route !is TvRoute.Library) return true
    return state.selectedLibraryId == route.libraryId &&
        state.libraryItems.isEmpty() &&
        tvLibraryContentDetermined(state)
}
