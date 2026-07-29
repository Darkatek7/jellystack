package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Minimal Jellyfin browse client covering libraries, paged items, continue watching, and detail fetches.
 */
class JellyfinBrowseApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val deviceId: String? = null,
    private val clientName: String = "Jellystack",
    private val deviceName: String = "KotlinMultiplatform",
    private val clientVersion: String = "0.1",
) {
    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.apply {
            appendIfAbsent("X-Emby-Token", accessToken)
            appendIfAbsent("X-Emby-Authorization", authHeaderValue())
        }
    }

    private fun HeadersBuilder.appendIfAbsent(
        name: String,
        value: String,
    ) {
        if (!contains(name)) {
            append(name, value)
        }
    }

    private fun authHeaderValue(): String {
        val sanitizedDevice = deviceId ?: "unknown"
        return buildString {
            append("MediaBrowser Client=\"")
            append(clientName)
            append("\", Device=\"")
            append(deviceName)
            append("\", DeviceId=\"")
            append(sanitizedDevice)
            append("\", Version=\"")
            append(clientVersion)
            append("\"")
        }
    }

    suspend fun fetchLibraries(userId: String): JellyfinViewsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Views")
            }.body()

    suspend fun fetchLibraryItems(
        userId: String,
        libraryId: String,
        startIndex: Int,
        limit: Int,
        includeItemTypes: String? = DEFAULT_INCLUDE_ITEM_TYPES,
        recursive: Boolean = true,
        filters: String? = null,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items")
                libraryId.takeIf { it.isNotBlank() }?.let { parameter("ParentId", it) }
                includeItemTypes?.takeIf { it.isNotBlank() }?.let { parameter("IncludeItemTypes", it) }
                parameter("Recursive", recursive)
                parameter("StartIndex", startIndex)
                parameter("Limit", limit)
                parameter("SortBy", "SortName")
                parameter("SortOrder", "Ascending")
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                filters?.let { parameter("Filters", it) }
            }.body()

    suspend fun fetchLatestItems(
        userId: String,
        libraryId: String,
        limit: Int,
        includeItemTypes: String,
    ): List<JellyfinItemDto> =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/Latest")
                parameter("ParentId", libraryId)
                parameter("Limit", limit)
                parameter("IncludeItemTypes", includeItemTypes)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            }.body()

    suspend fun fetchContinueWatching(
        userId: String,
        limit: Int,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/Resume")
                parameter("Limit", limit)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            }.body()

    suspend fun fetchNextUp(
        userId: String,
        limit: Int,
        parentId: String? = null,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/NextUp")
                parameter("Limit", limit)
                parameter("StartIndex", 0)
                parentId?.let { parameter("ParentId", it) }
                parameter("IncludeItemTypes", "Episode,Series")
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("EnableUserData", true)
            }.body()

    suspend fun fetchShowsNextUp(
        userId: String,
        limit: Int,
        parentId: String? = null,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Shows/NextUp")
                parameter("UserId", userId)
                parameter("Limit", limit)
                parameter("StartIndex", 0)
                parentId?.let { parameter("ParentId", it) }
                parameter("IncludeItemTypes", "Episode,Series")
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("EnableUserData", true)
            }.body()

    suspend fun fetchItemDetail(
        userId: String,
        itemId: String,
    ): JellyfinItemDetailDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items/$itemId")
                parameter("Fields", DETAIL_FIELDS)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("ImageTypeLimit", 1)
            }.body()

    suspend fun fetchSimilarItems(
        userId: String,
        itemId: String,
        limit: Int = 12,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Items/$itemId/Similar")
                parameter("UserId", userId)
                parameter("Limit", limit)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("EnableUserData", true)
            }.body()

    suspend fun fetchLocalTrailers(
        userId: String,
        itemId: String,
    ): List<JellyfinItemDto> =
        client
            .request {
                method = HttpMethod.Get
                configure("/Items/$itemId/LocalTrailers")
                parameter("UserId", userId)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            }.body()

    suspend fun fetchEpisodesForSeries(
        userId: String,
        seriesId: String,
    ): JellyfinItemsResponse =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/$userId/Items")
                parameter("ParentId", seriesId)
                parameter("IncludeItemTypes", "Episode")
                parameter("Recursive", true)
                parameter("Fields", REQUIRED_FIELDS)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
                parameter("SortBy", "ParentIndexNumber,IndexNumber,PremiereDate")
                parameter("SortOrder", "Ascending")
            }.body()

    suspend fun reportPlaybackProgress(
        userId: String,
        itemId: String,
        positionTicks: Long,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Users/$userId/Items/$itemId/PlaybackProgress")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("ItemId", itemId)
                    put("UserId", userId)
                    put("PositionTicks", positionTicks)
                },
            )
        }
    }

    suspend fun markPlaybackCompleted(
        userId: String,
        itemId: String,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Users/$userId/Items/$itemId/Played")
        }
    }

    suspend fun setPlayedStatus(
        userId: String,
        itemId: String,
        played: Boolean,
    ): JellyfinItemUserData {
        val response: HttpResponse =
            client.request {
                method = if (played) HttpMethod.Post else HttpMethod.Delete
                configure("/Users/$userId/PlayedItems/$itemId")
            }
        if (!response.status.isSuccess()) {
            throw RuntimeException("setPlayedStatus failed: ${response.status.description}")
        }
        return response.body()
    }

    suspend fun addFavorite(
        userId: String,
        itemId: String,
    ) {
        val response: HttpResponse =
            client.request {
                method = HttpMethod.Post
                configure("/Users/$userId/FavoriteItems/$itemId")
            }
        if (!response.status.isSuccess()) {
            throw RuntimeException("addFavorite failed: ${response.status.description}")
        }
    }

    suspend fun removeFavorite(
        userId: String,
        itemId: String,
    ) {
        val response: HttpResponse =
            client.request {
                method = HttpMethod.Delete
                configure("/Users/$userId/FavoriteItems/$itemId")
            }
        if (!response.status.isSuccess()) {
            throw RuntimeException("removeFavorite failed: ${response.status.description}")
        }
    }

    suspend fun fetchFavoriteIds(userId: String): Set<String> {
        val response: JellyfinItemsResponse =
            client
                .request {
                    method = HttpMethod.Get
                    configure("/Users/$userId/Items")
                    parameter("Recursive", true)
                    parameter("Filters", "IsFavorite")
                    parameter("Fields", "ItemId")
                    parameter("Limit", 10_000)
                }.body()
        return response.items.mapTo(mutableSetOf()) { it.id }
    }

    suspend fun startStreamingPlayback(
        userId: String,
        payload: kotlinx.serialization.json.JsonObject,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Sessions/Playing")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    suspend fun reportStreamingProgress(
        userId: String,
        payload: kotlinx.serialization.json.JsonObject,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Sessions/Playing/Progress")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    suspend fun stopStreamingPlayback(
        userId: String,
        payload: kotlinx.serialization.json.JsonObject,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Sessions/Playing/Stopped")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    companion object {
        const val DEFAULT_INCLUDE_ITEM_TYPES = "Movie,Series,Episode,BoxSet,MusicAlbum,MusicArtist"
        private const val REQUIRED_FIELDS =
            "PrimaryImageAspectRatio,MediaSourceCount,BasicSyncInfo,CanDelete,Genres," +
                "SeasonUserData,ChildCount,SeriesInfo,CollectionType,Overview,Taglines,Studios," +
                "PremiereDate,ProductionYear,ProviderIds,ParentLogoImageTag,ParentThumbImageTag," +
                "ParentArtImageTag,ParentBannerImageTag,LocationType"
        private const val DETAIL_FIELDS =
            REQUIRED_FIELDS +
                ",MediaStreams,SeasonUserData,ParentBackdropImageTags,ParentLogoImageTags," +
                "ProviderIds,Path,MediaSources,People,OriginalTitle,OriginalLanguage," +
                "ProductionLocations,Tags"
    }
}

@Serializable
data class JellyfinViewsResponse(
    @SerialName("Items")
    val items: List<JellyfinLibraryDto> = emptyList(),
)

@Serializable
data class JellyfinLibraryDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("CollectionType")
    val collectionType: String? = null,
    @SerialName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    @SerialName("ItemCount")
    val itemCount: Long? = null,
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items")
    val items: List<JellyfinItemDto> = emptyList(),
    @SerialName("TotalRecordCount")
    val totalRecordCount: Long? = null,
)

@Serializable
data class JellyfinItemDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Type")
    val type: String,
    @SerialName("MediaType")
    val mediaType: String? = null,
    @SerialName("LocationType")
    val locationType: String? = null,
    @SerialName("SortName")
    val sortName: String? = null,
    @SerialName("Overview")
    val overview: String? = null,
    @SerialName("Taglines")
    val taglines: List<String>? = null,
    @SerialName("ParentId")
    val parentId: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("PremiereDate")
    val premiereDate: String? = null,
    @SerialName("CommunityRating")
    val communityRating: Double? = null,
    @SerialName("OfficialRating")
    val officialRating: String? = null,
    @SerialName("IndexNumber")
    val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    @SerialName("SeriesName")
    val seriesName: String? = null,
    @SerialName("SeasonName")
    val seasonName: String? = null,
    @SerialName("ChannelId")
    val channelId: String? = null,
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    @SerialName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    @SerialName("SeriesPrimaryImageTag")
    val seriesPrimaryImageTag: String? = null,
    @SerialName("SeriesThumbImageTag")
    val seriesThumbImageTag: String? = null,
    @SerialName("SeriesBackdropImageTag")
    val seriesBackdropImageTag: String? = null,
    @SerialName("ParentLogoImageTag")
    val parentLogoImageTag: String? = null,
    @SerialName("ParentThumbImageTag")
    val parentThumbImageTag: String? = null,
    @SerialName("ParentArtImageTag")
    val parentArtImageTag: String? = null,
    @SerialName("ParentBannerImageTag")
    val parentBannerImageTag: String? = null,
    @SerialName("UserData")
    val userData: JellyfinItemUserData? = null,
    @SerialName("SeriesId")
    val seriesId: String? = null,
    @SerialName("SeasonId")
    val seasonId: String? = null,
    @SerialName("EpisodeTitle")
    val episodeTitle: String? = null,
    @SerialName("DateCreated")
    val dateCreated: String? = null,
)

@Serializable
data class JellyfinItemUserData(
    @SerialName("IsFavorite")
    val isFavorite: Boolean = false,
    @SerialName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerialName("PlayCount")
    val playCount: Int? = null,
    @SerialName("Played")
    val played: Boolean? = null,
    @SerialName("PlayedPercentage")
    val playedPercentage: Double? = null,
    @SerialName("LastPlayedDate")
    val lastPlayedDate: String? = null,
)

@Serializable
data class JellyfinItemDetailDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Overview")
    val overview: String? = null,
    @SerialName("OriginalTitle")
    val originalTitle: String? = null,
    @SerialName("OriginalLanguage")
    val originalLanguage: String? = null,
    @SerialName("Taglines")
    val taglines: List<String>? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("PremiereDate")
    val premiereDate: String? = null,
    @SerialName("CommunityRating")
    val communityRating: Double? = null,
    @SerialName("CriticRating")
    val criticRating: Double? = null,
    @SerialName("OfficialRating")
    val officialRating: String? = null,
    @SerialName("Genres")
    val genres: List<String>? = null,
    @SerialName("Studios")
    val studios: List<JellyfinStudioDto>? = null,
    @SerialName("ProductionLocations")
    val productionLocations: List<String>? = null,
    @SerialName("Tags")
    val tags: List<String>? = null,
    @SerialName("People")
    val people: List<JellyfinPersonDto>? = null,
    @SerialName("MediaSources")
    val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    @SerialName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    @SerialName("UserData")
    val userData: JellyfinItemUserData? = null,
    @SerialName("ProviderIds")
    val providerIds: Map<String, String>? = null,
)

@Serializable
data class JellyfinPersonDto(
    @SerialName("Id")
    val id: String? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("Role")
    val role: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("PrimaryImageTag")
    val primaryImageTag: String? = null,
)

@Serializable
data class JellyfinStudioDto(
    @SerialName("Name")
    val name: String,
)

@Serializable
data class JellyfinMediaSourceDto(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("VideoBitrate")
    val videoBitrate: Int? = null,
    @SerialName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsDirectStream")
    val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsTranscoding")
    val supportsTranscoding: Boolean? = null,
    @SerialName("MediaStreams")
    val mediaStreams: List<JellyfinMediaStreamDto> = emptyList(),
)

@Serializable
data class JellyfinMediaStreamDto(
    @SerialName("Type")
    val type: String,
    @SerialName("Index")
    val index: Int? = null,
    @SerialName("DisplayTitle")
    val displayTitle: String? = null,
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("Profile")
    val profile: String? = null,
    @SerialName("VideoRange")
    val videoRange: String? = null,
    @SerialName("VideoRangeType")
    val videoRangeType: String? = null,
    @SerialName("AverageFrameRate")
    val averageFrameRate: Double? = null,
    @SerialName("BitDepth")
    val bitDepth: Int? = null,
    @SerialName("Channels")
    val channels: Int? = null,
    @SerialName("ChannelLayout")
    val channelLayout: String? = null,
    @SerialName("Language")
    val language: String? = null,
    @SerialName("IsDefault")
    val isDefault: Boolean? = null,
    @SerialName("IsForced")
    val isForced: Boolean? = null,
    @SerialName("IsExternal")
    val isExternal: Boolean? = null,
    @SerialName("IsHearingImpaired")
    val isHearingImpaired: Boolean? = null,
    @SerialName("BitRate")
    val bitrate: Int? = null,
    @SerialName("Width")
    val width: Int? = null,
    @SerialName("Height")
    val height: Int? = null,
)
