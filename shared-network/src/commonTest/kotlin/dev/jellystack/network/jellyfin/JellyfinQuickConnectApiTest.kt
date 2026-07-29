package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinQuickConnectApiTest {
    @Test
    fun authorizeUsesConnectedUserTokenAndOfficialEndpoint() =
        runTest {
            var captured: HttpRequestData? = null
            val engine =
                MockEngine { request ->
                    captured = request
                    respond(
                        content = "true",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api =
                JellyfinQuickConnectAuthorizationApi(
                    client = client,
                    baseUrl = "https://example.test",
                    identity = identity(),
                    accessToken = "dummy-access-token",
                )

            assertTrue(api.authorize(code = "123456", userId = "user-id"))

            val request = requireNotNull(captured)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/QuickConnect/Authorize", request.url.encodedPath)
            assertEquals("123456", request.url.parameters["Code"])
            assertEquals("user-id", request.url.parameters["UserId"])
            assertEquals("dummy-access-token", request.headers["X-Emby-Token"])
            assertTrue(request.headers["X-Emby-Authorization"].orEmpty().contains("DeviceId=\"device-123\""))
            client.close()
        }

    @Test
    fun quickConnectUsesOfficialEndpointsAndOneClientIdentity() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val responses: ArrayDeque<Pair<String, HttpStatusCode>> =
                ArrayDeque(
                    listOf(
                        "true" to HttpStatusCode.OK,
                        """{"Authenticated":false,"Secret":"dummy-quick-connect-secret","Code":"123456"}""" to
                            HttpStatusCode.OK,
                        """{"Authenticated":true,"Secret":"dummy-quick-connect-secret","Code":"123456"}""" to
                            HttpStatusCode.OK,
                        """
                        {
                          "AccessToken":"dummy-access-token",
                          "ServerId":"server-id",
                          "User":{"Id":"user-id","Name":"Alice"}
                        }
                        """.trimIndent() to HttpStatusCode.OK,
                    ),
                )
            val engine =
                MockEngine { request ->
                    requests += request
                    val (body, status) = responses.removeFirst()
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api =
                JellyfinQuickConnectApi(
                    client = client,
                    baseUrl = "https://example.test",
                    identity =
                        JellyfinClientIdentity(
                            appName = "Jellystack",
                            appVersion = "0.14.2",
                            deviceName = "Pixel",
                            deviceId = "device-123",
                        ),
                )

            assertTrue(api.isEnabled())
            val initiated = api.initiate()
            val connected = api.poll(initiated.secret)
            val authenticated = api.authenticate(initiated.secret)

            assertEquals(
                listOf(
                    HttpMethod.Get,
                    HttpMethod.Post,
                    HttpMethod.Get,
                    HttpMethod.Post,
                ),
                requests.map { it.method },
            )
            assertEquals(
                listOf(
                    "/QuickConnect/Enabled",
                    "/QuickConnect/Initiate",
                    "/QuickConnect/Connect",
                    "/Users/AuthenticateWithQuickConnect",
                ),
                requests.map { it.url.encodedPath },
            )
            requests.forEach { request ->
                assertEquals(EXPECTED_AUTH_HEADER, request.headers["X-Emby-Authorization"])
            }
            assertEquals("dummy-quick-connect-secret", requests[2].url.parameters["secret"])
            assertEquals("""{"Secret":"dummy-quick-connect-secret"}""", requests[3].bodyText())
            assertFalse(initiated.authenticated)
            assertTrue(connected.authenticated)
            assertEquals("dummy-access-token", authenticated.accessToken)
            assertEquals("user-id", authenticated.user.id)
            client.close()
        }

    @Test
    fun disabledResponseMapsToFalse() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "false",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinQuickConnectApi(client, "https://example.test", identity())

            assertFalse(api.isEnabled())

            client.close()
        }

    @Test
    fun httpFailuresExposeOnlyStatusCode() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"Secret":"dummy-secret-must-not-leak"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinQuickConnectApi(client, "https://example.test", identity())

            val error = assertFailsWith<JellyfinQuickConnectHttpException> { api.isEnabled() }

            assertEquals(401, error.code)
            assertFalse(error.message.orEmpty().contains("dummy-secret-must-not-leak"))
            client.close()
        }

    @Test
    fun invalidJsonHasAStableNonSecretError() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"unexpected":"value"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinQuickConnectApi(client, "https://example.test", identity())

            val error = assertFailsWith<JellyfinQuickConnectInvalidResponseException> { api.isEnabled() }

            assertFalse(error.message.orEmpty().contains("unexpected"))
            client.close()
        }

    private fun identity() =
        JellyfinClientIdentity(
            appName = "Jellystack",
            appVersion = "0.14.2",
            deviceName = "Test device",
            deviceId = "device-123",
        )

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is io.ktor.http.content.TextContent -> content.text
            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }

    private companion object {
        const val EXPECTED_AUTH_HEADER =
            "MediaBrowser Client=\"Jellystack\", Device=\"Pixel\", DeviceId=\"device-123\", Version=\"0.14.2\""
    }
}
