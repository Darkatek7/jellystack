package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Routes existing shared phone/TV workflows to the configured native provider. */
class JellyseerrRepository(
    httpClient: HttpClient? = null,
    json: Json = NetworkJson.default,
    recommendationsStore: JellyseerrRecommendationStore? = null,
    clock: Clock = Clock.System,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val seerr: RequestProvider = SeerrRequestProvider(httpClient, json, recommendationsStore, clock, computeDispatcher)
    private val mediarium: RequestProvider = MediariumProvider(httpClient, clock)

    private fun provider(environment: JellyseerrEnvironment): RequestProvider =
        when (environment.provider) {
            RequestProviderType.SEERR -> seerr
            RequestProviderType.MEDIARIUM -> mediarium
        }

    suspend fun cachedRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationPage> = provider(environment).cachedRecommendations(environment, rail)

    suspend fun fetchRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ): JellyseerrRecommendationPage = provider(environment).fetchRecommendations(environment, rail, page)

    suspend fun fetchRecommendationDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = provider(environment).fetchRecommendationDetail(environment, tmdbId, mediaType)

    suspend fun fetchRecommendationPrimaryDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = provider(environment).fetchRecommendationPrimaryDetail(environment, tmdbId, mediaType)

    suspend fun fetchRecommendationDetailEnrichment(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
    ): JellyseerrMediaDetailEnrichment = provider(environment).fetchRecommendationDetailEnrichment(environment, primaryDetail)

    suspend fun fetchRecommendationDetailEnrichmentSection(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetailEnrichment =
        provider(environment).fetchRecommendationDetailEnrichmentSection(environment, primaryDetail, section)

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = DEFAULT_PAGE_SIZE,
        skip: Int = 0,
    ): JellyseerrRequestsPage = provider(environment).fetchRequests(environment, filter, take, skip)

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts = provider(environment).fetchCounts(environment)

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem> = provider(environment).search(environment, query, page)

    suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult = provider(environment).createRequest(environment, request)

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> = provider(environment).deleteRequest(environment, requestId)

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit> = provider(environment).removeMediaFromService(environment, mediaId, is4k)

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> = provider(environment).retryRequest(environment, requestId)

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> = provider(environment).updateRequestStatus(environment, requestId, status)

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile = provider(environment).profile(environment)

    suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles =
        provider(environment).fetchLanguageProfiles(environment)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
