package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class JellyfinPlaybackExtrasApiTest {
    @Test
    fun fetchExtrasRequestsChaptersAndTrickplayAndDecodesTheServerShape() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/Items/item-1", request.url.encodedPath)
                    assertEquals("user-1", request.url.parameters["UserId"])
                    assertEquals("Chapters,Trickplay", request.url.parameters["Fields"])
                    assertEquals("MediaBrowser Token=\"dummy-token\"", request.headers[HttpHeaders.Authorization])
                    respondJson(SERVER_RESPONSE)
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))

            val result = api(client).fetchExtras("item-1")

            val extras = assertIs<JellyfinPlaybackExtrasResult.Available>(result).extras
            assertEquals(
                listOf(
                    JellyfinChapterDto(startPositionTicks = 0L, name = "Opening", imageTag = "chapter-tag"),
                    JellyfinChapterDto(startPositionTicks = 6_000_000_000L, name = "Chapter 2", imageTag = null),
                ),
                extras.chapters,
            )
            val manifest = requireNotNull(extras.trickplay)["0a1b2c3d4e5f60718293a4b5c6d7e8f9"]
            assertEquals(
                JellyfinTrickplayInfoDto(
                    width = 320,
                    height = 180,
                    tileWidth = 10,
                    tileHeight = 10,
                    thumbnailCount = 250,
                    interval = 10_000,
                ),
                requireNotNull(manifest)["320"],
            )
            client.close()
        }

    @Test
    fun itemsWithoutChaptersOrTrickplayDecodeToNulls() =
        runTest {
            val client = clientResponding(HttpStatusCode.OK, """{"Id":"item-1","Name":"Movie"}""")

            val extras = assertIs<JellyfinPlaybackExtrasResult.Available>(api(client).fetchExtras("item-1")).extras

            assertNull(extras.chapters)
            assertNull(extras.trickplay)
            client.close()
        }

    @Test
    fun serverFailuresAreUnavailableSoPlaybackIsNeverBlocked() =
        runTest {
            val client = clientResponding(HttpStatusCode.InternalServerError, "server error")

            assertEquals(JellyfinPlaybackExtrasResult.Unavailable, api(client).fetchExtras("item-1"))
            client.close()
        }

    @Test
    fun cancellationPropagates() =
        runTest {
            val client =
                NetworkClientFactory.create(
                    ClientConfig(
                        engine = MockEngine { throw CancellationException("playback stopped") },
                        installLogging = false,
                    ),
                )

            assertFailsWith<CancellationException> { api(client).fetchExtras("item-1") }
            client.close()
        }

    private fun api(client: io.ktor.client.HttpClient) =
        JellyfinPlaybackExtrasApi(client, "https://example.test", "dummy-token", userId = "user-1")

    private fun clientResponding(
        status: HttpStatusCode,
        body: String,
    ) = NetworkClientFactory.create(
        ClientConfig(
            engine = MockEngine { respondJson(body, status) },
            installLogging = false,
        ),
    )

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        body,
        status,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        // Shape of BaseItemDto.Chapters and BaseItemDto.Trickplay as serialized by Jellyfin 10.9+.
        const val SERVER_RESPONSE = """
            {
              "Id": "item-1",
              "Name": "Movie",
              "Chapters": [
                {"StartPositionTicks": 0, "Name": "Opening", "ImageTag": "chapter-tag",
                 "ImageDateModified": "2026-01-01T00:00:00.0000000Z"},
                {"StartPositionTicks": 6000000000, "Name": "Chapter 2",
                 "ImageDateModified": "0001-01-01T00:00:00.0000000Z"}
              ],
              "Trickplay": {
                "0a1b2c3d4e5f60718293a4b5c6d7e8f9": {
                  "320": {"Width": 320, "Height": 180, "TileWidth": 10, "TileHeight": 10,
                          "ThumbnailCount": 250, "Interval": 10000, "Bandwidth": 12000}
                }
              }
            }
        """
    }
}
