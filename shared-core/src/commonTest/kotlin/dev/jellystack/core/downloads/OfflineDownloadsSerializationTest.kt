package dev.jellystack.core.downloads

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineDownloadsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun offlineMediaDecodesLegacyPayloadWithoutMetadata() {
        val media =
            json.decodeFromString<OfflineMedia>(
                """
                {
                  "mediaId": "episode-1",
                  "filePath": "/offline/episode-1.mp4",
                  "mimeType": "video/mp4",
                  "checksumSha256": null,
                  "sizeBytes": 1234,
                  "kind": "VIDEO",
                  "language": null,
                  "relativePath": "episode-1.mp4"
                }
                """.trimIndent(),
            )

        assertEquals("episode-1", media.mediaId)
        assertNull(media.metadata)
    }

    @Test
    fun downloadRequestRoundTripsMetadata() {
        val request =
            DownloadRequest(
                mediaId = "episode-1",
                downloadUrl = "https://example.test/video.mp4",
                headers = mapOf("Authorization" to "MediaBrowser token"),
                mimeType = "video/mp4",
                expectedSizeBytes = null,
                checksumSha256 = null,
                metadata =
                    OfflineMediaMetadata(
                        itemId = "episode-1",
                        libraryId = "shows",
                        name = "Pilot",
                        type = "Episode",
                        mediaType = "Video",
                        seriesName = "Sample Show",
                        indexNumber = 1,
                        parentIndexNumber = 1,
                    ),
            )

        val decoded = json.decodeFromString<DownloadRequest>(json.encodeToString(request))

        assertEquals("episode-1", decoded.metadata?.itemId)
        assertEquals("Sample Show", decoded.metadata?.seriesName)
        assertEquals(1, decoded.metadata?.indexNumber)
    }
}
