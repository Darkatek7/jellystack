package dev.jellystack.core.jellyfin

import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinSessionApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JellyfinSessionRepositoryTest {
    @Test
    fun mapsAdministratorAndSyncPlayPolicy() =
        runTest {
            val repository =
                repositoryWith(
                    """{"Id":"user-1","Name":"Admin","Policy":{"IsAdministrator":true,"SyncPlayAccess":"CreateAndJoinGroups"}}""",
                )

            val capabilities = repository.refresh()

            assertTrue(capabilities.isAdministrator)
            assertTrue(capabilities.canJoinSyncPlay)
            assertTrue(capabilities.canCreateSyncPlay)
            assertIs<JellyfinSessionState.Ready>(repository.state.value)
        }

    @Test
    fun mapsMissingPolicyToNoCapabilities() =
        runTest {
            val capabilities = repositoryWith("""{"Id":"user-2","Name":"Viewer"}""").refresh()

            assertFalse(capabilities.isAdministrator)
            assertEquals(JellyfinSyncPlayAccess.NONE, capabilities.syncPlayAccess)
        }

    @Test
    fun mapsJoinOnlySyncPlayPolicy() =
        runTest {
            val capabilities =
                repositoryWith(
                    """{"Id":"user-3","Name":"Viewer","Policy":{"SyncPlayAccess":"JoinGroups"}}""",
                ).refresh()

            assertTrue(capabilities.canJoinSyncPlay)
            assertFalse(capabilities.canCreateSyncPlay)
            assertEquals(JellyfinSyncPlayAccess.JOIN_GROUPS, capabilities.syncPlayAccess)
        }

    private fun repositoryWith(responseBody: String): JellyfinSessionRepository {
        val engine =
            MockEngine {
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = NetworkClientFactory.create(dev.jellystack.network.ClientConfig(engine = engine))
        val environment =
            JellyfinEnvironment(
                serverKey = "server",
                baseUrl = "https://jellyfin.example",
                accessToken = "dummy-token",
                userId = "user-1",
                deviceId = "device",
                deviceName = "test",
            )
        return JellyfinSessionRepository(
            environmentProvider = JellyfinEnvironmentProvider { environment },
            apiFactory = { JellyfinSessionApi(client, it.baseUrl, it.accessToken, it.deviceId) },
        )
    }
}
