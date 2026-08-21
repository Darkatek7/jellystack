package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

class HomeSectionsApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val deviceId: String? = null,
    private val clientVersion: String = DEFAULT_JELLYSTACK_CLIENT_VERSION,
) {
    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.apply {
            appendIfAbsent("X-Emby-Token", accessToken)
            appendIfAbsent(
                "X-Emby-Authorization",
                "MediaBrowser Client=\"Jellystack\", Device=\"Android\", " +
                    "DeviceId=\"${deviceId ?: "unknown"}\", Version=\"$clientVersion\"",
            )
        }
    }

    private fun HeadersBuilder.appendIfAbsent(
        name: String,
        value: String,
    ) {
        if (!contains(name)) append(name, value)
    }

    suspend fun meta(): HomeSectionsMetaDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/HomeScreen/Meta")
            }.body()

    suspend fun ready(): Boolean =
        client
            .request {
                method = HttpMethod.Get
                configure("/HomeScreen/Ready")
            }.status.value in 200..299

    suspend fun sections(
        userId: String,
        language: String?,
        page: Int,
        pageSize: Int?,
    ): HomeSectionsResultDto<HomeSectionInfoDto> =
        client
            .request {
                method = HttpMethod.Get
                configure("/HomeScreen/Sections")
                parameter("userId", userId)
                language?.takeIf(String::isNotBlank)?.let { parameter("language", it) }
                parameter("page", page)
                pageSize?.let { parameter("numResultsPerPage", it) }
            }.body()

    suspend fun sectionItems(
        sectionType: String,
        userId: String,
        additionalData: String?,
        language: String?,
    ): HomeSectionsResultDto<HomeSectionItemDto> =
        client
            .request {
                method = HttpMethod.Get
                configure("/HomeScreen/Section/$sectionType")
                parameter("userId", userId)
                additionalData?.takeIf(String::isNotBlank)?.let { parameter("additionalData", it) }
                language?.takeIf(String::isNotBlank)?.let { parameter("language", it) }
            }.body()
}

@Serializable
data class HomeSectionsMetaDto(
    @SerialName("Enabled") val enabled: Boolean = false,
    @SerialName("AllowUserOverride") val allowUserOverride: Boolean = false,
    @SerialName("PaginationEnabled") val paginationEnabled: Boolean = false,
    @SerialName("NumResultsPerPage") val numResultsPerPage: Int? = null,
)

@Serializable
data class HomeSectionsResultDto<T>(
    @SerialName("Items") val items: List<T> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
data class HomeSectionInfoDto(
    @SerialName("Section") val section: String? = null,
    @SerialName("DisplayText") val displayText: String? = null,
    @SerialName("Limit") val limit: Int = 1,
    @SerialName("Route") val route: String? = null,
    @SerialName("AdditionalData") val additionalData: String? = null,
    @SerialName("ContainerClass") val containerClass: String? = null,
    @SerialName("ViewMode") val viewMode: String? = null,
    @SerialName("DisplayTitleText") val displayTitleText: Boolean = true,
    @SerialName("ShowDetailsMenu") val showDetailsMenu: Boolean = true,
    @SerialName("OriginalPayload") val originalPayload: JsonElement? = null,
    @SerialName("AllowViewModeChange") val allowViewModeChange: Boolean = true,
    @SerialName("AllowHideWatched") val allowHideWatched: Boolean = false,
    @SerialName("OrderIndex") val orderIndex: Int = 0,
)

@Serializable
data class HomeSectionItemDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("SourceType") val sourceType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("SeriesThumbImageTag") val seriesThumbImageTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerialName("UserData") val userData: JellyfinItemUserData? = null,
)
