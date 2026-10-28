package dev.jellystack.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkRetryPolicyTest {
    @Test
    fun postFailingWithServerErrorIsSentOnce() =
        runTest {
            var sends = 0
            val engine =
                MockEngine {
                    sends += 1
                    respond("", HttpStatusCode.ServiceUnavailable)
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 2))
            try {
                val response = client.post("https://media.example/request")

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertEquals(1, sends)
            } finally {
                client.close()
            }
        }

    @Test
    fun getFailingWithServerErrorIsRetried() =
        runTest {
            var sends = 0
            val engine =
                MockEngine {
                    sends += 1
                    if (sends == 1) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("ok")
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 2))
            try {
                val response = client.get("https://media.example/items")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(2, sends)
            } finally {
                client.close()
            }
        }
}
