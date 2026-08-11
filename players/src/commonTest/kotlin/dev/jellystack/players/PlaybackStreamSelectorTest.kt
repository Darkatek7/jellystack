package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackStreamSelectorTest {
    @Test
    fun videoStreamMetadataFillsMissingSourceQualityMetadata() {
        val source =
            JellyfinMediaSource(
                id = "source",
                name = "Source",
                runTimeTicks = 1_000_000,
                container = "mkv",
                videoBitrate = null,
                supportsDirectPlay = false,
                supportsDirectStream = false,
                supportsTranscoding = true,
                streams =
                    listOf(
                        JellyfinMediaStream(
                            type = JellyfinMediaStreamType.VIDEO,
                            index = 0,
                            displayTitle = "1080p AV1",
                            codec = "av1",
                            language = null,
                            isDefault = true,
                            isForced = false,
                            bitrate = 9_000_000,
                            width = 1_920,
                            height = 1_080,
                        ),
                    ),
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertEquals(9_000_000, selection.videoBitrate)
        assertEquals(1_920, selection.videoWidth)
        assertEquals(1_080, selection.videoHeight)
    }

    @Test
    fun audioChannelCountSurvivesStreamSelection() {
        val source =
            JellyfinMediaSource(
                id = "source",
                name = "Source",
                runTimeTicks = 1_000_000,
                container = "mkv",
                videoBitrate = 8_000_000,
                supportsDirectPlay = true,
                supportsDirectStream = true,
                supportsTranscoding = true,
                streams =
                    listOf(
                        JellyfinMediaStream(
                            type = JellyfinMediaStreamType.AUDIO,
                            index = 1,
                            displayTitle = "English 5.1",
                            codec = "aac",
                            language = "eng",
                            isDefault = true,
                            isForced = false,
                            channels = 6,
                        ),
                    ),
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertEquals(6, selection.audioTracks.single().channels)
    }
}
