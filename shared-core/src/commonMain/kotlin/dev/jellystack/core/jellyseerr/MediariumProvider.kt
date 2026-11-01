package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.mediarium.MediariumAddResponseDto
import dev.jellystack.network.mediarium.MediariumApi
import dev.jellystack.network.mediarium.MediariumHttpException
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/** Uses Mediarium's own approval and download workflow; never emulates a Seerr service. */
class MediariumProvider(
    httpClient: HttpClient? = null,
    private val clock: Clock = Clock.System,
) : RequestProvider {
    private val client = httpClient ?: NetworkClientFactory.create(ClientConfig(configure = { followRedirects = false }))

    private fun api(environment: JellyseerrEnvironment): MediariumApi =
        MediariumApi(client, environment.baseUrl, requireNotNull(environment.apiKey))

    override suspend fun cachedRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationPage> = emptyList()

    override suspend fun fetchRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ): JellyseerrRecommendationPage {
        require(page > 0)
        // Native trending/popular lists are finite arrays, not paginated Seerr lists.
        val items =
            if (page != 1) {
                emptyList()
            } else {
                val api = api(environment)
                when (rail) {
                    JellyseerrRecommendationRail.TRENDS -> api.trending(false) + api.trending(true)
                    JellyseerrRecommendationRail.POPULAR_MOVIES -> api.popular(false)
                    JellyseerrRecommendationRail.POPULAR_SHOWS -> api.popular(true)
                    JellyseerrRecommendationRail.UPCOMING_MOVIES,
                    JellyseerrRecommendationRail.UPCOMING_SHOWS,
                    -> emptyList()
                }.mapNotNull { it.toSearchItem() }.distinctBy { it.mediaType to it.tmdbId }
            }
        return JellyseerrRecommendationPage(page, 1, items, clock.now())
    }

    override suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int,
    ): List<JellyseerrSearchItem> {
        require(page > 0)
        if (page != 1 || query.length < 2) return emptyList()
        return api(environment).search(query).mapNotNull { it.toSearchItem() }.distinctBy { it.mediaType to it.tmdbId }
    }

    override suspend fun fetchRecommendationPrimaryDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = api(environment).detail(tmdbId, mediaType.isTv()).toDetail(mediaType)

    override suspend fun fetchRecommendationDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail = fetchRecommendationPrimaryDetail(environment, tmdbId, mediaType)

    override suspend fun fetchRecommendationDetailEnrichment(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
    ): JellyseerrMediaDetailEnrichment = JellyseerrMediaDetailEnrichment(ratings = primaryDetail.ratings)

    override suspend fun fetchRecommendationDetailEnrichmentSection(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetailEnrichment = fetchRecommendationDetailEnrichment(environment, primaryDetail)

    override suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int,
        skip: Int,
    ): JellyseerrRequestsPage {
        require(take > 0 && skip >= 0)
        val requests =
            api(environment)
                .requests()
                .requests
                .orEmpty()
                .filter { it.kind == "movie" || it.kind == "tv" }
                .map { it.toSummary() }
                .filter { it.matches(filter) }
        return JellyseerrRequestsPage(
            page = skip / take + 1,
            pageSize = take,
            totalResults = requests.size,
            totalPages = ((requests.size + take - 1) / take).coerceAtLeast(1),
            results = requests.drop(skip).take(take),
        )
    }

    override suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts {
        val requests =
            api(environment)
                .requests()
                .requests
                .orEmpty()
                .filter { it.kind == "movie" || it.kind == "tv" }
        return JellyseerrRequestCounts(
            total = requests.size,
            movie = requests.count { it.kind == "movie" },
            tv = requests.count { it.kind == "tv" },
            pending = requests.count { it.status == "pending" },
            approved = requests.count { it.status == "approved" },
            declined = requests.count { it.status == "declined" },
        )
    }

    override suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult {
        if (request.is4k ||
            request.seasons is JellyseerrCreateSelection.Seasons ||
            request.serverId != null ||
            request.profileId != null ||
            request.languageProfileId != null
        ) {
            return JellyseerrCreateResult.Failure("Unsupported Mediarium request options")
        }
        return try {
            when (val result = api(environment).add(request.mediaId, request.mediaType.isTv())) {
                is MediariumAddResponseDto.Pending ->
                    JellyseerrCreateResult.Success(
                        result.request.toSummary().copy(tmdbId = request.mediaId),
                    )
                is MediariumAddResponseDto.Added -> JellyseerrCreateResult.AddedToProvider
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MediariumHttpException) {
            if (error.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(error.message.orEmpty())
            } else {
                JellyseerrCreateResult.Failure(error.message.orEmpty(), error)
            }
        } catch (error: Exception) {
            JellyseerrCreateResult.Failure("Mediarium request failed", error)
        }
    }

    override suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile {
        val account = api(environment).account()
        return JellyseerrProfile(
            id = account.id,
            displayName = account.name,
            permissions = 0,
            providerCapabilities =
                JellyseerrRequestCapabilities(
                    canRequestMovie = account.isAdmin || account.permissions?.movies == true,
                    canRequestTv = account.isAdmin || account.permissions?.tv == true,
                    canRequest4kMovie = false,
                    canRequest4kTv = false,
                    canUseAdvancedRequests = false,
                    canManageRequests = false,
                    canSelectSeasons = false,
                ),
        )
    }

    override suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles =
        JellyseerrLanguageProfiles.EMPTY

    // Native deletion/approval semantics differ; keep all management actions disabled for this provider.
    override suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> = unsupported()

    override suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean,
    ): Result<Unit> = unsupported()

    override suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> = unsupported()

    override suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> = unsupported()

    private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException("Mediarium management is not supported"))
}

private fun JellyseerrMediaType.isTv(): Boolean {
    require(this == JellyseerrMediaType.MOVIE || this == JellyseerrMediaType.TV)
    return this == JellyseerrMediaType.TV
}

private fun JellyseerrRequestSummary.matches(filter: JellyseerrRequestFilter): Boolean =
    when (filter) {
        JellyseerrRequestFilter.ALL -> true
        JellyseerrRequestFilter.PENDING -> requestStatus == JellyseerrRequestStatus.PENDING
        JellyseerrRequestFilter.APPROVED -> requestStatus == JellyseerrRequestStatus.APPROVED
        else -> false // The native history does not identify a playable Jellyfin item.
    }
