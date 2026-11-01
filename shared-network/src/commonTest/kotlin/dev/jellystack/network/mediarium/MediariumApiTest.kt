package dev.jellystack.network.mediarium

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.NetworkJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediariumApiTest {
    @Test
    fun searchPreservesUrlBaseAndEncodesQueryWithoutSeerrHeaders() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val client =
                jsonClient {
                    requests += it
                    """[{"tmdbId":101,"mediaType":"movie","title":"A fictional film","year":2024}]"""
                }
            try {
                val api = MediariumApi(client, "https://media.example.com/tools/mediarium/", "fictional-key")
                assertEquals(101, api.search("a & b").single().tmdbId)
                val request = requests.single()
                assertEquals("/tools/mediarium/api/discover/search", request.url.encodedPath)
                assertEquals("a & b", request.url.parameters["q"])
                assertEquals("fictional-key", request.headers["X-API-Key"])
                assertEquals(null, request.headers["X-API-User"])
                assertEquals(null, request.headers[HttpHeaders.Cookie])
            } finally {
                client.close()
            }
        }

    @Test
    fun movieAddSendsSearchNowAndDistinguishesImmediateAdd() =
        runTest {
            var body = ""
            val client =
                jsonClient(HttpStatusCode.Created) {
                    assertEquals(HttpMethod.Post, it.method)
                    assertEquals("/api/movies", it.url.encodedPath)
                    body = (it.body as TextContent).text
                    """{"id":73,"tmdbId":101,"status":"missing"}"""
                }
            try {
                val result = MediariumApi(client, "https://media.example.com", "fictional-key").add(101, false)
                assertEquals(73L, assertIs<MediariumAddResponseDto.Added>(result).item.id)
                val payload = NetworkJson.default.parseToJsonElement(body).jsonObject
                assertEquals("101", payload["tmdbId"]?.jsonPrimitive?.content)
                assertTrue(payload.getValue("searchNow").jsonPrimitive.boolean)
                assertFalse("mediaId" in payload)
            } finally {
                client.close()
            }
        }

    @Test
    fun seriesAddUsesNativeMonitorAndReturnsPendingApproval() =
        runTest {
            val client =
                jsonClient(HttpStatusCode.Accepted) {
                    assertEquals("/api/series", it.url.encodedPath)
                    val body = NetworkJson.default.parseToJsonElement((it.body as TextContent).text).jsonObject
                    assertEquals("all", body["monitor"]?.jsonPrimitive?.content)
                    assertTrue(body.getValue("searchNow").jsonPrimitive.boolean)
                    """{"requested":true,"request":{"id":7,"kind":"tv","title":"Fictional show","status":"pending"}}"""
                }
            try {
                val result = MediariumApi(client, "https://media.example.com", "fictional-key").add(102, true)
                assertEquals(7, assertIs<MediariumAddResponseDto.Pending>(result).request.id)
            } finally {
                client.close()
            }
        }

    @Test
    fun rejectsUnsuccessfulResponsesBeforeDecodingAndDoesNotExposeBody() =
        runTest {
            val client = jsonClient(HttpStatusCode.Forbidden) { """{"error":"private-server-information"}""" }
            try {
                val error =
                    assertFailsWith<MediariumHttpException> {
                        MediariumApi(client, "https://media.example.com", "fictional-key").add(101, false)
                    }
                assertEquals(HttpStatusCode.Forbidden, error.status)
                assertFalse(error.message.orEmpty().contains("private-server-information"))
            } finally {
                client.close()
            }
        }

    @Test
    fun cancellationIsPropagated() =
        runTest {
            val client = jsonClient { throw CancellationException("cancelled") }
            try {
                assertFailsWith<CancellationException> {
                    MediariumApi(client, "https://media.example.com", "fictional-key").search("fiction")
                }
            } finally {
                client.close()
            }
        }

    private fun jsonClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: (HttpRequestData) -> String,
    ): HttpClient =
        NetworkClientFactory.create(
            ClientConfig(
                engine =
                    MockEngine { request ->
                        respond(response(request), status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    },
                maxRetries = 0,
            ),
        )
}
