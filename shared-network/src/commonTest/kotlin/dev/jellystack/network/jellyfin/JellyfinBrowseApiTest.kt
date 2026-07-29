package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JellyfinBrowseApiTest {
    @Test
    fun fetchItemDetailRequestsRichMetadataFields() =
        runTest {
            var requestedFields = ""
            val engine =
                MockEngine { request ->
                    requestedFields = request.url.parameters["Fields"].orEmpty()
                    respond(
                        """
                        {
                          "Id": "episode-42",
                          "Name": "Episode",
                          "BackdropImageTags": ["episode-backdrop"],
                          "ParentBackdropImageTags": ["series-backdrop"]
                        }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val detail = api.fetchItemDetail("u-1", "episode-42")

            listOf(
                "People",
                "OriginalTitle",
                "OriginalLanguage",
                "ProductionLocations",
                "Tags",
                "ParentBackdropImageTags",
            ).forEach { field ->
                assertTrue(field in requestedFields, "Expected $field in $requestedFields")
            }
            assertEquals(listOf("episode-backdrop"), detail.backdropImageTags)
            assertEquals(listOf("series-backdrop"), detail.parentBackdropImageTags)
            client.close()
        }

    @Test
    fun fetchSimilarItemsUsesItemEndpointAndUserContext() =
        runTest {
            var requestedPath = ""
            var requestedUserId = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.encodedPath
                    requestedUserId = request.url.parameters["UserId"].orEmpty()
                    respond(
                        """{"Items":[{"Id":"related-1","Name":"Related","Type":"Movie"}],"TotalRecordCount":1}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val response = api.fetchSimilarItems("u-1", "movie-42", limit = 12)

            assertEquals("/Items/movie-42/Similar", requestedPath)
            assertEquals("u-1", requestedUserId)
            assertEquals(listOf("related-1"), response.items.map { it.id })
            client.close()
        }

    @Test
    fun setPlayedStatusPostsToPlayedItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Post &&
                            request.url.encodedPath.endsWith("/Users/u-1/PlayedItems/movie-42") -> {
                            respond(
                                """{"Played":true,"PlaybackPositionTicks":0}""",
                                HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val userData = api.setPlayedStatus("u-1", "movie-42", played = true)

            assertEquals(true, userData.played)
            client.close()
        }

    @Test
    fun setPlayedStatusDeletesPlayedItemsEndpointForUnplayed() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Delete &&
                            request.url.encodedPath.endsWith("/Users/u-1/PlayedItems/movie-42") -> {
                            respond(
                                """{"Played":false,"PlaybackPositionTicks":0}""",
                                HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val userData = api.setPlayedStatus("u-1", "movie-42", played = false)

            assertEquals(false, userData.played)
            client.close()
        }

    @Test
    fun addFavoritePostsToFavoriteItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Post &&
                            request.url.encodedPath.endsWith("/Users/u-1/FavoriteItems/movie-42") -> {
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.addFavorite("u-1", "movie-42") // expect success, no throw

            client.close()
        }

    @Test
    fun removeFavoriteDeletesFavoriteItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Delete &&
                            request.url.encodedPath.endsWith("/Users/u-1/FavoriteItems/movie-42") -> {
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.removeFavorite("u-1", "movie-42") // expect success, no throw

            client.close()
        }

    @Test
    fun fetchFavoriteIdsReturnsIds() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/Users/u-1/Items")) {
                        respond(
                            """{"Items":[{"Id":"a","Name":"A","Type":"Movie"},{"Id":"b","Name":"B","Type":"Movie"},{"Id":"c","Name":"C","Type":"Movie"}],"TotalRecordCount":3}""",
                            HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val ids = api.fetchFavoriteIds("u-1")

            assertEquals(setOf("a", "b", "c"), ids)

            client.close()
        }
}
