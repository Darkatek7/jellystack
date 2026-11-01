package dev.jellystack.core.livetv

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinLiveTvApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class LiveTvRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val environment =
        JellyfinEnvironment(
            serverKey = "srv-1",
            baseUrl = "https://example.test",
            accessToken = "dummy-token",
            userId = "user-1",
            deviceId = "device-1",
            deviceName = "Test",
        )

    @Test
    fun programsAreRequestedForTheWindowAndGroupedByChannelInStartOrder() =
        runTest {
            val repository = repository(PROGRAMS_JSON)
            val at = Instant.parse("2026-11-01T18:00:00Z")

            val guide = repository.programs(listOf("c1", "c2"), at = at, window = 6.hours)

            val request = requests.single()
            assertEquals("c1,c2", request.url.parameters["ChannelIds"])
            assertEquals("2026-11-01T18:00:00Z", request.url.parameters["MinEndDate"])
            assertEquals("2026-11-02T00:00:00Z", request.url.parameters["MaxStartDate"])
            assertEquals(listOf("early", "late"), guide.getValue("c1").map { it.id })
            assertEquals(listOf("other"), guide.getValue("c2").map { it.id })
        }

    @Test
    fun noChannelsOnTheServerMeansLiveTvIsNotOffered() =
        runTest {
            assertFalse(repository("""{"Items":[]}""").hasChannels())
            assertFalse(repository("", HttpStatusCode.NotFound).hasChannels())
            assertTrue(repository("""{"Items":[{"Id":"c1","Name":"Example One"}]}""").hasChannels())
        }

    private fun repository(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): LiveTvRepository {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false, maxRetries = 0))
        return LiveTvRepository(
            environmentProvider = JellyfinEnvironmentProvider { environment },
            apiFactory = { JellyfinLiveTvApi(client, it.baseUrl, it.accessToken) },
        )
    }

    private companion object {
        const val PROGRAMS_JSON =
            """{"Items":[
                {"Id":"late","ChannelId":"c1","Name":"Late","StartDate":"2026-11-01T19:00:00.0000000Z","EndDate":"2026-11-01T20:00:00.0000000Z"},
                {"Id":"early","ChannelId":"c1","Name":"Early","StartDate":"2026-11-01T17:30:00.0000000Z","EndDate":"2026-11-01T19:00:00.0000000Z"},
                {"Id":"other","ChannelId":"c2","Name":"Other","StartDate":"2026-11-01T17:30:00.0000000Z","EndDate":"2026-11-01T19:00:00.0000000Z"},
                {"Id":"broken","ChannelId":"c2","Name":"Broken","StartDate":"nonsense","EndDate":"2026-11-01T19:00:00.0000000Z"}
            ]}"""
    }
}
