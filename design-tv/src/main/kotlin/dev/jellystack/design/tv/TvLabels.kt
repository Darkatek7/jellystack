package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinChannelLayout
import dev.jellystack.core.jellyfin.JellyfinDynamicRange
import dev.jellystack.core.jellyfin.JellyfinImmersiveAudio
import dev.jellystack.core.jellyfin.JellyfinMediaFeatures
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.players.PlaybackMode

// Product and service names are the same in every language, so they are constants rather than strings.
internal const val TV_BRAND_JELLYSTACK = "Jellystack"
internal const val TV_BRAND_JELLYFIN = "Jellyfin"
internal const val TV_BRAND_SEERR = "Seerr"
internal const val TV_BRAND_TMDB = "TMDB"
internal const val TV_BRAND_IMDB = "IMDb"

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

// Format names such as Dolby Vision, DTS:X and 5.1 are not translated either.
internal fun JellyfinDynamicRange.label(): String =
    when (this) {
        JellyfinDynamicRange.DOLBY_VISION -> "Dolby Vision"
        JellyfinDynamicRange.HDR10_PLUS -> "HDR10+"
        JellyfinDynamicRange.HDR10 -> "HDR10"
        JellyfinDynamicRange.HLG -> "HLG"
        JellyfinDynamicRange.HDR -> "HDR"
    }

internal fun JellyfinImmersiveAudio.label(): String =
    when (this) {
        JellyfinImmersiveAudio.DOLBY_ATMOS -> "Dolby Atmos"
        JellyfinImmersiveAudio.DTS_X -> "DTS:X"
    }

internal fun JellyfinChannelLayout.label(): String =
    when (this) {
        JellyfinChannelLayout.MONO -> "Mono"
        JellyfinChannelLayout.STEREO -> "Stereo"
        JellyfinChannelLayout.SURROUND_5_1 -> "5.1"
        JellyfinChannelLayout.SURROUND_7_1 -> "7.1"
    }

/** Detail badges in a fixed order: resolution, HDR format, immersive audio, surround, captions. */
internal fun JellyfinMediaFeatures.badgeLabels(strings: TvStrings): List<String> =
    listOfNotNull(
        resolution?.label,
        dynamicRange?.label(),
        immersiveAudio?.label(),
        surround?.label(),
        strings.metadata.captionsBadge.takeIf { hearingImpairedSubtitles },
    )

internal fun PlaybackMode.label(strings: TvStrings): String =
    when (this) {
        PlaybackMode.DIRECT -> strings.modeDirect
        PlaybackMode.HLS -> strings.modeHls
        PlaybackMode.LOCAL -> strings.modeLocal
    }
