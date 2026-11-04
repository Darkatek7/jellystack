@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One "Recently added in <library>" row of the landing page. */
internal data class TvLibraryLandingRow(
    val library: JellyfinLibrary,
    val items: List<JellyfinItem>,
)

/**
 * A row per library that has recently added items, in the order of the libraries. The home feeds hold the newest movies
 * and shows of the server, so a library whose items are not among them has no row.
 */
internal fun tvLibraryLandingRows(
    libraries: List<JellyfinLibrary>,
    recentItems: List<JellyfinItem>,
): List<TvLibraryLandingRow> {
    val byLibrary = recentItems.distinctBy(JellyfinItem::id).groupBy(JellyfinItem::libraryId)
    return libraries.mapNotNull { library ->
        byLibrary[library.id]?.takeIf(List<JellyfinItem>::isNotEmpty)?.let { TvLibraryLandingRow(library, it) }
    }
}

private const val LANDING_TILES_KEY = "tiles"

private fun tvLibraryLandingRecentSection(libraryId: String) = "recent-$libraryId"

private data class LandingLocation(
    val columnIndex: Int,
    val rowKey: String,
    val rowIndex: Int,
)

/** The landing page of the Library destination: every library as a tile, then what was added to each of them. */
@Composable
internal fun TvLibraryLandingScreen(
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    routeKey: String,
    onSelectLibrary: (String) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraries = state.libraries
    val rows =
        remember(libraries, state.recentMovies, state.recentShows) {
            tvLibraryLandingRows(libraries, state.recentMovies + state.recentShows)
        }
    val columnState = rememberLazyListState()
    val rowStates = remember(routeKey) { mutableMapOf<String, LazyListState>() }

    fun rowState(key: String) = rowStates.getOrPut(key) { LazyListState() }

    val tileTargetIds = libraries.map { tvLibraryTargetId(it.id, "libraries") }
    val terminalTarget = if (libraries.isEmpty() && !state.isInitialLoading) TV_LIBRARY_EMPTY_ACTION_TARGET else null
    val locations =
        remember(libraries, rows) {
            buildMap {
                libraries.forEachIndexed { index, library ->
                    val tile = LandingLocation(TILES_COLUMN_INDEX, LANDING_TILES_KEY, index)
                    put(tvLibraryTargetId(library.id, "libraries"), tile)
                }
                rows.forEachIndexed { rowIndex, row ->
                    val section = tvLibraryLandingRecentSection(row.library.id)
                    row.items.forEachIndexed { index, item ->
                        val card = LandingLocation(TILES_COLUMN_INDEX + 1 + rowIndex, section, index)
                        put(tvLibraryTargetId(item.id, section), card)
                    }
                }
            }
        }
    TvRouteFocusMaterializer(
        ownerId = "library-landing:$routeKey",
        targetIds = locations.keys + setOfNotNull(terminalTarget),
        fallbackTargetIds = setOfNotNull(tileTargetIds.firstOrNull(), terminalTarget),
    ) { targetId ->
        val location = locations[targetId] ?: return@TvRouteFocusMaterializer targetId == terminalTarget
        columnState.scrollToItem(location.columnIndex)
        rowState(location.rowKey).scrollToItem(location.rowIndex)
        withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
            snapshotFlow { columnState.layoutInfo.visibleItemsInfo.any { it.index == location.columnIndex } }
                .first { it }
        } ?: false
    }
    LazyColumn(
        state = columnState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Text(
                strings.library,
                modifier = Modifier.padding(horizontal = 6.dp).tvHeading(),
                color = TvText,
                fontSize = TvTextSize.Headline,
                fontWeight = FontWeight.Bold,
            )
        }
        if (libraries.isEmpty()) {
            item(key = "empty") {
                if (state.isInitialLoading) {
                    TvSkeletonRows(strings.loading, rowCount = 1)
                } else {
                    TvEmptyState(
                        icon = Icons.Default.VideoLibrary,
                        title = strings.emptyStates.noLibraries,
                        hint = strings.emptyStates.librariesHint,
                        action = TvEmptyStateAction(strings.retry, TV_LIBRARY_EMPTY_ACTION_TARGET, onRetry),
                    )
                }
            }
            return@LazyColumn
        }
        item(key = LANDING_TILES_KEY, contentType = "library-tiles") {
            LazyRow(
                state = rowState(LANDING_TILES_KEY),
                modifier = Modifier.tvRowBleed(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = tvBleedRowPadding(6.dp),
            ) {
                itemsIndexed(
                    libraries,
                    key = { _, library -> library.id },
                    contentType = { _, _ -> "library-tile" },
                ) { index, library ->
                    val targetId = tvLibraryTargetId(library.id, "libraries")
                    TvLibraryTile(
                        name = library.name,
                        collectionType = library.collectionType,
                        imageUrl = tvLibraryTileImageUrl(state.imageBaseUrl, state.imageAccessToken, library),
                        subtitle = library.itemCount?.let(strings::itemCount),
                        onClick = { onSelectLibrary(library.id) },
                        onFocused = { focusMemory.remember(routeKey, "libraries", library.id, 1, index) },
                        tileModifier = Modifier.tvScreenEntryFocus(index == 0, targetId),
                        focusTargetId = targetId,
                        focusToNavigationRailOnLeft = index == 0,
                    )
                }
            }
        }
        itemsIndexed(
            rows,
            key = { _, row -> "recent-${row.library.id}" },
            contentType = { _, _ -> "recent-row" },
        ) { rowIndex, row ->
            val section = tvLibraryLandingRecentSection(row.library.id)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSectionTitle(strings.libraryScreen.recentlyAddedIn(row.library.name))
                LazyRow(
                    state = rowState(section),
                    modifier = Modifier.tvRowBleed(),
                    horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
                    contentPadding = tvBleedRowPadding(6.dp),
                ) {
                    itemsIndexed(
                        row.items,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "media-card" },
                    ) { index, item ->
                        val targetId = tvLibraryTargetId(item.id, section)
                        val text = item.tvCardText(strings)
                        val artwork = resolveTvJellyfinArtwork(item, landscape = true)
                        TvMediaCard(
                            title = text.title,
                            subtitle = text.subtitle,
                            progress = text.progress,
                            imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, artwork),
                            watched = item.isPlayed,
                            onClick = { onOpenItem(item) },
                            onFocused = { focusMemory.remember(routeKey, section, item.id, 2 + rowIndex, index) },
                            focusTargetId = targetId,
                            focusToNavigationRailOnLeft = index == 0,
                        )
                    }
                }
            }
        }
    }
}

/** The title is item 0 of the column, the tiles item 1, and the recent rows follow. */
private const val TILES_COLUMN_INDEX = 1
