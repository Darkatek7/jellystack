@file:Suppress("ktlint:standard:function-naming")

package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.tmdb.tmdbPosterUrl
import dev.jellystack.design.components.ShimmerPlaceholder
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.theme.JellystackLayoutTokens
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.back_to_discover
import jellystack_mobile.design.generated.resources.manage_servers
import jellystack_mobile.design.generated.resources.request_availability_available
import jellystack_mobile.design.generated.resources.request_availability_blacklisted
import jellystack_mobile.design.generated.resources.request_availability_deleted
import jellystack_mobile.design.generated.resources.request_availability_partial
import jellystack_mobile.design.generated.resources.request_availability_pending
import jellystack_mobile.design.generated.resources.request_availability_processing
import jellystack_mobile.design.generated.resources.request_availability_unknown
import jellystack_mobile.design.generated.resources.request_clear_search
import jellystack_mobile.design.generated.resources.request_filter_all
import jellystack_mobile.design.generated.resources.request_filter_approved
import jellystack_mobile.design.generated.resources.request_filter_available
import jellystack_mobile.design.generated.resources.request_filter_deleted
import jellystack_mobile.design.generated.resources.request_filter_failed
import jellystack_mobile.design.generated.resources.request_filter_label
import jellystack_mobile.design.generated.resources.request_filter_pending
import jellystack_mobile.design.generated.resources.request_filter_processing
import jellystack_mobile.design.generated.resources.request_filter_unavailable
import jellystack_mobile.design.generated.resources.request_media_collection
import jellystack_mobile.design.generated.resources.request_media_movie
import jellystack_mobile.design.generated.resources.request_media_person
import jellystack_mobile.design.generated.resources.request_media_series
import jellystack_mobile.design.generated.resources.request_media_unknown
import jellystack_mobile.design.generated.resources.request_no_results
import jellystack_mobile.design.generated.resources.request_refresh
import jellystack_mobile.design.generated.resources.request_requested_by
import jellystack_mobile.design.generated.resources.request_search_placeholder
import jellystack_mobile.design.generated.resources.request_search_results
import jellystack_mobile.design.generated.resources.request_season_summary
import jellystack_mobile.design.generated.resources.request_seasons_summary
import jellystack_mobile.design.generated.resources.request_status_approved
import jellystack_mobile.design.generated.resources.request_status_completed
import jellystack_mobile.design.generated.resources.request_status_declined
import jellystack_mobile.design.generated.resources.request_status_failed
import jellystack_mobile.design.generated.resources.request_status_pending
import jellystack_mobile.design.generated.resources.request_status_unknown
import jellystack_mobile.design.generated.resources.request_view_details
import jellystack_mobile.design.generated.resources.requests_empty_body
import jellystack_mobile.design.generated.resources.requests_empty_title
import jellystack_mobile.design.generated.resources.requests_title
import jellystack_mobile.design.generated.resources.retry
import jellystack_mobile.design.generated.resources.seerr_connect_body
import jellystack_mobile.design.generated.resources.seerr_connect_title
import jellystack_mobile.design.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.grid.items as gridItems

internal object RequestsTestTags {
    const val SEARCH_FIELD = "requests_search_field"
    const val FILTER_SELECTOR = "requests_filter_selector"
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
internal fun JellyseerrRequestsScreen(
    state: JellyseerrRequestsState,
    onBackToDiscover: () -> Unit = {},
    onSelectSearchResult: (JellyseerrSearchItem) -> Unit = {},
    onSelectExisting: (JellyseerrRequestSummary) -> Unit = {},
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
    onAddServer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val responsiveProfile = LocalResponsiveProfile.current
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + responsiveProfile.horizontalContentPadding,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + responsiveProfile.horizontalContentPadding,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )

    val isRefreshing =
        when (state) {
            JellyseerrRequestsState.Loading -> true
            is JellyseerrRequestsState.Ready -> state.isRefreshing
            else -> false
        }
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = onRefresh,
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState),
    ) {
        when (state) {
            JellyseerrRequestsState.Loading ->
                RequestsSkeleton(
                    contentPadding = listPadding,
                    modifier = Modifier.fillMaxSize(),
                )

            JellyseerrRequestsState.MissingServer ->
                MissingServerPlaceholder(
                    onAddServer = onAddServer,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(24.dp),
                )

            is JellyseerrRequestsState.Error ->
                ErrorPlaceholder(
                    message = state.message,
                    onRetry = onRefresh,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(24.dp),
                )

            is JellyseerrRequestsState.Ready ->
                RequestsContent(
                    state = state,
                    onBackToDiscover = onBackToDiscover,
                    onSelectSearchResult = onSelectSearchResult,
                    onSelectExisting = onSelectExisting,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                    onSelectFilter = onSelectFilter,
                    onRefresh = onRefresh,
                    contentPadding = listPadding,
                    modifier = Modifier.fillMaxSize(),
                )
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding() + 12.dp),
        )
    }
}

@Composable
private fun RequestsContent(
    state: JellyseerrRequestsState.Ready,
    onBackToDiscover: () -> Unit,
    onSelectSearchResult: (JellyseerrSearchItem) -> Unit,
    onSelectExisting: (JellyseerrRequestSummary) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val responsiveProfile = LocalResponsiveProfile.current
    val backLabel = stringResource(Res.string.back_to_discover)
    LazyVerticalGrid(
        modifier = modifier,
        columns =
            if (responsiveProfile.isExpanded) {
                GridCells.Fixed(2)
            } else {
                GridCells.Fixed(1)
            },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
                onClick = onBackToDiscover,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = backLabel },
            ) {
                Text(backLabel)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                query = state.query,
                isSearching = state.isSearching,
                onQueryChanged = onSearch,
                onClear = onClearSearch,
            )
        }
        if (state.searchResults.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(Res.string.request_search_results),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            gridItems(state.searchResults, key = { it.tmdbId }) { result ->
                SearchResultCard(
                    item = result,
                    onSelect = { onSelectSearchResult(result) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Divider() }
        } else if (state.query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(Res.string.request_no_results, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Divider() }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(
                selected = state.filter,
                isRefreshing = state.isRefreshing,
                onSelectFilter = onSelectFilter,
                onRefresh = onRefresh,
            )
        }

        if (state.requests.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyRequestsPlaceholder()
            }
        } else {
            gridItems(state.requests, key = { it.id }) { summary ->
                RequestCard(
                    summary = summary,
                    onSelect = { onSelectExisting(summary) },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    var textFieldValue by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(query)) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { value ->
                textFieldValue = value
                onQueryChanged(value.text)
            },
            placeholder = { Text(stringResource(Res.string.request_search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon =
                if (textFieldValue.text.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                textFieldValue = TextFieldValue("")
                                onClear()
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(Res.string.request_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            shape = RoundedCornerShape(16.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag(RequestsTestTags.SEARCH_FIELD),
        )
        if (isSearching) {
            LinearLoadingIndicator()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FilterRow(
    selected: JellyseerrRequestFilter,
    isRefreshing: Boolean,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.requests_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(JellystackLayoutTokens.minimumTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(Res.string.request_refresh),
                    )
                }
            }
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selected.localizedLabel(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(Res.string.request_filter_label)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .heightIn(min = JellystackLayoutTokens.minimumTouchTarget)
                        .testTag(RequestsTestTags.FILTER_SELECTOR),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                JellyseerrRequestFilter.values().forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.localizedLabel()) },
                        onClick = {
                            expanded = false
                            if (filter != selected) {
                                onSelectFilter(filter)
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        Divider()
    }
}

@Composable
private fun SearchResultCard(
    item: JellyseerrSearchItem,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val posterPlaceholder =
                remember(item.title) {
                    item.title
                        .firstOrNull { it.isLetterOrDigit() }
                        ?.uppercaseChar()
                        ?.toString()
                }
            val resolvedPosterPath =
                remember(item.posterPath, item.backdropPath) {
                    item.posterPath ?: item.backdropPath
                }
            PosterArtwork(
                posterPath = resolvedPosterPath,
                contentDescription = item.title,
                placeholderText = posterPlaceholder,
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val mediaTypeName = item.mediaType.localizedName()
                Text(
                    text =
                        buildString {
                            append(mediaTypeName)
                            item.releaseYear?.let {
                                append(" - ")
                                append(it)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val overviewText = item.overview
                if (!overviewText.isNullOrBlank()) {
                    Text(
                        text = overviewText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.requests.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.requests.forEach { request ->
                            StatusBadge(request.requestStatus)
                        }
                    }
                } else {
                    Text(
                        stringResource(Res.string.request_view_details),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    summary: JellyseerrRequestSummary,
    onSelect: () -> Unit,
) {
    Card(onClick = onSelect) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val mediaTypeName = summary.mediaType.localizedName()
            val resolvedTitle =
                remember(summary.title, summary.originalTitle, mediaTypeName) {
                    summary.title?.takeUnless { it.isBlank() }
                        ?: summary.originalTitle?.takeUnless { it.isBlank() }
                        ?: mediaTypeName
                }
            val posterPlaceholder =
                remember(resolvedTitle) {
                    resolvedTitle.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                }
            val resolvedPosterPath =
                remember(summary.posterPath, summary.backdropPath) {
                    summary.posterPath ?: summary.backdropPath
                }
            PosterArtwork(
                posterPath = resolvedPosterPath,
                contentDescription = resolvedTitle,
                placeholderText = posterPlaceholder,
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaTypeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = summary.requestStatus)
                    if (summary.availability.standard != JellyseerrMediaStatus.UNKNOWN) {
                        AvailabilityBadge(summary)
                    }
                }
                val requesterLine =
                    summary.requestedBy?.displayName?.let { requester ->
                        stringResource(Res.string.request_requested_by, requester)
                    }
                val seasonsLine =
                    if (summary.seasons.isNotEmpty()) {
                        summary.localizedSeasons()
                    } else {
                        null
                    }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    requesterLine?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    seasonsLine?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) {
                        ShimmerPlaceholder(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }
        }
        items(5) {
            RequestCardSkeleton()
        }
    }
}

@Composable
private fun RequestCardSkeleton() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterArtwork(
    posterPath: String?,
    contentDescription: String,
    placeholderText: String?,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(backgroundColor) { ColorPainter(backgroundColor) }
    val context = LocalPlatformContext.current
    val imageUrl = remember(posterPath) { tmdbPosterUrl(posterPath) }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter,
            )
        } else if (!placeholderText.isNullOrBlank()) {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: JellyseerrRequestStatus) {
    val colors =
        when (status) {
            JellyseerrRequestStatus.APPROVED,
            JellyseerrRequestStatus.COMPLETED,
            -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            JellyseerrRequestStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            JellyseerrRequestStatus.DECLINED,
            JellyseerrRequestStatus.FAILED,
            -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    StatusTextBadge(
        text = status.localizedLabel(),
        containerColor = colors.first,
        contentColor = colors.second,
    )
}

@Composable
private fun AvailabilityBadge(summary: JellyseerrRequestSummary) {
    val available =
        when (summary.availability.standard) {
            JellyseerrMediaStatus.AVAILABLE -> stringResource(Res.string.request_availability_available)
            JellyseerrMediaStatus.PROCESSING -> stringResource(Res.string.request_availability_processing)
            JellyseerrMediaStatus.PENDING -> stringResource(Res.string.request_availability_pending)
            JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> stringResource(Res.string.request_availability_partial)
            JellyseerrMediaStatus.BLACKLISTED -> stringResource(Res.string.request_availability_blacklisted)
            JellyseerrMediaStatus.DELETED -> stringResource(Res.string.request_availability_deleted)
            else -> stringResource(Res.string.request_availability_unknown)
        }
    StatusTextBadge(
        text = available,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusTextBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EmptyRequestsPlaceholder() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.requests_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.requests_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MissingServerPlaceholder(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.seerr_connect_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.seerr_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddServer) {
            Text(stringResource(Res.string.manage_servers))
        }
    }
}

@Composable
private fun ErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.something_went_wrong),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message.ifBlank { stringResource(Res.string.something_went_wrong) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.retry))
        }
    }
}

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
private fun LinearLoadingIndicator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .height(16.dp)
                    .width(16.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun JellyseerrRequestStatus.localizedLabel(): String =
    when (this) {
        JellyseerrRequestStatus.PENDING -> stringResource(Res.string.request_status_pending)
        JellyseerrRequestStatus.APPROVED -> stringResource(Res.string.request_status_approved)
        JellyseerrRequestStatus.DECLINED -> stringResource(Res.string.request_status_declined)
        JellyseerrRequestStatus.FAILED -> stringResource(Res.string.request_status_failed)
        JellyseerrRequestStatus.COMPLETED -> stringResource(Res.string.request_status_completed)
        JellyseerrRequestStatus.UNKNOWN -> stringResource(Res.string.request_status_unknown)
    }

@Composable
private fun JellyseerrRequestFilter.localizedLabel(): String =
    when (this) {
        JellyseerrRequestFilter.ALL -> stringResource(Res.string.request_filter_all)
        JellyseerrRequestFilter.PENDING -> stringResource(Res.string.request_filter_pending)
        JellyseerrRequestFilter.APPROVED -> stringResource(Res.string.request_filter_approved)
        JellyseerrRequestFilter.PROCESSING -> stringResource(Res.string.request_filter_processing)
        JellyseerrRequestFilter.AVAILABLE -> stringResource(Res.string.request_filter_available)
        JellyseerrRequestFilter.UNAVAILABLE -> stringResource(Res.string.request_filter_unavailable)
        JellyseerrRequestFilter.FAILED -> stringResource(Res.string.request_filter_failed)
        JellyseerrRequestFilter.DELETED -> stringResource(Res.string.request_filter_deleted)
    }

@Composable
private fun JellyseerrMediaType.localizedName(): String =
    when (this) {
        JellyseerrMediaType.MOVIE -> stringResource(Res.string.request_media_movie)
        JellyseerrMediaType.TV -> stringResource(Res.string.request_media_series)
        JellyseerrMediaType.PERSON -> stringResource(Res.string.request_media_person)
        JellyseerrMediaType.COLLECTION -> stringResource(Res.string.request_media_collection)
        JellyseerrMediaType.UNKNOWN -> stringResource(Res.string.request_media_unknown)
    }

@Composable
private fun JellyseerrRequestSummary.localizedSeasons(): String {
    val entries = mutableListOf<String>()
    for (season in seasons) {
        entries +=
            stringResource(
                Res.string.request_season_summary,
                season.seasonNumber,
                season.status.localizedLabel(),
            )
    }
    return stringResource(Res.string.request_seasons_summary, entries.joinToString())
}
