package dev.jellystack.core.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JellyseerrRequestsCoordinatorTest {
    private val environment =
        JellyseerrEnvironment(
            serverId = "srv-1",
            serverName = "Requests",
            baseUrl = "https://requests.test",
            apiKey = "dummy-credential",
            sessionCookie = null,
        )

    @Test
    fun emitsReadyStateWhenServerPresent() =
        runTest {
            val client = mockClient()
            val repository = JellyseerrRepository(httpClient = client)
            val provider = FakeEnvironmentProvider()
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = repository,
                    environmentProvider = provider,
                    scope = this,
                    pollIntervalMillis = 60_000,
                    enablePolling = false,
                    clock = FixedClock,
                )

            provider.update(environment)
            val ready = coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            assertEquals(1, ready.requests.size)
            assertEquals(
                "Admin",
                ready.requests
                    .first()
                    .requestedBy
                    ?.displayName,
            )
            assertEquals(1, ready.currentUserId)
            assertEquals(201, ready.currentRequestsByMedia[JellyseerrMediaType.MOVIE to 777]?.id)

            coordinator.shutdown()
        }

    @Test
    fun duplicateSubmitIsAdmittedOnlyOncePerMediaKey() =
        runTest {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseRequest = CompletableDeferred<Unit>()
            var createCalls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request") {
                            createCalls += 1
                            requestStarted.complete(Unit)
                            releaseRequest.await()
                            respondJson(createdRequestResponse(id = 301, tmdbId = 778))
                        } else {
                            defaultResponses(request)
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = FakeEnvironmentProvider(environment),
                    scope = this,
                    enablePolling = false,
                    clock = FixedClock,
                )
            coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            val item = movieItem(tmdbId = 778, title = "Dune")

            coordinator.submitRequest(item, JellyseerrRequestProfileSelection.ServerDefault)
            requestStarted.await()
            coordinator.submitRequest(item, JellyseerrRequestProfileSelection.ServerDefault)
            advanceUntilIdle()
            assertEquals(1, createCalls)

            releaseRequest.complete(Unit)
            val submitted =
                coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                    it.message?.code == JellyseerrMessageCode.RequestSubmitted
                }
            assertEquals(1, createCalls)
            assertEquals(
                JellyseerrOperationKey.Submit(item.mediaType, item.tmdbId),
                submitted.message?.operationKey,
            )
            coordinator.shutdown()
        }

    @Test
    fun submittedRequestRemainsInCurrentLookupWhenActiveFilterExcludesIt() =
        runTest {
            var availableFetches = 0
            val firstAvailableFetch = CompletableDeferred<Unit>()
            val submittedRefresh = CompletableDeferred<Unit>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Get &&
                                request.url.encodedPath == "/api/v1/request" &&
                                request.url.parameters["filter"] == "available" -> {
                                availableFetches += 1
                                firstAvailableFetch.complete(Unit)
                                if (availableFetches >= 2) submittedRefresh.complete(Unit)
                                respondJson(emptyRequestsResponse())
                            }
                            request.method == HttpMethod.Post &&
                                request.url.encodedPath == "/api/v1/request" ->
                                respondJson(createdRequestResponse(id = 302, tmdbId = 778))
                            else -> defaultResponses(request)
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = FakeEnvironmentProvider(environment),
                    scope = this,
                    enablePolling = false,
                    clock = FixedClock,
                )
            coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            coordinator.selectFilter(JellyseerrRequestFilter.AVAILABLE)
            firstAvailableFetch.await()
            advanceUntilIdle()

            coordinator.submitRequest(
                movieItem(tmdbId = 778, title = "Dune"),
                JellyseerrRequestProfileSelection.ServerDefault,
            )
            submittedRefresh.await()

            val ready =
                coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                    !it.isRefreshing &&
                        it.requests.isEmpty() &&
                        it.currentRequestsByMedia[JellyseerrMediaType.MOVIE to 778]?.id == 302
                }
            assertEquals(JellyseerrRequestFilter.AVAILABLE, ready.filter)
            assertEquals(emptyList(), ready.requests)
            assertEquals(302, ready.currentRequestsByMedia[JellyseerrMediaType.MOVIE to 778]?.id)
            assertEquals(JellyseerrRequestStatus.PENDING, ready.currentRequestsByMedia[JellyseerrMediaType.MOVIE to 778]?.requestStatus)
            coordinator.shutdown()
        }

    @Test
    fun requestFailuresAreSemanticAndPreserveSearchStateWithUniqueIds() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/search" ->
                                respondJson(
                                    """
                                    {
                                      "page":1,
                                      "totalPages":1,
                                      "totalResults":1,
                                      "results":[{
                                        "id":778,
                                        "mediaType":"movie",
                                        "title":"Dune",
                                        "overview":"Desert power",
                                        "releaseDate":"2021-10-22"
                                      }]
                                    }
                                    """.trimIndent(),
                                )
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request" ->
                                respondJson("""{"status":500,"message":"Upstream unavailable"}""", HttpStatusCode.InternalServerError)
                            else -> defaultResponses(request)
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = FakeEnvironmentProvider(environment),
                    scope = this,
                    pollIntervalMillis = 60_000,
                    enablePolling = false,
                    clock = FixedClock,
                )

            coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            coordinator.search("Dune")
            val searched =
                coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                    !it.isSearching && it.query == "Dune" && it.searchResults.isNotEmpty()
                }
            val item = searched.searchResults.single()

            coordinator.submitRequest(item, JellyseerrRequestProfileSelection.ServerDefault)
            val firstFailure =
                coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                    it.message?.code == JellyseerrMessageCode.RequestFailed
                }
            coordinator.acknowledgeMessage()
            coordinator.submitRequest(item, JellyseerrRequestProfileSelection.ServerDefault)
            val secondFailure =
                coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                    it.message?.code == JellyseerrMessageCode.RequestFailed &&
                        it.message.id != firstFailure.message?.id
                }

            assertEquals("Dune", secondFailure.message?.subject)
            assertEquals("Upstream unavailable", secondFailure.message?.detail)
            assertEquals(JellyseerrMessageRecovery.RefreshRequests, secondFailure.message?.recovery)
            assertEquals("Dune", secondFailure.searchResults.single().title)
            assertEquals("Dune", secondFailure.query)
            assertNotEquals(firstFailure.message?.id, secondFailure.message?.id)
            coordinator.shutdown()
        }

    @Test
    fun newerSearchCannotBeOverwrittenByAnOlderBlockedResponse() =
        runTest {
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/search") {
                            val query = request.url.parameters["query"].orEmpty()
                            if (query == "Dune") {
                                oldStarted.complete(Unit)
                                releaseOld.await()
                            }
                            respondJson(searchResponse(title = query, id = if (query == "Dune") 1 else 2))
                        } else {
                            defaultResponses(request)
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = JellyseerrRepository(httpClient = client),
                    environmentProvider = FakeEnvironmentProvider(environment),
                    scope = this,
                    enablePolling = false,
                    clock = FixedClock,
                )

            coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first()
            coordinator.search("Dune")
            oldStarted.await()
            coordinator.search("Dune 2")
            coordinator.state.filterIsInstance<JellyseerrRequestsState.Ready>().first {
                !it.isSearching && it.query == "Dune 2" && it.searchResults.singleOrNull()?.title == "Dune 2"
            }

            releaseOld.complete(Unit)
            advanceUntilIdle()

            val final = coordinator.state.value as JellyseerrRequestsState.Ready
            assertEquals("Dune 2", final.query)
            assertEquals("Dune 2", final.searchResults.single().title)
            coordinator.shutdown()
        }

    private fun searchResponse(
        title: String,
        id: Int,
    ) = """
        {
          "page":1,
          "totalPages":1,
          "totalResults":1,
          "results":[{
            "id":$id,
            "mediaType":"movie",
            "title":"$title",
            "releaseDate":"2021-10-22"
          }]
        }
        """.trimIndent()

    private fun movieItem(
        tmdbId: Int,
        title: String,
    ) = JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = JellyseerrMediaType.MOVIE,
        title = title,
        overview = null,
        releaseYear = null,
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )

    private fun createdRequestResponse(
        id: Int,
        tmdbId: Int,
    ) = """
        {
          "id":$id,
          "status":1,
          "type":"movie",
          "mediaId":$id,
          "createdAt":"2024-10-01T10:00:00.000Z",
          "updatedAt":"2024-10-01T10:00:00.000Z",
          "is4k":false,
          "requestedBy":{"id":1,"displayName":"Admin","username":"admin","permissions":18},
          "media":{"id":$id,"tmdbId":$tmdbId,"mediaType":"movie","status":2,"status4k":1,"title":"Dune"},
          "seasons":[]
        }
        """.trimIndent()

    private fun emptyRequestsResponse() =
        """
        {
          "pageInfo":{"pages":1,"pageSize":20,"results":0,"page":1},
          "results":[]
        }
        """.trimIndent()

    private fun mockClient(): HttpClient =
        HttpClient(
            MockEngine {
                defaultResponses(it)
            },
        ) {
            install(ContentNegotiation) {
                json(NetworkJson.default)
            }
        }

    private suspend fun MockRequestHandleScope.defaultResponses(request: HttpRequestData) =
        when {
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/auth/me" ->
                respondJson("""{"id":1,"displayName":"Admin","permissions":18}""")
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request" ->
                respondJson(
                    """
                    {
                      "pageInfo":{"pages":1,"pageSize":20,"results":1,"page":1},
                      "results":[
                        {
                          "id":201,
                          "status":1,
                          "type":"movie",
                          "mediaId":90,
                          "createdAt":"2024-10-01T10:00:00.000Z",
                          "updatedAt":"2024-10-01T10:00:00.000Z",
                          "is4k":false,
                          "requestedBy":{"id":1,"displayName":"Admin","username":"admin","permissions":18},
                          "media":{"id":90,"tmdbId":777,"mediaType":"movie","status":2,"status4k":1,"title":"New Movie"},
                          "seasons":[]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request/count" ->
                respondJson(
                    """{"total":1,"movie":1,"pending":1,"approved":0,"processing":0,"available":0,"completed":0,"declined":0,"tv":0}""",
                )
            else -> respondJson("{}", HttpStatusCode.NotFound)
        }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers =
            headersOf(
                HttpHeaders.ContentType,
                "application/json",
            ),
    )

    private class FakeEnvironmentProvider(
        initial: JellyseerrEnvironment? = null,
    ) : JellyseerrEnvironmentProvider {
        private val state = MutableStateFlow(initial)

        override suspend fun current(): JellyseerrEnvironment? = state.value

        override fun observe(): Flow<JellyseerrEnvironment?> = state

        fun update(environment: JellyseerrEnvironment?) {
            state.value = environment
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }
}
