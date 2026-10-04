package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeSectionsApiTest {
    @Test
    fun defaultAuthorizationHeaderUsesCurrentClientVersion() =
        runTest {
            var authorization = ""
            var legacyTokenHeader: String? = null
            var legacyAuthorizationHeader: String? = null
            val engine =
                MockEngine { request ->
                    authorization = request.headers[HttpHeaders.Authorization].orEmpty()
                    legacyTokenHeader = request.headers["X-Emby-Token"]
                    legacyAuthorizationHeader = request.headers["X-Emby-Authorization"]
                    respondOk()
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val api = HomeSectionsApi(client, "https://media.example", "dummy-token")

            api.ready()

            assertEquals(
                "MediaBrowser Client=\"Jellystack\", Device=\"Jellystack\", DeviceId=\"unknown\", " +
                    "Version=\"$DEFAULT_JELLYSTACK_CLIENT_VERSION\", Token=\"dummy-token\"",
                authorization,
            )
            assertNull(legacyTokenHeader)
            assertNull(legacyAuthorizationHeader)
            client.close()
        }
}
