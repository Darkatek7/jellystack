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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.delay

private enum class TvSearchSource { ALL, JELLYFIN, SEERR }

@Composable
internal fun TvHomeScreen(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    strings: TvStrings,
    autoCycle: Boolean,
    intervalSeconds: Int,
    focusMemory: TvFocusMemory,
    onRefresh: () -> Unit,
    onItem: (JellyfinItem) -> Unit,
    onLibrary: (JellyfinLibrary) -> Unit,
    onSeerrItem: (TvRoute.SeerrDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isInitialLoading && state.continueWatching.isEmpty() && state.nextUp.isEmpty()) {
        TvLoading(strings.loading, modifier)
        return
    }
    val spotlightItems =
        remember(state.continueWatching, state.nextUp, state.recentMovies) {
            (state.continueWatching + state.nextUp + state.recentMovies).distinctBy { it.id }.take(8)
        }
    var spotlightIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoCycle, intervalSeconds, spotlightItems.size) {
        if (autoCycle && spotlightItems.size > 1) {
            while (true) {
                delay(intervalSeconds.coerceAtLeast(6) * 1_000L)
                spotlightIndex = (spotlightIndex + 1) % spotlightItems.size
            }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (spotlightItems.isNotEmpty()) {
            item(key = "spotlight") {
                TvSpotlight(
                    item = spotlightItems[spotlightIndex.coerceIn(0, spotlightItems.lastIndex)],
                    state = state,
                    strings = strings,
                    onClick = { onItem(spotlightItems[spotlightIndex.coerceIn(0, spotlightItems.lastIndex)]) },
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
                    )
                }
            }
            else -> {
                item("libraries") { TvLibraryRow(state.libraries, state, strings.library, focusMemory, onLibrary) }
                if (state.continueWatching.isNotEmpty()) {
                    item("continue") { TvJellyfinRow(strings.continueWatching, state.continueWatching, state, focusMemory, onItem) }
                }
                if (state.nextUp.isNotEmpty()) {
                    item("next") { TvJellyfinRow(strings.nextUp, state.nextUp, state, focusMemory, onItem) }
                }
                if (state.recentShows.isNotEmpty()) {
                    item("shows") { TvJellyfinRow(strings.recentShows, state.recentShows, state, focusMemory, onItem) }
                }
                if (state.recentMovies.isNotEmpty()) {
                    item("movies") { TvJellyfinRow(strings.recentMovies, state.recentMovies, state, focusMemory, onItem) }
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

@Composable
private fun TvSpotlight(
    item: JellyfinItem,
    state: JellyfinHomeState,
    strings: TvStrings,
    onClick: () -> Unit,
) {
    val artwork = resolveTvJellyfinArtwork(item)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(390.dp)
                .background(TvSurface, RoundedCornerShape(26.dp))
                .tvFocusable(onClick = onClick, shape = RoundedCornerShape(26.dp)),
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
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.28f), Color.Transparent)),
                    RoundedCornerShape(26.dp),
                ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 52.dp).fillMaxWidth(0.48f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                item.seriesName ?: item.name,
                color = TvText,
                fontSize = 42.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            item.overview?.let { Text(it, color = TvTextMuted, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(listOfNotNull(item.productionYear?.toString(), item.officialRating).joinToString("  •  "), color = TvTextMuted)
            }
            TvActionButton(
                label = if ((item.positionTicks ?: 0L) > 0L) strings.continueLabel else strings.play,
                primary = true,
                onClick = onClick,
                leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
                modifier = Modifier.width(210.dp),
            )
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
    routeKey: String = "home",
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
                val artwork = resolveTvJellyfinArtwork(item)
                TvMediaCard(
                    title = item.episodeTitle ?: item.name,
                    subtitle = item.subtitleText(),
                    imageUrl =
                        jellyfinImageUrl(
                            state.imageBaseUrl,
                            state.imageAccessToken,
                            artwork,
                        ),
                    onClick = { onItem(item) },
                    onFocused = { focusMemory.remember(routeKey, title, item.id, horizontalIndex = index) },
                    modifier = if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
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
                    modifier = if (library.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
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
                TvMediaCard(
                    title = item.name,
                    subtitle =
                        listOfNotNull(
                            item.productionYear?.toString(),
                            item.communityRating?.let { "★ %.1f".format(it) },
                        ).joinToString("  •  ").ifBlank { null },
                    imageUrl = resolveTvHomeSectionImageUrl(item, imageBaseUrl, imageAccessToken),
                    landscape = section.viewMode != HomeSectionViewMode.PORTRAIT,
                    onFocused = { focusMemory.remember("home", section.id, item.id, horizontalIndex = index) },
                    modifier = if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
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
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(route.libraryId) { route.libraryId?.let(onSelectLibrary) }
    val routeKey = "library:${route.libraryId ?: "root"}"
    val visibleIds = if (route.libraryId == null) state.libraries.map { it.id } else state.libraryItems.map { it.id }
    val snapshot = focusMemory.restore(routeKey)
    val restoreId = focusMemory.resolveItem(routeKey, visibleIds).takeIf { snapshot != null }
    val focusRequester = remember(routeKey) { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = snapshot?.verticalIndex ?: 0)
    LaunchedEffect(restoreId, visibleIds) {
        if (restoreId != null) {
            listState.scrollToItem((snapshot?.verticalIndex ?: 0).coerceAtLeast(0))
            focusRequester.requestFocus()
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text(route.title ?: strings.library, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        if (route.libraryId == null) {
            itemsIndexed(state.libraries.chunked(5), key = { _, row -> row.joinToString { it.id } }) { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEachIndexed { columnIndex, library ->
                        TvMediaCard(
                            title = library.name,
                            subtitle = library.itemCount?.let { "$it items" },
                            imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, library.id, library.primaryImageTag),
                            onClick = { onSelectLibrary(library.id) },
                            onFocused = { focusMemory.remember(routeKey, "libraries", library.id, rowIndex + 1, columnIndex) },
                            modifier = if (library.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(state.libraryItems.chunked(5), key = { _, row -> row.joinToString { it.id } }) { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEachIndexed { columnIndex, item ->
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
                            onFocused = { focusMemory.remember(routeKey, "items", item.id, rowIndex + 1, columnIndex) },
                            modifier = if (item.id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                }
            }
            item { if (!state.endReached) TvActionButton(strings.loadMore, onLoadMore, modifier = Modifier.width(220.dp)) }
        }
    }
}

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
                modifier = Modifier.fillMaxWidth(0.66f).height(64.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(strings.all, { source = TvSearchSource.ALL }, primary = source == TvSearchSource.ALL)
                TvActionButton("Jellyfin", { source = TvSearchSource.JELLYFIN }, primary = source == TvSearchSource.JELLYFIN)
                TvActionButton("Seerr", { source = TvSearchSource.SEERR }, primary = source == TvSearchSource.SEERR)
            }
        }
        val visibleJellyfin = source != TvSearchSource.SEERR && jellyfinResults.isNotEmpty()
        val visibleSeerr = source != TvSearchSource.JELLYFIN && seerrResults.isNotEmpty()
        if (query.isNotBlank() && !visibleJellyfin && !visibleSeerr) item { Text(strings.noResults, color = TvTextMuted) }
        if (visibleJellyfin) item { TvJellyfinRow("Jellyfin", jellyfinResults, homeState, focusMemory, onJellyfinItem, "search") }
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
    modifier: Modifier = Modifier,
) {
    val ready = recommendations as? JellyseerrRecommendationsState.Ready
    LazyColumn(modifier.fillMaxSize(), contentPadding = TvScreenPadding, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item { Text(strings.discover, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
        if (ready == null) {
            item { Text(strings.connectSeerrPrompt, color = TvTextMuted) }
        } else {
            JellyseerrRecommendationRail.entries.forEach { rail ->
                val railState = ready.rails[rail]
                if (railState != null && railState.items.isNotEmpty()) {
                    item(rail.name) { TvSeerrRow(rail.label(), railState.items, focusMemory, "discover", onItem) }
                }
            }
        }
        val requestItems = (requests as? JellyseerrRequestsState.Ready)?.requests.orEmpty()
        if (requestItems.isNotEmpty()) {
            item("requests") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSectionTitle(strings.requests)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(requestItems, key = { it.id }) { request ->
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
                    modifier = if (id == restoreId) Modifier.focusRequester(focusRequester) else Modifier,
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
