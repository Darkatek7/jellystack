package dev.jellystack.network.jellyseerr

import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JellyseerrApiTest {
    @Test
    fun jellyfinQuickConnectUsesOfficialSeerrEndpoints() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val responses =
                ArrayDeque(
                    listOf(
                        """{"code":"123456","secret":"dummy-quick-connect-secret"}""",
                        """{"authenticated":true}""",
                        """{"id":7,"email":"quick@example.test","jellyfinUserId":"quick-user"}""",
                    ),
                )
            val engine =
                MockEngine { request ->
                    requests += request
                    respondJson(responses.removeFirst())
                }
            val api =
                JellyseerrApi.create(
                    "https://requests.test",
                    apiKey = null,
                    client = engine.jsonClient(),
                )

            val session = api.initiateJellyfinQuickConnect()
            val status = api.checkJellyfinQuickConnect(session.secret)
            api.loginWithJellyfinQuickConnect(session.secret)

            assertEquals("123456", session.code)
            assertTrue(status.authenticated)
            assertEquals(
                listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Post),
                requests.map { it.method },
            )
            assertEquals(
                listOf(
                    "/api/v1/auth/jellyfin/quickconnect/initiate",
                    "/api/v1/auth/jellyfin/quickconnect/check",
                    "/api/v1/auth/jellyfin/quickconnect/authenticate",
                ),
                requests.map { it.url.encodedPath },
            )
            assertEquals("dummy-quick-connect-secret", requests[1].url.parameters["secret"])
            assertEquals("""{"secret":"dummy-quick-connect-secret"}""", requests[2].bodyText())
        }

    @Test
    fun movieDetailsAndRatingsExposeSeerrScores() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/movie/550" ->
                            respondJson("""{"id":550,"title":"Fight Club","voteAverage":8.4,"voteCount":31000}""")
                        "/api/v1/movie/550/ratingscombined" ->
                            respondJson(
                                """{"rt":{"criticsScore":81,"audienceScore":96},"imdb":{"criticsScore":8.8}}""",
                            )
                        else -> error("Unexpected request ${request.method} ${request.url}")
                    }
                }
            val client = engine.jsonClient()
            val api = JellyseerrApi.create("https://requests.test", apiKey = "dummy-api-key", client = client)

            val detail = api.getMovieDetails(550)
            val ratings = api.getMovieRatingsCombined(550)

            assertEquals(8.4, detail.voteAverage)
            assertEquals(31_000, detail.voteCount)
            assertEquals(81.0, ratings.rt?.criticsScore)
            assertEquals(96.0, ratings.rt?.audienceScore)
            assertEquals(8.8, ratings.imdb?.criticsScore)
        }

    @Test
    fun tvRatingsExposeRottenTomatoesScores() =
        runTest {
            val engine = MockEngine { respondJson("""{"criticsScore":92,"audienceScore":88}""") }
            val client = engine.jsonClient()
            val api = JellyseerrApi.create("https://requests.test", apiKey = "dummy-api-key", client = client)

            val ratings = api.getTvRatings(1399)

            assertEquals(92.0, ratings.criticsScore)
            assertEquals(88.0, ratings.audienceScore)
        }

    @Test
    fun richMovieAndTvDetailsDecodeNestedMetadata() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/movie/550" ->
                            respondJson(
                                """
                                {
                                  "id":550,
                                  "imdbId":"tt0137523",
                                  "title":"Fight Club",
                                  "originalTitle":"Fight Club",
                                  "tagline":"Mischief. Mayhem. Soap.",
                                  "status":"Released",
                                  "budget":63000000,
                                  "spokenLanguages":[{"englishName":"English","iso_639_1":"en","name":"English"}],
                                  "releases":{"results":[{"iso_3166_1":"US","rating":"R"}]},
                                  "credits":{
                                    "cast":[{"id":287,"name":"Brad Pitt","character":"Tyler Durden","order":0,"profilePath":"/pitt.jpg"}],
                                    "crew":[{"id":7467,"name":"David Fincher","job":"Director","department":"Directing"}]
                                  },
                                  "collection":{"id":1,"name":"Fight Club Collection","posterPath":"/collection.jpg"},
                                  "keywords":[{"id":825,"name":"support group"}],
                                  "relatedVideos":[{"id":"trailer","key":"abc","site":"YouTube","type":"Trailer","official":true}]
                                }
                                """.trimIndent(),
                            )
                        "/api/v1/tv/1399" ->
                            respondJson(
                                """
                                {
                                  "id":1399,
                                  "name":"Game of Thrones",
                                  "originalName":"Game of Thrones",
                                  "tagline":"Winter Is Coming",
                                  "status":"Ended",
                                  "spokenLanguages":[{"englishName":"English","iso_639_1":"en","name":"English"}],
                                  "contentRatings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
                                  "createdBy":[{"id":9813,"name":"David Benioff"}],
                                  "credits":{"cast":[{"id":239019,"name":"Emilia Clarke","character":"Daenerys Targaryen"}]},
                                  "seasons":[{"id":3624,"seasonNumber":1,"name":"Season 1","episodeCount":10,"airDate":"2011-04-17"}],
                                  "keywords":[{"id":123,"name":"dragon"}],
                                  "relatedVideos":[{"id":"teaser","key":"xyz","site":"YouTube","type":"Teaser"}]
                                }
                                """.trimIndent(),
                            )
                        else -> error("Unexpected request ${request.method} ${request.url}")
                    }
                }
            val api = JellyseerrApi.create("https://requests.test", apiKey = "dummy-api-key", client = engine.jsonClient())

            val movie = api.getMovieDetails(550)
            val tv = api.getTvDetails(1399)

            assertEquals("tt0137523", movie.imdbId)
            assertEquals(
                "R",
                movie.releases
                    ?.results
                    ?.single()
                    ?.rating,
            )
            assertEquals(
                "Tyler Durden",
                movie.credits
                    ?.cast
                    ?.single()
                    ?.character,
            )
            assertEquals(
                "Director",
                movie.credits
                    ?.crew
                    ?.single()
                    ?.job,
            )
            assertEquals("Fight Club Collection", movie.collection?.name)
            assertEquals("support group", movie.keywords.single().name)
            assertEquals(
                "TV-MA",
                tv.contentRatings
                    ?.results
                    ?.single()
                    ?.rating,
            )
            assertEquals("David Benioff", tv.createdBy.single().name)
            assertEquals(1, tv.seasons.single().seasonNumber)
            assertEquals("dragon", tv.keywords.single().name)
            assertEquals("Teaser", tv.videos?.single()?.type)
        }

    @Test
    fun relatedEndpointsUseExpectedPathsAndPaging() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respondJson("""{"page":2,"totalPages":3,"totalResults":1,"results":[]}""")
                }
            val api = JellyseerrApi.create("https://requests.test", apiKey = "dummy-api-key", client = engine.jsonClient())

            api.getMovieSimilar(550, page = 2, language = "de")
            api.getMovieRecommendations(550, page = 2, language = "de")
            api.getTvSimilar(1399, page = 2, language = "de")
            api.getTvRecommendations(1399, page = 2, language = "de")

            assertEquals(
                listOf(
                    "/api/v1/movie/550/similar",
                    "/api/v1/movie/550/recommendations",
                    "/api/v1/tv/1399/similar",
                    "/api/v1/tv/1399/recommendations",
                ),
                requests.map { it.url.encodedPath },
            )
            assertTrue(requests.all { it.url.parameters["page"] == "2" })
            assertTrue(requests.all { it.url.parameters["language"] == "de" })
            assertTrue(requests.all { it.headers["X-API-Key"] == "dummy-api-key" })
        }

    @Test
    fun refreshesSessionCookieOnUnauthorized() =
        runTest {
            val captured = mutableListOf<HttpRequestData>()
            var callCount = 0
            val engine =
                MockEngine { request ->
                    captured += request
                    callCount += 1
                    if (callCount == 1) {
                        respond(
                            content = ByteReadChannel.Empty,
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respondSuccessfulEmpty()
                    }
                }
            val client =
                HttpClient(engine) {
                    expectSuccess = true
                }
            var currentCookie = "connect.sid=initial"
            var refreshCount = 0
            val handler =
                object : JellyseerrSessionCookieHandler {
                    override suspend fun currentCookie(): String? = currentCookie

                    override suspend fun refreshCookie(): String? {
                        refreshCount += 1
                        currentCookie = "connect.sid=refreshed"
                        return currentCookie
                    }
                }
            val api =
                JellyseerrApi.create(
                    baseUrl = "https://requests.example",
                    apiKey = null,
                    sessionCookie = currentCookie,
                    sessionHandler = handler,
                    client = client,
                )

            api.deleteRequest(123)

            assertEquals(2, captured.size)
            assertEquals("connect.sid=initial", captured.first().headers[HttpHeaders.Cookie])
            assertEquals("connect.sid=refreshed", captured.last().headers[HttpHeaders.Cookie])
            assertEquals(1, refreshCount)
        }

    @Test
    fun sessionRefreshPreservesCancellation() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel.Empty,
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val handler =
                object : JellyseerrSessionCookieHandler {
                    override suspend fun currentCookie(): String? = "connect.sid=expired"

                    override suspend fun refreshCookie(): String? = throw CancellationException("screen closed")
                }
            val api =
                JellyseerrApi.create(
                    baseUrl = "https://requests.example",
                    apiKey = null,
                    sessionCookie = "connect.sid=expired",
                    sessionHandler = handler,
                    client =
                        HttpClient(engine) {
                            expectSuccess = true
                        },
                )

            assertFailsWith<CancellationException> {
                api.deleteRequest(123)
            }
        }

    private fun MockRequestHandleScope.respondSuccessfulEmpty() =
        respond(
            content = ByteReadChannel.Empty,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockEngine.jsonClient(): HttpClient =
        HttpClient(this) {
            expectSuccess = true
            install(ContentNegotiation) { json(NetworkJson.default) }
        }

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is io.ktor.http.content.TextContent -> content.text
            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
}
