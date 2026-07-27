package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.ShellModalOwner
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.search_and_requests
import jellystack_mobile.design.generated.resources.search_and_requests_supporting
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    recommendationsState: JellyseerrRecommendationsState,
    recommendationDetails: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>,
    requestsState: JellyseerrRequestsState,
    languageProfiles: JellyseerrLanguageProfiles,
    contentPadding: PaddingValues,
    onAction: (DiscoverAction) -> Unit,
    onRecommendationsRefresh: () -> Unit,
    onRecommendationsRetry: (JellyseerrRecommendationRail) -> Unit,
    onRecommendationsLoadNext: (JellyseerrRecommendationRail) -> Unit,
    onRecommendationLoadDetail: (JellyseerrSearchItem) -> Unit,
    onRecommendationOpenDetails: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onRecommendationRequestOpen: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onRecommendationTrailer: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int, JellyseerrMediaTrailer?) -> Unit,
    onRecommendationImpression: (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit,
    onClearSearch: () -> Unit,
    onAddServer: () -> Unit,
    onShellModalChange: (ShellModalOwner?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    val hidePrimarySemantics = state.selected != null && !LocalResponsiveProfile.current.isExpanded
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (hidePrimarySemantics) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                ),
    ) {
        holder.SaveableStateProvider(state.destination.name) {
            when (state.destination) {
                DiscoverDestination.Feed ->
                    JellyseerrRecommendationsScreen(
                        state = recommendationsState,
                        detailStates = recommendationDetails,
                        onRefresh = onRecommendationsRefresh,
                        onRetryRail = onRecommendationsRetry,
                        onLoadMore = onRecommendationsLoadNext,
                        onOpenDetails = { rail, item, position ->
                            onAction(DiscoverAction.SelectRecommendation(rail, item, position))
                            onRecommendationOpenDetails(rail, item, position)
                        },
                        onLoadDetail = onRecommendationLoadDetail,
                        onRequestOpen = { rail, item, position ->
                            onAction(DiscoverAction.SelectRecommendation(rail, item, position))
                            onAction(DiscoverAction.OpenRequestConfiguration)
                            onRecommendationRequestOpen(rail, item, position)
                        },
                        onTrailer = onRecommendationTrailer,
                        onImpression = onRecommendationImpression,
                        languageProfiles = languageProfiles,
                        onAddServer = onAddServer,
                        contentPadding = contentPadding,
                        onShellModalChange = onShellModalChange,
                        feedHeader = {
                            SearchAndRequestsCard(
                                onClick = { onAction(DiscoverAction.OpenRequests) },
                            )
                        },
                    )

                DiscoverDestination.Requests ->
                    JellyseerrRequestsScreen(
                        state =
                            if (requestsState is JellyseerrRequestsState.Ready) {
                                requestsState.copy(
                                    query = state.requestQuery,
                                    filter = state.requestFilter,
                                )
                            } else {
                                requestsState
                            },
                        contentPadding = contentPadding,
                        onBackToDiscover = { onAction(DiscoverAction.BackToFeed) },
                        onSelectSearchResult = { onAction(DiscoverAction.SelectSearchResult(it)) },
                        onSelectExisting = { onAction(DiscoverAction.SelectExistingRequest(it)) },
                        onSearch = { onAction(DiscoverAction.RequestQueryChanged(it)) },
                        onClearSearch = onClearSearch,
                        onSelectFilter = { onAction(DiscoverAction.RequestFilterChanged(it)) },
                        onRefresh = { onAction(DiscoverAction.RefreshRequestStatus) },
                        onAddServer = onAddServer,
                    )
            }
        }
    }
}

@Composable
private fun SearchAndRequestsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.search_and_requests),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(Res.string.search_and_requests_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
