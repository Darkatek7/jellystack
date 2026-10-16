package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.SeerrRequestCommand
import dev.jellystack.core.jellyseerr.resolveSeerrRequestCommand

/** Whether the Seerr detail page still waits for its full detail, which the route's own data stands in for. */
internal enum class TvSeerrDetailStatus { READY, LOADING, FAILED }

internal fun JellyseerrMediaDetailState?.tvSeerrDetailStatus(): TvSeerrDetailStatus =
    when (this) {
        JellyseerrMediaDetailState.Loading -> TvSeerrDetailStatus.LOADING
        is JellyseerrMediaDetailState.Error -> TvSeerrDetailStatus.FAILED
        // No state means no load runs, for example while Seerr is not connected: the route's data is all there is.
        is JellyseerrMediaDetailState.Loaded, null -> TvSeerrDetailStatus.READY
    }

/** The one action in the Seerr hero that takes focus first. */
internal enum class TvSeerrPrimaryAction { RETRY, REQUEST, TRAILER, MY_LIST }

internal fun tvSeerrPrimaryAction(
    status: TvSeerrDetailStatus,
    canRequest: Boolean,
    hasTrailer: Boolean,
): TvSeerrPrimaryAction =
    when {
        status == TvSeerrDetailStatus.FAILED -> TvSeerrPrimaryAction.RETRY
        canRequest -> TvSeerrPrimaryAction.REQUEST
        hasTrailer -> TvSeerrPrimaryAction.TRAILER
        else -> TvSeerrPrimaryAction.MY_LIST
    }

/** What the Seerr hero offers, derived from the media-level state and not just the user's own request. */
internal data class TvSeerrRequestState(
    val mediaStatus: JellyseerrMediaStatus?,
    val command: SeerrRequestCommand,
    val requestableSeasons: List<Int>,
)

/**
 * Seerr's media status and request list cover every user, so a title another user already
 * requested is not requestable again. [activeRequest] is the current user's own request and
 * keeps the state correct right after submitting, before the detail reloads.
 */
internal fun resolveTvSeerrRequestState(
    mediaType: JellyseerrMediaType,
    detail: JellyseerrMediaDetail?,
    activeRequest: JellyseerrRequestSummary?,
    capabilities: JellyseerrRequestCapabilities,
): TvSeerrRequestState {
    val mediaStatus =
        detail?.availability?.standard?.takeUnless { it == JellyseerrMediaStatus.UNKNOWN }
            ?: activeRequest?.availability?.standard?.takeUnless { it == JellyseerrMediaStatus.UNKNOWN }
    val requests = detail?.requests.orEmpty() + listOfNotNull(activeRequest)
    val requestedSeasons = requests.flatMapTo(mutableSetOf()) { request -> request.seasons.map { it.seasonNumber } }
    val requestableSeasons =
        detail
            ?.availableSeasons
            .orEmpty()
            .distinct()
            .sorted()
            .filterNot(requestedSeasons::contains)
    return TvSeerrRequestState(
        mediaStatus = mediaStatus,
        command =
            resolveSeerrRequestCommand(
                mediaType = mediaType,
                mediaStatus = mediaStatus,
                hasRequest = requests.isNotEmpty(),
                requestableSeasons = requestableSeasons,
                capabilities = capabilities,
            ),
        requestableSeasons = requestableSeasons,
    )
}
