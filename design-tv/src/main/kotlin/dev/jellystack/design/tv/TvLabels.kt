package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.players.PlaybackMode

/** User-facing labels for shared enums so no raw enum names reach the screen. */
internal fun JellyseerrRecommendationRail.label(strings: TvStrings): String =
    when (this) {
        JellyseerrRecommendationRail.TRENDS -> strings.railTrending
        JellyseerrRecommendationRail.POPULAR_MOVIES -> strings.railPopularMovies
        JellyseerrRecommendationRail.POPULAR_SHOWS -> strings.railPopularShows
        JellyseerrRecommendationRail.UPCOMING_MOVIES -> strings.railUpcomingMovies
        JellyseerrRecommendationRail.UPCOMING_SHOWS -> strings.railUpcomingShows
    }

internal fun JellyseerrRequestStatus.label(strings: TvStrings): String =
    when (this) {
        JellyseerrRequestStatus.PENDING -> strings.requestPending
        JellyseerrRequestStatus.APPROVED -> strings.requestApproved
        JellyseerrRequestStatus.DECLINED -> strings.requestDeclined
        JellyseerrRequestStatus.FAILED -> strings.requestFailedStatus
        JellyseerrRequestStatus.COMPLETED -> strings.requestCompleted
        JellyseerrRequestStatus.UNKNOWN -> strings.requestUnknown
    }

internal fun JellyseerrMediaStatus?.label(strings: TvStrings): String =
    when (this) {
        null,
        JellyseerrMediaStatus.UNKNOWN,
        JellyseerrMediaStatus.PENDING,
        -> strings.requestPending

        JellyseerrMediaStatus.PROCESSING -> strings.availabilityProcessing
        JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> strings.availabilityPartiallyAvailable
        JellyseerrMediaStatus.AVAILABLE -> strings.availabilityAvailable
        JellyseerrMediaStatus.BLACKLISTED,
        JellyseerrMediaStatus.DELETED,
        -> strings.availabilityUnavailable
    }

internal fun PlaybackMode.label(strings: TvStrings): String =
    when (this) {
        PlaybackMode.DIRECT -> strings.modeDirect
        PlaybackMode.HLS -> strings.modeHls
        PlaybackMode.LOCAL -> strings.modeLocal
    }
