@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.floor

@Serializable
enum class TvLibraryMode { BROWSE, ALL_TITLES }

internal enum class TvLibraryCardShape { PORTRAIT, LANDSCAPE }

@Immutable
internal data class TvLibraryBrowseLabels(
    val continueWatching: String,
    val nextUp: String,
    val recentlyAdded: String,
    val myList: String,
    val minutesLeft: String,
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
        items.forSelectedLibrary().takeIf(List<JellyfinItem>::isNotEmpty)?.let { values ->
            TvCinematicRow(
                id = id,
                title = title,
                cards = values.map { it.toCinematicCard(state, selectedIds, labels.minutesLeft) },
            )
        }

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
    }
}

private fun JellyfinItem.toCinematicCard(
    state: JellyfinHomeState,
    selectedIds: Set<String>,
    minutesLeft: String,
): TvCinematicCard {
    val text = tvCardText(minutesLeft)
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
                    backdropUrl =
                        library?.let {
                            jellyfinImageUrl(
                                baseUrl = state.imageBaseUrl,
                                token = state.imageAccessToken,
                                itemId = it.id,
                                tag = it.primaryImageTag,
                                maxWidth = TvArtworkSize.HERO.maxWidth,
                            )
                        },
                ),
            ),
    )

internal const val TV_LIBRARY_ALL_TITLES_ROW = "all-titles"
internal const val TV_LIBRARY_ALL_TITLES_CARD = "all-titles"

internal fun tvLibraryAllTitlesColumnCount(
    widthDp: Float,
    fontScale: Float,
): Int {
    val usableWidth = widthDp - (TvLayoutTokens.SafeInsets.horizontal.value * 2f)
    val scaledMinimumCardWidth = 136f + ((fontScale.coerceAtLeast(1f) - 1f) * 80f)
    return floor(
        (usableWidth + TvLayoutTokens.CardSpacing.value) /
            (scaledMinimumCardWidth + TvLayoutTokens.CardSpacing.value),
    ).toInt().coerceIn(1, 7)
}

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
                    labels =
                        TvLibraryBrowseLabels(
                            continueWatching = strings.continueWatching,
                            nextUp = strings.nextUp,
                            recentlyAdded = strings.recentlyAdded,
                            myList = strings.myList,
                            minutesLeft = strings.metadata.minutesLeft,
                        ),
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
            playback =
                TvCinematicCardPlayback(
                    labels = TvCinematicHintLabels(play = strings.play, resume = strings.continueLabel, details = strings.details),
                    canPlay = { card -> allKnownItems[card.id]?.isBrowseContainer() == false },
                    onPlay = { card -> allKnownItems[card.id]?.let(onPlayItem) },
                ),
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
        TvLibraryAllTitles(
            route = route,
            state = state,
            strings = strings,
            focusMemory = focusMemory,
            collectionType = collectionType,
            onQueryChanged = onQueryChanged,
            onOpenItem = onOpenItem,
            onOpenContainer = onOpenContainer,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun TvLibraryAllTitles(
    route: TvRoute.Library,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    collectionType: String?,
    onQueryChanged: (LibraryBrowseQuery) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
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
    val focusLocations = tvLibraryGridFocusLocations(itemTargetIds, terminalTarget)
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val cardShape = tvLibraryCardShape(collectionType, reliablePosters = tvLibraryHasReliablePosters(state.libraryItems))
        val columns =
            if (cardShape == TvLibraryCardShape.PORTRAIT) {
                tvLibraryAllTitlesColumnCount(maxWidth.value, fontScale)
            } else {
                ((maxWidth.value - (TvLayoutTokens.SafeInsets.horizontal.value * 2f)) / 248f).toInt().coerceAtLeast(1)
            }
        LaunchedEffect(state.libraryItems.size, state.endReached, state.isPageLoading, state.libraryErrorMessage, columns) {
            androidx.compose.runtime
                .snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index
                        ?.minus(1) ?: -1
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = TvScreenPadding,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
        ) {
            item(key = "all-titles-header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvLibraryAllTitlesHeader(route.title ?: strings.library)
                    TvLibraryQueryControls(
                        query = state.libraryBrowseQuery,
                        labels = strings.tvLibraryQueryLabels(),
                        availableYears =
                            state.libraryItems
                                .mapNotNull(JellyfinItem::productionYear)
                                .distinct()
                                .sortedDescending(),
                        availableMediaTypes = tvLibraryMediaTypes(collectionType),
                        onQueryChanged = onQueryChanged,
                        // While the first page loads there is no card yet, so focus starts on the first control.
                        entryFocus = state.libraryItems.isEmpty(),
                    )
                }
            }
            itemsIndexed(state.libraryItems, key = { _, item -> item.id }) { index, item ->
                val artwork = resolveTvJellyfinArtwork(item, landscape = cardShape == TvLibraryCardShape.LANDSCAPE)
                val targetId = tvLibraryTargetId(item.id)
                TvMediaCard(
                    title = item.name,
                    subtitle = item.subtitleText(),
                    imageUrl =
                        jellyfinImageUrl(
                            state.imageBaseUrl,
                            state.imageAccessToken,
                            artwork,
                            if (cardShape == TvLibraryCardShape.PORTRAIT) TvArtworkSize.PORTRAIT_CARD else TvArtworkSize.LANDSCAPE_CARD,
                        ),
                    selected = item.id in state.favorites,
                    format = if (cardShape == TvLibraryCardShape.PORTRAIT) TvMediaCardFormat.POSTER else TvMediaCardFormat.LANDSCAPE,
                    artworkFit =
                        if (artwork?.imageType ==
                            "Primary"
                        ) {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        } else {
                            TvMediaCardArtworkFit.CROP
                        },
                    fillWidth = true,
                    onClick = { item.open(onOpenItem, onOpenContainer) },
                    onFocused = {
                        focusMemory.remember(routeKey, "items", item.id, index + 1, index)
                    },
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = isTvGridLeftEdge(index, columns),
                    modifier = Modifier.tvScreenEntryFocus(index == 0, targetId).testTag("tv-library-all-title-${item.id}"),
                )
            }
            if (state.isLibraryLoading || state.isPageLoading) {
                item(key = "all-titles-loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TvPurple)
                    }
                }
            } else if (state.libraryErrorMessage != null) {
                item(key = "all-titles-error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            strings.libraryLoadFailed,
                            color =
                                androidx.compose.ui.graphics
                                    .Color(0xFFFFA59E),
                        )
                        TvActionButton(strings.retry, onRetry, modifier = Modifier.width(220.dp), focusTargetId = TV_LIBRARY_RETRY_TARGET)
                    }
                }
            } else if (state.libraryItems.isEmpty()) {
                item(key = "all-titles-empty", span = { GridItemSpan(maxLineSpan) }) {
                    TvFocusPlaceholder(strings.noResults, TV_LIBRARY_EMPTY_TARGET)
                }
            }
        }
    }
}

@Composable
private fun TvLibraryAllTitlesHeader(title: String) {
    Text(
        text = title,
        color = TvText,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("tv-library-browse-title"),
    )
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

private fun tvLibraryHasReliablePosters(items: List<JellyfinItem>): Boolean {
    if (items.isEmpty()) return true
    val withPoster = items.count { it.primaryImageTag != null || it.seriesPrimaryImageTag != null }
    return withPoster.toFloat() / items.size >= 0.6f
}

/** Progress of a trailer whose player reports none. */
private object TvNoTrailerProgress : State<Float> {
    override val value: Float = 0f
}
