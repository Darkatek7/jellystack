package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            val selection =
                PlaybackStreamSelector().select(request.mediaSources).copy(
                    subtitleTracks =
                        listOf(
                            SubtitleTrack(
                                id = "english",
                                language = "en",
                                title = "English",
                                format = SubtitleFormat.VTT,
                                isDefault = true,
                                isForced = false,
                                streamIndex = 2,
                            ),
                        ),
                )

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
            assertModernQueryAuthentication(source)
            val subtitle = source.subtitles.single()
            assertEquals("english", subtitle.trackId)
            assertTrue(subtitle.url.contains("/Videos/item-av1/av1-source/Subtitles/2/stream.vtt"))
            assertEquals("dummy-token", subtitle.url.queryParameter("ApiKey"))
            assertNull(subtitle.url.queryParameter("api_key"))
        }

    @Test
    fun audioDownloadUsesModernQueryAuthOnOriginalAudioFile() =
        runTest {
            val mediaSource =
                JellyfinMediaSource(
                    id = "audio-source",
                    name = "Original audio",
                    runTimeTicks = 10_000_000,
                    container = "flac",
                    videoBitrate = null,
                    supportsDirectPlay = true,
                    supportsDirectStream = true,
                    supportsTranscoding = true,
                    streams = emptyList(),
                )
            val request =
                PlaybackRequest(
                    mediaId = "song-1",
                    mediaSources = listOf(mediaSource),
                    mediaKind = PlaybackMediaKind.AUDIO,
                )
            val selection = PlaybackStreamSelector().select(request.mediaSources).copy(mode = PlaybackMode.DIRECT)

            val source =
                JellyfinDirectDownloadSourceResolver().resolve(request, selection, environment(), 0L, PlaybackSourceOptions())

            assertTrue(source.url.contains("/Audio/song-1/stream.flac?Static=true"))
            assertModernQueryAuthentication(source)
            assertEquals("audio/flac", source.mimeType)
            assertTrue(source.subtitles.isEmpty())
        }

    private fun assertModernQueryAuthentication(source: ResolvedPlaybackSource) {
        assertEquals("dummy-token", source.url.queryParameter("ApiKey"))
        assertNull(source.url.queryParameter("api_key"))
        assertFalse(
            source.headers.keys.any { name ->
                name.equals("Authorization", ignoreCase = true) ||
                    name.startsWith("X-Emby-", ignoreCase = true) ||
                    name.startsWith("X-MediaBrowser-", ignoreCase = true)
            },
        )
    }

    private fun String.queryParameter(name: String): String? =
        substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstNotNullOfOrNull { parameter ->
                parameter
                    .substringAfter('=', missingDelimiterValue = "")
                    .takeIf { parameter.substringBefore('=') == name }
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
