package dev.jellystack.network.mediarium

import kotlinx.serialization.Serializable

@Serializable
data class MediariumTitleDto(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: Int = 0,
    val overview: String? = null,
    val posterUrl: String? = null,
    val libraryId: Long = 0,
    val status: String? = null,
)

@Serializable
data class MediariumDetailDto(
    val tmdbId: Int,
    val title: String,
    val year: Int = 0,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val libraryId: Long = 0,
    val status: String? = null,
    val runtime: Int? = null,
    val genres: List<String>? = null,
    val rating: Double? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val language: String? = null,
    val imdbId: String? = null,
    val tagline: String? = null,
    val certification: String? = null,
    val contentRating: String? = null,
    val releaseStatus: String? = null,
    val seasonList: List<MediariumSeasonDto>? = null,
)

@Serializable
data class MediariumSeasonDto(
    val number: Int,
    val name: String,
    val episodes: Int,
    val airDate: String? = null,
)

@Serializable
data class MediariumRequestDto(
    val id: Int,
    val kind: String,
    val title: String,
    val poster: String? = null,
    val requestedBy: Int? = null,
    val requester: String? = null,
    val status: String,
    val createdAt: String? = null,
    val decidedAt: String? = null,
    val availableAt: String? = null,
    val fourK: Boolean = false,
)

@Serializable
data class MediariumRequestsDto(
    val requests: List<MediariumRequestDto>? = null,
)

@Serializable
data class MediariumPermissionsDto(
    val movies: Boolean = false,
    val tv: Boolean = false,
)

@Serializable
data class MediariumAccountDto(
    val id: Int,
    val name: String? = null,
    val isAdmin: Boolean = false,
    val permissions: MediariumPermissionsDto? = null,
)

@Serializable
data class MediariumAddMovieDto(
    val tmdbId: Int,
    val searchNow: Boolean,
)

@Serializable
data class MediariumAddSeriesDto(
    val tmdbId: Int,
    val monitor: String,
    val searchNow: Boolean,
)

@Serializable
data class MediariumPendingDto(
    val request: MediariumRequestDto,
)

@Serializable
data class MediariumLibraryItemDto(
    val id: Long,
)

sealed interface MediariumAddResponseDto {
    data class Pending(
        val request: MediariumRequestDto,
    ) : MediariumAddResponseDto

    data class Added(
        val item: MediariumLibraryItemDto,
    ) : MediariumAddResponseDto
}
