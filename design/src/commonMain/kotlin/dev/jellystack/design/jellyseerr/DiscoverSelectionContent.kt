package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrDetailEnrichmentSection
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestVariant
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.close
import jellystack_mobile.design.generated.resources.open_in_seerr
import jellystack_mobile.design.generated.resources.request
import jellystack_mobile.design.generated.resources.request_action_approve
import jellystack_mobile.design.generated.resources.request_action_approve_confirm
import jellystack_mobile.design.generated.resources.request_action_delete
import jellystack_mobile.design.generated.resources.request_action_delete_confirm
import jellystack_mobile.design.generated.resources.request_action_delete_media
import jellystack_mobile.design.generated.resources.request_action_delete_media_confirm
import jellystack_mobile.design.generated.resources.request_detail_load_failed
import jellystack_mobile.design.generated.resources.request_more_seasons
import jellystack_mobile.design.generated.resources.request_permission_denied
import jellystack_mobile.design.generated.resources.request_status_approved
import jellystack_mobile.design.generated.resources.request_status_completed
import jellystack_mobile.design.generated.resources.request_status_declined
import jellystack_mobile.design.generated.resources.request_status_failed
import jellystack_mobile.design.generated.resources.request_status_pending
import jellystack_mobile.design.generated.resources.request_status_unknown
import jellystack_mobile.design.generated.resources.retry
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DiscoverSelectionContent(
    state: DiscoverUiState,
    detailStates: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>,
    languageProfiles: JellyseerrLanguageProfiles,
    requests: List<JellyseerrRequestSummary> = emptyList(),
    currentRequestsByMedia: Map<Pair<JellyseerrMediaType, Int>, JellyseerrRequestSummary> = emptyMap(),
    liveRequestStateAvailable: Boolean = false,
    capabilities: JellyseerrRequestCapabilities = JellyseerrRequestCapabilities.ALL,
    onSelectProfile: (JellyseerrRequestProfileSelection) -> Unit,
    onSelectVariant: (JellyseerrRequestVariant) -> Unit = {},
    onSelectSeasons: (JellyseerrCreateSelection) -> Unit,
    onSubmit: (JellyseerrSearchItem, JellyseerrRequestProfileSelection, JellyseerrCreateSelection?) -> Unit,
    onSubmitVariant: (
        JellyseerrSearchItem,
        JellyseerrRequestProfileSelection,
        JellyseerrCreateSelection?,
        JellyseerrRequestVariant,
    ) -> Unit = { item, profile, seasons, _ -> onSubmit(item, profile, seasons) },
    onApprove: (JellyseerrRequestSummary) -> Unit,
    onDelete: (JellyseerrRequestSummary) -> Unit,
    onRemoveMedia: (JellyseerrRequestSummary) -> Unit,
    onConfigureRequest: () -> Unit,
    onCloseRequestConfiguration: () -> Unit,
    onRetryDetail: (JellyseerrSearchItem) -> Unit = {},
    onRetryEnrichment: (JellyseerrSearchItem, JellyseerrDetailEnrichmentSection) -> Unit = { _, _ -> },
    onOpenRelatedDetail: (SeerrDetailOrigin, JellyseerrSearchItem) -> Unit = { _, _ -> },
    onDetailViewStateChange: (SeerrDetailKey, SeerrDetailViewState) -> Unit = { _, _ -> },
    onTrailer: (SeerrDetailEntry, JellyseerrMediaTrailer?) -> Unit = { _, _ -> },
    isAdmin: Boolean = false,
    currentUserId: Int? = null,
    pendingApprovals: Set<Int> = emptySet(),
    onClose: () -> Unit,
    initialFocusModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val entry = state.selected ?: return
    val request =
        resolveSeerrDetailRequest(
            entry = entry,
            requests = requests,
            currentRequestsByMedia = currentRequestsByMedia,
            liveRequestStateAvailable = liveRequestStateAvailable,
        )
    val item =
        entry.item.copy(
            availability =
                resolveSeerrDetailAvailability(
                    entry = entry,
                    request = request,
                    liveRequestStateAvailable = liveRequestStateAvailable,
                ),
            requests = listOfNotNull(request),
        )
    val detailState = detailStates[entry.key.mediaType to entry.key.tmdbId]

    key(entry.key) {
        DetailEntryContent(
            entry = entry,
            item = item,
            request = request,
            detailState = detailState,
            capabilities = capabilities,
            onApprove = onApprove,
            onDelete = onDelete,
            onRemoveMedia = onRemoveMedia,
            onConfigureRequest = {
                val remaining =
                    request
                        ?.let {
                            requestableSeasons(
                                availableSeasons =
                                    (detailState as? JellyseerrMediaDetailState.Loaded)
                                        ?.detail
                                        ?.availableSeasons
                                        .orEmpty(),
                                summary = it,
                            )
                        }.orEmpty()
                if (remaining.isNotEmpty()) {
                    onSelectSeasons(JellyseerrCreateSelection.Seasons(remaining))
                }
                onConfigureRequest()
            },
            onRetryDetail = { onRetryDetail(item) },
            onRetryEnrichment = { section -> onRetryEnrichment(item, section) },
            onOpenRelatedDetail = onOpenRelatedDetail,
            onDetailViewStateChange = onDetailViewStateChange,
            onTrailer = onTrailer,
            onClose = onClose,
            isAdmin = isAdmin,
            currentUserId = currentUserId,
            pendingApprovals = pendingApprovals,
            modifier = modifier,
        )
    }

    if (state.isRequestConfigurationOpen) {
        RequestItemConfiguration(
            item = item,
            request = request,
            state = state,
            detailState = detailState,
            languageProfiles = languageProfiles,
            capabilities = capabilities,
            onSelectProfile = onSelectProfile,
            onSelectVariant = onSelectVariant,
            onSelectSeasons = onSelectSeasons,
            onSubmit = onSubmitVariant,
            onClose = onCloseRequestConfiguration,
            onRetryDetail = onRetryDetail,
            initialFocusModifier = initialFocusModifier,
        )
    }
}

@Composable
private fun DetailEntryContent(
    entry: SeerrDetailEntry,
    item: JellyseerrSearchItem,
    request: JellyseerrRequestSummary?,
    detailState: JellyseerrMediaDetailState?,
    capabilities: JellyseerrRequestCapabilities,
    onApprove: (JellyseerrRequestSummary) -> Unit,
    onDelete: (JellyseerrRequestSummary) -> Unit,
    onRemoveMedia: (JellyseerrRequestSummary) -> Unit,
    onConfigureRequest: () -> Unit,
    onRetryDetail: () -> Unit,
    onRetryEnrichment: (JellyseerrDetailEnrichmentSection) -> Unit,
    onOpenRelatedDetail: (SeerrDetailOrigin, JellyseerrSearchItem) -> Unit,
    onDetailViewStateChange: (SeerrDetailKey, SeerrDetailViewState) -> Unit,
    onTrailer: (SeerrDetailEntry, JellyseerrMediaTrailer?) -> Unit,
    onClose: () -> Unit,
    isAdmin: Boolean,
    currentUserId: Int?,
    pendingApprovals: Set<Int>,
    modifier: Modifier,
) {
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = entry.viewState.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = entry.viewState.firstVisibleItemScrollOffset,
        )
    LaunchedEffect(listState, entry.key, entry.viewState.selectedSection) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged()
            .collect { (index, offset) ->
                onDetailViewStateChange(
                    entry.key,
                    entry.viewState.copy(
                        firstVisibleItemIndex = index,
                        firstVisibleItemScrollOffset = offset,
                    ),
                )
            }
    }

    val availableSeasons =
        (detailState as? JellyseerrMediaDetailState.Loaded)
            ?.detail
            ?.availableSeasons
            .orEmpty()
    val remainingSeasons =
        request?.let { requestableSeasons(availableSeasons, it) }
            ?: availableSeasons
    val mediaStatus = item.availability.standard
    val requestCommand =
        resolveSeerrRequestCommand(
            mediaType = item.mediaType,
            mediaStatus = mediaStatus,
            hasRequest = request != null,
            requestableSeasons = remainingSeasons,
            capabilities = capabilities,
        )
    val status =
        if (requestCommand.permissionDenied) {
            stringResource(Res.string.request_permission_denied)
        } else if (requestCommand.showStatus) {
            availabilityLabel(item)
                ?: request?.requestStatus?.localizedLabel()
        } else {
            null
        }
    val primaryAction =
        when (requestCommand.primaryAction) {
            SeerrPrimaryAction.Request -> stringResource(Res.string.request)
            SeerrPrimaryAction.RequestMoreSeasons ->
                stringResource(Res.string.request_more_seasons)
            null -> null
        }
    val loaded = detailState as? JellyseerrMediaDetailState.Loaded
    val jellyseerrUrl = loaded?.detail?.jellyseerrUrl?.takeIf(String::isNotBlank)
    val canApprove =
        isAdmin &&
            request?.requestStatus == JellyseerrRequestStatus.PENDING &&
            request.id !in pendingApprovals
    val canRemoveMedia = isAdmin && request?.canRemoveFromService == true
    val canDeleteRequest = canDeleteSeerrRequest(request, isAdmin, currentUserId)
    val showOverflow =
        jellyseerrUrl != null || canApprove || canRemoveMedia || canDeleteRequest
    var overflowExpanded by remember(entry.key) { mutableStateOf(false) }
    var confirmation by remember(entry.key) { mutableStateOf<ManagementConfirmation?>(null) }
    val uriHandler = LocalUriHandler.current

    Box(modifier = modifier.fillMaxSize()) {
        JellyseerrMediaDetailPage(
            item = item,
            detailState = detailState,
            onRetry = onRetryDetail,
            onTrailer = { trailer ->
                handleSeerrTrailerSelection(
                    entry = entry,
                    trailer = trailer,
                    openUri = uriHandler::openUri,
                    onTrailer = onTrailer,
                )
            },
            onClose = onClose,
            actions = {},
            modifier = Modifier.fillMaxSize(),
            selectedSection = entry.viewState.selectedSection,
            onSectionSelected = { section ->
                onDetailViewStateChange(
                    entry.key,
                    entry.viewState.copy(selectedSection = section),
                )
            },
            commandState =
                JellyseerrDetailCommandState(
                    primaryActionLabel = primaryAction,
                    statusLabel = status,
                    showOverflow = showOverflow,
                ),
            onPrimaryAction = onConfigureRequest,
            onOverflow = { overflowExpanded = true },
            onOpenRelatedTitle = onOpenRelatedDetail,
            onVideo = { video ->
                handleSeerrVideoSelection(
                    video = video,
                    openUri = uriHandler::openUri,
                )
            },
            enrichment = loaded?.detail?.enrichment,
            enrichmentLoadingSections = loaded?.enrichmentLoadingSections.orEmpty(),
            onRetryEnrichment = onRetryEnrichment,
            listState = listState,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 24.dp),
        ) {
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                jellyseerrUrl?.let { url ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.open_in_seerr)) },
                        onClick = {
                            overflowExpanded = false
                            runCatching { uriHandler.openUri(url) }
                        },
                    )
                }
                if (canApprove) {
                    val summary = requireNotNull(request)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.request_action_approve)) },
                        onClick = {
                            overflowExpanded = false
                            confirmation = ManagementConfirmation.Approve(summary)
                        },
                    )
                }
                if (canRemoveMedia) {
                    val summary = requireNotNull(request)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.request_action_delete_media)) },
                        onClick = {
                            overflowExpanded = false
                            confirmation = ManagementConfirmation.RemoveMedia(summary)
                        },
                    )
                }
                if (canDeleteRequest) {
                    val summary = requireNotNull(request)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.request_action_delete)) },
                        onClick = {
                            overflowExpanded = false
                            confirmation = ManagementConfirmation.Delete(summary)
                        },
                    )
                }
            }
        }
    }

    confirmation?.let { pending ->
        val actionLabel =
            when (pending) {
                is ManagementConfirmation.Approve ->
                    stringResource(Res.string.request_action_approve)
                is ManagementConfirmation.Delete ->
                    stringResource(Res.string.request_action_delete)
                is ManagementConfirmation.RemoveMedia ->
                    stringResource(Res.string.request_action_delete_media)
            }
        val body =
            when (pending) {
                is ManagementConfirmation.Approve ->
                    stringResource(Res.string.request_action_approve_confirm)
                is ManagementConfirmation.Delete ->
                    stringResource(Res.string.request_action_delete_confirm)
                is ManagementConfirmation.RemoveMedia ->
                    stringResource(Res.string.request_action_delete_media_confirm)
            }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(actionLabel) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        when (pending) {
                            is ManagementConfirmation.Approve -> onApprove(pending.summary)
                            is ManagementConfirmation.Delete -> onDelete(pending.summary)
                            is ManagementConfirmation.RemoveMedia -> onRemoveMedia(pending.summary)
                        }
                    },
                ) {
                    Text(actionLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestItemConfiguration(
    item: JellyseerrSearchItem,
    request: JellyseerrRequestSummary?,
    state: DiscoverUiState,
    detailState: JellyseerrMediaDetailState?,
    languageProfiles: JellyseerrLanguageProfiles,
    capabilities: JellyseerrRequestCapabilities,
    onSelectProfile: (JellyseerrRequestProfileSelection) -> Unit,
    onSelectVariant: (JellyseerrRequestVariant) -> Unit,
    onSelectSeasons: (JellyseerrCreateSelection) -> Unit,
    onSubmit: (
        JellyseerrSearchItem,
        JellyseerrRequestProfileSelection,
        JellyseerrCreateSelection?,
        JellyseerrRequestVariant,
    ) -> Unit,
    onClose: () -> Unit,
    onRetryDetail: (JellyseerrSearchItem) -> Unit,
    initialFocusModifier: Modifier,
) {
    if (item.mediaType == JellyseerrMediaType.TV && detailState !is JellyseerrMediaDetailState.Loaded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (detailState) {
                    is JellyseerrMediaDetailState.Error -> {
                        Text(
                            stringResource(Res.string.request_detail_load_failed),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(detailState.message, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { onRetryDetail(item) },
                            modifier = initialFocusModifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                    else -> {
                        CircularProgressIndicator()
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.close))
                }
            }
        }
        return
    }
    val profiles =
        when (item.mediaType) {
            JellyseerrMediaType.MOVIE -> languageProfiles.movies
            JellyseerrMediaType.TV -> languageProfiles.tv
            else -> emptyList()
        }
    val availableSeasons =
        (detailState as? JellyseerrMediaDetailState.Loaded)
            ?.detail
            ?.availableSeasons
            .orEmpty()
    val remainingSeasons =
        request?.let { requestableSeasons(availableSeasons, it) }
            ?: availableSeasons
    RequestConfiguration(
        item = item,
        profiles = profiles,
        availableSeasons = remainingSeasons,
        selected = state.pendingProfileSelection,
        seasonSelection = state.pendingSeasonSelection,
        capabilities = capabilities,
        variant = state.pendingRequestVariant,
        requestAllAvailableSeasonsExplicitly = request != null,
        isSubmitting =
            state.pendingOperation is DiscoverPendingOperation.Submit &&
                state.pendingOperation.mediaType == item.mediaType &&
                state.pendingOperation.tmdbId == item.tmdbId,
        onSelect = onSelectProfile,
        onSelectVariant = onSelectVariant,
        onSelectSeasons = onSelectSeasons,
        onSubmit = {
            onSubmit(
                item,
                state.pendingProfileSelection,
                it,
                state.pendingRequestVariant,
            )
        },
        onClose = onClose,
        initialFocusModifier = initialFocusModifier,
    )
}

private sealed interface ManagementConfirmation {
    val summary: JellyseerrRequestSummary

    data class Approve(
        override val summary: JellyseerrRequestSummary,
    ) : ManagementConfirmation

    data class Delete(
        override val summary: JellyseerrRequestSummary,
    ) : ManagementConfirmation

    data class RemoveMedia(
        override val summary: JellyseerrRequestSummary,
    ) : ManagementConfirmation
}

internal fun requestableSeasons(
    availableSeasons: List<Int>,
    summary: JellyseerrRequestSummary,
): List<Int> {
    if (summary.mediaType != JellyseerrMediaType.TV) return emptyList()
    val requested = summary.seasons.mapTo(mutableSetOf()) { it.seasonNumber }
    return availableSeasons.distinct().sorted().filterNot(requested::contains)
}

internal fun resolveSeerrDetailRequest(
    entry: SeerrDetailEntry,
    requests: List<JellyseerrRequestSummary>,
    currentRequestsByMedia: Map<Pair<JellyseerrMediaType, Int>, JellyseerrRequestSummary>,
    liveRequestStateAvailable: Boolean = false,
): JellyseerrRequestSummary? =
    if (entry.origin == SeerrDetailOrigin.Requests) {
        entry.requestId?.let { requestId ->
            requests.firstOrNull { it.id == requestId }
                ?: currentRequestsByMedia[entry.key.mediaType to entry.key.tmdbId]
                    ?.takeIf { it.id == requestId }
                ?: entry.item.requests
                    .firstOrNull { it.id == requestId }
                    ?.takeUnless { liveRequestStateAvailable }
        }
    } else {
        currentRequestsByMedia[entry.key.mediaType to entry.key.tmdbId]
            ?: requests.firstOrNull {
                it.tmdbId == entry.key.tmdbId &&
                    it.mediaType == entry.key.mediaType
            }
            ?: entry.item.requests
                .firstOrNull()
                ?.takeUnless { liveRequestStateAvailable }
    }

internal fun canDeleteSeerrRequest(
    request: JellyseerrRequestSummary?,
    isAdmin: Boolean,
    currentUserId: Int?,
): Boolean =
    request != null &&
        (
            isAdmin ||
                currentUserId != null &&
                request.requestedBy?.id == currentUserId
        )

internal fun resolveSeerrDetailAvailability(
    entry: SeerrDetailEntry,
    request: JellyseerrRequestSummary?,
    liveRequestStateAvailable: Boolean,
): JellyseerrMediaAvailability =
    if (
        liveRequestStateAvailable &&
        request == null &&
        (
            entry.origin == SeerrDetailOrigin.Requests ||
                entry.item.requests.isNotEmpty()
        )
    ) {
        JellyseerrMediaAvailability(standard = null, `4k` = null)
    } else {
        request?.availability ?: entry.item.availability
    }

internal fun handleSeerrTrailerSelection(
    entry: SeerrDetailEntry,
    trailer: JellyseerrMediaTrailer?,
    openUri: (String) -> Unit,
    onTrailer: (SeerrDetailEntry, JellyseerrMediaTrailer?) -> Unit,
) {
    openFirstAvailableUri(trailer?.externalPlaybackUris().orEmpty(), openUri)
    onTrailer(entry, trailer)
}

internal fun handleSeerrVideoSelection(
    video: JellyseerrMediaVideo,
    openUri: (String) -> Unit,
) {
    openFirstAvailableUri(video.externalPlaybackUris(), openUri)
}

private fun JellyseerrMediaTrailer.externalPlaybackUris(): List<String> = externalPlaybackUris(url = url, site = site, key = key)

private fun JellyseerrMediaVideo.externalPlaybackUris(): List<String> = externalPlaybackUris(url = url, site = site, key = key)

private fun externalPlaybackUris(
    url: String?,
    site: String?,
    key: String?,
): List<String> =
    buildList {
        url?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        val youtubeKey =
            key
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.takeIf {
                    site.equals("YouTube", ignoreCase = true) ||
                        (site.isNullOrBlank() && url.isNullOrBlank())
                }
        if (youtubeKey != null) {
            add("vnd.youtube://$youtubeKey")
            add("https://www.youtube.com/watch?v=$youtubeKey")
        }
    }.distinct()

private fun openFirstAvailableUri(
    candidates: List<String>,
    openUri: (String) -> Unit,
) {
    candidates.firstOrNull { candidate ->
        runCatching { openUri(candidate) }.isSuccess
    }
}

internal fun JellyseerrRequestSummary.toSearchItemOrNull(): JellyseerrSearchItem? {
    val resolvedTmdbId = tmdbId ?: return null
    val resolvedTitle = title ?: originalTitle ?: return null
    return JellyseerrSearchItem(
        tmdbId = resolvedTmdbId,
        mediaType = mediaType,
        title = resolvedTitle,
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
