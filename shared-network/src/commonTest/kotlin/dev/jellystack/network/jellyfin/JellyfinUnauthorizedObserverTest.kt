package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinUnauthorizedObserverTest {
    private val identity = JellyfinClientIdentity(appVersion = "1", deviceName = "TV", deviceId = "device")

    @Test
    fun reportsRejectedRequestsWithTheirTokenAndIgnoresOtherAnswers() =
        runTest {
            val reported = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/expired" -> respond("", HttpStatusCode.Unauthorized)
                        "/forbidden" -> respond("", HttpStatusCode.Forbidden)
                        else -> respond("ok", HttpStatusCode.OK)
                    }
                }
            val client =
                NetworkClientFactory.create(
                    ClientConfig(engine = engine, configure = { observeJellyfinUnauthorized { reported += it } }),
                )
            try {
                val authorization = jellyfinAuthorizationValue(identity, "token +&?")
                client.get("https://media.example/ok") { header(HttpHeaders.Authorization, authorization) }
                client.get("https://media.example/forbidden") { header(HttpHeaders.Authorization, authorization) }
                assertEquals(emptyList(), reported)

                client.get("https://media.example/expired") { header(HttpHeaders.Authorization, authorization) }
                assertEquals(listOf<String?>("token +&?"), reported)
            } finally {
                client.close()
            }
        }

    @Test
    fun readsTheTokenFromAuthorizationHeaderOrApiKey() {
        assertEquals("tok en+1", jellyfinAccessTokenOf(jellyfinAuthorizationValue(identity, "tok en+1"), apiKey = null))
        assertEquals("from-query", jellyfinAccessTokenOf(authorization = null, apiKey = "from-query"))
        assertNull(jellyfinAccessTokenOf(jellyfinAuthorizationValue(identity), apiKey = null))
    }
}
