package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSectionsApiTest {
    @Test
    fun defaultAuthorizationHeaderUsesCurrentAndroidVersion() =
        runTest {
            var authorization = ""
            val engine =
                MockEngine { request ->
                    authorization = request.headers["X-Emby-Authorization"].orEmpty()
                    respondOk()
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val api = HomeSectionsApi(client, "https://media.example", "dummy-token")

            api.ready()

            assertEquals(
                "MediaBrowser Client=\"Jellystack\", Device=\"Android\", DeviceId=\"unknown\", Version=\"0.15.1\"",
                authorization,
            )
            client.close()
        }
}
