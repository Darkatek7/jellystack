@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryMediaType
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
enum class TvLibraryMode {
    /** Cinematic browse rows that end in an "All titles" card. */
    BROWSE,

    /** The title grid, opened from the browse rows' "All titles" card. */
    ALL_TITLES,

    /** The title grid as the library's only screen, for a library without browse rows. */
    TITLES_ONLY,
}

internal enum class TvLibraryCardShape { PORTRAIT, LANDSCAPE }

@Immutable
internal data class TvLibraryBrowseLabels(
    val continueWatching: String,
    val nextUp: String,
    val recentlyAdded: String,
    val myList: String,
    val minutesLeft: String,
    val untitled: String,
)

internal fun buildTvLibraryBrowseRows(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    myListItems: List<JellyfinItem>,
    labels: TvLibraryBrowseLabels,
    collectionType: String? = null,
): List<TvCinematicRow> {
    val selectedLibraryId = state.selectedLibraryId

    fun List<JellyfinItem>.forSelectedLibrary(): List<JellyfinItem> =
        filter { item -> selectedLibraryId == null || item.libraryId == selectedLibraryId }
            .distinctBy(JellyfinItem::id)

    fun row(
        id: String,
        title: String,
        items: List<JellyfinItem>,
        selectedIds: Set<String> = state.favorites,
    ): TvCinematicRow? =
        tvCinematicRowOrNull(
            id = id,
            title = title.ifBlank { labels.untitled },
            cards = items.forSelectedLibrary().mapNotNull { it.toCinematicCard(state, selectedIds, labels) },
        )

    val supportsNextUp = collectionType?.lowercase() in setOf("tvshows", "series", "mixed", null)
    val recentlyAdded =
        when (collectionType?.lowercase()) {
            "tvshows", "series" -> state.recentShows
            "movies" -> state.recentMovies
            else -> (state.recentMovies + state.recentShows).distinctBy(JellyfinItem::id)
        }
    val serverRows =
        (homeSections as? HomeSectionsState.Ready)
            ?.sections
            .orEmpty()
            .mapNotNull { section ->
                val items =
                    section.items
                        .filter { it.action == HomeSectionAction.JELLYFIN }
                        .mapNotNull { it.jellyfinItem }
                row("server:${section.id}", section.title, items)
            }

    return buildList {
        row("continue", labels.continueWatching, state.continueWatching)?.let(::add)
        if (supportsNextUp) row("next-up", labels.nextUp, state.nextUp)?.let(::add)
        row("recent", labels.recentlyAdded, recentlyAdded)?.let(::add)
        row("my-list", labels.myList, myListItems, myListItems.map(JellyfinItem::id).toSet())?.let(::add)
        addAll(serverRows)
        // The rows are keyed by ID; the plugin may repeat a section.
    }.distinctBy(TvCinematicRow::id)
}

/**
 * How a library opens: on its browse rows, or straight on its title grid when none of those rows would show anything
 * (books, music, playlists), instead of a stage with a lone "All titles" card. While the home feeds or the server
 * sections still load, rows may yet appear, so the library opens on its rows.
 */
internal fun tvLibraryEntryMode(
    libraryId: String,
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    myListItems: List<JellyfinItem>,
    labels: TvLibraryBrowseLabels,
): TvLibraryMode {
    val stillLoading = state.isInitialLoading || state.isHomeLoading || homeSections == HomeSectionsState.Loading
    if (stillLoading) return TvLibraryMode.BROWSE
    val rows =
        buildTvLibraryBrowseRows(
            state = state.copy(selectedLibraryId = libraryId),
            homeSections = homeSections,
            myListItems = myListItems,
            labels = labels,
            collectionType = state.libraries.firstOrNull { it.id == libraryId }?.collectionType,
        )
    return if (rows.isEmpty()) TvLibraryMode.TITLES_ONLY else TvLibraryMode.BROWSE
}

/** Null for an item without an ID: such a card could be neither keyed nor focused. */
private fun JellyfinItem.toCinematicCard(
    state: JellyfinHomeState,
    selectedIds: Set<String>,
    labels: TvLibraryBrowseLabels,
): TvCinematicCard? {
    if (id.isBlank()) return null
    val text = tvCardText(labels.minutesLeft, labels.untitled)
    return TvCinematicCard(
        id = id,
        title = text.title,
        subtitle = text.subtitle,
        overview = overview,
        artworkUrl =
            jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, resolveTvJellyfinArtwork(this, landscape = true)),
        backdropUrl = tvJellyfinBackdropUrl(state.imageBaseUrl, state.imageAccessToken, this),
        selected = id in selectedIds,
        played = isPlayed || (playedPercentage ?: 0.0) >= 90.0,
        resumeFraction = text.progress?.takeIf { it in 0.01f..0.99f },
        logoUrl = tvJellyfinLogoUrl(state.imageBaseUrl, state.imageAccessToken, this),
        metadata = tvStageMetadata(),
        remainingMinutes = tvRemainingMinutes(runTimeTicks, positionTicks).takeUnless { isBrowseContainer() },
    )
}

/**
 * The last segment of a library's browse rows: one card that opens every title of the library with sorting and
 * filters. It shows the library's own artwork, as the library cards on the home screen do.
 */
internal fun tvLibraryAllTitlesRow(
    state: JellyfinHomeState,
    library: JellyfinLibrary?,
    rowTitle: String,
    cardTitle: String,
    itemCount: String?,
): TvCinematicRow =
    TvCinematicRow(
        id = TV_LIBRARY_ALL_TITLES_ROW,
        title = rowTitle,
        cards =
            listOf(
                TvCinematicCard(
                    id = TV_LIBRARY_ALL_TITLES_CARD,
                    title = cardTitle,
                    subtitle = itemCount,
                    artworkUrl =
                        library?.let {
                            jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, it.id, it.primaryImageTag)
                        },
                    // The library image usually shows the library name; upscaled to the stage it reads as noise.
                    keepsBackdrop = true,
                ),
            ),
    )

internal const val TV_LIBRARY_ALL_TITLES_ROW = "all-titles"
internal const val TV_LIBRARY_ALL_TITLES_CARD = "all-titles"

internal fun tvLibraryCardShape(
    collectionType: String?,
    reliablePosters: Boolean,
): TvLibraryCardShape =
    if (reliablePosters && collectionType?.lowercase() in setOf("movies", "tvshows", "series", "mixed", null)) {
        TvLibraryCardShape.PORTRAIT
    } else {
        TvLibraryCardShape.LANDSCAPE
    }

@Composable
internal fun TvSelectedLibraryScreen(
    route: TvRoute.Library,
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    myListItems: List<JellyfinItem>,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    collectionType: String?,
    rememberedQuery: LibraryBrowseQuery,
    onModeChanged: (TvLibraryMode) -> Unit,
    onQueryChanged: (LibraryBrowseQuery) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
    onPlayItem: (JellyfinItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onJumpToLetter: (String) -> Unit = {},
    onLetterJumpHandled: (Long) -> Unit = {},
    trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    trailerPreviewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
    onPreviewFocus: (JellyfinItem, String) -> Unit = { _, _ -> },
    onPreviewClear: () -> Unit = {},
) {
    val routeKey = route.focusRouteKey(state.browsePath.map { it.id })
    val allKnownItems = remember(state, homeSections, myListItems) { tvLibraryKnownItems(state, homeSections, myListItems) }
    if (route.mode == TvLibraryMode.BROWSE) {
        val library = state.libraries.firstOrNull { it.id == route.libraryId }
        val libraryName = route.title ?: library?.name ?: strings.library
        val rows =
            remember(state, homeSections, myListItems, collectionType, strings, library) {
                buildTvLibraryBrowseRows(
                    state = state,
                    homeSections = homeSections,
                    myListItems = myListItems,
                    labels = strings.libraryBrowseLabels(),
                    collectionType = collectionType,
                ) +
                    tvLibraryAllTitlesRow(
                        state = state,
                        library = library,
                        rowTitle = strings.library,
                        cardTitle = strings.allTitles,
                        itemCount = library?.itemCount?.let(strings::itemCount),
                    )
            }
        var focusedAnchor by remember(routeKey) { mutableStateOf(focusMemory.restore(routeKey)?.anchor) }
        val focusedItem = focusedAnchor?.itemId?.let(allKnownItems::get)
        val focusedPresentationId =
            focusedAnchor?.let { anchor ->
                tvCinematicFocusTargetId(requireNotNull(anchor.sectionId), requireNotNull(anchor.itemId))
            }
        val trailerPlaying =
            focusedItem?.let { item -> trailerPreviewState.showsTvStagePreview(item.id, focusedPresentationId) } == true
        val currentOnPlayItem by rememberUpdatedState(onPlayItem)
        val playback =
            remember(allKnownItems, strings) {
                TvCinematicCardPlayback(
                    labels =
                        TvCinematicHintLabels(
                            play = strings.play,
                            resume = strings.continueLabel,
                            details = strings.details,
                            endsAt = strings.player.endsAt,
                        ),
                    canPlay = { card -> allKnownItems[card.id]?.isBrowseContainer() == false },
                    onPlay = { card -> allKnownItems[card.id]?.let(currentOnPlayItem) },
                )
            }
        TvCinematicBrowse(
            state =
                TvCinematicBrowseState(
                    hero = TvCinematicHero(title = libraryName, eyebrow = libraryName),
                    rows = rows,
                    focusedAnchor = focusedAnchor,
                ),
            onCardFocused = { anchor, _ ->
                focusedAnchor = anchor
                focusMemory.remember(routeKey, anchor, horizontalCenter = 0f)
                val item = allKnownItems[anchor.itemId]
                if (item != null) {
                    onPreviewFocus(
                        item,
                        tvCinematicFocusTargetId(requireNotNull(anchor.sectionId), requireNotNull(anchor.itemId)),
                    )
                } else {
                    // The "All titles" card has no trailer, so the previous card's trailer stops with its picture.
                    onPreviewClear()
                }
            },
            onCardClick = { card ->
                if (card.id == TV_LIBRARY_ALL_TITLES_CARD) {
                    onModeChanged(TvLibraryMode.ALL_TITLES)
                } else {
                    allKnownItems[card.id]?.open(onOpenItem, onOpenContainer)
                }
            },
            playback = playback,
            trailer =
                trailerPreviewEngine?.takeIf { trailerPlaying }?.let { engine ->
                    TvCinematicTrailer(engine, strings.trailer, previewSoundEnabled, previewProgress ?: TvNoTrailerProgress)
                },
            modifier = modifier,
        )
    } else {
        LaunchedEffect(route.libraryId, rememberedQuery) {
            if (state.libraryBrowseQuery != rememberedQuery) onQueryChanged(rememberedQuery)
        }
        // Each folder gets its own grid. A shared grid keeps its filter chips through a folder change; the chip the
        // system then focuses is remembered for the folder just left and replaces the card to return to.
        key(state.browsePath) {
            TvLibraryAllTitles(
                route = route,
                state = state,
                strings = strings,
                focusMemory = focusMemory,
                collectionType = collectionType,
                onQueryChanged = onQueryChanged,
                onJumpToLetter = onJumpToLetter,
                onLetterJumpHandled = onLetterJumpHandled,
                onOpenItem = onOpenItem,
                onOpenContainer = onOpenContainer,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun TvLibraryAllTitles(
    route: TvRoute.Library,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    collectionType: String?,
    onQueryChanged: (LibraryBrowseQuery) -> Unit,
    onJumpToLetter: (String) -> Unit,
    onLetterJumpHandled: (Long) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
    initialPanel: TvLibraryPanelEntry? = null,
) {
    val routeKey = route.focusRouteKey(state.browsePath.map { it.id })
    val gridState = rememberLazyGridState()
    val itemTargetIds = state.libraryItems.map { tvLibraryTargetId(it.id) }
    val terminalTarget =
        tvLibraryTerminalFocusTarget(
            libraryId = route.libraryId,
            itemCount = itemTargetIds.size,
            isLibraryLoading = state.isLibraryLoading,
            isPageLoading = state.isPageLoading,
            hasError = state.libraryErrorMessage != null,
        )
    // The header sits outside the grid, so an item's grid index is its list index.
    val focusLocations = remember(itemTargetIds, terminalTarget) { tvLibraryTitleGridFocusLocations(itemTargetIds, terminalTarget) }
    TvRouteFocusMaterializer(
        ownerId = "library-all-titles:$routeKey",
        targetIds = focusLocations.keys,
        fallbackTargetIds = setOfNotNull(itemTargetIds.firstOrNull(), terminalTarget),
    ) { targetId ->
        val index = focusLocations[targetId] ?: return@TvRouteFocusMaterializer false
        gridState.scrollToItem(index)
        withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
            androidx.compose.runtime
                .snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                .first { it }
        } ?: false
    }
    val library = route.libraryId?.let { id -> state.libraries.firstOrNull { it.id == id } }
    val sortFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    var panelVisible by remember { mutableStateOf(initialPanel != null) }
    var panelEntry by remember { mutableStateOf(initialPanel ?: TvLibraryPanelEntry.FILTER) }
    val closePanel: () -> Unit = {
        panelVisible = false
        val opener = if (panelEntry == TvLibraryPanelEntry.SORT) sortFocus else filterFocus
        runCatchingFocus { opener.requestFocus() }
    }
    // Previews and screenshot tests have no back dispatcher.
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled = panelVisible, onBack = closePanel)
    val query = state.libraryBrowseQuery
    val showsRail =
        tvLibraryShowsLetterRail(query, state.totalLibraryItemCount?.toInt() ?: state.libraryItems.size) &&
            state.browsePath.isEmpty() &&
            state.libraryItems.isNotEmpty()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val cardShape = tvLibraryCardShape(collectionType, reliablePosters = tvLibraryHasReliablePosters(state.libraryItems))
        val railReserve = if (showsRail) TV_LIBRARY_RAIL_WIDTH_DP + TV_LIBRARY_RAIL_GAP_DP else 0f
        val columns = tvLibraryGridColumns(cardShape, maxWidth.value, fontScale, railReserve)
        LaunchedEffect(state.libraryItems.size, state.endReached, state.isPageLoading, state.libraryErrorMessage, columns) {
            androidx.compose.runtime
                .snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: -1
                }.collect { lastVisibleIndex ->
                    if (
                        shouldLoadNextLibraryPage(
                            lastVisibleIndex = lastVisibleIndex,
                            totalItemCount = state.libraryItems.size,
                            isLibraryLoading = state.isLibraryLoading,
                            isPageLoading = state.isPageLoading,
                            endReached = state.endReached,
                            hasError = state.libraryErrorMessage != null,
                            columnCount = columns,
                        )
                    ) {
                        onLoadMore()
                    }
                }
        }
        val jumpFocus = remember { FocusRequester() }
        var jumpIndex by remember { mutableIntStateOf(-1) }
        val letterJump = state.libraryLetterJump
        LaunchedEffect(letterJump?.requestId) {
            val jump = letterJump ?: return@LaunchedEffect
            jumpIndex = jump.index
            gridState.scrollToItem(jump.index)
            androidx.compose.runtime
                .snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == jump.index } }
                .first { it }
            withFrameNanos { }
            runCatchingFocus { jumpFocus.requestFocus() }
            onLetterJumpHandled(jump.requestId)
        }
        val labels = strings.tvLibraryQueryLabels()
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = TvLayoutTokens.ContentStart,
                    end = TvLayoutTokens.SafeInsets.horizontal,
                    top = TvLayoutTokens.SafeInsets.vertical,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val activeFilters = tvLibraryActiveFilterCount(query)
            TvLibraryHeader(
                state =
                    TvLibraryHeaderState(
                        title = route.title ?: strings.library,
                        itemCount = (state.totalLibraryItemCount ?: library?.itemCount)?.let(strings::itemCount),
                        sortLabel = "${labels.sort}: ${query.sort.label(labels)}",
                        filterLabel = tvLibraryFilterLabel(strings.libraryScreen.filter, activeFilters),
                        activeFilterCount = activeFilters,
                    ),
                onSort = {
                    panelEntry = TvLibraryPanelEntry.SORT
                    panelVisible = true
                },
                onFilter = {
                    panelEntry = TvLibraryPanelEntry.FILTER
                    panelVisible = true
                },
                sortFocus = sortFocus,
                filterFocus = filterFocus,
                // While the first page loads there is no card yet, so focus starts on the first control.
                entryFocus = state.libraryItems.isEmpty(),
                modifier = Modifier.padding(horizontal = TvLayoutTokens.FocusHaloPadding / 2),
            )
            Row(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 54.dp, start = 6.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(TV_LIBRARY_GRID_ROW_SPACING_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
                ) {
                    itemsIndexed(state.libraryItems, key = {
                            _,
                            item,
                        ->
                        item.id
                    }, contentType = { _, _ -> "library-title" }) { index, item ->
                        TvLibraryGridCard(
                            item = item,
                            index = index,
                            columns = columns,
                            cardShape = cardShape,
                            images = TvLibraryGridImages(state.imageBaseUrl, state.imageAccessToken),
                            favorites = state.favorites,
                            strings = strings,
                            focusMemory = focusMemory,
                            routeKey = routeKey,
                            entryFocus = index == 0,
                            onClick = { item.open(onOpenItem, onOpenContainer) },
                            focusRequester = jumpFocus.takeIf { index == jumpIndex },
                        )
                    }
                    libraryGridStatusItems(state, strings, cardShape, columns, onRetry)
                }
                if (showsRail) {
                    Spacer(Modifier.width(TV_LIBRARY_RAIL_GAP_DP.dp))
                    TvLibraryAzRail(
                        strings = strings.libraryScreen,
                        onJump = onJumpToLetter,
                        modifier = Modifier.padding(top = 10.dp, bottom = 54.dp),
                    )
                }
            }
        }
        val reducedMotion = LocalTvFocusAppearance.current.reducedMotion
        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = if (reducedMotion) EnterTransition.None else slideInHorizontally(tween(TV_LIBRARY_PANEL_MILLIS)) { it },
            exit = if (reducedMotion) ExitTransition.None else slideOutHorizontally(tween(TV_LIBRARY_PANEL_MILLIS)) { it },
        ) {
            TvLibraryFilterPanel(
                query = query,
                labels = labels,
                panelStrings = strings.libraryScreen,
                availableYears =
                    state.libraryItems
                        .mapNotNull(JellyfinItem::productionYear)
                        .distinct()
                        .sortedDescending(),
                availableMediaTypes = tvLibraryMediaTypes(collectionType),
                entry = panelEntry,
                onQueryChanged = onQueryChanged,
            )
        }
    }
}

private fun LazyGridScope.libraryGridStatusItems(
    state: JellyfinHomeState,
    strings: TvStrings,
    cardShape: TvLibraryCardShape,
    columns: Int,
    onRetry: () -> Unit,
) {
    if ((state.isLibraryLoading || state.isPageLoading) && state.libraryItems.isEmpty()) {
        tvSkeletonGridCells(
            label = strings.loading,
            count = columns * 2,
            format = if (cardShape == TvLibraryCardShape.PORTRAIT) TvMediaCardFormat.POSTER else TvMediaCardFormat.LANDSCAPE,
            keyPrefix = "all-titles-skeleton",
        )
    } else if (state.isLibraryLoading || state.isPageLoading) {
        item(key = "all-titles-loading", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TvPurple)
            }
        }
    } else if (state.libraryErrorMessage != null) {
        item(key = "all-titles-error", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.libraryLoadFailed, color = TvError)
                TvActionButton(strings.retry, onRetry, modifier = Modifier.width(220.dp), focusTargetId = TV_LIBRARY_RETRY_TARGET)
            }
        }
    } else if (state.libraryItems.isEmpty()) {
        item(key = "all-titles-empty", span = { GridItemSpan(maxLineSpan) }) {
            TvEmptyState(
                icon = Icons.Default.VideoLibrary,
                title = strings.noResults,
                hint = if (state.libraryBrowseQuery.isDefault) strings.emptyStates.libraryHint else strings.emptyStates.filterHint,
            )
        }
    }
}

/** Where the artwork of a title grid comes from. */
internal data class TvLibraryGridImages(
    val baseUrl: String?,
    val accessToken: String?,
)

/** Items sit at their list index, since the header is not part of the grid; the status cell follows the last item. */
internal fun tvLibraryTitleGridFocusLocations(
    itemTargetIds: List<String>,
    terminalTarget: String?,
): Map<String, Int> =
    buildMap {
        itemTargetIds.forEachIndexed { index, targetId -> put(targetId, index) }
        terminalTarget?.let { put(it, itemTargetIds.size) }
    }

internal fun tvLibraryGridColumns(
    cardShape: TvLibraryCardShape,
    widthDp: Float,
    fontScale: Float,
    reservedEndDp: Float = 0f,
): Int =
    if (cardShape == TvLibraryCardShape.PORTRAIT) {
        tvLibraryPosterColumns(widthDp, fontScale, reservedEndDp)
    } else {
        ((widthDp - (TvLayoutTokens.SafeInsets.horizontal.value * 2f) - reservedEndDp) / 248f).toInt().coerceAtLeast(1)
    }

private const val TV_LIBRARY_RAIL_GAP_DP = 8f
private const val TV_LIBRARY_GRID_ROW_SPACING_DP = 10
private const val TV_LIBRARY_PANEL_MILLIS = 200

private inline fun runCatchingFocus(block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalStateException) {
        // The target is not attached while its screen is leaving; focus then stays where it is.
    }
}

/** One title of a library grid: the poster, and below it the title on one line and the year. */
@Composable
internal fun TvLibraryGridCard(
    item: JellyfinItem,
    index: Int,
    columns: Int,
    cardShape: TvLibraryCardShape,
    images: TvLibraryGridImages,
    favorites: Set<String>,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    routeKey: String,
    entryFocus: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val artwork = resolveTvJellyfinArtwork(item, landscape = cardShape == TvLibraryCardShape.LANDSCAPE)
    val targetId = tvLibraryTargetId(item.id)
    val title = item.name.ifBlank { strings.messages.untitled }
    val year = item.subtitleYear()
    val imageUrl =
        jellyfinImageUrl(
            images.baseUrl,
            images.accessToken,
            artwork,
            if (cardShape == TvLibraryCardShape.PORTRAIT) TvArtworkSize.PORTRAIT_CARD else TvArtworkSize.LANDSCAPE_CARD,
        )
    val progress = item.tvCardText(strings).progress
    TvMediaCard(
        title = title,
        subtitle = year,
        imageUrl = imageUrl,
        selected = item.id in favorites,
        badge = tvJellyfinCardBadge(item, strings),
        watched = item.playedPercentage?.let { it >= WATCHED_PERCENTAGE } == true,
        progress = progress?.takeIf { it in 0.01f..0.99f },
        format = if (cardShape == TvLibraryCardShape.PORTRAIT) TvMediaCardFormat.POSTER else TvMediaCardFormat.LANDSCAPE,
        artworkFit =
            if (artwork?.imageType == "Primary") {
                TvMediaCardArtworkFit.CONTAIN_PORTRAIT
            } else {
                TvMediaCardArtworkFit.CROP
            },
        fillWidth = true,
        onClick = onClick,
        onFocused = { focusMemory.remember(routeKey, "items", item.id, index + 1, index) },
        focusTargetId = targetId,
        providedFocusRequester = focusRequester,
        focusToNavigationRailOnLeft = isTvGridLeftEdge(index, columns),
        modifier = Modifier.tvScreenEntryFocus(entryFocus, targetId).testTag("tv-library-all-title-${item.id}"),
    )
}

private const val WATCHED_PERCENTAGE = 90.0

/** The year of a title; an episode shows its season and number instead. */
private fun JellyfinItem.subtitleYear(): String? =
    if (type.equals("Episode", ignoreCase = true)) {
        tvEpisodeCode(parentIndexNumber, indexNumber)
    } else {
        productionYear?.toString()
    }

private fun tvLibraryKnownItems(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    myListItems: List<JellyfinItem>,
): Map<String, JellyfinItem> =
    (
        state.continueWatching +
            state.nextUp +
            state.recentMovies +
            state.recentShows +
            myListItems +
            (homeSections as? HomeSectionsState.Ready)
                ?.sections
                .orEmpty()
                .flatMap { section -> section.items.mapNotNull { it.jellyfinItem } }
    ).distinctBy(JellyfinItem::id).associateBy(JellyfinItem::id)

private fun JellyfinItem.open(
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
) = if (isBrowseContainer()) onOpenContainer(this) else onOpenItem(this)

internal fun TvStrings.libraryBrowseLabels() =
    TvLibraryBrowseLabels(
        continueWatching = continueWatching,
        nextUp = nextUp,
        recentlyAdded = recentlyAdded,
        myList = myList,
        minutesLeft = metadata.minutesLeft,
        untitled = messages.untitled,
    )

private fun TvStrings.tvLibraryQueryLabels() =
    TvLibraryQueryLabels(
        sort = sort,
        title = titleSort,
        dateAdded = dateAddedSort,
        releaseYear = releaseYearSort,
        ascending = ascending,
        descending = descending,
        played = playedFilter,
        unplayed = unplayed,
        favoritesOnly = favoritesOnly,
        genre = genre,
        year = releaseYearSort,
        mediaType = mediaType,
        mediaTypeNames = mediaTypeLabels,
        all = all,
        clear = clearFilters,
        apply = applyLabel,
        cancel = cancel,
    )

private fun tvLibraryMediaTypes(collectionType: String?): List<LibraryMediaType> =
    when (collectionType?.lowercase()) {
        "movies" -> listOf(LibraryMediaType.MOVIE)
        "tvshows", "series" -> listOf(LibraryMediaType.SERIES, LibraryMediaType.EPISODE)
        "musicvideos" -> listOf(LibraryMediaType.MUSIC_VIDEO)
        "music" -> listOf(LibraryMediaType.AUDIO)
        else -> LibraryMediaType.entries
    }

internal fun tvLibraryHasReliablePosters(items: List<JellyfinItem>): Boolean {
    if (items.isEmpty()) return true
    val withPoster = items.count { it.primaryImageTag != null || it.seriesPrimaryImageTag != null }
    return withPoster.toFloat() / items.size >= 0.6f
}

/** Progress of a trailer whose player reports none. */
private object TvNoTrailerProgress : State<Float> {
    override val value: Float = 0f
}
