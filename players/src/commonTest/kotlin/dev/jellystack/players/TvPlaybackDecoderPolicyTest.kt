package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackDecoderPolicyTest {
    @Test
    fun snapshotDefensivelyCopiesRuntimeEvidence() {
        val mutableVideo = mutableMapOf(PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED)
        val snapshot = snapshot(videoSupport = mutableVideo)

        mutableVideo[PlaybackVideoCodec.H264] = CapabilitySupport.UNSUPPORTED

        assertEquals(CapabilitySupport.SUPPORTED, snapshot.videoSupport(PlaybackVideoCodec.H264))
    }

    @Test
    fun advancedVideoRequiresSupportedCodecAndHardwareEvidence() {
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    videoSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                    videoHardwareSupport =
                        mapOf(
                            PlaybackVideoCodec.AV1 to CapabilitySupport.SUPPORTED,
                            PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED,
                            PlaybackVideoCodec.VP9 to CapabilitySupport.UNSUPPORTED,
                            PlaybackVideoCodec.H264 to CapabilitySupport.UNKNOWN,
                        ),
                ),
            )

        assertEquals(
            setOf(PlaybackVideoCodec.AV1, PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264),
            capabilities.videoCodecs,
        )
        assertFalse(PlaybackVideoCodec.VP9 in capabilities.videoCodecs)
    }

    @Test
    fun unsupportedAndContradictoryAdvancedVideoIsNeverAdvertised() {
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    videoSupport =
                        mapOf(
                            PlaybackVideoCodec.AV1 to CapabilitySupport.UNSUPPORTED,
                            PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED,
                            PlaybackVideoCodec.VP9 to CapabilitySupport.UNKNOWN,
                            PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED,
                        ),
                    videoHardwareSupport =
                        mapOf(
                            PlaybackVideoCodec.AV1 to CapabilitySupport.SUPPORTED,
                            PlaybackVideoCodec.HEVC to CapabilitySupport.UNSUPPORTED,
                            PlaybackVideoCodec.VP9 to CapabilitySupport.SUPPORTED,
                        ),
                ),
            )

        assertEquals(setOf(PlaybackVideoCodec.H264), capabilities.videoCodecs)
        assertEquals(setOf(PlaybackVideoCodec.H264), capabilities.transcodingVideoCodecs)
        assertEquals(setOf(PlaybackAudioCodec.AAC), capabilities.transcodingAudioCodecs)
    }

    @Test
    fun partialInspectionUsesH264AacOnlyAsConservativeTranscodeFallback() {
        val capabilities =
            selectTvDecoderCapabilities(
                PlaybackCapabilitySnapshot(
                    videoSupport = mapOf(PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED),
                    videoHardwareSupport = mapOf(PlaybackVideoCodec.HEVC to CapabilitySupport.UNKNOWN),
                    audioSupport = emptyMap(),
                    maxAudioChannelCounts = emptyMap(),
                    inspectionCompleteness = PlaybackCapabilityInspection.PARTIAL,
                ),
            )

        assertTrue(capabilities.videoCodecs.isEmpty())
        assertTrue(capabilities.audioCodecs.isEmpty())
        assertEquals(setOf(PlaybackVideoCodec.H264), capabilities.transcodingVideoCodecs)
        assertEquals(setOf(PlaybackAudioCodec.AAC), capabilities.transcodingAudioCodecs)
        assertEquals(2, capabilities.maxTranscodingAudioChannelCount)
    }

    @Test
    fun inspectionFailureProducesInteroperableTranscodeWithoutOptimisticDirectPlay() {
        val capabilities = selectTvDecoderCapabilities(PlaybackCapabilitySnapshot.failed())

        assertTrue(capabilities.videoCodecs.isEmpty())
        assertTrue(capabilities.audioCodecs.isEmpty())
        assertEquals(setOf(PlaybackVideoCodec.H264), capabilities.transcodingVideoCodecs)
        assertEquals(setOf(PlaybackAudioCodec.AAC), capabilities.transcodingAudioCodecs)
    }

    @Test
    fun channelLimitsAreClampedAndUnknownLimitsRemainStereo() {
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    audioSupport =
                        mapOf(
                            PlaybackAudioCodec.AAC to CapabilitySupport.SUPPORTED,
                            PlaybackAudioCodec.EAC3 to CapabilitySupport.SUPPORTED,
                            PlaybackAudioCodec.OPUS to CapabilitySupport.SUPPORTED,
                        ),
                    maxAudioChannelCounts =
                        mapOf(
                            PlaybackAudioCodec.AAC to 32,
                            PlaybackAudioCodec.EAC3 to 12,
                        ),
                ),
            )

        assertEquals(2, capabilities.maxAudioChannelCounts[PlaybackAudioCodec.AAC])
        assertEquals(6, capabilities.maxAudioChannelCounts[PlaybackAudioCodec.EAC3])
        assertEquals(2, capabilities.maxAudioChannelCounts[PlaybackAudioCodec.OPUS])
    }

    private fun snapshot(
        videoSupport: Map<PlaybackVideoCodec, CapabilitySupport> = emptyMap(),
        videoHardwareSupport: Map<PlaybackVideoCodec, CapabilitySupport> = emptyMap(),
        audioSupport: Map<PlaybackAudioCodec, CapabilitySupport> = emptyMap(),
        maxAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
    ): PlaybackCapabilitySnapshot =
        PlaybackCapabilitySnapshot(
            videoSupport = videoSupport,
            videoHardwareSupport = videoHardwareSupport,
            audioSupport = audioSupport,
            maxAudioChannelCounts = maxAudioChannelCounts,
            inspectionCompleteness = PlaybackCapabilityInspection.COMPLETE,
        )
}
