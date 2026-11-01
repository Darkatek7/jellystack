package dev.jellystack.core.jellyseerr

/** Provider boundary; legacy domain names are retained to avoid a UI-wide rename. */
interface RequestProvider {
    suspend fun cachedRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationPage>

    suspend fun fetchRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ): JellyseerrRecommendationPage

    suspend fun fetchRecommendationDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail

    suspend fun fetchRecommendationPrimaryDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail

    suspend fun fetchRecommendationDetailEnrichment(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
    ): JellyseerrMediaDetailEnrichment

    suspend fun fetchRecommendationDetailEnrichmentSection(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetailEnrichment

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = 20,
        skip: Int = 0,
    ): JellyseerrRequestsPage

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem>

    suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit>

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit>

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary>

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary>

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile

    suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles
}
