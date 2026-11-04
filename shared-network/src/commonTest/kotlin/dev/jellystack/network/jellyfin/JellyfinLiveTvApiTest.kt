package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinLiveTvApiTest {
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun channelsAreRequestedInChannelOrderWithABoundedLimitAndDecoded() =
        runTest {
            val api = api { respondJson(CHANNELS_JSON) }

            val channels = api.fetchChannels(userId = "user-1").items

            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/LiveTv/Channels", request.url.encodedPath)
            assertEquals("user-1", request.url.parameters["UserId"])
            assertEquals("true", request.url.parameters["EnableUserData"])
            assertEquals("SortName", request.url.parameters["SortBy"])
            assertEquals("500", request.url.parameters["Limit"])
            assertEquals("false", request.url.parameters["AddCurrentProgram"])
            assertEquals("MediaBrowser Token=\"dummy-token\"", request.headers[HttpHeaders.Authorization])
            assertEquals(listOf("channel-1", "channel-2"), channels.map { it.id })
            assertEquals("12.1", channels[0].channelNumber)
            assertEquals("tag-1", channels[0].imageTags?.get("Primary"))
            assertNull(channels[1].name)
        }

    @Test
    fun programsAskForTheWindowOfTheGivenChannelsOnly() =
        runTest {
            val api = api { respondJson(PROGRAMS_JSON) }

            val programs =
                api
                    .fetchPrograms(
                        userId = "user-1",
                        channelIds = listOf("channel-1", "channel-2"),
                        minEndDate = "2026-11-01T18:00:00Z",
                        maxStartDate = "2026-11-02T00:00:00Z",
                    ).items

            val request = requests.single()
            assertEquals("/LiveTv/Programs", request.url.encodedPath)
            assertEquals("channel-1,channel-2", request.url.parameters["ChannelIds"])
            assertEquals("2026-11-01T18:00:00Z", request.url.parameters["MinEndDate"])
            assertEquals("2026-11-02T00:00:00Z", request.url.parameters["MaxStartDate"])
            assertEquals("Overview", request.url.parameters["Fields"])
            assertEquals("user-1", request.url.parameters["UserId"])
            assertEquals("Example News", programs.single().name)
            assertEquals("2026-11-01T17:30:00.0000000Z", programs.single().startDate)
        }

    @Test
    fun noChannelsToAskForMeansNoRequest() =
        runTest {
            val api = api { error("no request expected") }

            val result = api.fetchPrograms("user-1", emptyList(), "2026-11-01T18:00:00Z", "2026-11-02T00:00:00Z")

            assertTrue(result.items.isEmpty())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun aServerWithoutLiveTvReadsAsNoChannelsAndNoPrograms() =
        runTest {
            listOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden).forEach { status ->
                val api = api { respond("", status) }

                assertTrue(api.fetchChannels("user-1").items.isEmpty(), "channels $status")
                assertTrue(api.fetchPrograms("user-1", listOf("c"), "a", "b").items.isEmpty(), "programs $status")
            }
        }

    @Test
    fun otherFailuresAreNotMistakenForAServerWithoutLiveTv() =
        runTest {
            val api = api { respond("", HttpStatusCode.Unauthorized) }

            assertFailsWith<IllegalStateException> { api.fetchChannels("user-1") }
        }

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): JellyfinLiveTvApi {
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false, maxRetries = 0))
        return JellyfinLiveTvApi(client, "https://example.test", "dummy-token")
    }

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private companion object {
        const val CHANNELS_JSON =
            """{"Items":[
                {"Id":"channel-1","Name":"Example One","ChannelNumber":"12.1","ImageTags":{"Primary":"tag-1"},"Type":"TvChannel"},
                {"Id":"channel-2","Number":"13","Type":"TvChannel"}
            ],"TotalRecordCount":2}"""
        const val PROGRAMS_JSON =
            """{"Items":[{"Id":"program-1","ChannelId":"channel-1","Name":"Example News","EpisodeTitle":"Evening",
                "StartDate":"2026-11-01T17:30:00.0000000Z","EndDate":"2026-11-01T18:30:00.0000000Z","Type":"Program"}]}"""
    }
}
