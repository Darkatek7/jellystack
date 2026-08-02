package dev.jellystack.players.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaTrack
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CastPlayerTest {
    @Test
    fun buildLoadRequestSetsContentTypeStreamTypeAndTracks() {
        val snapshot =
            CastSessionSnapshot(
                mediaId = "media-1",
                title = "Sample",
                seriesName = null,
                episodeName = null,
                artworkUrl = null,
                streamUrl = "https://example.com/video.mp4",
                positionMs = 10_000L,
                durationMs = 120_000L,
                isPaused = false,
                contentType = "video/mp4",
                streamType = CastStreamType.BUFFERED,
                subtitleTracks =
                    listOf(
                        CastSubtitleTrack(
                            id = "sub-1",
                            url = "https://example.com/subs.vtt",
                            mimeType = "text/vtt",
                            language = "en",
                            label = "English",
                            isForced = false,
                        ),
                    ),
                selectedSubtitleTrackId = "sub-1",
            )
        val player = CastPlayer(clientProvider = { null })

        val request = player.buildLoadRequest(snapshot)
        val mediaInfo = request.mediaInfo
        assertEquals("video/mp4", mediaInfo.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, mediaInfo.streamType)

        val tracks = mediaInfo.mediaTracks
        assertNotNull(tracks)
        assertEquals(1, tracks.size)
        assertEquals(MediaTrack.TYPE_TEXT, tracks[0].type)

        val active = request.activeTrackIds
        assertNotNull(active)
        assertEquals(1, active.size)
        assertEquals(tracks[0].id, active[0])
    }
}
