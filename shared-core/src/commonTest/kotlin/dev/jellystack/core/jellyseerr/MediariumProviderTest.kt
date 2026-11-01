package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediariumProviderTest {
    private val environment =
        JellyseerrEnvironment(
            "fictional-server",
            "Mediarium",
            "https://media.example.com",
            "fictional-key",
            null,
            provider = RequestProviderType.MEDIARIUM,
        )

    @Test
    fun repositoryRoutesByProviderAndDefaultsToSeerr() =
        runTest {
            val paths = mutableListOf<String>()
            val client =
                client { path ->
                    paths += path
                    when (path) {
                        "/api/discover/search" -> """[{"tmdbId":101,"mediaType":"movie","title":"Fiction"}]"""
                        "/api/v1/search" -> """{"results":[{"id":102,"mediaType":"movie","title":"Other fiction"}]}"""
                        else -> error("Unexpected path $path")
                    }
                }
            try {
                val repository = JellyseerrRepository(httpClient = client)
                assertEquals(101, repository.search(environment, "fiction").single().tmdbId)
                val seerr = JellyseerrEnvironment("other", "Seerr", "https://requests.example.com", "other-key", null)
                assertEquals(102, repository.search(seerr, "fiction").single().tmdbId)
                assertEquals(listOf("/api/discover/search", "/api/v1/search"), paths)
            } finally {
                client.close()
            }
        }

    @Test
    fun requestHistoryNeverGuessesTmdbIdentityOrPlayableAvailability() =
        runTest {
            val client =
                client {
                    """{"requests":[{"id":7,"kind":"movie","title":"Fiction","status":"approved","availableAt":"2026-01-01T00:00:00Z"}]}"""
                }
            try {
                val page = MediariumProvider(client).fetchRequests(environment, JellyseerrRequestFilter.ALL)
                val request = page.results.single()
                assertNull(request.tmdbId)
                assertNull(request.mediaId)
                assertFalse(request.availability.isAvailable)
                assertEquals(JellyseerrRequestStatus.APPROVED, request.requestStatus)
            } finally {
                client.close()
            }
        }

    @Test
    fun downloadedInMediariumDoesNotImplyPlayableInJellyfin() =
        runTest {
            val client =
                client {
                    """{"tmdbId":101,"title":"Fiction","libraryId":73,"status":"downloaded","genres":null,"seasonList":null}"""
                }
            try {
                val detail = MediariumProvider(client).fetchRecommendationDetail(environment, 101, JellyseerrMediaType.MOVIE)
                assertFalse(detail.availability.isAvailable)
                assertEquals("downloaded", detail.status)
                assertNull(detail.jellyfinUrl)
                assertNull(detail.jellyseerrUrl)
            } finally {
                client.close()
            }
        }

    @Test
    fun finiteSearchDoesNotRepeatFirstPageAndUnsupportedOptionsNeverReachServer() =
        runTest {
            var calls = 0
            val client =
                client {
                    calls++
                    "[]"
                }
            try {
                val provider = MediariumProvider(client)
                assertTrue(provider.search(environment, "fiction", 2).isEmpty())
                val request =
                    JellyseerrCreateRequest(101, null, JellyseerrMediaType.TV, seasons = JellyseerrCreateSelection.Seasons(listOf(1)))
                assertIs<JellyseerrCreateResult.Failure>(provider.createRequest(environment, request))
                assertEquals(0, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun profileMapsPerMediaPermissionWithoutGrantingSeerrAdminFeatures() =
        runTest {
            val client =
                client {
                    """{"id":3,"name":"Fictional viewer","isAdmin":false,"permissions":{"movies":true,"tv":false}}"""
                }
            try {
                val capabilities = MediariumProvider(client).profile(environment).requestCapabilities()
                assertTrue(capabilities.canRequestMovie)
                assertFalse(capabilities.canRequestTv)
                assertFalse(capabilities.canRequest4kMovie)
                assertFalse(capabilities.canUseAdvancedRequests)
                assertFalse(capabilities.canManageRequests)
                assertFalse(capabilities.canSelectSeasons)
            } finally {
                client.close()
            }
        }

    @Test
    fun submittingPreservesCancellation() =
        runTest {
            val client = client { throw CancellationException("cancelled") }
            try {
                assertFailsWith<CancellationException> {
                    MediariumProvider(client).createRequest(environment, JellyseerrCreateRequest(101, null, JellyseerrMediaType.MOVIE))
                }
            } finally {
                client.close()
            }
        }

    private fun client(response: (String) -> String): HttpClient =
        NetworkClientFactory.create(
            ClientConfig(
                engine =
                    MockEngine {
                        respond(
                            response(it.url.encodedPath),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                maxRetries = 0,
            ),
        )
}
