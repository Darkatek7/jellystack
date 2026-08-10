package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinSyncPlayApiTest {
    @Test
    fun mapsGroupsAndUsesAuthenticatedSyncPlayEndpoints() =
        runTest {
            val requests = mutableListOf<Pair<String, String>>()
            val engine =
                MockEngine { request ->
                    requests += request.url.encodedPath to request.method.value
                    assertEquals("token", request.headers["X-Emby-Token"])
                    when (request.url.encodedPath) {
                        "/SyncPlay/List" ->
                            respond(
                                content = ByteReadChannel(GROUPS_JSON),
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        "/SyncPlay/New" ->
                            respond(
                                content = ByteReadChannel(GROUP_JSON),
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        else -> respondOk()
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val api = JellyfinSyncPlayApi(client, "https://media.example", "token", "device")

            val groups = api.groups()
            val created = api.createGroup("Family")
            api.joinGroup(created.groupId)
            api.seek(12_340_000L)

            assertEquals("Living room", groups.single().groupName)
            assertEquals("Family", created.groupName)
            assertTrue(requests.contains("/SyncPlay/Join" to "POST"))
            assertTrue(requests.contains("/SyncPlay/Seek" to "POST"))
            client.close()
        }

    @Test
    fun groupsMapsForbiddenResponseWithoutTryingToDecodeIt() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Forbidden"),
                        status = HttpStatusCode.Forbidden,
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val api = JellyfinSyncPlayApi(client, "https://media.example", "token", "device")

            val failure = assertFailsWith<JellyfinSyncPlayException> { api.groups() }

            assertEquals(JellyfinSyncPlayFailure.ACCESS_DENIED, failure.failure)
            assertEquals(HttpStatusCode.Forbidden.value, failure.statusCode)
            assertFalse(failure.message.orEmpty().contains("media.example"))
            client.close()
        }

    @Test
    fun groupsMapsUnauthorizedResponse() =
        runTest {
            val client = clientResponding(HttpStatusCode.Unauthorized, "Unauthorized")
            val failure =
                assertFailsWith<JellyfinSyncPlayException> {
                    JellyfinSyncPlayApi(client, "https://media.example", "token", "device").groups()
                }

            assertEquals(JellyfinSyncPlayFailure.UNAUTHORIZED, failure.failure)
            assertEquals(HttpStatusCode.Unauthorized.value, failure.statusCode)
            client.close()
        }

    @Test
    fun groupsMapsInvalidSuccessfulBody() =
        runTest {
            val client = clientResponding(HttpStatusCode.OK, "not-json")
            val failure =
                assertFailsWith<JellyfinSyncPlayException> {
                    JellyfinSyncPlayApi(client, "https://media.example", "token", "device").groups()
                }

            assertEquals(JellyfinSyncPlayFailure.INVALID_RESPONSE, failure.failure)
            client.close()
        }

    private fun clientResponding(
        status: HttpStatusCode,
        body: String,
    ) = NetworkClientFactory.create(
        ClientConfig(
            engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(body),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            maxRetries = 0,
        ),
    )

    private companion object {
        const val GROUP_JSON =
            """{"GroupId":"group-2","GroupName":"Family","State":"Idle","Participants":[]}"""
        const val GROUPS_JSON =
            """[{"GroupId":"group-1","GroupName":"Living room","State":"Paused","Participants":["user-1"]}]"""
    }
}
