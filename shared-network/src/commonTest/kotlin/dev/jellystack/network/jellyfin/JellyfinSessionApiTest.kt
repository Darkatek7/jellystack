package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinSessionApiTest {
    @Test
    fun disablingUserPreservesUnknownPolicyFields() =
        runTest {
            var postedPolicy = ""
            val engine =
                MockEngine { request ->
                    when (request.method.value to request.url.encodedPath) {
                        "GET" to "/Users/user-2" ->
                            respond(
                                content = ByteReadChannel(USER_JSON),
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        "POST" to "/Users/user-2/Policy" -> {
                            postedPolicy = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respondOk()
                        }
                        else -> error("Unexpected request ${request.method.value} ${request.url.encodedPath}")
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
            val api = JellyfinSessionApi(client, "https://media.example", "dummy-token")

            api.setUserDisabled("user-2", true)

            assertTrue(postedPolicy.contains("\"IsDisabled\":true"))
            assertTrue(postedPolicy.contains("\"EnableMediaPlayback\":true"))
            assertTrue(postedPolicy.contains("\"CustomPluginPolicy\":\"preserved\""))
            assertFalse(postedPolicy.contains("AccessToken"))
            client.close()
        }

    private companion object {
        const val USER_JSON =
            """{"Id":"user-2","Name":"Viewer","Policy":{"IsDisabled":false,"EnableMediaPlayback":true,"CustomPluginPolicy":"preserved"}}"""
    }
}
