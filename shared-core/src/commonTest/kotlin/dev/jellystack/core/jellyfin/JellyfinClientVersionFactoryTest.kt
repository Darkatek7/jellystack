package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinClientVersionFactoryTest {
    @Test
    fun sessionFactoryUsesTheActiveAppVersion() =
        runTest {
            val authorization = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    authorization += request.headers["X-Emby-Authorization"].orEmpty()
                    respond(
                        content = ByteReadChannel("""{"Id":"user-1","Name":"Viewer"}"""),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val factory = defaultJellyfinSessionApiFactory { client }

            factory(environment("0.15.1")).currentUser()
            factory(environment("0.16.0-tv-beta.4")).currentUser()

            assertEquals(
                listOf("0.15.1", "0.16.0-tv-beta.4"),
                authorization.map(::authorizationVersion),
            )
            client.close()
        }

    @Test
    fun homeSectionsFactoryUsesTheActiveAppVersion() =
        runTest {
            val authorization = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    authorization += request.headers["X-Emby-Authorization"].orEmpty()
                    respond(content = ByteReadChannel.Empty)
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val factory = defaultHomeSectionsApiFactory { client }

            factory(environment("0.15.1")).ready()
            factory(environment("0.16.0-tv-beta.4")).ready()

            assertEquals(
                listOf("0.15.1", "0.16.0-tv-beta.4"),
                authorization.map(::authorizationVersion),
            )
            client.close()
        }

    private fun environment(clientVersion: String) =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://media.example",
            accessToken = "dummy-token",
            userId = "user",
            deviceId = "device",
            deviceName = "Test device",
            clientVersion = clientVersion,
        )

    private fun authorizationVersion(header: String): String =
        requireNotNull(Regex("""Version="([^"]+)"""").find(header)?.groupValues?.get(1))
}
