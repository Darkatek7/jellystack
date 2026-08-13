@file:Suppress(
    "CyclomaticComplexMethod",
    "ExplicitItLambdaParameter",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.SpotlightCandidate
import dev.jellystack.core.jellyfin.buildSpotlightCandidates
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import dev.jellystack.players.AndroidPlayerEngine
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

private const val TV_HOME_HERO_HEIGHT_DP = 260
internal fun tvHomeFirstCardTopDp(): Int = 20 + TV_HOME_HERO_HEIGHT_DP + 28 + 24 + 14

private enum class TvSearchSource { ALL, JELLYFIN, SEERR }

@Composable
internal fun TvHomeScreen(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    strings: TvStrings,
    autoCycle: Boolean,
    intervalSeconds: Int,
    railOpen: Boolean,
    trailerPreviewState: TvTrailerPreviewState,
    focusMemory: TvFocusMemory,
    onRefresh: () -> Unit,
    onPreviewFocus: (JellyfinItem) -> Unit,
    onPreviewBlur: (JellyfinItem) -> Unit,
    trailerPreviewEngine: AndroidPlayerEngine,
    previewSoundEnabled: Boolean,
    previewProgress: Float,
    onPlayItem: (JellyfinItem) -> Unit,
    onItem: (JellyfinItem) -> Unit,
    onLibrary: (JellyfinLibrary) -> Unit,
    onSeerrItem: (TvRoute.SeerrDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isHomeLoading && state.continueWatching.isEmpty() && state.nextUp.isEmpty()) {
        TvLoading(strings.loading, modifier)
        return
    }
    val recentCandidates =
        remember(state.recentShows, state.recentMovies) {
            buildSpotlightCandidates(state.recentShows, state.recentMovies, Clock.System.now())
        }
    val homeListState = rememberLazyListState()
    val entryFocusRequester = LocalTvScreenEntryFocusRequester.current
    val entryFocusGate = remember { TvHomeEntryFocusGate() }
    LaunchedEffect(recentCandidates.isNotEmpty(), railOpen) {
        if (entryFocusGate.consume(recentCandidates.isNotEmpty(), railOpen)) {
            homeListState.scrollToItem(0)
            entryFocusRequester?.requestFocus()
        }
    }
    var spotlightItemId by remember { mutableStateOf<String?>(null) }
    var heroActionFocused by remember { mutableStateOf(false) }
    val spotlightIndex = recentCandidates.indexOfFirst { it.actionItem.id == spotlightItemId }.takeIf { it >= 0 } ?: 0
    LaunchedEffect(recentCandidates.map { it.actionItem.id }) {
        if (spotlightItemId !in recentCandidates.map { it.actionItem.id }) {
            spotlightItemId = recentCandidates.firstOrNull()?.actionItem?.id
        }
    }
    val previewPlaying = trailerPreviewState is TvTrailerPreviewState.Playing
    LaunchedEffect(autoCycle, intervalSeconds, recentCandidates.size, railOpen, previewPlaying, heroActionFocused, spotlightItemId) {
        if (autoCycle && recentCandidates.size > 1 && !railOpen && !previewPlaying && !heroActionFocused) {
            while (true) {
                delay(intervalSeconds.coerceAtLeast(6) * 1_000L)
                val current = recentCandidates.indexOfFirst { it.actionItem.id == spotlightItemId }.coerceAtLeast(0)
                spotlightItemId = recentCandidates[(current + 1) % recentCandidates.size].actionItem.id
            }
        }
    }
    val needsRowEntry = recentCandidates.isEmpty()
    val firstDefaultRowKey =
        when {
            state.continueWatching.isNotEmpty() -> "continue"
            state.nextUp.isNotEmpty() -> "next"
            state.libraries.isNotEmpty() -> "libraries"
            else -> null
        }
    LazyColumn(
        state = homeListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (recentCandidates.isNotEmpty()) {
            item(key = "spotlight") {
                val candidate = recentCandidates[spotlightIndex.coerceIn(0, recentCandidates.lastIndex)]
                TvSpotlight(
                    candidate = candidate,
                    position = spotlightIndex + 1,
                    total = recentCandidates.size,
                    state = state,
                    strings = strings,
                    onActionFocusChanged = { heroActionFocused = it },
                    onPlay = { onPlayItem(candidate.actionItem) },
                    onDetails = { onItem(candidate.actionItem) },
                )
            }
            item("recent-30") {
                TvJellyfinRow(
                    title = strings.newInLastThirtyDays,
                    items = recentCandidates.map { it.actionItem },
                    displayItemsById = recentCandidates.associate { it.actionItem.id to it.displayItem },
                    landscape = false,
                    state = state,
                    focusMemory = focusMemory,
                    onItem = onItem,
                    onPreviewFocus = onPreviewFocus,
                    onPreviewBlur = onPreviewBlur,
                    trailerPreviewState = trailerPreviewState,
                    trailerPreviewEngine = trailerPreviewEngine,
                    previewSoundEnabled = previewSoundEnabled,
                    previewProgress = previewProgress,
                )
            }
        }
        when (homeSections) {
            is HomeSectionsState.Ready -> {
                items(homeSections.sections, key = { "plugin:${it.id}" }) { section ->
                    TvHomeSectionRow(
                        section = section,
                        imageBaseUrl = homeSections.imageBaseUrl,
                        imageAccessToken = homeSections.imageAccessToken,
                        focusMemory = focusMemory,
                        onItem = onItem,
                        onSeerrItem = onSeerrItem,
                        onPreviewFocus = onPreviewFocus,
                        onPreviewBlur = onPreviewBlur,
                        trailerPreviewState = trailerPreviewState,
                        trailerPreviewEngine = trailerPreviewEngine,
                        previewSoundEnabled = previewSoundEnabled,
                        previewProgress = previewProgress,
                        screenEntry = needsRowEntry && section.id == homeSections.sections.firstOrNull()?.id,
                    )
                }
            }
            else -> {
                if (state.continueWatching.isNotEmpty()) {
                    item("continue") {
                        TvJellyfinRow(
                            strings.continueWatching,
                            state.continueWatching,
                            state,
                            focusMemory,
                            onItem,
                            onPreviewFocus = onPreviewFocus,
                            onPreviewBlur = onPreviewBlur,
                            trailerPreviewState = trailerPreviewState,
                            trailerPreviewEngine = trailerPreviewEngine,
                            previewSoundEnabled = previewSoundEnabled,
                            previewProgress = previewProgress,
                            screenEntry = needsRowEntry && firstDefaultRowKey == "continue",
                        )
                    }
                }
                if (state.nextUp.isNotEmpty()) {
                    item("next") {
                        TvJellyfinRow(
                            strings.nextUp,
                            state.nextUp,
                            state,
                            focusMemory,
                            onItem,
                            onPreviewFocus = onPreviewFocus,
                            onPreviewBlur = onPreviewBlur,
                            trailerPreviewState = trailerPreviewState,
                            trailerPreviewEngine = trailerPreviewEngine,
                            previewSoundEnabled = previewSoundEnabled,
                            previewProgress = previewProgress,
                            screenEntry = needsRowEntry && firstDefaultRowKey == "next",
                        )
                    }
                }
                item("libraries") {
                    TvLibraryRow(
                        state.libraries,
                        state,
                        strings.myMedia,
                        focusMemory,
                        onLibrary,
                        screenEntry = needsRowEntry && firstDefaultRowKey == "libraries",
                    )
                }
            }
        }
        state.errorMessage?.let { message ->
            item("error") {
                TvActionButton("${strings.retry}: $message", onRefresh)
            }
        }
    }
}

internal fun shouldRequestHomeEntryFocus(hasRecentContent: Boolean, railOpen: Boolean): Boolean =
    hasRecentContent && !railOpen

internal class TvHomeEntryFocusGate {
    private var consumed = false

    fun consume(hasRecentContent: Boolean, railOpen: Boolean): Boolean {
        if (consumed || !shouldRequestHomeEntryFocus(hasRecentContent, railOpen)) return false
        consumed = true
        return true
    }
}

@Composable
private fun TvSpotlight(
    candidate: SpotlightCandidate,
    position: Int,
    total: Int,
    state: JellyfinHomeState,
    strings: TvStrings,
    onActionFocusChanged: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    val item = candidate.displayItem
    val artwork = resolveTvJellyfinArtwork(item)
    Box(
        modifier = Modifier.fillMaxWidth().height(TV_HOME_HERO_HEIGHT_DP.dp),
    ) {
        AsyncImage(
            model =
                jellyfinImageUrl(
                    state.imageBaseUrl,
                    state.imageAccessToken,
                    artwork,
                    1600,
                ),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(TvBackground, TvBackground.copy(alpha = 0.84f), Color.Transparent, TvBackground.copy(alpha = 0.08f))),
                ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, TvBackground))))
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 28.dp, bottom = 20.dp).fillMaxWidth(0.46f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = TvPurple, modifier = Modifier.size(18.dp))
                Text(strings.recentlyAdded, color = TvPurple, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            val logoTag = item.logoImageTag ?: item.parentLogoImageTag
            if (logoTag != null) {
                AsyncImage(
                    model = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, item.seriesId ?: item.id, logoTag, "Logo", 700),
                    contentDescription = item.seriesName ?: item.name,
                    modifier = Modifier.widthIn(max = 330.dp).heightIn(max = 76.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    item.seriesName ?: item.name,
                    color = TvText,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
            item.overview?.let { Text(it, color = TvTextMuted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOfNotNull(
                        item.productionYear?.toString(),
                        item.communityRating?.let { "%.1f".format(it) },
                        item.officialRating,
                    ).joinToString("  •  "),
                    color = TvTextMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    label = if ((candidate.actionItem.positionTicks ?: 0L) > 0L) strings.continueLabel else strings.play,
                    primary = true,
                    onClick = onPlay,
                    leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
                    modifier = Modifier.width(160.dp).tvScreenEntryFocus().onFocusChanged { onActionFocusChanged(it.hasFocus) },
                    focusToNavigationRailOnLeft = true,
                )
                TvActionButton(
                    label = strings.details,
                    onClick = onDetails,
                    leading = { Icon(Icons.Default.Info, null, tint = TvText) },
                    modifier = Modifier.width(140.dp).onFocusChanged { onActionFocusChanged(it.hasFocus) },
                )
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 22.dp), horizontalAlignment = Alignment.End) {
            Text("%02d | %02d".format(position, total), color = TvPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("30 days", color = TvTextMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TvJellyfinRow(
    title: String,
    items: List<JellyfinItem>,
    state: JellyfinHomeState,
    focusMemory: TvFocusMemory,
    onItem: (JellyfinItem) -> Unit,
    displayItemsById: Map<String, JellyfinItem> = emptyMap(),
    landscape: Boolean = true,
    onPreviewFocus: (JellyfinItem) -> Unit = {},
    onPreviewBlur: (JellyfinItem) -> Unit = {},
    trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    trailerPreviewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: Float = 0f,
    routeKey: String = "home",
    screenEntry: Boolean = false,
) {
    val snapshot = focusMemory.restore(routeKey)?.takeIf { it.rowKey == title }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = snapshot?.horizontalIndex ?: 0)
    val focusRequester = remember(title) { FocusRequester() }
    val restoreId = focusMemory.resolveItem(routeKey, items.map { it.id }).takeIf { snapshot != null }
    LaunchedEffect(restoreId, items.map { it.id }) {
        if (restoreId != null) {
            listState.scrollToItem(items.indexOfFirst { it.id == restoreId }.coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val displayItem = displayItemsById[item.id] ?: item
                val artwork = resolveTvJellyfinArtwork(displayItem, landscape)
                TvMediaCard(
                    title = displayItem.episodeTitle ?: displayItem.name,
                    subtitle = displayItem.subtitleText(),
                    imageUrl =
                        jellyfinImageUrl(
                            state.imageBaseUrl,
                            state.imageAccessToken,
                            artwork,
                        ),
                    onClick = { onItem(item) },
                    landscape = landscape,
                    onFocused = {
                        focusMemory.remember(routeKey, title, item.id, horizontalIndex = index)
                        onPreviewFocus(item)
                    },
                    onFocusChanged = { focused -> if (!focused) onPreviewBlur(item) },
                    previewing = trailerPreviewState.isPlaying(item.id),
                    previewEngine = trailerPreviewEngine,
                    previewSoundEnabled = previewSoundEnabled,
                    previewProgress = previewProgress,
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0)
                            .then(if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

@Composable
private fun TvLibraryRow(
    libraries: List<JellyfinLibrary>,
    state: JellyfinHomeState,
    title: String,
    focusMemory: TvFocusMemory,
    onLibrary: (JellyfinLibrary) -> Unit,
    screenEntry: Boolean = false,
) {
    if (libraries.isEmpty()) return
    val rowKey = "libraries"
    val snapshot = focusMemory.restore("home")?.takeIf { it.rowKey == rowKey }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = snapshot?.horizontalIndex ?: 0)
    val focusRequester = remember { FocusRequester() }
    val restoreId = focusMemory.resolveItem("home", libraries.map { it.id }).takeIf { snapshot != null }
    LaunchedEffect(restoreId, libraries.map { it.id }) {
        if (restoreId != null) {
            listState.scrollToItem(libraries.indexOfFirst { it.id == restoreId }.coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(libraries, key = { _, it -> it.id }) { index, library ->
                TvMediaCard(
                    title = library.name,
                    subtitle = library.itemCount?.let { "$it items" },
                    imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, library.id, library.primaryImageTag),
                    onClick = { onLibrary(library) },
                    onFocused = { focusMemory.remember("home", "libraries", library.id, horizontalIndex = index) },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0)
                            .then(if (library.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

@Composable
private fun TvHomeSectionRow(
    section: HomeSection,
    imageBaseUrl: String?,
    imageAccessToken: String?,
    focusMemory: TvFocusMemory,
    onItem: (JellyfinItem) -> Unit,
    onSeerrItem: (TvRoute.SeerrDetail) -> Unit,
    onPreviewFocus: (JellyfinItem) -> Unit,
    onPreviewBlur: (JellyfinItem) -> Unit,
    trailerPreviewState: TvTrailerPreviewState,
    trailerPreviewEngine: AndroidPlayerEngine,
    previewSoundEnabled: Boolean,
    previewProgress: Float,
    screenEntry: Boolean = false,
) {
    val snapshot = focusMemory.restore("home")?.takeIf { it.rowKey == section.id }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = snapshot?.horizontalIndex ?: 0)
    val focusRequester = remember(section.id) { FocusRequester() }
    val restoreId = focusMemory.resolveItem("home", section.items.map { it.id }).takeIf { snapshot != null }
    LaunchedEffect(restoreId, section.items.map { it.id }) {
        if (restoreId != null) {
            listState.scrollToItem(section.items.indexOfFirst { it.id == restoreId }.coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvSectionTitle(section.title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(section.items, key = { _, it -> it.id }) { index, item ->
                val previewItem = item.jellyfinItem?.takeIf { item.action == HomeSectionAction.JELLYFIN }
                TvMediaCard(
                    title = item.name,
                    subtitle =
                        listOfNotNull(
                            item.productionYear?.toString(),
                            item.communityRating?.let { "★ %.1f".format(it) },
                        ).joinToString("  •  ").ifBlank { null },
                    imageUrl = resolveTvHomeSectionImageUrl(item, imageBaseUrl, imageAccessToken),
                    landscape = section.viewMode != HomeSectionViewMode.PORTRAIT,
                    onFocused = {
                        focusMemory.remember("home", section.id, item.id, horizontalIndex = index)
                        previewItem?.let(onPreviewFocus)
                    },
                    onFocusChanged = { focused -> if (!focused) previewItem?.let(onPreviewBlur) },
                    previewing = previewItem?.let { trailerPreviewState.isPlaying(it.id) } == true,
                    previewEngine = trailerPreviewEngine,
                    previewSoundEnabled = previewSoundEnabled,
                    previewProgress = previewProgress,
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0)
                            .then(if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    focusToNavigationRailOnLeft = index == 0,
                    onClick = {
                        when (item.action) {
                            HomeSectionAction.JELLYFIN -> item.jellyfinItem?.let(onItem)
                            HomeSectionAction.SEERR -> {
                                val tmdbId = item.seerrTmdbId ?: return@TvMediaCard
                                onSeerrItem(
                                    TvRoute.SeerrDetail(
                                        tmdbId = tmdbId,
                                        mediaType =
                                            dev.jellystack.core.jellyseerr.JellyseerrMediaType
                                                .from(item.seerrMediaType),
                                        title = item.name,
                                        overview = item.overview,
                                        releaseYear = item.productionYear?.toString(),
                                    ),
                                )
                            }
                            HomeSectionAction.INFORMATION -> item.jellyfinItem?.let(onItem)
                        }
                    },
                )
            }
        }
    }
}

private fun TvTrailerPreviewState.isPlaying(itemId: String): Boolean =
    this is TvTrailerPreviewState.Playing && target.itemId == itemId

@Composable
internal fun TvLibraryScreen(
    route: TvRoute.Library,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onSelectLibrary: (String) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(route.libraryId) { route.libraryId?.let(onSelectLibrary) }
    val routeKey = "library:${route.libraryId ?: "root"}"
    val visibleIds = if (route.libraryId == null) state.libraries.map { it.id } else state.libraryItems.map { it.id }
    val snapshot = focusMemory.restore(routeKey)
    val restoreId = focusMemory.resolveItem(routeKey, visibleIds).takeIf { snapshot != null }
    val focusRequester = remember(routeKey) { FocusRequester() }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = snapshot?.verticalIndex ?: 0)
    LaunchedEffect(restoreId, visibleIds) {
        if (restoreId != null) {
            gridState.scrollToItem((snapshot?.verticalIndex ?: 0).coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(
        route.libraryId,
        state.libraryItems.size,
        state.isInitialLoading,
        state.isPageLoading,
        state.endReached,
        state.errorMessage,
    ) {
        if (route.libraryId == null) return@LaunchedEffect
        snapshotFlow {
            val lastGridIndex =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            (lastGridIndex - 1).coerceAtMost(state.libraryItems.lastIndex)
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (
                shouldLoadNextLibraryPage(
                    lastVisibleIndex = lastVisibleIndex,
                    totalItemCount = state.libraryItems.size,
                    isInitialLoading = state.isInitialLoading,
                    isPageLoading = state.isPageLoading,
                    endReached = state.endReached,
                    hasError = state.errorMessage != null,
                )
            ) {
                onLoadMore()
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(route.title ?: strings.library, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        if (route.libraryId == null) {
            gridItemsIndexed(state.libraries, key = { _, library -> library.id }) { index, library ->
                TvMediaCard(
                    title = library.name,
                    subtitle = library.itemCount?.let { "$it items" },
                    imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, library.id, library.primaryImageTag),
                    onClick = { onSelectLibrary(library.id) },
                    onFocused = { focusMemory.remember(routeKey, "libraries", library.id, index + 1, index) },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(index == 0)
                            .then(if (library.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    fillWidth = true,
                    focusToNavigationRailOnLeft = index % 4 == 0,
                )
            }
        } else {
            gridItemsIndexed(state.libraryItems, key = { _, item -> item.id }) { index, item ->
                val artwork = resolveTvJellyfinArtwork(item)
                TvMediaCard(
                    title = item.name,
                    subtitle = item.subtitleText(),
                    imageUrl =
                        jellyfinImageUrl(
                            state.imageBaseUrl,
                            state.imageAccessToken,
                            artwork,
                        ),
                    onClick = { if (item.isBrowseContainer()) onOpenContainer(item) else onOpenItem(item) },
                    onFocused = { focusMemory.remember(routeKey, "items", item.id, index + 1, index) },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(index == 0)
                            .then(if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    fillWidth = true,
                    focusToNavigationRailOnLeft = index % 4 == 0,
                )
            }
            if (state.isInitialLoading || state.isPageLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TvPurple)
                    }
                }
            } else if (state.errorMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(strings.libraryLoadFailed, color = Color(0xFFFFA59E), fontSize = 18.sp)
                        TvActionButton(
                            strings.retry,
                            if (state.libraryItems.isEmpty()) onRetry else onLoadMore,
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun shouldLoadNextLibraryPage(
    lastVisibleIndex: Int,
    totalItemCount: Int,
    isInitialLoading: Boolean,
    isPageLoading: Boolean,
    endReached: Boolean,
    hasError: Boolean,
): Boolean {
    val pagingBlocked =
        listOf(
            totalItemCount <= 0,
            lastVisibleIndex < 0,
            isInitialLoading,
            isPageLoading,
            endReached,
            hasError,
        ).any { it }
    if (pagingBlocked) return false
    val thresholdIndex = (totalItemCount - 1 - LIBRARY_PREFETCH_ITEM_COUNT).coerceAtLeast(0)
    return lastVisibleIndex >= thresholdIndex
}

private const val LIBRARY_PREFETCH_ITEM_COUNT = 8

@Composable
internal fun TvSearchScreen(
    jellyfinResults: List<JellyfinItem>,
    requestsState: JellyseerrRequestsState,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onQueryChanged: (String) -> Unit,
    onJellyfinItem: (JellyfinItem) -> Unit,
    onSeerrItem: (JellyseerrSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(TvSearchSource.ALL) }
    val sourceFocusRequester = remember { FocusRequester() }
    val seerrResults = (requestsState as? JellyseerrRequestsState.Ready)?.searchResults.orEmpty()
    LazyColumn(modifier.fillMaxSize(), contentPadding = TvScreenPadding, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text(strings.search, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChanged(it)
                },
                placeholder = { Text(strings.searchHint) },
                singleLine = true,
                colors = tvOutlinedTextFieldColors(),
                modifier =
                    Modifier
                        .tvScreenEntryFocus()
                        .fillMaxWidth(0.66f)
                        .height(64.dp)
                        .tvReturnToNavigationRailOnLeft()
                        .focusProperties {
                            down = sourceFocusRequester
                            right = sourceFocusRequester
                        },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    strings.all,
                    { source = TvSearchSource.ALL },
                    modifier = Modifier.focusRequester(sourceFocusRequester),
                    primary = source == TvSearchSource.ALL,
                    focusToNavigationRailOnLeft = true,
                )
                TvActionButton("Jellyfin", { source = TvSearchSource.JELLYFIN }, primary = source == TvSearchSource.JELLYFIN)
                TvActionButton("Seerr", { source = TvSearchSource.SEERR }, primary = source == TvSearchSource.SEERR)
            }
        }
        val visibleJellyfin = source != TvSearchSource.SEERR && jellyfinResults.isNotEmpty()
        val visibleSeerr = source != TvSearchSource.JELLYFIN && seerrResults.isNotEmpty()
        if (query.isNotBlank() && !visibleJellyfin && !visibleSeerr) item { Text(strings.noResults, color = TvTextMuted) }
        if (visibleJellyfin) item {
            TvJellyfinRow("Jellyfin", jellyfinResults, homeState, focusMemory, onJellyfinItem, routeKey = "search")
        }
        if (visibleSeerr) item { TvSeerrRow("Seerr", seerrResults, focusMemory, "search", onSeerrItem) }
    }
}

@Composable
internal fun TvDiscoverScreen(
    recommendations: JellyseerrRecommendationsState,
    requests: JellyseerrRequestsState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onItem: (JellyseerrSearchItem) -> Unit,
    onConnectSeerr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = recommendations as? JellyseerrRecommendationsState.Ready
    val firstPopulatedRail =
        ready?.let { state ->
            JellyseerrRecommendationRail.entries.firstOrNull { rail -> state.rails[rail]?.items?.isNotEmpty() == true }
        }
    LazyColumn(modifier.fillMaxSize(), contentPadding = TvScreenPadding, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item { Text(strings.discover, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
        if (ready == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(strings.connectSeerrPrompt, color = TvTextMuted)
                    TvActionButton(
                        "${strings.settings}: Seerr",
                        onConnectSeerr,
                        modifier = Modifier.tvScreenEntryFocus(),
                        primary = true,
                    )
                }
            }
        } else {
            JellyseerrRecommendationRail.entries.forEach { rail ->
                val railState = ready.rails[rail]
                if (railState != null && railState.items.isNotEmpty()) {
                    item(rail.name) {
                        TvSeerrRow(
                            rail.label(),
                            railState.items,
                            focusMemory,
                            "discover",
                            onItem,
                            screenEntry = rail == firstPopulatedRail,
                        )
                    }
                }
            }
        }
        val requestItems = (requests as? JellyseerrRequestsState.Ready)?.requests.orEmpty()
        if (requestItems.isNotEmpty()) {
            item("requests") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSectionTitle(strings.requests)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(requestItems, key = { _, request -> request.id }) { index, request ->
                            val item = request.toSearchItem()
                            TvMediaCard(
                                title = request.title ?: request.originalTitle ?: "Request ${request.id}",
                                subtitle =
                                    listOfNotNull(
                                        request.requestStatus.name,
                                        request.availability.standard?.name,
                                    ).joinToString(" - "),
                                imageUrl = tmdbImageUrl(request.backdropPath ?: request.posterPath, request.backdropPath != null),
                                onClick = { item?.let(onItem) },
                                modifier = Modifier.tvScreenEntryFocus(firstPopulatedRail == null && index == 0),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeerrRow(
    title: String,
    items: List<JellyseerrSearchItem>,
    focusMemory: TvFocusMemory,
    routeKey: String,
    onItem: (JellyseerrSearchItem) -> Unit,
    screenEntry: Boolean = false,
) {
    val ids = items.map { "${it.mediaType}:${it.tmdbId}" }
    val snapshot = focusMemory.restore(routeKey)?.takeIf { it.rowKey == title }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = snapshot?.horizontalIndex ?: 0)
    val focusRequester = remember(routeKey, title) { FocusRequester() }
    val restoreId = focusMemory.resolveItem(routeKey, ids).takeIf { snapshot != null }
    LaunchedEffect(restoreId, ids) {
        if (restoreId != null) {
            listState.scrollToItem(ids.indexOf(restoreId).coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(items, key = { _, item -> "${item.mediaType}:${item.tmdbId}" }) { index, item ->
                val id = "${item.mediaType}:${item.tmdbId}"
                TvMediaCard(
                    title = item.title,
                    subtitle = item.releaseYear,
                    imageUrl = tmdbImageUrl(item.backdropPath ?: item.posterPath, backdrop = item.backdropPath != null),
                    onClick = { onItem(item) },
                    onFocused = { focusMemory.remember(routeKey, title, id, horizontalIndex = index) },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0)
                            .then(if (id == restoreId) Modifier.focusRequester(focusRequester) else Modifier),
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

internal fun JellyseerrSearchItem.toTvRoute(): TvRoute.SeerrDetail =
    TvRoute.SeerrDetail(tmdbId, mediaType, title, overview, posterPath, backdropPath, releaseYear, tvdbId)

private fun JellyfinItem.subtitleText(): String? =
    listOfNotNull(
        productionYear?.toString(),
        if (type.equals("Episode", true)) "S${parentIndexNumber ?: 0} E${indexNumber ?: 0}" else null,
        communityRating?.let { "★ %.1f".format(it) },
    ).joinToString("  •  ").ifBlank { null }

private fun JellyseerrRecommendationRail.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun JellyseerrRequestSummary.toSearchItem(): JellyseerrSearchItem? {
    val id = tmdbId ?: return null
    return JellyseerrSearchItem(
        tmdbId = id,
        mediaType = mediaType,
        title = title ?: originalTitle ?: "Request $id",
        overview = null,
        releaseYear = null,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = mediaId,
        tvdbId = tvdbId,
        availability = availability,
        requests = listOf(this),
    )
}
