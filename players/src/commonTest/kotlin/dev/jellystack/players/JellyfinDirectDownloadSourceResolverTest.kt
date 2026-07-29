package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinDirectDownloadSourceResolverTest {
    @Test
    fun av1DownloadUsesOriginalStaticFileAndNeverHls() =
        runTest {
            val mediaSource =
                JellyfinMediaSource(
                    id = "av1-source",
                    name = "AV1 original",
                    runTimeTicks = 10_000_000,
                    container = "mkv",
                    videoBitrate = 5_000_000,
                    supportsDirectPlay = true,
                    supportsDirectStream = true,
                    supportsTranscoding = true,
                    streams =
                        listOf(
                            JellyfinMediaStream(
                                type = JellyfinMediaStreamType.VIDEO,
                                index = 0,
                                displayTitle = "AV1 1080p",
                                codec = "av1",
                                language = null,
                                isDefault = true,
                                isForced = false,
                                bitrate = 5_000_000,
                                width = 1920,
                                height = 1080,
                            ),
                        ),
                )
            val request = PlaybackRequest(mediaId = "item-av1", mediaSources = listOf(mediaSource))
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                JellyfinDirectDownloadSourceResolver().resolve(
                    request,
                    selection,
                    environment(),
                    0L,
                    PlaybackSourceOptions(),
                )

            assertEquals(PlaybackMode.DIRECT, source.mode)
            assertTrue(source.url.contains("/Videos/item-av1/stream.mkv?Static=true"))
            assertTrue(source.url.contains("MediaSourceId=av1-source"))
            assertFalse(source.url.contains("m3u8", ignoreCase = true))
            assertEquals("video/x-matroska", source.mimeType)
        }

    private fun environment() =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://example.test",
            accessToken = "dummy-token",
            userId = "user",
            deviceId = "device",
            deviceName = "Test",
        )
}
