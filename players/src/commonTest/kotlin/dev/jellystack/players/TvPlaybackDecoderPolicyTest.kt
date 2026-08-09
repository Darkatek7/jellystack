package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackDecoderPolicyTest {
    @Test
    fun softwareOnlyAdvancedVideoCodecsAreNotAdvertisedForTvDirectPlay() {
        val capabilities =
            selectTvDecoderCapabilities(
                listOf(
                    PlaybackDecoderDescriptor("video/av01", isHardwareAccelerated = false),
                    PlaybackDecoderDescriptor("video/hevc", isHardwareAccelerated = false),
                    PlaybackDecoderDescriptor("video/x-vnd.on2.vp9", isHardwareAccelerated = false),
                    PlaybackDecoderDescriptor("video/avc", isHardwareAccelerated = false),
                    PlaybackDecoderDescriptor("audio/mp4a-latm", isHardwareAccelerated = false),
                ),
            )

        assertTrue(PlaybackVideoCodec.H264 in capabilities.videoCodecs)
        assertFalse(PlaybackVideoCodec.AV1 in capabilities.videoCodecs)
        assertFalse(PlaybackVideoCodec.HEVC in capabilities.videoCodecs)
        assertFalse(PlaybackVideoCodec.VP9 in capabilities.videoCodecs)
        assertTrue(PlaybackAudioCodec.AAC in capabilities.audioCodecs)
    }

    @Test
    fun hardwareAdvancedVideoCodecsRemainAvailableOnTv() {
        val capabilities =
            selectTvDecoderCapabilities(
                listOf(
                    PlaybackDecoderDescriptor("video/av01", isHardwareAccelerated = true),
                    PlaybackDecoderDescriptor("video/hevc", isHardwareAccelerated = true),
                    PlaybackDecoderDescriptor("video/x-vnd.on2.vp9", isHardwareAccelerated = true),
                    PlaybackDecoderDescriptor("video/avc", isHardwareAccelerated = true),
                    PlaybackDecoderDescriptor("audio/eac3", isHardwareAccelerated = true),
                ),
            )

        assertTrue(PlaybackVideoCodec.entries.all(capabilities.videoCodecs::contains))
        assertTrue(PlaybackAudioCodec.EAC3 in capabilities.audioCodecs)
    }

    @Test
    fun emulatorMayAdvertisePlatformSoftwareVideoDecoders() {
        val capabilities =
            selectTvDecoderCapabilities(
                decoders =
                    listOf(
                        PlaybackDecoderDescriptor("video/hevc", isHardwareAccelerated = false),
                        PlaybackDecoderDescriptor("video/x-vnd.on2.vp9", isHardwareAccelerated = false),
                    ),
                allowSoftwareAdvancedVideo = true,
            )

        assertTrue(PlaybackVideoCodec.HEVC in capabilities.videoCodecs)
        assertTrue(PlaybackVideoCodec.VP9 in capabilities.videoCodecs)
    }

    @Test
    fun aliasesAndEncodersNeverBecomePlaybackCapabilities() {
        val capabilities =
            selectTvDecoderCapabilities(
                listOf(
                    PlaybackDecoderDescriptor("video/av01", isHardwareAccelerated = true, isAlias = true),
                    PlaybackDecoderDescriptor("video/hevc", isHardwareAccelerated = true, isEncoder = true),
                ),
            )

        assertTrue(capabilities.videoCodecs.isEmpty())
    }
}
