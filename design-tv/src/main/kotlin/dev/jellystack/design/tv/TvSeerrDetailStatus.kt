package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState

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
