package dev.jellystack.players

import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto
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

    @Test
    fun passthroughOnlyCodecsAreDirectPlayedInMkvButNeverOfferedAsTranscodeTargets() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Soundbar TV",
                capabilities =
                    selectTvDecoderCapabilities(
                        passthroughSnapshot(
                            supported =
                                setOf(
                                    PlaybackAudioCodec.AAC,
                                    PlaybackAudioCodec.EAC3,
                                    PlaybackAudioCodec.DTS,
                                    PlaybackAudioCodec.TRUEHD,
                                ),
                            channels =
                                mapOf(
                                    PlaybackAudioCodec.AAC to 2,
                                    PlaybackAudioCodec.EAC3 to 6,
                                    PlaybackAudioCodec.DTS to 8,
                                    PlaybackAudioCodec.TRUEHD to 8,
                                ),
                        ),
                    ),
            )

        val mkvAudio =
            profile.directPlayProfiles
                .single { it.container == "mkv" }
                .audioCodec
                .split(',')
        assertTrue("dts" in mkvAudio && "truehd" in mkvAudio, mkvAudio.toString())
        profile.directPlayProfiles.filter { it.container != "mkv" }.forEach { direct ->
            assertFalse("dts" in direct.audioCodec || "truehd" in direct.audioCodec, direct.toString())
        }
        profile.transcodingProfiles.forEach { transcode ->
            assertFalse("dts" in transcode.audioCodec || "truehd" in transcode.audioCodec, transcode.toString())
        }
        assertEquals("eac3,aac", profile.transcodingProfiles.first().audioCodec)
        assertEquals("6", profile.transcodingProfiles.first().maxAudioChannels)
        assertEquals("8", profile.audioChannelLimit("dts"))
        assertEquals("8", profile.audioChannelLimit("truehd"))
    }

    @Test
    fun coreOnlyDtsSinkKeepsTheDtsChannelLimitAtFiveDotOne() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Core DTS soundbar",
                capabilities =
                    selectTvDecoderCapabilities(
                        passthroughSnapshot(
                            supported = setOf(PlaybackAudioCodec.AAC, PlaybackAudioCodec.DTS),
                            channels = mapOf(PlaybackAudioCodec.AAC to 2, PlaybackAudioCodec.DTS to 6),
                        ),
                    ),
            )

        assertEquals("6", profile.audioChannelLimit("dts"))
    }

    @Test
    fun stereoOnlySinkNeverGetsDtsOrTrueHd() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Stereo TV",
                capabilities =
                    selectTvDecoderCapabilities(
                        passthroughSnapshot(
                            supported = setOf(PlaybackAudioCodec.AAC),
                            channels = mapOf(PlaybackAudioCodec.AAC to 2),
                        ),
                    ),
            )

        assertTrue(profile.directPlayProfiles.none { "dts" in it.audioCodec || "truehd" in it.audioCodec })
        assertTrue(profile.codecProfiles.none { it.codec == "dts" || it.codec == "truehd" })
    }

    @Test
    fun softwareDecoderLetsAStereoSinkDirectPlayDtsAndTrueHdInMkvWithEightChannels() {
        val profile = stereoSinkProfile(softwareAudioChannelCounts = mapOf(PlaybackAudioCodec.DTS to 8, PlaybackAudioCodec.TRUEHD to 8))

        val mkvAudio =
            profile.directPlayProfiles
                .single { it.container == "mkv" }
                .audioCodec
                .split(',')
        assertTrue("dts" in mkvAudio && "truehd" in mkvAudio, mkvAudio.toString())
        profile.directPlayProfiles.filter { it.container != "mkv" }.forEach { direct ->
            assertFalse("dts" in direct.audioCodec || "truehd" in direct.audioCodec, direct.toString())
        }
        assertEquals("8", profile.audioChannelLimit("dts"))
        assertEquals("8", profile.audioChannelLimit("truehd"))
    }

    @Test
    fun withoutASoftwareDecoderAStereoSinkStillGetsNeitherDtsNorTrueHd() {
        val profile = stereoSinkProfile(softwareAudioChannelCounts = emptyMap())

        assertTrue(profile.directPlayProfiles.none { "dts" in it.audioCodec || "truehd" in it.audioCodec })
        assertTrue(profile.codecProfiles.none { it.codec == "dts" || it.codec == "truehd" })
    }

    @Test
    fun softwareDecodingNeverChangesWhatTheServerMayTranscodeTo() {
        val withSoftware =
            stereoSinkProfile(
                softwareAudioChannelCounts =
                    mapOf(
                        PlaybackAudioCodec.DTS to 8,
                        PlaybackAudioCodec.TRUEHD to 8,
                        PlaybackAudioCodec.AC3 to 6,
                        PlaybackAudioCodec.EAC3 to 6,
                    ),
            )
        val without = stereoSinkProfile(softwareAudioChannelCounts = emptyMap())

        assertEquals(without.transcodingProfiles, withSoftware.transcodingProfiles)
    }

    @Test
    fun softwareOnlyDolbyDigitalIsDirectPlayedWithSixChannelsButNeverATranscodeTarget() {
        val profile = stereoSinkProfile(softwareAudioChannelCounts = mapOf(PlaybackAudioCodec.AC3 to 6, PlaybackAudioCodec.EAC3 to 6))

        val mkvAudio =
            profile.directPlayProfiles
                .single { it.container == "mkv" }
                .audioCodec
                .split(',')
        assertTrue("ac3" in mkvAudio && "eac3" in mkvAudio, mkvAudio.toString())
        assertEquals("6", profile.audioChannelLimit("ac3"))
        assertEquals("6", profile.audioChannelLimit("eac3"))
        profile.transcodingProfiles.forEach { transcode ->
            assertFalse("ac3" in transcode.audioCodec || "eac3" in transcode.audioCodec, transcode.toString())
        }
    }

    @Test
    fun softwareDecoderRaisesTheDtsLimitOfACoreOnlyPassthroughSink() {
        val profile =
            PlaybackDeviceProfileFactory.create(
                name = "Core DTS soundbar",
                capabilities =
                    selectTvDecoderCapabilities(
                        passthroughSnapshot(
                            supported = setOf(PlaybackAudioCodec.AAC, PlaybackAudioCodec.DTS),
                            channels = mapOf(PlaybackAudioCodec.AAC to 2, PlaybackAudioCodec.DTS to 6),
                            softwareAudioChannelCounts = mapOf(PlaybackAudioCodec.DTS to 8),
                        ),
                    ),
            )

        assertEquals("8", profile.audioChannelLimit("dts"))
    }

    @Test
    fun dolbyVisionProfile5IsNotDirectPlayedWithoutADolbyVisionDecoder() {
        val ranges = hevcRanges(hdr = setOf(PlaybackHdrFormat.HDR10, PlaybackHdrFormat.HLG), displaySupportsDolbyVision = true)

        assertFalse(PlaybackVideoRange.DOVI in ranges)
        assertFalse(PlaybackVideoRange.DOVI_WITH_EL in ranges)
    }

    @Test
    fun dolbyVisionProfile8WithHdr10BaseLayerDirectPlaysOnHdrHevcHardware() {
        val ranges = hevcRanges(hdr = setOf(PlaybackHdrFormat.HDR10), displaySupportsDolbyVision = false)

        assertEquals(
            setOf(
                PlaybackVideoRange.SDR,
                PlaybackVideoRange.DOVI_WITH_SDR,
                PlaybackVideoRange.HDR10,
                PlaybackVideoRange.HDR10_PLUS,
                PlaybackVideoRange.DOVI_WITH_HDR10,
                PlaybackVideoRange.DOVI_WITH_HDR10_PLUS,
            ),
            ranges,
        )
        assertFalse(PlaybackVideoRange.HLG in ranges)
    }

    @Test
    fun pureDolbyVisionNeedsADecoderAndADisplayThatListsDolbyVision() {
        val hdr = setOf(PlaybackHdrFormat.HDR10, PlaybackHdrFormat.HLG)

        assertTrue(PlaybackVideoRange.DOVI in hevcRanges(hdr, setOf(5, 8), displaySupportsDolbyVision = true))
        assertFalse(PlaybackVideoRange.DOVI in hevcRanges(hdr, setOf(5, 8), displaySupportsDolbyVision = false))
        assertFalse(PlaybackVideoRange.DOVI in hevcRanges(hdr, setOf(8), displaySupportsDolbyVision = true))
    }

    @Test
    fun profile7EnhancementLayerNeedsItsOwnDolbyVisionDecoderLikeProfile5() {
        val hdr = setOf(PlaybackHdrFormat.HDR10)

        assertTrue(PlaybackVideoRange.DOVI_WITH_EL in hevcRanges(hdr, setOf(7, 8), displaySupportsDolbyVision = true))
        assertFalse(PlaybackVideoRange.DOVI_WITH_EL in hevcRanges(hdr, setOf(8), displaySupportsDolbyVision = true))
    }

    @Test
    fun eightBitOnlyHevcDecoderPlaysOnlySdrBaseLayers() {
        val ranges = hevcRanges(hdr = emptySet(), displaySupportsDolbyVision = true)

        assertEquals(setOf(PlaybackVideoRange.SDR, PlaybackVideoRange.DOVI_WITH_SDR), ranges)
    }

    @Test
    fun decoderWithoutProfileEvidenceLeavesVideoRangeUnrestricted() {
        val capabilities =
            selectTvDecoderCapabilities(
                snapshot(
                    videoSupport =
                        mapOf(
                            PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED,
                            PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED,
                        ),
                    videoHardwareSupport = mapOf(PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED),
                ),
            )

        assertTrue(capabilities.videoRanges.isEmpty())
        assertTrue(PlaybackDeviceProfileFactory.create("TV", capabilities).codecProfiles.none { it.type == "Video" })
    }

    private fun hevcRanges(
        hdr: Set<PlaybackHdrFormat>,
        dolbyVisionProfiles: Set<Int> = emptySet(),
        displaySupportsDolbyVision: Boolean,
    ): Set<PlaybackVideoRange> =
        selectTvDecoderCapabilities(
            snapshot(
                videoSupport =
                    mapOf(
                        PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED,
                        PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED,
                    ),
                videoHardwareSupport = mapOf(PlaybackVideoCodec.HEVC to CapabilitySupport.SUPPORTED),
                hdrSupport = mapOf(PlaybackVideoCodec.HEVC to hdr),
                dolbyVisionProfiles = dolbyVisionProfiles,
                displaySupportsDolbyVision = displaySupportsDolbyVision,
            ),
        ).videoRanges.getValue(PlaybackVideoCodec.HEVC)

    private fun stereoSinkProfile(softwareAudioChannelCounts: Map<PlaybackAudioCodec, Int>): JellyfinDeviceProfileDto =
        PlaybackDeviceProfileFactory.create(
            name = "Stereo TV",
            capabilities =
                selectTvDecoderCapabilities(
                    passthroughSnapshot(
                        supported = setOf(PlaybackAudioCodec.AAC),
                        channels = mapOf(PlaybackAudioCodec.AAC to 2),
                        softwareAudioChannelCounts = softwareAudioChannelCounts,
                    ),
                ),
        )

    private fun passthroughSnapshot(
        supported: Set<PlaybackAudioCodec>,
        channels: Map<PlaybackAudioCodec, Int>,
        softwareAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
    ) = snapshot(
        videoSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
        videoHardwareSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.UNSUPPORTED },
        audioSupport =
            PlaybackAudioCodec.entries.associateWith {
                if (it in supported) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED
            },
        maxAudioChannelCounts = channels,
        softwareAudioChannelCounts = softwareAudioChannelCounts,
    )

    private fun JellyfinDeviceProfileDto.audioChannelLimit(codec: String): String =
        codecProfiles
            .single { it.codec == codec }
            .conditions
            .single { it.property == "AudioChannels" }
            .value

    private fun snapshot(
        videoSupport: Map<PlaybackVideoCodec, CapabilitySupport> = emptyMap(),
        videoHardwareSupport: Map<PlaybackVideoCodec, CapabilitySupport> = emptyMap(),
        audioSupport: Map<PlaybackAudioCodec, CapabilitySupport> = emptyMap(),
        maxAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
        hdrSupport: Map<PlaybackVideoCodec, Set<PlaybackHdrFormat>> = emptyMap(),
        dolbyVisionProfiles: Set<Int> = emptySet(),
        displaySupportsDolbyVision: Boolean = false,
        softwareAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
    ): PlaybackCapabilitySnapshot =
        PlaybackCapabilitySnapshot(
            videoSupport = videoSupport,
            videoHardwareSupport = videoHardwareSupport,
            audioSupport = audioSupport,
            maxAudioChannelCounts = maxAudioChannelCounts,
            inspectionCompleteness = PlaybackCapabilityInspection.COMPLETE,
            hdrSupport = hdrSupport,
            dolbyVisionProfiles = dolbyVisionProfiles,
            displaySupportsDolbyVision = displaySupportsDolbyVision,
            softwareAudioChannelCounts = softwareAudioChannelCounts,
        )
}
