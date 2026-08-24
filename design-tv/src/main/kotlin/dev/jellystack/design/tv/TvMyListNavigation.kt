package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.profile.SavedMediaRecord

private const val MISSING_PROFILE_MESSAGE = "Authenticated TV content requires a profile"

internal fun SavedMediaRecord.toTvRoute(): TvRoute.SeerrDetail? {
    val tmdbId = providerIds.tmdbId?.toIntOrNull()
    val routeMediaType =
        when (mediaType) {
            "movie" -> JellyseerrMediaType.MOVIE
            "tv" -> JellyseerrMediaType.TV
            else -> null
        }
    return if (tmdbId != null && routeMediaType != null) {
        TvRoute.SeerrDetail(
            tmdbId = tmdbId,
            mediaType = routeMediaType,
            title = title,
            posterPath = posterPath,
            backdropPath = backdropPath,
            tvdbId = providerIds.tvdbId?.toIntOrNull(),
        )
    } else {
        null
    }
}

internal fun requiredProfileId(profileId: String?): String = profileId ?: error(MISSING_PROFILE_MESSAGE)
