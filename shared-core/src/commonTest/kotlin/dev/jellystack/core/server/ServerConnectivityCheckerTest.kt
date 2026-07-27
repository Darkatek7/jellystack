package dev.jellystack.core.server

import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerConnectivityCheckerTest {
    @Test
    fun jellyfinSuccessReturnsToken() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertTrue(request.url.encodedPath.endsWith("/Users/AuthenticateByName"))
                    respond(
                        content =
                            """{"AccessToken":"token123","User":{"Id":"user42","Name":"Demo"},"ServerId":"srv"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val checker = checkerWithEngine(engine)

            val result =
                checker.test(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Home Jellyfin",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-credential"),
                    ),
                )

            val success = assertIs<ConnectivityResult.Success>(result)
            val credential = assertIs<StoredCredential.Jellyfin>(success.credentials)
            assertTrue(credential.accessToken.isNotBlank())
        }

    @Test
    fun jellyfinFailureProducesFailure() =
        runTest {
            val checker = checkerWithEngine(MockEngine { respondError(HttpStatusCode.Unauthorized) })

            val result =
                checker.test(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-invalid-password"),
                    ),
                )

            assertIs<ConnectivityResult.Failure>(result)
        }

    @Test
    fun sonarrSuccessUsesApiKey() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertTrue(request.headers["X-Api-Key"] == "dummy-api-key")
                    respond(
                        content = """{"appName":"Sonarr","version":"3.0.0"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val checker = checkerWithEngine(engine)

            val result =
                checker.test(
                    ServerRegistration(
                        type = ServerType.SONARR,
                        name = "Shows",
                        baseUrl = "https://sonarr.example",
                        credentials = CredentialInput.ApiKey(apiKey = "dummy-api-key"),
                    ),
                )

            assertIs<StoredCredential.ApiKey>((result as ConnectivityResult.Success).credentials)
        }

    @Test
    fun jellyseerrFailurePropagates() =
        runTest {
            val checker = checkerWithEngine(MockEngine { respondError(HttpStatusCode.InternalServerError) })

            val result =
                checker.test(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = "https://requests.example",
                        credentials = CredentialInput.ApiKey(apiKey = "dummy-api-key"),
                    ),
                )

            assertIs<ConnectivityResult.Failure>(result)
        }

    @Test
    fun jellyseerrAcceptsSessionCookieFallback() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertTrue(request.headers[HttpHeaders.Cookie] == "connect.sid=session")
                    respond(
                        content = """{"version":"2.0"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val checker = checkerWithEngine(engine)

            val result =
                checker.test(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = "https://requests.example",
                        credentials = CredentialInput.ApiKey(apiKey = null, sessionCookie = "connect.sid=session"),
                    ),
                )

            val credential = assertIs<StoredCredential.ApiKey>((result as ConnectivityResult.Success).credentials)
            assertEquals(null, credential.apiKey)
            assertEquals("connect.sid=session", credential.sessionCookie)
        }

    private fun checkerWithEngine(engine: MockEngine): ServerConnectivityChecker =
        ServerConnectivityChecker(
            clientFactory = { config ->
                NetworkClientFactory.create(
                    config.copy(engine = engine, installLogging = false),
                )
            },
            idGenerator = { "device-123" },
        )
}
