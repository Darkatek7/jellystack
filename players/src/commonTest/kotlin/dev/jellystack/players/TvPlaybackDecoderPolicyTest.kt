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

    @Test
    fun surroundCapableTvPrefersDolbyTranscodeTargetsWithTheirOwnChannelLimit() {
        val dolbyAndAac = setOf(PlaybackAudioCodec.AAC, PlaybackAudioCodec.AC3, PlaybackAudioCodec.EAC3, PlaybackAudioCodec.MP3)
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    audioSupport =
                        PlaybackAudioCodec.entries.associateWith {
                            if (it in dolbyAndAac) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED
                        },
                    maxAudioChannelCounts =
                        mapOf(
                            PlaybackAudioCodec.AAC to 8,
                            PlaybackAudioCodec.AC3 to 6,
                            PlaybackAudioCodec.EAC3 to 8,
                            PlaybackAudioCodec.MP3 to 2,
                        ),
                ),
            )

        assertEquals(
            listOf(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AC3, PlaybackAudioCodec.AAC, PlaybackAudioCodec.MP3),
            capabilities.transcodingAudioCodecPreference,
        )
        assertEquals(6, capabilities.maxTranscodingAudioChannelCount)
    }

    @Test
    fun aacOnlyTvKeepsStereoAacAsTheTranscodeTarget() {
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    audioSupport = mapOf(PlaybackAudioCodec.AAC to CapabilitySupport.SUPPORTED),
                    maxAudioChannelCounts = mapOf(PlaybackAudioCodec.AAC to 6),
                ),
            )

        assertEquals(listOf(PlaybackAudioCodec.AAC), capabilities.transcodingAudioCodecPreference)
        assertEquals(2, capabilities.maxTranscodingAudioChannelCount)
    }

    @Test
    fun unverifiedAacFallbackStillGetsAStereoCodecProfileSoSurroundTargetsCannotRaiseIt() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Partially inspected TV",
                capabilities =
                    selectTvDecoderCapabilities(
                        PlaybackCapabilitySnapshot(
                            videoSupport = mapOf(PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED),
                            videoHardwareSupport = emptyMap(),
                            audioSupport = mapOf(PlaybackAudioCodec.EAC3 to CapabilitySupport.SUPPORTED),
                            maxAudioChannelCounts = mapOf(PlaybackAudioCodec.EAC3 to 6),
                            inspectionCompleteness = PlaybackCapabilityInspection.PARTIAL,
                        ),
                    ),
            )

        assertEquals("eac3,aac", profile.transcodingProfiles.first().audioCodec)
        assertEquals("6", profile.transcodingProfiles.first().maxAudioChannels)
        assertEquals(
            "2",
            profile.codecProfiles
                .single { it.codec == "aac" }
                .conditions
                .single()
                .value,
        )
    }

    @Test
    fun onlyHardwareAv1IsOfferedForVideoCopyAndOnlyInTheFmp4TranscodingProfile() {
        fun transcodingVideoCodecs(av1Hardware: CapabilitySupport) =
            PlaybackDeviceProfileFactory
                .create(
                    name = "Test TV",
                    capabilities =
                        selectTvDecoderCapabilities(
                            snapshot(
                                videoSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                                videoHardwareSupport =
                                    PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED } +
                                        (PlaybackVideoCodec.AV1 to av1Hardware),
                                audioSupport = PlaybackAudioCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                            ),
                        ),
                ).transcodingProfiles
                .associate { it.container to it.videoCodec }

        // AV1 stays last so the server never picks it as an encoder target.
        assertEquals(
            mapOf("ts" to "hevc,h264", "mp4" to "hevc,h264,av1"),
            transcodingVideoCodecs(CapabilitySupport.SUPPORTED),
        )
        assertEquals(
            mapOf("ts" to "hevc,h264", "mp4" to "hevc,h264"),
            transcodingVideoCodecs(CapabilitySupport.UNSUPPORTED),
        )
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
