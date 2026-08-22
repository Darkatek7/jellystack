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

class JellyfinMediaSegmentsApiTest {
    @Test
    fun fetchSegmentsGetsAuthenticatedItemSegmentsAndDecodesTheQueryResult() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/MediaSegments/item-1", request.url.encodedPath)
                    assertEquals("dummy-token", request.headers["X-Emby-Token"])
                    respondJson(
                        """{"Items":[{"Id":"segment-1","ItemId":"item-1","Type":"Intro","StartTicks":12000000,"EndTicks":45000000}]}""",
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))

            val result = JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")

            val available = assertIs<JellyfinMediaSegmentsResult.Available>(result)
            assertEquals(
                JellyfinMediaSegmentDto(
                    id = "segment-1",
                    itemId = "item-1",
                    type = "Intro",
                    startTicks = 12_000_000,
                    endTicks = 45_000_000,
                ),
                available.segments.single(),
            )
            client.close()
        }

    @Test
    fun fetchSegmentsAcceptsAnEmptyQueryResult() =
        runTest {
            val client = clientResponding(HttpStatusCode.OK, """{"Items":[]}""")

            val result = JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")

            assertEquals(JellyfinMediaSegmentsResult.Available(emptyList()), result)
            client.close()
        }

    @Test
    fun fetchSegmentsPreservesAnUnknownSegmentType() =
        runTest {
            val client =
                clientResponding(
                    HttpStatusCode.OK,
                    """{"Items":[{"Id":"segment-2","ItemId":"item-1","Type":"FutureMarker","StartTicks":1,"EndTicks":2}]}""",
                )

            val result = JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")

            assertEquals(
                "FutureMarker",
                (assertIs<JellyfinMediaSegmentsResult.Available>(result)).segments.single().type,
            )
            client.close()
        }

    @Test
    fun fetchSegmentsTreatsAnUnsupportedEndpointAsNoSegments() =
        runTest {
            val client = clientResponding(HttpStatusCode.NotFound, "not found", expectSuccess = true)

            val result = JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")

            assertEquals(JellyfinMediaSegmentsResult.Available(emptyList()), result)
            client.close()
        }

    @Test
    fun fetchSegmentsMakesFeatureFetchFailuresUnavailable() =
        runTest {
            val client = clientResponding(HttpStatusCode.InternalServerError, "server error")

            val result = JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")

            assertEquals(JellyfinMediaSegmentsResult.Unavailable, result)
            client.close()
        }

    @Test
    fun fetchSegmentsPropagatesCancellation() =
        runTest {
            val client =
                NetworkClientFactory.create(
                    ClientConfig(
                        engine = MockEngine { throw CancellationException("playback stopped") },
                        installLogging = false,
                    ),
                )

            assertFailsWith<CancellationException> {
                JellyfinMediaSegmentsApi(client, "https://example.test", "dummy-token").fetchSegments("item-1")
            }
            client.close()
        }

    private fun clientResponding(
        status: HttpStatusCode,
        body: String,
        expectSuccess: Boolean = false,
    ) = NetworkClientFactory.create(
        ClientConfig(
            engine = MockEngine { respondJson(body, status) },
            installLogging = false,
            configure = { this.expectSuccess = expectSuccess },
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
}
