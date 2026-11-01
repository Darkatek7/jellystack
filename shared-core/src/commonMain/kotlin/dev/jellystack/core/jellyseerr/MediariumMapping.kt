package dev.jellystack.core.jellyseerr

import dev.jellystack.network.mediarium.MediariumDetailDto
import dev.jellystack.network.mediarium.MediariumRequestDto
import dev.jellystack.network.mediarium.MediariumTitleDto
import kotlinx.datetime.Instant

internal fun MediariumTitleDto.toSearchItem(): JellyseerrSearchItem? {
    val type = JellyseerrMediaType.from(mediaType)
    if (tmdbId <= 0 || type !in setOf(JellyseerrMediaType.MOVIE, JellyseerrMediaType.TV)) return null
    return JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = type,
        title = title,
        overview = overview,
        releaseYear = year.takeIf { it > 0 }?.toString(),
        posterPath = posterUrl,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = providerAvailability(libraryId),
        requests = emptyList(),
    )
}

internal fun MediariumDetailDto.toDetail(type: JellyseerrMediaType): JellyseerrMediaDetail =
    JellyseerrMediaDetail(
        tmdbId = tmdbId,
        mediaType = type,
        title = title,
        year = year.takeIf { it > 0 }?.toString(),
        overview = overview,
        runtimeMinutes = runtime,
        genres = genres.orEmpty(),
        releaseDate = releaseDate ?: firstAirDate,
        revenue = null,
        originalLanguage = language,
        productionCountries = emptyList(),
        studios = emptyList(),
        ratings = JellyseerrMediaRatings(rating, null, null, null),
        trailer = null,
        posterPath = posterUrl,
        backdropPath = backdropUrl,
        jellyseerrUrl = null,
        jellyfinUrl = null,
        imdbId = imdbId,
        tvdbId = null,
        tagline = tagline,
        certification = certification ?: contentRating,
        status = status ?: releaseStatus,
        seasons =
            seasonList.orEmpty().map {
                JellyseerrSeason(null, it.number, it.name, null, it.airDate, it.episodes, null)
            },
        availability = providerAvailability(libraryId),
    )

internal fun MediariumRequestDto.toSummary(): JellyseerrRequestSummary =
    JellyseerrRequestSummary(
        id = id,
        mediaId = null,
        tmdbId = null, // The API deliberately omits payload: title matching would be ambiguous.
        tvdbId = null,
        title = title,
        originalTitle = null,
        mediaType = JellyseerrMediaType.from(kind),
        requestStatus =
            when (status) {
                "pending" -> JellyseerrRequestStatus.PENDING
                "approved" -> JellyseerrRequestStatus.APPROVED
                "declined" -> JellyseerrRequestStatus.DECLINED
                else -> JellyseerrRequestStatus.UNKNOWN
            },
        availability = JellyseerrMediaAvailability(null, null),
        is4k = fourK,
        canRemoveFromService = false,
        createdAt = createdAt.toInstantOrNull(),
        updatedAt = decidedAt.toInstantOrNull(),
        requestedBy = JellyseerrUser(requestedBy, requester, null, null),
        profileName = null,
        seasons = emptyList(),
        posterPath = poster,
        backdropPath = null,
        canManageAtProvider = false,
        provider = RequestProviderType.MEDIARIUM,
    )

/** Being in Mediarium prevents duplicate adds; only Jellyfin can confirm playback availability. */
private fun providerAvailability(libraryId: Long): JellyseerrMediaAvailability =
    JellyseerrMediaAvailability(
        standard = if (libraryId > 0) JellyseerrMediaStatus.PROCESSING else JellyseerrMediaStatus.UNKNOWN,
        `4k` = null,
    )

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
