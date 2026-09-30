package dev.jellystack.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkClientLanguageTest {
    @Test
    fun requestsUseCurrentLanguageAndRespectExplicitOverrides() =
        runTest {
            var language: String? = "de-AT"
            val languages = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    languages += request.headers[HttpHeaders.AcceptLanguage]
                    respond("ok")
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, acceptLanguageProvider = { language }))
            try {
                client.get("https://media.example")
                language = "en-US"
                client.get("https://media.example")
                client.get("https://media.example") { header(HttpHeaders.AcceptLanguage, "fr") }
                language = null
                client.get("https://media.example")
                language = " "
                client.get("https://media.example")

                assertEquals(listOf("de-AT", "en-US", "fr", null, null), languages)
            } finally {
                client.close()
            }
        }
}
