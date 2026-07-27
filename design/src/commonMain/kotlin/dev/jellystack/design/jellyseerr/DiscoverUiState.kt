package dev.jellystack.design.jellyseerr

import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMessageCode
import dev.jellystack.core.jellyseerr.JellyseerrOperationKey
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.design.navigation.DiscoverDestination

internal data class SeerrDetailKey(
    val mediaType: JellyseerrMediaType,
    val tmdbId: Int,
)

internal enum class SeerrDetailOrigin {
    Trends,
    Search,
    Requests,
    Similar,
    Recommendations,
}

internal data class SeerrDetailViewState(
    val selectedSection: JellyseerrDetailSection = JellyseerrDetailSection.Overview,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

internal enum class SeerrPrimaryAction {
    Request,
    RequestMoreSeasons,
}

internal data class SeerrRequestCommand(
    val primaryAction: SeerrPrimaryAction?,
    val showStatus: Boolean,
)

internal data class SeerrDetailEntry(
    val key: SeerrDetailKey,
    val item: JellyseerrSearchItem,
    val origin: SeerrDetailOrigin,
    val requestId: Int? = null,
    val recommendationRail: JellyseerrRecommendationRail? = null,
    val recommendationPosition: Int? = null,
    val viewState: SeerrDetailViewState = SeerrDetailViewState(),
)

internal sealed interface DiscoverPendingOperation {
    val operationKey: JellyseerrOperationKey

    data class Submit(
        val mediaType: JellyseerrMediaType,
        val tmdbId: Int,
    ) : DiscoverPendingOperation {
        override val operationKey: JellyseerrOperationKey =
            JellyseerrOperationKey.Submit(mediaType, tmdbId)
    }

    data class Approve(
        val requestId: Int,
    ) : DiscoverPendingOperation {
        override val operationKey: JellyseerrOperationKey =
            JellyseerrOperationKey.Request(requestId)
    }

    data class Delete(
        val requestId: Int,
    ) : DiscoverPendingOperation {
        override val operationKey: JellyseerrOperationKey =
            JellyseerrOperationKey.Request(requestId)
    }

    data class RemoveMedia(
        val requestId: Int,
    ) : DiscoverPendingOperation {
        override val operationKey: JellyseerrOperationKey =
            JellyseerrOperationKey.Request(requestId)
    }
}

internal data class DiscoverUiState(
    val destination: DiscoverDestination = DiscoverDestination.Feed,
    val feedScrollKey: String = "discover-feed",
    val requestQuery: String = "",
    val requestFilter: JellyseerrRequestFilter = JellyseerrRequestFilter.ALL,
    val detailBackStack: List<SeerrDetailEntry> = emptyList(),
    val isRequestConfigurationOpen: Boolean = false,
    val pendingItemKey: SeerrDetailKey? = null,
    val pendingProfileSelection: JellyseerrRequestProfileSelection =
        JellyseerrRequestProfileSelection.ServerDefault,
    val pendingSeasonSelection: JellyseerrCreateSelection =
        JellyseerrCreateSelection.AllSeasons,
    val pendingOperation: DiscoverPendingOperation? = null,
) {
    val selected: SeerrDetailEntry?
        get() = detailBackStack.lastOrNull()
}

internal sealed interface DiscoverAction {
    data object OpenRequests : DiscoverAction

    data object BackToFeed : DiscoverAction

    data class SelectRecommendation(
        val rail: JellyseerrRecommendationRail,
        val item: JellyseerrSearchItem,
        val position: Int,
    ) : DiscoverAction

    data class SelectSearchResult(
        val item: JellyseerrSearchItem,
    ) : DiscoverAction

    data class SelectExistingRequest(
        val summary: JellyseerrRequestSummary,
    ) : DiscoverAction

    data class OpenRelatedDetail(
        val item: JellyseerrSearchItem,
        val origin: SeerrDetailOrigin,
    ) : DiscoverAction

    data class UpdateDetailViewState(
        val key: SeerrDetailKey,
        val viewState: SeerrDetailViewState,
    ) : DiscoverAction

    data object OpenRequestConfiguration : DiscoverAction

    data object CloseRequestConfiguration : DiscoverAction

    data class RequestQueryChanged(
        val query: String,
    ) : DiscoverAction

    data class RequestFilterChanged(
        val filter: JellyseerrRequestFilter,
    ) : DiscoverAction

    data class SelectProfile(
        val selection: JellyseerrRequestProfileSelection,
    ) : DiscoverAction

    data class SelectSeasonSelection(
        val selection: JellyseerrCreateSelection,
    ) : DiscoverAction

    data object CloseSelection : DiscoverAction

    data object RefreshRequestStatus : DiscoverAction

    data class OperationStarted(
        val operation: DiscoverPendingOperation,
    ) : DiscoverAction

    data class OperationFinished(
        val code: JellyseerrMessageCode,
        val operationKey: JellyseerrOperationKey?,
    ) : DiscoverAction
}

internal fun DiscoverUiState.reduce(action: DiscoverAction): DiscoverUiState =
    when (action) {
        DiscoverAction.OpenRequests -> copy(destination = DiscoverDestination.Requests)
        DiscoverAction.BackToFeed ->
            copy(
                destination = DiscoverDestination.Feed,
                detailBackStack = emptyList(),
                isRequestConfigurationOpen = false,
                pendingOperation = null,
            )
        is DiscoverAction.SelectRecommendation ->
            pushDetail(
                entry =
                    SeerrDetailEntry(
                        key = action.item.detailKey(),
                        item = action.item,
                        origin = SeerrDetailOrigin.Trends,
                        recommendationRail = action.rail,
                        recommendationPosition = action.position,
                    ),
            )
        is DiscoverAction.SelectSearchResult ->
            pushDetail(
                entry =
                    SeerrDetailEntry(
                        key = action.item.detailKey(),
                        item = action.item,
                        origin = SeerrDetailOrigin.Search,
                        requestId =
                            action.item.requests
                                .firstOrNull()
                                ?.id,
                    ),
            )
        is DiscoverAction.SelectExistingRequest -> {
            val item = action.summary.toSearchItemOrNull()
            if (item == null) {
                this
            } else {
                pushDetail(
                    entry =
                        SeerrDetailEntry(
                            key = item.detailKey(),
                            item = item,
                            origin = SeerrDetailOrigin.Requests,
                            requestId = action.summary.id,
                        ),
                )
            }
        }
        is DiscoverAction.OpenRelatedDetail -> {
            require(
                action.origin == SeerrDetailOrigin.Similar ||
                    action.origin == SeerrDetailOrigin.Recommendations,
            ) {
                "Nested Seerr details must originate from Similar or Recommendations."
            }
            pushDetail(
                entry =
                    SeerrDetailEntry(
                        key = action.item.detailKey(),
                        item = action.item,
                        origin = action.origin,
                        requestId =
                            action.item.requests
                                .firstOrNull()
                                ?.id,
                    ),
            )
        }
        is DiscoverAction.UpdateDetailViewState -> {
            val index = detailBackStack.indexOfLast { it.key == action.key }
            if (index < 0) {
                this
            } else {
                copy(
                    detailBackStack =
                        detailBackStack.toMutableList().apply {
                            this[index] = this[index].copy(viewState = action.viewState)
                        },
                )
            }
        }
        DiscoverAction.OpenRequestConfiguration ->
            copy(isRequestConfigurationOpen = selected != null)
        DiscoverAction.CloseRequestConfiguration ->
            copy(isRequestConfigurationOpen = false, pendingOperation = null)
        is DiscoverAction.RequestQueryChanged -> copy(requestQuery = action.query)
        is DiscoverAction.RequestFilterChanged -> copy(requestFilter = action.filter)
        is DiscoverAction.SelectProfile -> copy(pendingProfileSelection = action.selection)
        is DiscoverAction.SelectSeasonSelection -> copy(pendingSeasonSelection = action.selection)
        DiscoverAction.CloseSelection ->
            copy(
                detailBackStack = detailBackStack.dropLast(1),
                isRequestConfigurationOpen = false,
                pendingOperation = null,
            )
        DiscoverAction.RefreshRequestStatus -> this
        is DiscoverAction.OperationStarted -> copy(pendingOperation = action.operation)
        is DiscoverAction.OperationFinished -> {
            val operation = pendingOperation
            if (!operation.matches(action.code, action.operationKey)) {
                this
            } else {
                copy(
                    isRequestConfigurationOpen =
                        isRequestConfigurationOpen &&
                            !(
                                operation is DiscoverPendingOperation.Submit &&
                                    action.code == JellyseerrMessageCode.RequestSubmitted
                            ),
                    pendingOperation = null,
                )
            }
        }
    }

private fun DiscoverUiState.pushDetail(entry: SeerrDetailEntry): DiscoverUiState {
    val sameTop = selected?.key == entry.key
    val resolvedEntry =
        if (sameTop) {
            entry.copy(viewState = requireNotNull(selected).viewState)
        } else {
            entry
        }
    val stack =
        if (sameTop) {
            detailBackStack.dropLast(1) + resolvedEntry
        } else {
            detailBackStack + resolvedEntry
        }
    return copy(
        detailBackStack = stack,
        isRequestConfigurationOpen = false,
        pendingItemKey = resolvedEntry.key,
        pendingProfileSelection =
            pendingProfileSelection.takeIf { pendingItemKey == resolvedEntry.key }
                ?: JellyseerrRequestProfileSelection.ServerDefault,
        pendingSeasonSelection =
            pendingSeasonSelection.takeIf { pendingItemKey == resolvedEntry.key }
                ?: JellyseerrCreateSelection.AllSeasons,
    )
}

private fun JellyseerrSearchItem.detailKey(): SeerrDetailKey = SeerrDetailKey(mediaType = mediaType, tmdbId = tmdbId)

private fun DiscoverPendingOperation?.matches(
    code: JellyseerrMessageCode,
    completedOperationKey: JellyseerrOperationKey?,
): Boolean =
    this?.operationKey == completedOperationKey &&
        when (this) {
            is DiscoverPendingOperation.Submit ->
                code in
                    setOf(
                        JellyseerrMessageCode.RequestSubmitted,
                        JellyseerrMessageCode.RequestDuplicate,
                        JellyseerrMessageCode.RequestFailed,
                    )
            is DiscoverPendingOperation.Approve ->
                code in
                    setOf(
                        JellyseerrMessageCode.RequestApproved,
                        JellyseerrMessageCode.ApprovalFailed,
                    )
            is DiscoverPendingOperation.Delete ->
                code in
                    setOf(
                        JellyseerrMessageCode.RequestRemoved,
                        JellyseerrMessageCode.DeleteFailed,
                    )
            is DiscoverPendingOperation.RemoveMedia ->
                code in
                    setOf(
                        JellyseerrMessageCode.MediaRequeued,
                        JellyseerrMessageCode.MediaIdMissing,
                        JellyseerrMessageCode.MediaRequeueFailed,
                        JellyseerrMessageCode.RemoveMediaFailed,
                    )
            null -> false
        }

internal fun resolveSeerrRequestCommand(
    mediaType: JellyseerrMediaType,
    mediaStatus: JellyseerrMediaStatus?,
    hasRequest: Boolean,
    requestableSeasons: List<Int>,
): SeerrRequestCommand {
    val requestMoreSeasons =
        mediaType == JellyseerrMediaType.TV &&
            requestableSeasons.isNotEmpty() &&
            (
                hasRequest ||
                    mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE
            )
    val primaryAction =
        when {
            mediaStatus == JellyseerrMediaStatus.AVAILABLE -> null
            mediaStatus == JellyseerrMediaStatus.PENDING -> null
            mediaStatus == JellyseerrMediaStatus.PROCESSING -> null
            requestMoreSeasons -> SeerrPrimaryAction.RequestMoreSeasons
            mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> null
            hasRequest -> null
            else -> SeerrPrimaryAction.Request
        }
    return SeerrRequestCommand(
        primaryAction = primaryAction,
        showStatus = hasRequest || mediaStatus != null && mediaStatus != JellyseerrMediaStatus.UNKNOWN,
    )
}
