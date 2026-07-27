package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLogBuffer
import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JellyseerrRepositoryTest {
    private val environment =
        JellyseerrEnvironment(
            serverId = "srv-1",
            serverName = "Requests",
            baseUrl = "https://requests.test",
            apiKey = "dummy-credential",
            sessionCookie = null,
        )

    @Test
    fun duplicateRequestsReturnFriendlyResult() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request") {
                            respondJson(
                                status = HttpStatusCode.Conflict,
                                body = """{"status":409,"message":"This title has already been requested."}""",
                            )
                        } else {
                            error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val result =
                repository.createRequest(
                    environment,
                    JellyseerrCreateRequest(
                        mediaId = 100,
                        tvdbId = null,
                        mediaType = JellyseerrMediaType.MOVIE,
                    ),
                )

            assertIs<JellyseerrCreateResult.Duplicate>(result)
            assertTrue(result.message.contains("already", ignoreCase = true))
        }

    @Test
    fun submitRequestPrioritisesTmdbIdAndLogs() =
        runTest {
            JellystackLogBuffer.clear()
            val recordedBodies = mutableListOf<String>()
            val bodyRecorded = Channel<Unit>(Channel.UNLIMITED)
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request" -> {
                                val bodyText = (request.body as? TextContent)?.text
                                assertNotNull(bodyText, "Expected JSON request body")
                                recordedBodies += bodyText
                                bodyRecorded.send(Unit)
                                respond(
                                    content =
                                        """
                                        {
                                          "id": 401,
                                          "status": 1,
                                          "type": "movie",
                                          "createdAt": "2024-10-01T00:00:00.000Z",
                                          "updatedAt": "2024-10-01T00:00:00.000Z",
                                          "mediaId": 401,
                                          "mediaType": "movie",
                                          "media": null
                                        }
                                        """.trimIndent(),
                                    status = HttpStatusCode.Created,
                                    headers =
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            "application/json",
                                        ),
                                )
                            }
                            else -> respondJson("{}", HttpStatusCode.NotFound)
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)
            val provider = TestEnvironmentProvider(environment)
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = repository,
                    environmentProvider = provider,
                    scope = this,
                    pollIntervalMillis = 60_000,
                    enablePolling = false,
                )

            advanceUntilIdle()

            val item =
                JellyseerrSearchItem(
                    tmdbId = 1234,
                    mediaType = JellyseerrMediaType.MOVIE,
                    title = "Example Movie",
                    overview = "Overview",
                    releaseYear = "2024",
                    posterPath = null,
                    backdropPath = null,
                    mediaInfoId = 9999,
                    tvdbId = 567,
                    availability =
                        JellyseerrMediaAvailability(
                            standard = JellyseerrMediaStatus.UNKNOWN,
                            `4k` = null,
                        ),
                    requests = emptyList(),
                )
            val profile =
                JellyseerrLanguageProfileOption(
                    languageProfileId = 44,
                    name = "HD",
                    serviceId = 301,
                    serviceName = "Radarr",
                    is4k = false,
                    isDefault = true,
                    profileId = 22,
                )

            coordinator.submitRequest(
                item = item,
                profileSelection = JellyseerrRequestProfileSelection.ServerDefault,
            )
            advanceUntilIdle()
            bodyRecorded.receive()

            val serverDefaultPayload =
                NetworkJson.default.parseToJsonElement(recordedBodies.single()).jsonObject
            assertEquals(item.tmdbId, serverDefaultPayload["mediaId"]?.jsonPrimitive?.int)
            assertFalse("serverId" in serverDefaultPayload)
            assertFalse("profileId" in serverDefaultPayload)
            assertFalse("languageProfileId" in serverDefaultPayload)

            val namedItem = item.copy(tmdbId = item.tmdbId + 1, title = "Named profile movie")
            coordinator.submitRequest(
                item = namedItem,
                profileSelection = JellyseerrRequestProfileSelection.Profile(profile),
            )
            advanceUntilIdle()
            bodyRecorded.receive()

            val namedPayload =
                NetworkJson.default.parseToJsonElement(recordedBodies.last()).jsonObject
            assertEquals(namedItem.tmdbId, namedPayload["mediaId"]?.jsonPrimitive?.int)
            assertEquals(profile.serviceId, namedPayload["serverId"]?.jsonPrimitive?.int)
            assertEquals(profile.profileId, namedPayload["profileId"]?.jsonPrimitive?.int)
            assertEquals(
                profile.languageProfileId,
                namedPayload["languageProfileId"]?.jsonPrimitive?.int,
            )

            val debugEntries = JellystackLogBuffer.entries.filter { it.startsWith("D:") }
            assertTrue(debugEntries.any { it.contains("Submitting Jellyseerr request tmdbId=${item.tmdbId}") })
            assertTrue(debugEntries.any { it.contains("Creating Seerr request payload id=${item.tmdbId}") })

            coordinator.shutdown()
        }

    @Test
    fun rapidSubmitRequestsRetainDistinctIds() =
        runTest {
            JellystackLogBuffer.clear()
            val recordedBodies = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/request" -> {
                                val bodyText = (request.body as? TextContent)?.text
                                assertNotNull(bodyText, "Expected JSON request body")
                                recordedBodies += bodyText
                                respond(
                                    content =
                                        """
                                        {
                                          "id": 401,
                                          "status": 1,
                                          "type": "movie",
                                          "createdAt": "2024-10-01T00:00:00.000Z",
                                          "updatedAt": "2024-10-01T00:00:00.000Z",
                                          "mediaId": 401,
                                          "mediaType": "movie",
                                          "media": null
                                        }
                                        """.trimIndent(),
                                    status = HttpStatusCode.Created,
                                    headers =
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            "application/json",
                                        ),
                                )
                            }
                            else -> respondJson("{}", HttpStatusCode.NotFound)
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)
            val provider = TestEnvironmentProvider(environment)
            val coordinator =
                JellyseerrRequestsCoordinator(
                    repository = repository,
                    environmentProvider = provider,
                    scope = this,
                    pollIntervalMillis = 60_000,
                    enablePolling = false,
                )

            advanceUntilIdle()

            val profile =
                JellyseerrLanguageProfileOption(
                    languageProfileId = 44,
                    name = "HD",
                    serviceId = 301,
                    serviceName = "Radarr",
                    is4k = false,
                    isDefault = true,
                    profileId = 22,
                )
            val first =
                JellyseerrSearchItem(
                    tmdbId = 2001,
                    mediaType = JellyseerrMediaType.MOVIE,
                    title = "First",
                    overview = null,
                    releaseYear = null,
                    posterPath = null,
                    backdropPath = null,
                    mediaInfoId = 9001,
                    tvdbId = null,
                    availability =
                        JellyseerrMediaAvailability(
                            standard = JellyseerrMediaStatus.UNKNOWN,
                            `4k` = null,
                        ),
                    requests = emptyList(),
                )
            val second =
                JellyseerrSearchItem(
                    tmdbId = 2002,
                    mediaType = JellyseerrMediaType.TV,
                    title = "Second",
                    overview = null,
                    releaseYear = null,
                    posterPath = null,
                    backdropPath = null,
                    mediaInfoId = 9002,
                    tvdbId = 888,
                    availability =
                        JellyseerrMediaAvailability(
                            standard = JellyseerrMediaStatus.UNKNOWN,
                            `4k` = null,
                        ),
                    requests = emptyList(),
                )

            coordinator.submitRequest(first, profile)
            coordinator.submitRequest(second, profile)
            advanceUntilIdle()

            assertEquals(2, recordedBodies.size)
            val submittedIds =
                recordedBodies
                    .mapNotNull { body ->
                        NetworkJson.default
                            .parseToJsonElement(body)
                            .jsonObject["mediaId"]
                            ?.jsonPrimitive
                            ?.int
                    }.toSet()
            assertEquals(setOf(first.tmdbId, second.tmdbId), submittedIds)

            val submitLogs =
                JellystackLogBuffer.entries
                    .filter { it.startsWith("D:Submitting Jellyseerr request tmdbId=") }
                    .mapNotNull { entry ->
                        entry.substringAfter("tmdbId=").substringBefore(' ').toIntOrNull()
                    }
            assertEquals(2, submitLogs.size)
            assertTrue(submitLogs.contains(first.tmdbId))
            assertTrue(submitLogs.contains(second.tmdbId))

            coordinator.shutdown()
        }

    @Test
    fun fetchRequestsMapsStatuses() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when {
                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request" ->
                                respondJson(
                                    body =
                                        """
                                        {
                                          "pageInfo":{"pages":1,"pageSize":20,"results":1,"page":1},
                                          "results":[
                                            {
                                              "id":101,
                                              "status":2,
                                              "type":"movie",
                                              "mediaId":77,
                                              "createdAt":"2024-09-01T10:00:00.000Z",
                                              "updatedAt":"2024-09-01T12:00:00.000Z",
                                              "is4k":false,
                                              "canRemove":true,
                                              "profileName":"HD-1080p",
                                              "requestedBy":{"id":4,"displayName":"Alice","username":"alice","permissions":18},
                                              "media":{
                                                "id":77,
                                                "tmdbId":555,
                                                "mediaType":"movie",
                                                "status":5,
                                                "status4k":2,
                                                "title":"Dune Part Two",
                                                "requests":[]
                                              },
                                              "seasons":[]
                                            }
                                          ]
                                        }
                                        """.trimIndent(),
                                )
                            request.method == HttpMethod.Get && request.url.encodedPath == "/api/v1/request/count" ->
                                respondJson(
                                    """{"total":1,"movie":1,"pending":0,"approved":1,"processing":0,"available":1,"completed":0,"declined":0,"tv":0}""",
                                )
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val page = repository.fetchRequests(environment, JellyseerrRequestFilter.ALL)

            assertEquals(1, page.results.size)
            val request = page.results.first()
            assertEquals(JellyseerrRequestStatus.APPROVED, request.requestStatus)
            assertEquals(JellyseerrMediaStatus.AVAILABLE, request.availability.standard)
            assertEquals("Alice", request.requestedBy?.displayName)
        }

    @Test
    fun fetchRecommendationDetailReadsTrailerFromRelatedVideos() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.encodedPath == "/api/v1/movie/12345") {
                            respondJson(
                                status = HttpStatusCode.OK,
                                body =
                                    """
                                    {
                                      "id": 12345,
                                      "title": "Toy Story 5",
                                      "relatedVideos": [
                                        {
                                          "id": "v1",
                                          "key": "dQw4w9WgXcQ",
                                          "site": "YouTube",
                                          "type": "Trailer",
                                          "name": "Official Trailer",
                                          "official": true
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                            )
                        } else {
                            error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val detail =
                repository.fetchRecommendationDetail(
                    environment,
                    tmdbId = 12345,
                    mediaType = JellyseerrMediaType.MOVIE,
                )

            val trailer = detail.trailer
            assertNotNull(trailer, "trailer should be parsed from relatedVideos")
            assertEquals("dQw4w9WgXcQ", trailer.key)
            assertEquals("YouTube", trailer.site)
            assertEquals("Official Trailer", trailer.name)
            assertEquals("Trailer", trailer.type)
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", trailer.url)
        }

    @Test
    fun fetchMovieRecommendationDetailCombinesAllSeerrRatings() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/v1/movie/550" ->
                                respondJson(
                                    """{"id":550,"title":"Fight Club","voteAverage":8.4,"voteCount":31000}""",
                                )
                            "/api/v1/movie/550/ratingscombined" ->
                                respondJson(
                                    """{"rt":{"criticsScore":81,"audienceScore":96},"imdb":{"criticsScore":8.8}}""",
                                )
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val detail =
                repository.fetchRecommendationDetail(
                    environment,
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                )

            val ratings = assertNotNull(detail.ratings)
            assertEquals(8.4, ratings.tmdb)
            assertEquals(8.8, ratings.imdb)
            assertEquals(81.0, ratings.rottenTomatoesCritics)
            assertEquals(96.0, ratings.rottenTomatoesAudience)
        }

    @Test
    fun fetchTvRecommendationDetailCombinesTmdbAndSeerrRatings() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/v1/tv/1399" ->
                                respondJson("""{"id":1399,"name":"Game of Thrones","voteAverage":8.5}""")
                            "/api/v1/tv/1399/ratings" ->
                                respondJson("""{"criticsScore":89,"audienceScore":85}""")
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val detail =
                repository.fetchRecommendationDetail(
                    environment,
                    tmdbId = 1399,
                    mediaType = JellyseerrMediaType.TV,
                )

            val ratings = assertNotNull(detail.ratings)
            assertEquals(8.5, ratings.tmdb)
            assertEquals(89.0, ratings.rottenTomatoesCritics)
            assertEquals(85.0, ratings.rottenTomatoesAudience)
        }

    @Test
    fun fetchTvRecommendationDetailMapsAvailableSeasons() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.encodedPath == "/api/v1/tv/77") {
                            respondJson(
                                body =
                                    """
                                    {
                                      "id": 77,
                                      "name": "Dune: Prophecy",
                                      "numberOfSeasons": 3
                                    }
                                    """.trimIndent(),
                            )
                        } else {
                            error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) {
                        json(NetworkJson.default)
                    }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val detail =
                repository.fetchRecommendationDetail(
                    environment,
                    tmdbId = 77,
                    mediaType = JellyseerrMediaType.TV,
                )

            assertEquals(listOf(1, 2, 3), detail.availableSeasons)
        }

    @Test
    fun fetchRecommendationPrimaryDetailReturnsWithoutOptionalRequests() =
        runTest {
            val requestedPaths = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requestedPaths += request.url.encodedPath
                        when (request.url.encodedPath) {
                            "/api/v1/movie/550" ->
                                respondJson(
                                    """
                                    {
                                      "id":550,
                                      "title":"Fight Club",
                                      "voteAverage":8.4,
                                      "tagline":"Mischief. Mayhem. Soap."
                                    }
                                    """.trimIndent(),
                                )
                            else -> error("Primary detail must not request optional endpoint ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val primary =
                repository.fetchRecommendationPrimaryDetail(
                    environment,
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                )

            assertEquals(listOf("/api/v1/movie/550"), requestedPaths)
            assertEquals("Fight Club", primary.title)
            assertEquals("Mischief. Mayhem. Soap.", primary.tagline)
            assertEquals(8.4, primary.ratings?.tmdb)
            assertEquals(primary.ratings, primary.enrichment.ratings)
            assertTrue(primary.enrichment.similar.isEmpty())
            assertTrue(primary.enrichment.recommendations.isEmpty())
            assertTrue(primary.enrichment.failedSections.isEmpty())
        }

    @Test
    fun fetchRecommendationDetailMapsRichMetadataAndRelatedTitles() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/v1/movie/550" ->
                                respondJson(
                                    """
                                    {
                                      "id":550,
                                      "title":"Fight Club",
                                      "originalTitle":"Fight Club",
                                      "tagline":"Mischief. Mayhem. Soap.",
                                      "status":"Released",
                                      "budget":63000000,
                                      "voteAverage":8.4,
                                      "spokenLanguages":[{"englishName":"English","iso_639_1":"en"}],
                                      "releases":{"results":[{"iso_3166_1":"US","rating":"R"}]},
                                      "credits":{
                                        "cast":[{"id":287,"name":"Brad Pitt","character":"Tyler Durden","order":0}],
                                        "crew":[{"id":7467,"name":"David Fincher","job":"Director","department":"Directing"}]
                                      },
                                      "collection":{"id":1,"name":"Fight Club Collection","posterPath":"/collection.jpg"},
                                      "keywords":[{"id":825,"name":"support group"}],
                                      "relatedVideos":[
                                        {"id":"trailer","name":"Trailer","key":"abc","site":"YouTube","type":"Trailer","official":true},
                                        {"id":"teaser","name":"Teaser","key":"def","site":"YouTube","type":"Teaser"}
                                      ]
                                    }
                                    """.trimIndent(),
                                )
                            "/api/v1/movie/550/ratingscombined" ->
                                respondJson("""{"rt":{"criticsScore":81},"imdb":{"criticsScore":8.8}}""")
                            "/api/v1/movie/550/similar" ->
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":13,"title":"Forrest Gump","releaseDate":"1994-07-06"}]}""",
                                )
                            "/api/v1/movie/550/recommendations" ->
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":680,"mediaType":"movie","title":"Pulp Fiction"}]}""",
                                )
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val detail =
                repository.fetchRecommendationDetail(
                    environment,
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                )

            assertEquals("Fight Club", detail.originalTitle)
            assertEquals("Mischief. Mayhem. Soap.", detail.tagline)
            assertEquals("R", detail.certification)
            assertEquals("Released", detail.status)
            assertEquals(63_000_000L, detail.budget)
            assertEquals(listOf("English"), detail.languages)
            assertEquals("Tyler Durden", detail.cast.single().character)
            assertEquals("Director", detail.crew.single().job)
            assertEquals("Fight Club Collection", detail.collection?.name)
            assertEquals(listOf("support group"), detail.keywords)
            assertEquals(listOf("Trailer", "Teaser"), detail.videos.map { it.type })
            assertEquals(8.8, detail.enrichment.ratings?.imdb)
            assertEquals(
                JellyseerrMediaType.MOVIE,
                detail.enrichment.similar
                    .single()
                    .mediaType,
            )
            assertEquals(
                "Forrest Gump",
                detail.enrichment.similar
                    .single()
                    .title,
            )
            assertEquals(
                "Pulp Fiction",
                detail.enrichment.recommendations
                    .single()
                    .title,
            )
            assertTrue(detail.enrichment.failedSections.isEmpty())
        }

    @Test
    fun optionalDetailFailuresRemainSectionLocal() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/v1/tv/1399" ->
                                respondJson(
                                    """
                                    {
                                      "id":1399,
                                      "name":"Game of Thrones",
                                      "originalName":"Game of Thrones",
                                      "voteAverage":8.5,
                                      "contentRatings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
                                      "createdBy":[{"id":9813,"name":"David Benioff"}],
                                      "seasons":[
                                        {"id":0,"seasonNumber":0,"name":"Specials","episodeCount":5},
                                        {"id":1,"seasonNumber":1,"name":"Season 1","episodeCount":10}
                                      ]
                                    }
                                    """.trimIndent(),
                                )
                            "/api/v1/tv/1399/ratings",
                            "/api/v1/tv/1399/similar",
                            ->
                                respondJson("""{"message":"optional service unavailable"}""", HttpStatusCode.ServiceUnavailable)
                            "/api/v1/tv/1399/recommendations" ->
                                respondJson(
                                    """{"page":1,"totalPages":1,"results":[{"id":94997,"name":"House of the Dragon"}]}""",
                                )
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)

            val primary =
                repository.fetchRecommendationPrimaryDetail(
                    environment,
                    tmdbId = 1399,
                    mediaType = JellyseerrMediaType.TV,
                )
            val enrichment = repository.fetchRecommendationDetailEnrichment(environment, primary)

            assertEquals(8.5, primary.ratings?.tmdb)
            assertEquals("TV-MA", primary.certification)
            assertEquals(listOf(0, 1), primary.seasons.map { it.seasonNumber })
            assertEquals(listOf(1), primary.availableSeasons)
            assertEquals("Creator", primary.crew.single().job)
            assertEquals(8.5, enrichment.ratings?.tmdb)
            assertTrue(JellyseerrDetailEnrichmentSection.RATINGS in enrichment.failedSections)
            assertTrue(JellyseerrDetailEnrichmentSection.SIMILAR in enrichment.failedSections)
            assertFalse(JellyseerrDetailEnrichmentSection.RECOMMENDATIONS in enrichment.failedSections)
            assertEquals(
                "House of the Dragon",
                enrichment.recommendations
                    .single()
                    .title,
            )
            assertEquals(
                JellyseerrMediaType.TV,
                enrichment.recommendations
                    .single()
                    .mediaType,
            )
        }

    @Test
    fun optionalDetailCancellationIsRethrown() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/api/v1/movie/550" -> respondJson("""{"id":550,"title":"Fight Club"}""")
                            "/api/v1/movie/550/ratingscombined" -> throw CancellationException("detail closed")
                            "/api/v1/movie/550/similar",
                            "/api/v1/movie/550/recommendations",
                            ->
                                respondJson("""{"page":1,"totalPages":1,"results":[]}""")
                            else -> error("Unexpected request ${request.method} ${request.url}")
                        }
                    },
                ) {
                    install(ContentNegotiation) { json(NetworkJson.default) }
                }
            val repository = JellyseerrRepository(httpClient = client)
            val primary =
                repository.fetchRecommendationPrimaryDetail(
                    environment,
                    tmdbId = 550,
                    mediaType = JellyseerrMediaType.MOVIE,
                )

            assertFailsWith<CancellationException> {
                repository.fetchRecommendationDetailEnrichment(environment, primary)
            }
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

    private class TestEnvironmentProvider(
        initial: JellyseerrEnvironment?,
    ) : JellyseerrEnvironmentProvider {
        private val state = MutableStateFlow(initial)

        override suspend fun current(): JellyseerrEnvironment? = state.value

        override fun observe(): Flow<JellyseerrEnvironment?> = state
    }
}
