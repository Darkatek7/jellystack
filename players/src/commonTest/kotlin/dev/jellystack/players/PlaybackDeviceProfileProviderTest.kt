package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackDeviceProfileProviderTest {
    @Test
    fun modernDirectCodecsIncludeAv1AndVp9WhileTranscodingPrefersHevcThenH264() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Test Android",
                capabilities =
                    PlaybackDecoderCapabilities(
                        setOf(
                            PlaybackVideoCodec.AV1,
                            PlaybackVideoCodec.HEVC,
                            PlaybackVideoCodec.VP9,
                            PlaybackVideoCodec.H264,
                        ),
                    ),
            )

        assertEquals(
            listOf("mp4,m4v", "mkv", "webm", "ts,mpegts"),
            profile.directPlayProfiles.map { it.container },
        )
        assertEquals("av1,hevc,h264", profile.directPlayProfiles.first().videoCodec)
        assertEquals("av1,hevc,vp9,h264", profile.directPlayProfiles[1].videoCodec)
        assertEquals("av1,vp9", profile.directPlayProfiles[2].videoCodec)
        assertEquals("hevc,h264", profile.directPlayProfiles[3].videoCodec)
        assertEquals("opus,vorbis", profile.directPlayProfiles[2].audioCodec)
        assertEquals(listOf("hevc", "h264"), profile.transcodingProfiles.map { it.videoCodec })
    }

    @Test
    fun h264RemainsTheConservativeTranscodeFallback() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Fallback",
                capabilities = PlaybackDecoderCapabilities(setOf(PlaybackVideoCodec.H264)),
            )

        assertEquals(
            listOf("mp4,m4v", "mkv", "ts,mpegts"),
            profile.directPlayProfiles.map { it.container },
        )
        assertTrue(profile.directPlayProfiles.all { it.videoCodec == "h264" })
        assertFalse(profile.directPlayProfiles.any { it.container == "webm" })
        assertEquals(listOf("h264"), profile.transcodingProfiles.map { it.videoCodec })
    }

    @Test
    fun detectedCapabilitiesNeverInventUnsupportedH264Profiles() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "No detected decoder",
                capabilities = PlaybackDecoderCapabilities(emptySet()),
            )

        assertTrue(profile.directPlayProfiles.isEmpty())
        assertTrue(profile.transcodingProfiles.isEmpty())
    }

    @Test
    fun vp9OnlyCapabilityIsAdvertisedOnlyForMkvAndWebm() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "VP9 only",
                capabilities = PlaybackDecoderCapabilities(setOf(PlaybackVideoCodec.VP9)),
            )

        assertEquals(listOf("mkv", "webm"), profile.directPlayProfiles.map { it.container })
        assertTrue(profile.directPlayProfiles.all { it.videoCodec == "vp9" })
        assertTrue(profile.transcodingProfiles.isEmpty())
    }
}
