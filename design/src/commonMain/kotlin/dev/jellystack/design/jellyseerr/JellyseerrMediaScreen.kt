@file:Suppress("FunctionName")

package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.jellyseerr.JellyseerrCollection
import dev.jellystack.core.jellyseerr.JellyseerrDetailEnrichmentSection
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfileOption
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.tmdb.TmdbPosterSize
import dev.jellystack.core.tmdb.tmdbPosterUrl
import dev.jellystack.design.TestTags
import dev.jellystack.design.components.ImageTextScrim
import dev.jellystack.design.components.ShimmerPlaceholder
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.navigation.ShellModalOwner
import dev.jellystack.design.theme.JellystackDimens
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.close
import jellystack_mobile.design.generated.resources.detail_load_failed
import jellystack_mobile.design.generated.resources.discover_connect_body
import jellystack_mobile.design.generated.resources.discover_connect_title
import jellystack_mobile.design.generated.resources.discover_radar
import jellystack_mobile.design.generated.resources.discover_refresh_trends
import jellystack_mobile.design.generated.resources.discover_trends
import jellystack_mobile.design.generated.resources.minutes
import jellystack_mobile.design.generated.resources.open_in_seerr
import jellystack_mobile.design.generated.resources.original_language
import jellystack_mobile.design.generated.resources.production_countries
import jellystack_mobile.design.generated.resources.rating_imdb
import jellystack_mobile.design.generated.resources.rating_rt_audience
import jellystack_mobile.design.generated.resources.rating_rt_critics
import jellystack_mobile.design.generated.resources.rating_tmdb
import jellystack_mobile.design.generated.resources.recommendation_availability_description
import jellystack_mobile.design.generated.resources.recommendation_status_description
import jellystack_mobile.design.generated.resources.recommendations_empty
import jellystack_mobile.design.generated.resources.recommendations_popular_movies
import jellystack_mobile.design.generated.resources.recommendations_popular_shows
import jellystack_mobile.design.generated.resources.recommendations_trends
import jellystack_mobile.design.generated.resources.recommendations_upcoming_movies
import jellystack_mobile.design.generated.resources.recommendations_upcoming_shows
import jellystack_mobile.design.generated.resources.release_date
import jellystack_mobile.design.generated.resources.request
import jellystack_mobile.design.generated.resources.request_availability_available
import jellystack_mobile.design.generated.resources.request_availability_blacklisted
import jellystack_mobile.design.generated.resources.request_availability_deleted
import jellystack_mobile.design.generated.resources.request_availability_partial
import jellystack_mobile.design.generated.resources.request_availability_pending
import jellystack_mobile.design.generated.resources.request_availability_processing
import jellystack_mobile.design.generated.resources.request_media_collection
import jellystack_mobile.design.generated.resources.request_media_movie
import jellystack_mobile.design.generated.resources.request_media_person
import jellystack_mobile.design.generated.resources.request_media_series
import jellystack_mobile.design.generated.resources.request_media_unknown
import jellystack_mobile.design.generated.resources.request_status_approved
import jellystack_mobile.design.generated.resources.request_status_completed
import jellystack_mobile.design.generated.resources.request_status_declined
import jellystack_mobile.design.generated.resources.request_status_failed
import jellystack_mobile.design.generated.resources.request_status_pending
import jellystack_mobile.design.generated.resources.request_status_unknown
import jellystack_mobile.design.generated.resources.retry
import jellystack_mobile.design.generated.resources.revenue
import jellystack_mobile.design.generated.resources.runtime
import jellystack_mobile.design.generated.resources.show_less
import jellystack_mobile.design.generated.resources.show_more
import jellystack_mobile.design.generated.resources.sign_in
import jellystack_mobile.design.generated.resources.something_went_wrong
import jellystack_mobile.design.generated.resources.studios
import jellystack_mobile.design.generated.resources.watch_trailer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private data class RecommendationSelection(
    val rail: JellyseerrRecommendationRail,
    val item: JellyseerrSearchItem,
    val position: Int,
)

@Composable
private fun MediaTabsSkeleton(tabCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(tabCount) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(44.dp),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun RecommendationsSkeleton(contentPadding: PaddingValues) {
    val layoutDirection = LocalLayoutDirection.current
    val profile = LocalResponsiveProfile.current
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + profile.horizontalContentPadding,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + profile.horizontalContentPadding,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                shape = RoundedCornerShape(10.dp),
            )
        }
        items(3) {
            RecommendationRailSkeleton()
        }
    }
}

@Composable
private fun RecommendationRailSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(24.dp),
            shape = RoundedCornerShape(8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(6) {
                RecommendationCardSkeleton()
            }
        }
    }
}

@Composable
private fun RecommendationCardSkeleton() {
    Card(
        modifier =
            Modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    132.dp
                } else {
                    148.dp
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
internal fun JellyseerrRecommendationsScreen(
    state: JellyseerrRecommendationsState,
    detailStates: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>,
    onRefresh: () -> Unit,
    onRetryRail: (JellyseerrRecommendationRail) -> Unit,
    onLoadMore: (JellyseerrRecommendationRail) -> Unit,
    onOpenDetails: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onLoadDetail: (JellyseerrSearchItem) -> Unit,
    onRequestOpen: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onTrailer: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int, JellyseerrMediaTrailer?) -> Unit,
    onImpression: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    languageProfiles: JellyseerrLanguageProfiles,
    onAddServer: () -> Unit,
    contentPadding: PaddingValues,
    onShellModalChange: (ShellModalOwner?) -> Unit,
    feedHeader: (@Composable () -> Unit)? = null,
) {
    val isRefreshing =
        when (state) {
            JellyseerrRecommendationsState.Loading -> true
            is JellyseerrRecommendationsState.Ready -> state.rails.values.any { it.isLoading }
            else -> false
        }
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = onRefresh,
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState),
    ) {
        when (state) {
            JellyseerrRecommendationsState.Loading -> RecommendationsSkeleton(contentPadding)

            JellyseerrRecommendationsState.MissingServer ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.discover_connect_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.discover_connect_body),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onAddServer) {
                        Text(stringResource(Res.string.sign_in))
                    }
                }

            is JellyseerrRecommendationsState.Error ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message.ifBlank { stringResource(Res.string.something_went_wrong) },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    ElevatedButton(onClick = onRefresh) {
                        Text(stringResource(Res.string.retry))
                    }
                }

            is JellyseerrRecommendationsState.Ready ->
                RecommendationsContent(
                    rails = state.rails,
                    detailStates = detailStates,
                    onRefresh = onRefresh,
                    onRetryRail = onRetryRail,
                    onLoadMore = onLoadMore,
                    onOpenDetails = onOpenDetails,
                    onLoadDetail = onLoadDetail,
                    onRequestOpen = onRequestOpen,
                    onTrailer = onTrailer,
                    onImpression = onImpression,
                    languageProfiles = languageProfiles,
                    contentPadding = contentPadding,
                    onShellModalChange = onShellModalChange,
                    feedHeader = feedHeader,
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationsContent(
    rails: Map<JellyseerrRecommendationRail, JellyseerrRecommendationRailState>,
    detailStates: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>,
    onRefresh: () -> Unit,
    onRetryRail: (JellyseerrRecommendationRail) -> Unit,
    onLoadMore: (JellyseerrRecommendationRail) -> Unit,
    onOpenDetails: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onLoadDetail: (JellyseerrSearchItem) -> Unit,
    onRequestOpen: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onTrailer: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int, JellyseerrMediaTrailer?) -> Unit,
    onImpression: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    languageProfiles: JellyseerrLanguageProfiles,
    contentPadding: PaddingValues,
    onShellModalChange: (ShellModalOwner?) -> Unit,
    feedHeader: (@Composable () -> Unit)?,
) {
    val layoutDirection = LocalLayoutDirection.current
    val profile = LocalResponsiveProfile.current
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + profile.horizontalContentPadding,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + profile.horizontalContentPadding,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )
    val railStates =
        JellyseerrRecommendationRail.entries.map { rail ->
            rail to (rails[rail] ?: defaultUiRailState(rail))
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        feedHeader?.let { header ->
            item(key = "discover-feed-header") { header() }
        }
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .testTag("recommendationsTopSpacer"),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.discover_radar),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = stringResource(Res.string.discover_trends),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(Res.string.discover_refresh_trends),
                        )
                    }
                }
            }
        }
        railStates.forEach { (rail, state) ->
            item(key = rail.name) {
                RecommendationRail(
                    rail = rail,
                    state = state,
                    onRetry = onRetryRail,
                    onLoadMore = onLoadMore,
                    onCardClick = { selection ->
                        onOpenDetails(selection.rail, selection.item, selection.position)
                    },
                    onCardLongPress = { selection ->
                        onRequestOpen(selection.rail, selection.item, selection.position)
                    },
                    onImpression = onImpression,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationRail(
    rail: JellyseerrRecommendationRail,
    state: JellyseerrRecommendationRailState,
    onRetry: (JellyseerrRecommendationRail) -> Unit,
    onLoadMore: (JellyseerrRecommendationRail) -> Unit,
    onCardClick: (RecommendationSelection) -> Unit,
    onCardLongPress: (RecommendationSelection) -> Unit,
    onImpression: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rail.localizedTitle(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.width(96.dp))
            }
        }
        val message = state.errorMessage
        if (state.items.isEmpty() && message != null && !state.isLoading) {
            OutlinedCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.ifBlank { stringResource(Res.string.something_went_wrong) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    Button(onClick = { onRetry(rail) }) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }
            return
        }
        if (state.items.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(Res.string.recommendations_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
            )
            return
        }
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val trackedIds = remember(state.items) { mutableSetOf<Int>() }
        LaunchedEffect(state.items, state.canLoadMore) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .map { visible: List<LazyListItemInfo> -> visible.map { it.index } }
                .distinctUntilChanged()
                .collectLatest { indices: List<Int> ->
                    if (indices.isEmpty()) return@collectLatest
                    val lastVisible = indices.maxOrNull() ?: return@collectLatest
                    if (state.canLoadMore && !state.isLoading && lastVisible >= state.items.size - 5) {
                        onLoadMore(rail)
                    }
                    indices.forEach { index ->
                        val item = state.items.getOrNull(index) ?: return@forEach
                        if (trackedIds.add(item.tmdbId)) {
                            onImpression(rail, item, index)
                        }
                    }
                }
        }
        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            contentPadding =
                PaddingValues(horizontal = LocalResponsiveProfile.current.horizontalContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
        ) {
            itemsIndexed(state.items, key = { _, item -> item.tmdbId }) { index, item ->
                RecommendationCard(
                    rail = rail,
                    item = item,
                    position = index,
                    onClick = onCardClick,
                    onLongPress = onCardLongPress,
                )
            }
        }
        if (message != null && state.items.isNotEmpty() && !state.isLoading) {
            ElevatedAssistChip(
                onClick = { onRetry(rail) },
                label = { Text(message, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendationCard(
    rail: JellyseerrRecommendationRail,
    item: JellyseerrSearchItem,
    position: Int,
    onClick: (RecommendationSelection) -> Unit,
    onLongPress: (RecommendationSelection) -> Unit,
) {
    val statusLabel = currentRequestLabel(item)
    val availability = availabilityLabel(item).takeIf { statusLabel == null }
    val statusDescription = statusLabel?.let { stringResource(Res.string.recommendation_status_description, it) }
    val availabilityDescription =
        availability?.let { stringResource(Res.string.recommendation_availability_description, it) }
    val cardWidth = 152.dp
    val posterHeight = cardWidth * 1.5f
    val footerHeight = 92.dp
    val cardHeight = posterHeight + footerHeight
    val semanticsDescription =
        buildList {
            add(item.title)
            item.releaseYear?.let { add(it) }
            add(item.mediaType.displayName())
            statusDescription?.let(::add)
            availabilityDescription?.let(::add)
        }.joinToString(", ")
    Card(
        modifier =
            Modifier
                .width(cardWidth)
                .height(cardHeight)
                .semantics { contentDescription = semanticsDescription }
                .combinedClickable(
                    role = Role.Button,
                    onClick = { onClick(RecommendationSelection(rail, item, position)) },
                    onLongClick = { onLongPress(RecommendationSelection(rail, item, position)) },
                ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(posterHeight)) {
                PosterArtwork(
                    posterPath = item.posterPath,
                    contentDescription = item.title,
                    placeholderText =
                        item.title
                            .takeIf { it.isNotBlank() }
                            ?.firstOrNull()
                            ?.toString(),
                    modifier = Modifier.fillMaxSize(),
                )
                (statusLabel ?: availability)?.let { label ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(footerHeight)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        buildString {
                            append(item.mediaType.displayName())
                            item.releaseYear?.let {
                                append(" - ")
                                append(it)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    ElevatedAssistChip(
        onClick = {},
        label = { Text(text = text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        enabled = false,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = containerColor,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = containerColor,
                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .semantics { contentDescription = text },
    )
}

@Composable
internal fun JellyseerrMediaDetailPage(
    item: JellyseerrSearchItem,
    detailState: JellyseerrMediaDetailState?,
    onRetry: () -> Unit,
    onTrailer: (JellyseerrMediaTrailer?) -> Unit,
    onClose: () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedSection: JellyseerrDetailSection = JellyseerrDetailSection.Overview,
    commandState: JellyseerrDetailCommandState = JellyseerrDetailCommandState(),
    enrichment: JellyseerrMediaDetailEnrichment? = null,
    enrichmentLoading: Boolean = false,
    enrichmentLoadingSections: Set<JellyseerrDetailEnrichmentSection> = emptySet(),
    onSectionSelected: (JellyseerrDetailSection) -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    onOverflow: () -> Unit = {},
    onOpenRelatedTitle: (SeerrDetailOrigin, JellyseerrSearchItem) -> Unit = { _, _ -> },
    onOpenCollection: ((JellyseerrCollection) -> Unit)? = null,
    onVideo: (JellyseerrMediaVideo) -> Unit = {},
    onRetryEnrichment: ((JellyseerrDetailEnrichmentSection) -> Unit)? = null,
    listState: LazyListState? = null,
) {
    LaunchedEffect(item.mediaType, item.tmdbId) {
        if (detailState == null) onRetry()
    }
    JellyseerrImmersiveDetailPageContent(
        item = item,
        detailState = detailState,
        selectedSection = selectedSection,
        commandState = commandState,
        onSectionSelected = onSectionSelected,
        onRetry = onRetry,
        onBack = onClose,
        onPrimaryAction = onPrimaryAction,
        onOverflow = onOverflow,
        onOpenRelatedTitle = onOpenRelatedTitle,
        onOpenCollection = onOpenCollection,
        onTrailer = onTrailer,
        onVideo = onVideo,
        modifier = modifier,
        enrichment = enrichment,
        enrichmentLoading = enrichmentLoading,
        enrichmentLoadingSections =
            enrichmentLoadingSections +
                (
                    detailState as?
                        JellyseerrMediaDetailState.Loaded
                )?.enrichmentLoadingSections.orEmpty(),
        onRetryEnrichment = onRetryEnrichment,
        listState = listState,
        supportingActions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationDetailSheet(
    selection: RecommendationSelection,
    detailState: JellyseerrMediaDetailState?,
    languageProfiles: JellyseerrLanguageProfiles,
    onDismiss: () -> Unit,
    onRequestOpen: (RecommendationSelection) -> Unit,
    onRetry: () -> Unit,
    onTrailer: (JellyseerrMediaTrailer?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val item = selection.item
    var selectedSection by
        rememberSaveable(item.mediaType, item.tmdbId) {
            mutableStateOf(JellyseerrDetailSection.Overview)
        }
    LaunchedEffect(selection.item.tmdbId) {
        if (detailState == null) {
            onRetry()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        JellyseerrMediaDetailPage(
            item = item,
            detailState = detailState,
            onRetry = onRetry,
            onTrailer = onTrailer,
            onClose = onDismiss,
            actions = {},
            modifier = Modifier.fillMaxSize(),
            selectedSection = selectedSection,
            onSectionSelected = { selectedSection = it },
            commandState =
                JellyseerrDetailCommandState(
                    primaryActionLabel =
                        stringResource(Res.string.request)
                            .takeUnless { item.isRequested },
                    statusLabel = currentRequestLabel(item) ?: availabilityLabel(item),
                ),
            onPrimaryAction = {
                onRequestOpen(selection)
                onDismiss()
            },
        )
    }
}

@Composable
private fun DetailLoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        PlaceholderLine(widthFraction = 0.9f)
        PlaceholderLine(widthFraction = 0.8f)
        PlaceholderLine(widthFraction = 0.6f)
    }
}

@Composable
private fun DetailErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.detail_load_failed),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message.ifBlank { stringResource(Res.string.something_went_wrong) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
private fun DetailContent(
    detail: JellyseerrMediaDetail,
    displayTitle: String,
    displayYear: String?,
    runtimeLabel: String?,
    uriHandler: UriHandler,
    onTrailer: (JellyseerrMediaTrailer?) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val metadataItems =
        listOfNotNull(
            stringResource(Res.string.release_date) to detail.releaseDate,
            stringResource(Res.string.runtime) to runtimeLabel,
            stringResource(Res.string.revenue) to formatRevenue(detail.revenue),
            stringResource(Res.string.original_language) to formatLanguage(detail.originalLanguage),
            stringResource(Res.string.production_countries) to joinReadable(detail.productionCountries),
            stringResource(Res.string.studios) to joinReadable(detail.studios),
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(JellystackDimens.railSpacing),
    ) {
        BackdropBanner(
            backdropPath = detail.backdropPath ?: detail.posterPath,
            contentDescription = displayTitle,
            onClose = onClose,
        )
        TitleBlock(title = displayTitle, year = displayYear)
        CompactHeaderRow(
            ratings = detail.ratings,
            genres = detail.genres,
        )
        OverviewBlock(text = detail.overview)
        MetadataCard(items = metadataItems)
        WatchTrailerButton(
            trailer = detail.trailer,
            uriHandler = uriHandler,
            onTrailer = onTrailer,
        )
        OpenInSeerrButton(
            url = detail.jellyseerrUrl,
            uriHandler = uriHandler,
        )
    }
}

@Composable
private fun BackdropBanner(
    backdropPath: String?,
    contentDescription: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val background = MaterialTheme.colorScheme.surfaceVariant
    val placeholderColor = remember(background) { ColorPainter(background) }
    val context = LocalPlatformContext.current
    val request =
        remember(backdropPath) {
            backdropPath?.let { path ->
                tmdbPosterUrl(path, TmdbPosterSize.ORIGINAL)?.let { url ->
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build()
                }
            }
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(background)
                .testTag(TestTags.RECOMMENDATION_BACKDROP),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholderColor,
                error = placeholderColor,
            )
        } else {
            Text(
                text = contentDescription.takeIf { it.isNotBlank() }?.firstOrNull()?.toString() ?: "",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ImageTextScrim(modifier = Modifier.fillMaxSize())
        onClose?.let {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 3.dp,
            ) {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.close),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCloseHeader(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.close),
            )
        }
    }
}

@Composable
private fun TitleBlock(
    title: String,
    year: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        year?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactHeaderRow(
    ratings: JellyseerrMediaRatings?,
    genres: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ratings?.let { RatingsRow(it) }
        if (genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.forEach { genre ->
                    GenrePill(genre)
                }
            }
        }
    }
}

@Composable
private fun GenrePill(genre: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = genre,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun OverviewBlock(text: String?) {
    val overview = text?.takeIf { it.isNotBlank() } ?: return
    var expanded by remember { mutableStateOf(false) }
    val maxLines = if (expanded) Int.MAX_VALUE else 5
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (overview.length > 240) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) Res.string.show_less else Res.string.show_more,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(items: List<Pair<String, String?>>) {
    val visibleItems = items.filter { !it.second.isNullOrBlank() }
    if (visibleItems.isEmpty()) {
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(JellystackDimens.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleItems.forEach { (label, value) ->
                InfoRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun WatchTrailerButton(
    trailer: JellyseerrMediaTrailer?,
    uriHandler: UriHandler,
    onTrailer: (JellyseerrMediaTrailer) -> Unit,
) {
    val current = trailer?.takeIf { it.key?.isNotBlank() == true } ?: return
    ElevatedButton(
        onClick = {
            onTrailer(current)
            openTrailer(uriHandler, current)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.watch_trailer))
    }
}

@Composable
private fun OpenInSeerrButton(
    url: String?,
    uriHandler: UriHandler,
) {
    val target = url?.takeIf { it.isNotBlank() } ?: return
    OutlinedButton(
        onClick = { runCatching { uriHandler.openUri(target) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.open_in_seerr))
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) {
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.widthIn(min = 120.dp, max = 180.dp),
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingsRow(ratings: JellyseerrMediaRatings) {
    val chips =
        listOfNotNull(
            ratings.tmdb?.let { stringResource(Res.string.rating_tmdb, formatScore(it)) },
            ratings.imdb?.let { stringResource(Res.string.rating_imdb, formatScore(it)) },
            ratings.rottenTomatoesCritics?.let { stringResource(Res.string.rating_rt_critics, formatPercent(it)) },
            ratings.rottenTomatoesAudience?.let { stringResource(Res.string.rating_rt_audience, formatPercent(it)) },
        )
    if (chips.isEmpty()) {
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(TestTags.SEERR_RATINGS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { index, label ->
            val colors =
                when (index) {
                    0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                }
            RatingPill(
                label = label,
                containerColor = colors.first,
                contentColor = colors.second,
            )
        }
    }
}

@Composable
private fun RatingPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaceholderLine(
    widthFraction: Float,
    height: Dp = 12.dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun formatRuntime(minutes: Int?): String? {
    val total = minutes ?: return null
    if (total <= 0) return null
    return stringResource(Res.string.minutes, total)
}

private fun formatRevenue(revenue: Long?): String? {
    val value = revenue ?: return null
    return when {
        value >= 1_000_000_000L -> "$${value / 1_000_000_000}B"
        value >= 1_000_000L -> "$${value / 1_000_000}M"
        value >= 1_000L -> "$${value / 1_000}K"
        value > 0L -> "$$value"
        else -> null
    }
}

private fun formatLanguage(code: String?): String? = code?.takeIf { it.isNotBlank() }?.uppercase()

private fun joinReadable(values: List<String>): String? = values.filter { it.isNotBlank() }.ifEmpty { null }?.joinToString(" - ")

private fun formatScore(score: Double): String = ((score * 10.0).roundToInt() / 10.0).toString()

private fun formatPercent(score: Double): String = "${score.roundToInt()}%"

/**
 * Open the trailer in the YouTube app when available, falling back to the browser.
 *
 * Tries the custom-scheme [vnd.youtube] intent first; if the platform reports the URI
 * cannot be opened (no handler installed, etc.), it falls back to the public
 * `youtube.com/watch` URL so the user can still play the trailer.
 */
private fun openTrailer(
    uriHandler: UriHandler,
    trailer: JellyseerrMediaTrailer,
) {
    val key = trailer.key?.takeIf { it.isNotBlank() } ?: return
    runCatching { uriHandler.openUri("vnd.youtube://$key") }
        .recoverCatching { uriHandler.openUri("https://www.youtube.com/watch?v=$key") }
}

@Composable
private fun PosterArtwork(
    posterPath: String?,
    contentDescription: String,
    placeholderText: String?,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val background = MaterialTheme.colorScheme.surfaceVariant
    val placeholderColor = remember(background) { ColorPainter(background) }
    val context = LocalPlatformContext.current
    val request =
        remember(posterPath) {
            posterPath?.let { path ->
                ImageRequest
                    .Builder(context)
                    .data(tmdbPosterUrl(path))
                    .crossfade(true)
                    .build()
            }
        }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = request,
                contentDescription = contentDescription,
                placeholder = placeholderColor,
                error = placeholderColor,
            )
        } else if (!placeholderText.isNullOrBlank()) {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun profilesFor(
    item: JellyseerrSearchItem,
    profiles: JellyseerrLanguageProfiles,
): List<JellyseerrLanguageProfileOption> =
    when (item.mediaType) {
        JellyseerrMediaType.MOVIE -> profiles.movies
        JellyseerrMediaType.TV -> profiles.tv
        else -> emptyList()
    }

@Composable
internal fun availabilityLabel(item: JellyseerrSearchItem): String? {
    val status = item.availability.standard ?: return null
    return when (status) {
        JellyseerrMediaStatus.AVAILABLE -> stringResource(Res.string.request_availability_available)
        JellyseerrMediaStatus.PROCESSING -> stringResource(Res.string.request_availability_processing)
        JellyseerrMediaStatus.PENDING -> stringResource(Res.string.request_availability_pending)
        JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> stringResource(Res.string.request_availability_partial)
        JellyseerrMediaStatus.BLACKLISTED -> stringResource(Res.string.request_availability_blacklisted)
        JellyseerrMediaStatus.DELETED -> stringResource(Res.string.request_availability_deleted)
        JellyseerrMediaStatus.UNKNOWN -> null
    }
}

@Composable
internal fun currentRequestLabel(item: JellyseerrSearchItem): String? {
    val request = item.requests.firstOrNull() ?: return null
    return request.requestStatus.label()
}

@Composable
private fun JellyseerrRequestStatus.label(): String =
    when (this) {
        JellyseerrRequestStatus.PENDING -> stringResource(Res.string.request_status_pending)
        JellyseerrRequestStatus.APPROVED -> stringResource(Res.string.request_status_approved)
        JellyseerrRequestStatus.DECLINED -> stringResource(Res.string.request_status_declined)
        JellyseerrRequestStatus.FAILED -> stringResource(Res.string.request_status_failed)
        JellyseerrRequestStatus.COMPLETED -> stringResource(Res.string.request_status_completed)
        JellyseerrRequestStatus.UNKNOWN -> stringResource(Res.string.request_status_unknown)
    }

@Composable
private fun JellyseerrMediaType.displayName(): String =
    when (this) {
        JellyseerrMediaType.MOVIE -> stringResource(Res.string.request_media_movie)
        JellyseerrMediaType.TV -> stringResource(Res.string.request_media_series)
        JellyseerrMediaType.PERSON -> stringResource(Res.string.request_media_person)
        JellyseerrMediaType.COLLECTION -> stringResource(Res.string.request_media_collection)
        JellyseerrMediaType.UNKNOWN -> stringResource(Res.string.request_media_unknown)
    }

@Composable
private fun JellyseerrRecommendationRail.localizedTitle(): String =
    stringResource(
        when (this) {
            JellyseerrRecommendationRail.TRENDS -> Res.string.recommendations_trends
            JellyseerrRecommendationRail.POPULAR_MOVIES -> Res.string.recommendations_popular_movies
            JellyseerrRecommendationRail.POPULAR_SHOWS -> Res.string.recommendations_popular_shows
            JellyseerrRecommendationRail.UPCOMING_MOVIES -> Res.string.recommendations_upcoming_movies
            JellyseerrRecommendationRail.UPCOMING_SHOWS -> Res.string.recommendations_upcoming_shows
        },
    )

private fun defaultUiRailState(rail: JellyseerrRecommendationRail): JellyseerrRecommendationRailState =
    JellyseerrRecommendationRailState(
        rail = rail,
        items = emptyList(),
        isLoading = false,
        errorMessage = null,
        canLoadMore = true,
        nextPage = 1,
        lastUpdated = null,
        isStale = false,
    )
