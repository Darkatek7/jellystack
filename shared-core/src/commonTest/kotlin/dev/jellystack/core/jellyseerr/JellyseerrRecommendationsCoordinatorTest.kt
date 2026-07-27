package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JellyseerrRecommendationsCoordinatorTest {
    @Test
    fun primaryDetailIsPublishedBeforeEnrichmentUpdatesSameEntry() =
        runTest {
            val enrichmentGate = CompletableDeferred<Unit>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.url.encodedPath == "/api/v1/movie/550" ->
                                respondJson(
                                    """{"id":550,"title":"Fight Club","voteAverage":8.4}""",
                                )
                            request.url.encodedPath == "/api/v1/movie/550/ratingscombined" -> {
                                enrichmentGate.await()
                                respondJson("""{"imdb":{"criticsScore":8.8}}""")
                            }
                            request.url.encodedPath == "/api/v1/movie/550/similar" -> {
                                enrichmentGate.await()
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":13,"title":"Forrest Gump"}]}""",
                                )
                            }
                            request.url.encodedPath == "/api/v1/movie/550/recommendations" -> {
                                enrichmentGate.await()
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":680,"title":"Pulp Fiction"}]}""",
                                )
                            }
                            request.url.encodedPath.startsWith("/api/v1/discover/") ->
                                respondJson("""{"page":1,"totalPages":1,"results":[]}""")
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val environment =
                JellyseerrEnvironment(
                    serverId = "srv-1",
                    serverName = "Requests",
                    baseUrl = "https://requests.test",
                    apiKey = "dummy-credential",
                    sessionCookie = null,
                )
            val coordinator =
                JellyseerrRecommendationsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = TestEnvironmentProvider(environment),
                    scope = this,
                )
            advanceUntilIdle()
            val item =
                JellyseerrSearchItem(
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                    title = "Fight Club",
                    overview = null,
                    releaseYear = "1999",
                    posterPath = null,
                    backdropPath = null,
                    mediaInfoId = null,
                    tvdbId = null,
                    availability = JellyseerrMediaAvailability(null, null),
                    requests = emptyList(),
                )

            coordinator.loadDetail(item)

            val key = item.mediaType to item.tmdbId
            val primary =
                assertIs<JellyseerrMediaDetailState.Loaded>(
                    coordinator.details
                        .first { it[key] is JellyseerrMediaDetailState.Loaded }[key],
                )
            assertTrue(primary.enrichmentLoading)
            assertEquals(8.4, primary.detail.ratings?.tmdb)
            assertTrue(
                primary.detail.enrichment.similar
                    .isEmpty(),
            )

            enrichmentGate.complete(Unit)

            val enriched =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        coordinator.details
                            .first { details ->
                                (details[key] as? JellyseerrMediaDetailState.Loaded)
                                    ?.enrichmentLoading == false
                            }[key]
                    }
                }
            assertIs<JellyseerrMediaDetailState.Loaded>(enriched)
            assertEquals(false, enriched.enrichmentLoading)
            assertEquals(8.8, enriched.detail.ratings?.imdb)
            assertEquals(
                "Forrest Gump",
                enriched.detail.enrichment.similar
                    .single()
                    .title,
            )
            assertEquals(
                "Pulp Fiction",
                enriched.detail.enrichment.recommendations
                    .single()
                    .title,
            )

            coordinator.shutdown()
            advanceUntilIdle()
        }

    @Test
    fun requestSummariesAreJoinedByMediaTypeAndTmdbId() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.encodedPath.startsWith("/api/v1/discover/")) {
                            respondJson(
                                """
                                {
                                  "page":1,
                                  "totalPages":1,
                                  "results":[
                                    {"id":42,"mediaType":"movie","title":"Collision Movie"},
                                    {"id":42,"mediaType":"tv","name":"Collision Show"}
                                  ]
                                }
                                """.trimIndent(),
                            )
                        } else {
                            error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val environment =
                JellyseerrEnvironment(
                    serverId = "srv-1",
                    serverName = "Requests",
                    baseUrl = "https://requests.test",
                    apiKey = "dummy-credential",
                    sessionCookie = null,
                )
            val coordinator =
                JellyseerrRecommendationsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = TestEnvironmentProvider(environment),
                    scope = this,
                )
            val loaded =
                coordinator.state.first { state ->
                    val ready = state as? JellyseerrRecommendationsState.Ready
                    ready
                        ?.rails
                        ?.get(JellyseerrRecommendationRail.TRENDS)
                        ?.items
                        ?.size == 2
                }
            assertIs<JellyseerrRecommendationsState.Ready>(loaded)
            coordinator.updateRequests(
                listOf(
                    requestSummary(
                        id = 301,
                        mediaType = JellyseerrMediaType.MOVIE,
                        title = "Collision Movie",
                    ),
                    requestSummary(
                        id = 302,
                        mediaType = JellyseerrMediaType.TV,
                        title = "Collision Show",
                    ),
                ),
            )

            val ready =
                assertIs<JellyseerrRecommendationsState.Ready>(
                    coordinator.state.first { state ->
                        val items =
                            (state as? JellyseerrRecommendationsState.Ready)
                                ?.rails
                                ?.get(JellyseerrRecommendationRail.TRENDS)
                                ?.items
                                .orEmpty()
                        items.size == 2 && items.all { it.requests.size == 1 }
                    },
                )
            val items = ready.rails.getValue(JellyseerrRecommendationRail.TRENDS).items
            assertEquals(
                301,
                items
                    .single { it.mediaType == JellyseerrMediaType.MOVIE }
                    .requests
                    .single()
                    .id,
            )
            assertEquals(
                302,
                items
                    .single { it.mediaType == JellyseerrMediaType.TV }
                    .requests
                    .single()
                    .id,
            )

            coordinator.shutdown()
            advanceUntilIdle()
        }

    @Test
    fun similarRetryCallsOnlySimilarAndPreservesRecommendations() =
        runTest {
            val retryGate = CompletableDeferred<Unit>()
            var ratingsCalls = 0
            var similarCalls = 0
            var recommendationCalls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.url.encodedPath == "/api/v1/movie/550" ->
                                respondJson(
                                    """{"id":550,"title":"Fight Club","voteAverage":8.4}""",
                                )
                            request.url.encodedPath == "/api/v1/movie/550/ratingscombined" -> {
                                ratingsCalls += 1
                                respondJson("""{"imdb":{"criticsScore":8.8}}""")
                            }
                            request.url.encodedPath == "/api/v1/movie/550/similar" -> {
                                similarCalls += 1
                                if (similarCalls == 1) {
                                    respondJson("{}", HttpStatusCode.InternalServerError)
                                } else {
                                    retryGate.await()
                                    respondJson(
                                        """{"page":1,"totalPages":1,"results":[{"id":13,"title":"Forrest Gump"}]}""",
                                    )
                                }
                            }
                            request.url.encodedPath == "/api/v1/movie/550/recommendations" -> {
                                recommendationCalls += 1
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":680,"title":"Pulp Fiction"}]}""",
                                )
                            }
                            request.url.encodedPath.startsWith("/api/v1/discover/") ->
                                respondJson("""{"page":1,"totalPages":1,"results":[]}""")
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val environment =
                JellyseerrEnvironment(
                    serverId = "srv-1",
                    serverName = "Requests",
                    baseUrl = "https://requests.test",
                    apiKey = "dummy-credential",
                    sessionCookie = null,
                )
            val coordinator =
                JellyseerrRecommendationsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = TestEnvironmentProvider(environment),
                    scope = this,
                )
            advanceUntilIdle()
            val item =
                JellyseerrSearchItem(
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                    title = "Fight Club",
                    overview = null,
                    releaseYear = "1999",
                    posterPath = null,
                    backdropPath = null,
                    mediaInfoId = null,
                    tvdbId = null,
                    availability = JellyseerrMediaAvailability(null, null),
                    requests = emptyList(),
                )
            val key = item.mediaType to item.tmdbId
            coordinator.loadDetail(item)
            val initial =
                assertIs<JellyseerrMediaDetailState.Loaded>(
                    coordinator.details.first {
                        val loaded = it[key] as? JellyseerrMediaDetailState.Loaded
                        loaded != null && !loaded.enrichmentLoading
                    }[key],
                )
            assertTrue(JellyseerrDetailEnrichmentSection.SIMILAR in initial.detail.enrichment.failedSections)
            assertEquals(
                "Pulp Fiction",
                initial.detail.enrichment.recommendations
                    .single()
                    .title,
            )

            coordinator.retryDetailEnrichment(
                item,
                JellyseerrDetailEnrichmentSection.SIMILAR,
            )
            val retrying =
                assertIs<JellyseerrMediaDetailState.Loaded>(
                    coordinator.details.first {
                        val loaded = it[key] as? JellyseerrMediaDetailState.Loaded
                        JellyseerrDetailEnrichmentSection.SIMILAR in
                            loaded?.enrichmentLoadingSections.orEmpty()
                    }[key],
                )
            assertEquals(
                "Pulp Fiction",
                retrying.detail.enrichment.recommendations
                    .single()
                    .title,
            )
            assertTrue(JellyseerrDetailEnrichmentSection.SIMILAR in retrying.detail.enrichment.failedSections)

            retryGate.complete(Unit)

            val retried =
                assertIs<JellyseerrMediaDetailState.Loaded>(
                    coordinator.details.first {
                        val loaded = it[key] as? JellyseerrMediaDetailState.Loaded
                        loaded != null &&
                            JellyseerrDetailEnrichmentSection.SIMILAR !in
                            loaded.enrichmentLoadingSections &&
                            JellyseerrDetailEnrichmentSection.SIMILAR !in
                            loaded.detail.enrichment.failedSections &&
                            loaded.detail.enrichment.similar
                                .isNotEmpty()
                    }[key],
                )
            assertFalse(JellyseerrDetailEnrichmentSection.SIMILAR in retried.detail.enrichment.failedSections)
            assertEquals(
                "Forrest Gump",
                retried.detail.enrichment.similar
                    .single()
                    .title,
            )
            assertEquals(
                "Pulp Fiction",
                retried.detail.enrichment.recommendations
                    .single()
                    .title,
            )
            assertEquals(1, ratingsCalls)
            assertEquals(2, similarCalls)
            assertEquals(1, recommendationCalls)

            coordinator.shutdown()
            advanceUntilIdle()
        }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun requestSummary(
        id: Int,
        mediaType: JellyseerrMediaType,
        title: String,
    ) = JellyseerrRequestSummary(
        id = id,
        mediaId = id,
        tmdbId = 42,
        tvdbId = null,
        title = title,
        originalTitle = null,
        mediaType = mediaType,
        requestStatus = JellyseerrRequestStatus.PENDING,
        availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.PENDING,
                `4k` = null,
            ),
        is4k = false,
        canRemoveFromService = false,
        createdAt = null,
        updatedAt = null,
        requestedBy = null,
        profileName = null,
        seasons = emptyList(),
        posterPath = null,
        backdropPath = null,
    )

    private class TestEnvironmentProvider(
        initial: JellyseerrEnvironment?,
    ) : JellyseerrEnvironmentProvider {
        private val state = MutableStateFlow(initial)

        override suspend fun current(): JellyseerrEnvironment? = state.value

        override fun observe(): Flow<JellyseerrEnvironment?> = state
    }
}
