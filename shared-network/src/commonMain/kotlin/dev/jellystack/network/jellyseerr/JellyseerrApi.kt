package dev.jellystack.network.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

private const val API_PREFIX = "/api/v1"
private const val HEADER_API_KEY = "X-API-Key"
private const val HEADER_API_USER = "X-API-User"

interface JellyseerrSessionCookieHandler {
    suspend fun currentCookie(): String?

    suspend fun refreshCookie(): String?
}

/**
 * Thin wrapper over Jellyseerr REST API endpoints used by the app.
 * Handles common headers and request building but keeps response models close
 * to their wire representations so higher layers can map to domain models.
 */
class JellyseerrApi internal constructor(
    private val client: HttpClient,
    private val apiBaseUrl: String,
    private val apiKey: String?,
    sessionCookie: String?,
    private val sessionHandler: JellyseerrSessionCookieHandler?,
    private val apiUserId: Int? = null,
) {
    private var sessionCookieCache: String? = sessionCookie

    companion object {
        fun create(
            baseUrl: String,
            apiKey: String?,
            sessionCookie: String? = null,
            apiUserId: Int? = null,
            sessionHandler: JellyseerrSessionCookieHandler? = null,
            client: HttpClient? = null,
            clientConfig: ClientConfig.() -> Unit = {},
        ): JellyseerrApi {
            val normalizedBase = baseUrl.trimEnd('/')
            val httpClient =
                client
                    ?: NetworkClientFactory.create(
                        ClientConfig(
                            installLogging = false,
                        ).apply(clientConfig),
                    )
            return JellyseerrApi(
                client = httpClient,
                apiBaseUrl = normalizedBase + API_PREFIX,
                apiKey = apiKey,
                sessionCookie = sessionCookie,
                sessionHandler = sessionHandler,
                apiUserId = apiUserId,
            )
        }
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrSearchResponseDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/search") {
                    applyAuthHeaders(cookie)
                    parameter("query", query)
                    parameter("page", page)
                    if (!language.isNullOrBlank()) {
                        parameter("language", language)
                    }
                }.body()
        }

    suspend fun discoverTrending(
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/discover/trending",
            page = page,
            language = language,
        )

    suspend fun discoverPopularMovies(
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/discover/movies",
            page = page,
            language = language,
        ) {
            parameter("sortBy", "popularity.desc")
        }

    suspend fun discoverPopularTv(
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/discover/tv",
            page = page,
            language = language,
        ) {
            parameter("sortBy", "popularity.desc")
        }

    suspend fun discoverUpcomingMovies(
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/discover/movies/upcoming",
            page = page,
            language = language,
        )

    suspend fun discoverUpcomingTv(
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/discover/tv/upcoming",
            page = page,
            language = language,
        )

    suspend fun listRequests(
        take: Int = 20,
        skip: Int = 0,
        filter: String? = null,
        sort: String? = null,
        sortDirection: String? = null,
        mediaType: String? = null,
        requestedBy: Int? = null,
    ): JellyseerrRequestsResponseDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/request") {
                    applyAuthHeaders(cookie)
                    parameter("take", take)
                    parameter("skip", skip)
                    filter?.let { parameter("filter", it) }
                    sort?.let { parameter("sort", it) }
                    sortDirection?.let { parameter("sortDirection", it) }
                    mediaType?.let { parameter("mediaType", it) }
                    requestedBy?.let { parameter("requestedBy", it) }
                }.body()
        }

    suspend fun getMovieDetails(movieId: Int): JellyseerrMovieDetailsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/movie/$movieId") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getMovieRatingsCombined(movieId: Int): JellyseerrCombinedRatingsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/movie/$movieId/ratingscombined") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getMovieSimilar(
        movieId: Int,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/movie/$movieId/similar",
            page = page,
            language = language,
        )

    suspend fun getMovieRecommendations(
        movieId: Int,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/movie/$movieId/recommendations",
            page = page,
            language = language,
        )

    suspend fun getTvDetails(showId: Int): JellyseerrTvDetailsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/tv/$showId") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getTvSimilar(
        showId: Int,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/tv/$showId/similar",
            page = page,
            language = language,
        )

    suspend fun getTvRecommendations(
        showId: Int,
        page: Int = 1,
        language: String? = null,
    ): JellyseerrDiscoverResponseDto =
        discover(
            path = "/tv/$showId/recommendations",
            page = page,
            language = language,
        )

    suspend fun getTvRatings(showId: Int): JellyseerrRtRatingDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/tv/$showId/ratings") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getRequestCounts(): JellyseerrRequestCountsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/request/count") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun createRequest(payload: JellyseerrCreateRequestPayload): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request") {
                    applyAuthHeaders(cookie)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }.toBodyOrThrow()
        }

    suspend fun listRadarrServices(): List<JellyseerrServiceSummaryDto> =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/service/radarr") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun listSonarrServices(): List<JellyseerrServiceSummaryDto> =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/service/sonarr") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getRadarrServiceDetails(serviceId: Int): JellyseerrServiceDetailsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/service/radarr/$serviceId") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun getSonarrServiceDetails(serviceId: Int): JellyseerrServiceDetailsDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/service/sonarr/$serviceId") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun loginWithCredentials(payload: JellyseerrLocalLoginPayload): JellyseerrAuthResponse =
        client
            .post("$apiBaseUrl/auth/local") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.toAuthResponse()

    suspend fun loginWithJellyfin(payload: JellyseerrJellyfinLoginPayload): JellyseerrAuthResponse =
        client
            .post("$apiBaseUrl/auth/jellyfin") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.toAuthResponse()

    suspend fun initiateJellyfinQuickConnect(): JellyseerrQuickConnectSessionDto =
        client
            .post("$apiBaseUrl/auth/jellyfin/quickconnect/initiate")
            .body()

    suspend fun checkJellyfinQuickConnect(secret: String): JellyseerrQuickConnectStatusDto =
        client
            .get("$apiBaseUrl/auth/jellyfin/quickconnect/check") {
                parameter("secret", secret)
            }.body()

    suspend fun loginWithJellyfinQuickConnect(secret: String): JellyseerrAuthResponse =
        client
            .post("$apiBaseUrl/auth/jellyfin/quickconnect/authenticate") {
                contentType(ContentType.Application.Json)
                setBody(JellyseerrQuickConnectSecretPayload(secret))
            }.toAuthResponse()

    suspend fun deleteRequest(requestId: Int) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/request/$requestId") {
                applyAuthHeaders(cookie)
            }
        }
    }

    suspend fun updateRequestStatus(
        requestId: Int,
        status: String,
    ): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request/$requestId/$status") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun retryRequest(requestId: Int): JellyseerrRequestDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .post("$apiBaseUrl/request/$requestId/retry") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    suspend fun deleteMedia(mediaId: Int) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/media/$mediaId") {
                applyAuthHeaders(cookie)
            }
        }
    }

    suspend fun deleteMediaFiles(
        mediaId: Int,
        is4k: Boolean = false,
    ) {
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client.delete("$apiBaseUrl/media/$mediaId/file") {
                applyAuthHeaders(cookie)
                parameter("is4k", is4k)
            }
        }
    }

    suspend fun getProfile(): JellyseerrProfileDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl/auth/me") {
                    applyAuthHeaders(cookie)
                }.body()
        }

    private suspend fun discover(
        path: String,
        page: Int,
        language: String?,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): JellyseerrDiscoverResponseDto =
        withSessionRetry {
            val cookie = prepareSessionCookie()
            client
                .get("$apiBaseUrl$path") {
                    applyAuthHeaders(cookie)
                    parameter("page", page)
                    if (!language.isNullOrBlank()) {
                        parameter("language", language)
                    }
                    configure()
                }.body()
        }

    private suspend fun prepareSessionCookie(): String? {
        val handlerCookie = sessionHandler?.currentCookie()
        if (!handlerCookie.isNullOrBlank()) {
            sessionCookieCache = handlerCookie
        }
        return sessionCookieCache
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders(cookie: String?) {
        apiKey?.let { header(HEADER_API_KEY, it) }
        apiUserId?.let { header(HEADER_API_USER, it) }
        cookie?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Cookie, it) }
    }

    private suspend fun <T> withSessionRetry(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                val handler = sessionHandler
                if (handler == null || !shouldRefreshSession(error) || attempt == 1) {
                    throw error
                }
                sessionCookieCache =
                    try {
                        handler.refreshCookie()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        throw error
                    }
                attempt++
            }
        }
        throw lastError ?: IllegalStateException("Session retry failed")
    }

    private fun shouldRefreshSession(error: Throwable): Boolean =
        when (error) {
            is ClientRequestException ->
                error.response.status == HttpStatusCode.Unauthorized ||
                    error.response.status == HttpStatusCode.Forbidden
            is JellyseerrHttpException ->
                error.status == HttpStatusCode.Unauthorized ||
                    error.status == HttpStatusCode.Forbidden
            else -> false
        }

    private suspend inline fun <reified T> HttpResponse.toBodyOrThrow(): T {
        if (!status.isSuccess()) {
            val responseText = runCatching { bodyAsText() }.getOrNull()
            throw JellyseerrHttpException(status, responseText)
        }
        return body()
    }

    private suspend fun HttpResponse.toAuthResponse(): JellyseerrAuthResponse =
        JellyseerrAuthResponse(
            user = body(),
            sessionCookie =
                headers
                    .getAll(HttpHeaders.SetCookie)
                    ?.mapNotNull { header ->
                        header.substringBefore(';').takeIf { '=' in it }
                    }?.joinToString(separator = "; ")
                    ?.takeIf { it.isNotBlank() },
        )
}

class JellyseerrHttpException(
    val status: HttpStatusCode,
    val responseBody: String?,
) : Exception(
        buildString {
            append("HTTP ${status.value} ${status.description}")
            if (!responseBody.isNullOrBlank()) {
                append(": ")
                append(responseBody)
            }
        },
    )

@Serializable
data class JellyseerrSearchResponseDto(
    val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("totalResults") val totalResults: Int = 0,
    val results: List<JellyseerrSearchResultDto> = emptyList(),
)

@Serializable
data class JellyseerrDiscoverResponseDto(
    val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("totalResults") val totalResults: Int = 0,
    val results: List<JellyseerrDiscoverResultDto> = emptyList(),
)

@Serializable
data class JellyseerrDiscoverResultDto(
    val id: Int,
    @SerialName("mediaType") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("mediaInfo") val mediaInfo: JellyseerrMediaInfoDto? = null,
)

@Serializable
data class JellyseerrSearchResultDto(
    val id: Int,
    @SerialName("mediaType") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("mediaInfo") val mediaInfo: JellyseerrMediaInfoDto? = null,
)

@Serializable
data class JellyseerrMediaInfoDto(
    val id: Int,
    @SerialName("tmdbId") val tmdbId: Int? = null,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    @SerialName("serviceId") val serviceId: Int? = null,
    @SerialName("serviceId4k") val serviceId4k: Int? = null,
    @SerialName("externalServiceSlug") val externalServiceSlug: String? = null,
    @SerialName("externalServiceSlug4k") val externalServiceSlug4k: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("originalName") val originalName: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    val slug: String? = null,
    @SerialName("requestCount") val requestCount: Int? = null,
    val requests: List<JellyseerrRequestDto> = emptyList(),
)

@Serializable
data class JellyseerrRequestDto(
    val id: Int,
    @SerialName("mediaId") val mediaId: Int? = null,
    @SerialName("status") val status: Int = 1,
    @SerialName("type") val type: String,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("profileName") val profileName: String? = null,
    @SerialName("canRemove") val canRemove: Boolean? = null,
    @SerialName("requestedBy") val requestedBy: JellyseerrUserDto? = null,
    @SerialName("modifiedBy") val modifiedBy: JellyseerrUserDto? = null,
    val media: JellyseerrMediaInfoDto? = null,
    val seasons: List<JellyseerrSeasonDto> = emptyList(),
)

@Serializable
data class JellyseerrSeasonDto(
    val id: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("status") val status: Int? = null,
)

@Serializable
data class JellyseerrUserDto(
    val id: Int? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("jellyfinUsername") val jellyfinUsername: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    val permissions: Int? = null,
)

data class JellyseerrAuthResponse(
    val user: JellyseerrUserDto,
    val sessionCookie: String?,
)

@Serializable
data class JellyseerrRequestsResponseDto(
    @SerialName("pageInfo") val pageInfo: JellyseerrPageInfoDto? = null,
    val results: List<JellyseerrRequestDto> = emptyList(),
)

@Serializable
data class JellyseerrPageInfoDto(
    val pages: Int? = null,
    @SerialName("pageSize") val pageSize: Int? = null,
    val results: Int? = null,
    val page: Int? = null,
)

@Serializable
data class JellyseerrRequestCountsDto(
    val total: Int? = null,
    val movie: Int? = null,
    val tv: Int? = null,
    val pending: Int? = null,
    val approved: Int? = null,
    val declined: Int? = null,
    val processing: Int? = null,
    val available: Int? = null,
    val completed: Int? = null,
)

@Serializable
data class JellyseerrProfileDto(
    val id: Int,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    val permissions: Int = 0,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("userType") @Serializable(with = JellyseerrUserTypeSerializer::class) val userType: Int? = null,
)

@Serializable
data class JellyseerrMovieDetailsDto(
    val id: Int,
    @SerialName("imdbId") val imdbId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("voteAverage") @Serializable(with = NullableDoubleSerializer::class) val voteAverage: Double? = null,
    @SerialName("voteCount") val voteCount: Int? = null,
    val overview: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val tagline: String? = null,
    val certification: String? = null,
    val genres: List<JellyseerrGenreDto> = emptyList(),
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("spokenLanguages") val spokenLanguages: List<JellyseerrSpokenLanguageDto> = emptyList(),
    @SerialName("productionCompanies") val productionCompanies: List<JellyseerrCompanyDto> = emptyList(),
    @SerialName("productionCountries") val productionCountries: List<JellyseerrProductionCountryDto> = emptyList(),
    val credits: JellyseerrCreditsDto? = null,
    val releases: JellyseerrMovieReleasesDto? = null,
    val collection: JellyseerrCollectionDto? = null,
    @SerialName("belongsToCollection") val belongsToCollection: JellyseerrCollectionDto? = null,
    val keywords: List<JellyseerrKeywordDto> = emptyList(),
    @SerialName("relatedVideos") val videos: List<JellyseerrVideoDto>? = null,
    @SerialName("externalIds") val externalIds: JellyseerrExternalIdsDto? = null,
    @SerialName("ratings") val ratings: JellyseerrRatingsDto? = null,
    @SerialName("mediaInfo") val mediaInfo: JellyseerrMediaInfoDto? = null,
)

@Serializable
data class JellyseerrTvDetailsDto(
    val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("originalName") val originalName: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("voteAverage") @Serializable(with = NullableDoubleSerializer::class) val voteAverage: Double? = null,
    @SerialName("voteCount") val voteCount: Int? = null,
    val overview: String? = null,
    @SerialName("episodeRunTime") val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val tagline: String? = null,
    val certification: String? = null,
    val genres: List<JellyseerrGenreDto> = emptyList(),
    @SerialName("originCountry") val originCountry: List<String> = emptyList(),
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    val languages: List<String> = emptyList(),
    @SerialName("spokenLanguages") val spokenLanguages: List<JellyseerrSpokenLanguageDto> = emptyList(),
    val networks: List<JellyseerrCompanyDto> = emptyList(),
    @SerialName("productionCompanies") val productionCompanies: List<JellyseerrCompanyDto> = emptyList(),
    @SerialName("productionCountries") val productionCountries: List<JellyseerrProductionCountryDto> = emptyList(),
    @SerialName("createdBy") val createdBy: List<JellyseerrCreatedByDto> = emptyList(),
    val credits: JellyseerrCreditsDto? = null,
    @SerialName("contentRatings") val contentRatings: JellyseerrContentRatingsDto? = null,
    val seasons: List<JellyseerrTvSeasonDto> = emptyList(),
    val keywords: List<JellyseerrKeywordDto> = emptyList(),
    @SerialName("relatedVideos") val videos: List<JellyseerrVideoDto>? = null,
    @SerialName("externalIds") val externalIds: JellyseerrExternalIdsDto? = null,
    @SerialName("ratings") val ratings: JellyseerrRatingsDto? = null,
    @SerialName("mediaInfo") val mediaInfo: JellyseerrMediaInfoDto? = null,
    @SerialName("numberOfSeasons") val numberOfSeasons: Int? = null,
)

@Serializable
data class JellyseerrGenreDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class JellyseerrCompanyDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("originCountry") val originCountry: String? = null,
)

@Serializable
data class JellyseerrProductionCountryDto(
    @SerialName("iso_3166_1") val isoCode: String? = null,
    val name: String? = null,
)

@Serializable
data class JellyseerrSpokenLanguageDto(
    @SerialName("englishName") val englishName: String? = null,
    @SerialName("iso_639_1") val isoCode: String? = null,
    val name: String? = null,
)

@Serializable
data class JellyseerrCreditsDto(
    val cast: List<JellyseerrCastDto> = emptyList(),
    val crew: List<JellyseerrCrewDto> = emptyList(),
)

@Serializable
data class JellyseerrCastDto(
    val id: Int? = null,
    val name: String? = null,
    val character: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
    val order: Int? = null,
)

@Serializable
data class JellyseerrCrewDto(
    val id: Int? = null,
    val name: String? = null,
    val job: String? = null,
    val department: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
)

@Serializable
data class JellyseerrCreatedByDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
)

@Serializable
data class JellyseerrMovieReleasesDto(
    val results: List<JellyseerrMovieReleaseRegionDto> = emptyList(),
)

@Serializable
data class JellyseerrMovieReleaseRegionDto(
    @SerialName("iso_3166_1") val isoCode: String? = null,
    val rating: String? = null,
    @SerialName("release_dates") val releaseDates: List<JellyseerrMovieReleaseDateDto> = emptyList(),
)

@Serializable
data class JellyseerrMovieReleaseDateDto(
    val certification: String? = null,
)

@Serializable
data class JellyseerrContentRatingsDto(
    val results: List<JellyseerrContentRatingDto> = emptyList(),
)

@Serializable
data class JellyseerrContentRatingDto(
    @SerialName("iso_3166_1") val isoCode: String? = null,
    val rating: String? = null,
)

@Serializable
data class JellyseerrTvSeasonDto(
    val id: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("episodeCount") val episodeCount: Int? = null,
    @SerialName("posterPath") val posterPath: String? = null,
)

@Serializable
data class JellyseerrCollectionDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    val parts: List<JellyseerrDiscoverResultDto> = emptyList(),
)

@Serializable
data class JellyseerrKeywordDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class JellyseerrVideoDto(
    val id: String? = null,
    val name: String? = null,
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val url: String? = null,
    val size: Int? = null,
    val official: Boolean? = null,
    @SerialName("publishedAt") val publishedAt: String? = null,
)

@Serializable
data class JellyseerrExternalIdsDto(
    @SerialName("imdbId") val imdbId: String? = null,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("tmdbId") val tmdbId: Int? = null,
)

@Serializable
data class JellyseerrRatingsDto(
    val tmdb: JellyseerrScoreDto? = null,
    val imdb: JellyseerrScoreDto? = null,
    @SerialName("rottenTomatoes") val rottenTomatoes: JellyseerrRottenTomatoesDto? = null,
)

@Serializable
data class JellyseerrScoreDto(
    @Serializable(with = NullableDoubleSerializer::class) val rating: Double? = null,
    @SerialName("voteAverage") @Serializable(with = NullableDoubleSerializer::class) val voteAverage: Double? = null,
    @Serializable(with = NullableDoubleSerializer::class) val value: Double? = null,
)

@Serializable
data class JellyseerrRottenTomatoesDto(
    val critics: JellyseerrScoreDto? = null,
    val audience: JellyseerrScoreDto? = null,
)

@Serializable
data class JellyseerrCombinedRatingsDto(
    val rt: JellyseerrRtRatingDto? = null,
    val imdb: JellyseerrImdbRatingDto? = null,
)

@Serializable
data class JellyseerrRtRatingDto(
    @SerialName("criticsScore")
    @Serializable(with = NullableDoubleSerializer::class)
    val criticsScore: Double? = null,
    @SerialName("audienceScore")
    @Serializable(with = NullableDoubleSerializer::class)
    val audienceScore: Double? = null,
)

@Serializable
data class JellyseerrImdbRatingDto(
    @SerialName("criticsScore")
    @Serializable(with = NullableDoubleSerializer::class)
    val criticsScore: Double? = null,
)

@OptIn(ExperimentalSerializationApi::class)
object NullableDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableDouble", PrimitiveKind.DOUBLE).nullable

    override fun serialize(
        encoder: Encoder,
        value: Double?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element === JsonNull) {
                return null
            }
            val primitive = element as? JsonPrimitive ?: return null
            return primitive.doubleOrNull ?: primitive.intOrNull?.toDouble() ?: primitive.contentOrNull?.toDoubleOrNull()
        }
        return decoder.decodeNullableSerializableValue(Double.serializer())
    }
}

@Serializable
data class JellyseerrCreateRequestPayload(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("seasons") val seasons: JsonElement? = null,
    @SerialName("is4k") val is4k: Boolean? = null,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("languageProfileId") val languageProfileId: Int? = null,
    @SerialName("userId") val userId: Int? = null,
    @SerialName("tags") val tags: List<Int>? = null,
)

@Serializable
data class JellyseerrServiceSummaryDto(
    val id: Int,
    val name: String? = null,
    @SerialName("is4k") val is4k: Boolean? = null,
    @SerialName("isDefault") val isDefault: Boolean? = null,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeLanguageProfileId") val activeLanguageProfileId: Int? = null,
)

@Serializable
data class JellyseerrServiceDetailsDto(
    val server: JellyseerrServiceSummaryDto,
    val profiles: List<JellyseerrQualityProfileDto> = emptyList(),
    @SerialName("languageProfiles") val languageProfiles: List<JellyseerrLanguageProfileDto>? = null,
)

@Serializable
data class JellyseerrQualityProfileDto(
    val id: Int,
    val name: String,
)

@Serializable
data class JellyseerrLanguageProfileDto(
    val id: Int,
    val name: String,
    @SerialName("profileId") val profileId: Int? = null,
)

fun seasonsAll(): JsonElement = JsonPrimitive("all")

fun seasonsList(numbers: List<Int>): JsonElement = JsonArray(numbers.map { JsonPrimitive(it) })

@Serializable
data class JellyseerrLocalLoginPayload(
    val email: String,
    val password: String,
)

@Serializable
data class JellyseerrJellyfinLoginPayload(
    val username: String,
    val password: String,
)

@Serializable
data class JellyseerrQuickConnectSessionDto(
    val code: String = "",
    val secret: String = "",
)

@Serializable
data class JellyseerrQuickConnectStatusDto(
    val authenticated: Boolean = false,
)

@Serializable
data class JellyseerrQuickConnectSecretPayload(
    val secret: String,
)

@OptIn(ExperimentalSerializationApi::class)
object JellyseerrUserTypeSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JellyseerrUserType", PrimitiveKind.INT).nullable

    override fun serialize(
        encoder: Encoder,
        value: Int?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }

    override fun deserialize(decoder: Decoder): Int? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return when (element) {
                JsonNull -> null
                is JsonPrimitive -> element.intOrNull ?: element.content.toIntOrNull()
                else -> null
            }
        }
        return decoder.decodeNullableSerializableValue(Int.serializer())
    }
}
