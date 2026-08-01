package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinPlaybackApiTest {
    @Test
    fun fetchPlaybackInfoPostsDeviceProfileAndDecodesMediaSource() =
        runTest {
            var body = ""
            val engine =
                MockEngine { request ->
                    body = request.bodyText()
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/Items/item-1/PlaybackInfo", request.url.encodedPath)
                    assertEquals("user-1", request.url.parameters["UserId"])
                    respond(
                        """{"PlaySessionId":"play-1","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,"TranscodingUrl":"/Videos/item-1/master.m3u8?VideoCodec=hevc","TranscodingContainer":"ts","TranscodingSubProtocol":"hls"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinPlaybackApi(client, "https://example.test", "dummy-token")
            val request =
                JellyfinPlaybackInfoRequestDto(
                    userId = "user-1",
                    deviceProfile =
                        JellyfinDeviceProfileDto(
                            name = "Android",
                            directPlayProfiles = emptyList(),
                            transcodingProfiles = emptyList(),
                        ),
                    mediaSourceId = "source-1",
                    audioStreamIndex = 2,
                    subtitleStreamIndex = 5,
                    startTimeTicks = 900_000,
                    maxStreamingBitrate = 420_000,
                    enableDirectPlay = false,
                    enableDirectStream = false,
                    enableTranscoding = true,
                    allowVideoStreamCopy = false,
                    allowAudioStreamCopy = true,
                )

            val response = api.fetchPlaybackInfo("item-1", "user-1", request)

            assertTrue(body.contains("\"MaxStreamingBitrate\":420000"))
            assertTrue(body.contains("\"AllowVideoStreamCopy\":false"))
            assertTrue(body.contains("\"SubtitleStreamIndex\":5"))
            assertEquals("play-1", response.playSessionId)
            assertEquals(
                "hevc",
                response.mediaSources
                    .single()
                    .transcodingUrl
                    ?.substringAfter("VideoCodec="),
            )
            client.close()
        }

    @Test
    fun omittedSupportsTranscodingRemainsUnknown() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"MediaSources":[{"Id":"source-1","TranscodingUrl":"/Videos/item/master.m3u8"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val response =
                JellyfinPlaybackApi(client, "https://example.test", "dummy-token")
                    .fetchPlaybackInfo(
                        "item-1",
                        "user-1",
                        JellyfinPlaybackInfoRequestDto(
                            userId = "user-1",
                            deviceProfile =
                                JellyfinDeviceProfileDto(
                                    name = "Test",
                                    directPlayProfiles = emptyList(),
                                    transcodingProfiles = emptyList(),
                                ),
                            enableDirectPlay = true,
                            enableDirectStream = true,
                            enableTranscoding = true,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                        ),
                    )

            assertNull(response.mediaSources.single().supportsTranscoding)
            client.close()
        }
}

private fun HttpRequestData.bodyText(): String =
    when (val content = body) {
        is TextContent -> content.text
        is ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
