package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinSessionApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinAdminRepositoryTest {
    @Test
    fun loadsOverviewUsersAndActivityThenStartsScan() =
        runTest {
            var scanRequested = false
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/System/Info" -> json("""{"ServerName":"Living room","Version":"10.10.7"}""")
                        "/Items/Counts" -> json("""{"MovieCount":12,"SeriesCount":4,"EpisodeCount":63,"SongCount":8}""")
                        "/Users" ->
                            json(
                                """[{"Id":"u1","Name":"Admin","Policy":{"IsAdministrator":true}},{"Id":"u2","Name":"Viewer","Policy":{"IsDisabled":true}}]""",
                            )
                        "/System/ActivityLog/Entries" -> json("""{"Items":[{"Id":9,"Name":"Library scan finished"}]}""")
                        "/Library/Refresh" -> {
                            scanRequested = true
                            respondOk()
                        }
                        else -> error("Unexpected request ${request.method.value} ${request.url.encodedPath}")
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val environment =
                JellyfinEnvironment("server", "https://media.example", "dummy-token", "u1", "device", "test")
            val session =
                JellyfinSessionRepository(
                    JellyfinEnvironmentProvider { environment },
                    { JellyfinSessionApi(client, it.baseUrl, it.accessToken, it.deviceId) },
                )
            val repository = JellyfinAdminRepository(session)

            repository.refresh()

            assertEquals(
                "Living room",
                repository.state.value.overview
                    ?.serverName,
            )
            assertEquals(
                12,
                repository.state.value.overview
                    ?.counts
                    ?.movies,
            )
            assertEquals(2, repository.state.value.users.size)
            assertTrue(
                repository.state.value.users
                    .last()
                    .isDisabled,
            )
            assertEquals(
                "Library scan finished",
                repository.state.value.activity
                    .single()
                    .name,
            )
            assertFalse(repository.state.value.isLoading)

            repository.startLibraryScan()

            assertTrue(scanRequested)
            assertEquals(JellyfinAdminNotice.LIBRARY_SCAN_STARTED, repository.state.value.notice)
            client.close()
        }

    private fun MockRequestHandleScope.json(value: String) =
        respond(
            content = ByteReadChannel(value),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
