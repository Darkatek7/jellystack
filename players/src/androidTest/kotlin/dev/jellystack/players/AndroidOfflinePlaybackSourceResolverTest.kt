package dev.jellystack.players

import dev.jellystack.core.downloads.InMemoryOfflineMediaStore
import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.downloads.OfflineMediaKind
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidOfflinePlaybackSourceResolverTest {
    @Test
    fun resolveIncludesDownloadedSubtitleFiles() {
        val root = File(System.getProperty("java.io.tmpdir"), "offline-resolver-test-${System.nanoTime()}").apply { mkdirs() }
        val videoFile = File(root, "video.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val subtitleFile = File(root, "subtitle_en.vtt").apply { writeText("WEBVTT\n\n00:00.000 --> 00:01.000\nHello") }

        val store = InMemoryOfflineMediaStore()
        store.write(
            OfflineMedia(
                mediaId = "item-1",
                filePath = videoFile.absolutePath,
                mimeType = "video/mp4",
                checksumSha256 = null,
                sizeBytes = videoFile.length(),
                kind = OfflineMediaKind.VIDEO,
            ),
        )
        store.write(
            OfflineMedia(
                mediaId = "item-1::sub::3",
                filePath = subtitleFile.absolutePath,
                mimeType = "text/vtt",
                checksumSha256 = null,
                sizeBytes = subtitleFile.length(),
                kind = OfflineMediaKind.SUBTITLE,
                language = "en",
                relativePath = "item-1/subtitles/3_en.vtt",
            ),
        )

        val resolver = AndroidOfflinePlaybackSourceResolver(store)
        val source = resolver.resolve(store.read("item-1")!!)

        assertEquals(PlaybackMode.LOCAL, source.mode)
        assertEquals(1, source.subtitles.size)
        assertEquals("3", source.subtitles.first().trackId)
        assertTrue(
            source.subtitles
                .first()
                .url
                .startsWith("file:"),
        )
    }
}
