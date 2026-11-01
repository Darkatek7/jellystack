package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Compatibility facade; existing workflows delegate to the shared request-provider boundary. */
class JellyseerrRepository(
    httpClient: HttpClient? = null,
    json: Json = NetworkJson.default,
    recommendationsStore: JellyseerrRecommendationStore? = null,
    clock: Clock = Clock.System,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val seerr: RequestProvider = SeerrRequestProvider(httpClient, json, recommendationsStore, clock, computeDispatcher)

    suspend fun cachedRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationPage> = seerr.cachedRecommendations(environment, rail)

    suspend fun fetchRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ): JellyseerrRecommendationPage = seerr.fetchRecommendations(environment, rail, page)

    suspend fun fetchRecommendationDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = seerr.fetchRecommendationDetail(environment, tmdbId, mediaType)

    suspend fun fetchRecommendationPrimaryDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = seerr.fetchRecommendationPrimaryDetail(environment, tmdbId, mediaType)

    suspend fun fetchRecommendationDetailEnrichment(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
    ): JellyseerrMediaDetailEnrichment = seerr.fetchRecommendationDetailEnrichment(environment, primaryDetail)

    suspend fun fetchRecommendationDetailEnrichmentSection(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetailEnrichment = seerr.fetchRecommendationDetailEnrichmentSection(environment, primaryDetail, section)

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = DEFAULT_PAGE_SIZE,
        skip: Int = 0,
    ): JellyseerrRequestsPage = seerr.fetchRequests(environment, filter, take, skip)

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts = seerr.fetchCounts(environment)

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem> = seerr.search(environment, query, page)

    suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult = seerr.createRequest(environment, request)

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> = seerr.deleteRequest(environment, requestId)

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit> = seerr.removeMediaFromService(environment, mediaId, is4k)

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> = seerr.retryRequest(environment, requestId)

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> = seerr.updateRequestStatus(environment, requestId, status)

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile = seerr.profile(environment)

    suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles =
        seerr.fetchLanguageProfiles(environment)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
