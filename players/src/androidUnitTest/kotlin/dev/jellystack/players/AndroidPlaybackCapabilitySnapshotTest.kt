package dev.jellystack.players

import android.media.MediaCodecInfo.CodecProfileLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPlaybackCapabilitySnapshotTest {
    @Test
    fun encodersAndAliasesDoNotCountAsPlaybackEvidence() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(types = listOf("video/av01"), isEncoder = true),
                        fakeCodec(types = listOf("video/hevc"), isAlias = true),
                        fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED),
                    )
                },
            )

        val snapshot = source.snapshot()

        assertEquals(CapabilitySupport.UNSUPPORTED, snapshot.videoSupport(PlaybackVideoCodec.AV1))
        assertEquals(CapabilitySupport.UNSUPPORTED, snapshot.videoSupport(PlaybackVideoCodec.HEVC))
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.videoSupport(PlaybackVideoCodec.H264))
    }

    @Test
    fun oneBrokenCodecRetainsOtherEvidenceAndMarksInspectionPartial() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/avc", "audio/mp4a-latm"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            channels = mapOf("audio/mp4a-latm" to 6),
                        ),
                        object : AndroidRuntimeCodecEntry {
                            override val isEncoder: Boolean = false
                            override val isAlias: Boolean = false
                            override val hardwareSupport: CapabilitySupport = CapabilitySupport.UNKNOWN

                            override fun supportedTypes(): List<String> = error("registry entry failed")

                            override fun maxInputChannelCount(mimeType: String): Int? = null
                        },
                    )
                },
            )

        val snapshot = source.snapshot()

        assertEquals(PlaybackCapabilityInspection.PARTIAL, snapshot.inspectionCompleteness)
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.videoSupport(PlaybackVideoCodec.H264))
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.AAC))
        assertEquals(CapabilitySupport.UNKNOWN, snapshot.videoSupport(PlaybackVideoCodec.HEVC))
        assertEquals(6, snapshot.maxAudioChannelCounts[PlaybackAudioCodec.AAC])
    }

    @Test
    fun perTypeCapabilityFailureDoesNotDiscardCodecSupport() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("audio/eac3"),
                            maxChannelFailure = setOf("audio/eac3"),
                        ),
                    )
                },
            )

        val snapshot = source.snapshot()

        assertEquals(PlaybackCapabilityInspection.PARTIAL, snapshot.inspectionCompleteness)
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.EAC3))
        assertFalse(snapshot.maxAudioChannelCounts.containsKey(PlaybackAudioCodec.EAC3))
    }

    @Test
    fun registryFailureProducesFullyUnknownSnapshot() {
        val source = AndroidRuntimePlaybackCapabilitySnapshotSource(codecEntries = { error("registry unavailable") })

        val snapshot = source.snapshot()

        assertEquals(PlaybackCapabilityInspection.FAILED, snapshot.inspectionCompleteness)
        assertTrue(PlaybackVideoCodec.entries.all { snapshot.videoSupport(it) == CapabilitySupport.UNKNOWN })
        assertTrue(PlaybackAudioCodec.entries.all { snapshot.audioSupport(it) == CapabilitySupport.UNKNOWN })
    }

    @Test
    fun tvProviderDependsOnSnapshotSourceAndUsesConservativeFailureProfile() {
        val provider =
            AndroidTvPlaybackDeviceProfileProvider(
                snapshotSource = PlaybackCapabilitySnapshotSource { PlaybackCapabilitySnapshot.failed() },
            )

        val profile = provider.deviceProfile()

        assertTrue(profile.directPlayProfiles.isEmpty())
        assertEquals(listOf("h264", "h264"), profile.transcodingProfiles.map { it.videoCodec })
        assertEquals(listOf("aac", "aac"), profile.transcodingProfiles.map { it.audioCodec })
    }

    @Test
    fun passthroughSinkAddsDtsAndTrueHdAndRaisesChannelLimitsBeyondTheDecoders() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/avc", "audio/ac3"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            channels = mapOf("audio/ac3" to 2),
                        ),
                    )
                },
                audioPassthroughProbe =
                    AudioPassthroughProbe {
                        AudioPassthroughSupport(
                            mapOf(
                                PlaybackAudioCodec.AC3 to 6,
                                PlaybackAudioCodec.DTS to 8,
                                PlaybackAudioCodec.TRUEHD to 8,
                            ),
                        )
                    },
            )

        val snapshot = source.snapshot()

        assertEquals(PlaybackCapabilityInspection.COMPLETE, snapshot.inspectionCompleteness)
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.DTS))
        assertEquals(CapabilitySupport.SUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.TRUEHD))
        assertEquals(CapabilitySupport.UNSUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.EAC3))
        assertEquals(6, snapshot.maxAudioChannelCounts[PlaybackAudioCodec.AC3])
        assertEquals(8, snapshot.maxAudioChannelCounts[PlaybackAudioCodec.TRUEHD])
    }

    @Test
    fun stereoPcmSinkReportsNoPassthroughEvidence() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = { listOf(fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED)) },
                audioPassthroughProbe = AudioPassthroughProbe { AudioPassthroughSupport.NONE },
            )

        val snapshot = source.snapshot()

        assertEquals(CapabilitySupport.UNSUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.DTS))
        assertEquals(CapabilitySupport.UNSUPPORTED, snapshot.audioSupport(PlaybackAudioCodec.TRUEHD))
        assertTrue(snapshot.maxAudioChannelCounts.isEmpty())
    }

    @Test
    fun softwareDecoderAddsDtsAndTrueHdOnAStereoSinkAndDolbyDigitalOnlyWithoutAPlatformDecoder() {
        val ffmpeg = SoftwareAudioDecoderProbe { true }
        val withoutPlatformDolby =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = { listOf(fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED)) },
                softwareAudioDecoderProbe = ffmpeg,
            ).snapshot()
        val withPlatformDolby =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/avc", "audio/ac3", "audio/eac3"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            channels = mapOf("audio/ac3" to 6, "audio/eac3" to 6),
                        ),
                    )
                },
                softwareAudioDecoderProbe = ffmpeg,
            ).snapshot()

        assertEquals(
            mapOf(
                PlaybackAudioCodec.DTS to 8,
                PlaybackAudioCodec.TRUEHD to 8,
                PlaybackAudioCodec.AC3 to 6,
                PlaybackAudioCodec.EAC3 to 6,
            ),
            withoutPlatformDolby.softwareAudioChannelCounts,
        )
        assertEquals(
            mapOf(PlaybackAudioCodec.DTS to 8, PlaybackAudioCodec.TRUEHD to 8),
            withPlatformDolby.softwareAudioChannelCounts,
        )
        assertEquals(PlaybackCapabilityInspection.COMPLETE, withoutPlatformDolby.inspectionCompleteness)
    }

    @Test
    fun codecIsOnlyDeclaredWhenEveryMimeTypeOfItIsDecodable() {
        val dtsCoreOnly = SoftwareAudioDecoderProbe { it == "audio/vnd.dts" || it == "audio/true-hd" }
        val snapshot =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = { listOf(fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED)) },
                softwareAudioDecoderProbe = dtsCoreOnly,
            ).snapshot()

        assertEquals(mapOf(PlaybackAudioCodec.TRUEHD to 8), snapshot.softwareAudioChannelCounts)
    }

    @Test
    fun missingOrBrokenSoftwareDecoderAddsNothingAndKeepsInspectionComplete() {
        fun snapshot(probe: SoftwareAudioDecoderProbe) =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = { listOf(fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED)) },
                softwareAudioDecoderProbe = probe,
            ).snapshot()

        val absent = snapshot(SoftwareAudioDecoderProbe.NONE)
        val broken = snapshot { error("native library failed to load") }

        assertTrue(absent.softwareAudioChannelCounts.isEmpty())
        assertTrue(broken.softwareAudioChannelCounts.isEmpty())
        assertEquals(PlaybackCapabilityInspection.COMPLETE, broken.inspectionCompleteness)
    }

    @Test
    fun failedPassthroughProbeIsUnknownEvidenceNotOptimisticPassthrough() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = { listOf(fakeCodec(types = listOf("video/avc"), hardwareSupport = CapabilitySupport.SUPPORTED)) },
                audioPassthroughProbe = AudioPassthroughProbe { error("audio service unavailable") },
            )

        val snapshot = source.snapshot()
        val profile = AndroidTvPlaybackDeviceProfileProvider(snapshotSource = { snapshot }).deviceProfile()

        assertEquals(PlaybackCapabilityInspection.PARTIAL, snapshot.inspectionCompleteness)
        assertEquals(CapabilitySupport.UNKNOWN, snapshot.audioSupport(PlaybackAudioCodec.DTS))
        assertTrue(profile.directPlayProfiles.none { "dts" in it.audioCodec || "truehd" in it.audioCodec })
    }

    @Test
    fun hardwareDolbyVisionAndHdrProfilesAreReadFromTheDecoders() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/hevc"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            profiles =
                                mapOf(
                                    "video/hevc" to setOf(CodecProfileLevel.HEVCProfileMain, CodecProfileLevel.HEVCProfileMain10HDR10),
                                ),
                        ),
                        fakeCodec(
                            types = listOf("video/dolby-vision"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            profiles =
                                mapOf(
                                    "video/dolby-vision" to
                                        setOf(
                                            CodecProfileLevel.DolbyVisionProfileDvheStn,
                                            CodecProfileLevel.DolbyVisionProfileDvheSt,
                                        ),
                                ),
                        ),
                    )
                },
                dolbyVisionDisplayProbe = DolbyVisionDisplayProbe { true },
            )

        val snapshot = source.snapshot()

        assertEquals(setOf(PlaybackHdrFormat.HDR10, PlaybackHdrFormat.HLG), snapshot.hdrSupport[PlaybackVideoCodec.HEVC])
        assertEquals(setOf(5, 8), snapshot.dolbyVisionProfiles)
        assertTrue(snapshot.displaySupportsDolbyVision)
    }

    @Test
    fun softwareDolbyVisionDecoderAndEightBitHevcDoNotCountAsHdr() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/hevc"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            profiles = mapOf("video/hevc" to setOf(CodecProfileLevel.HEVCProfileMain)),
                        ),
                        fakeCodec(
                            types = listOf("video/dolby-vision"),
                            hardwareSupport = CapabilitySupport.UNSUPPORTED,
                            profiles = mapOf("video/dolby-vision" to setOf(CodecProfileLevel.DolbyVisionProfileDvheStn)),
                        ),
                    )
                },
            )

        val snapshot = source.snapshot()

        assertEquals(emptySet(), snapshot.hdrSupport[PlaybackVideoCodec.HEVC])
        assertTrue(snapshot.dolbyVisionProfiles.isEmpty())
        assertFalse(snapshot.displaySupportsDolbyVision)
    }

    @Test
    fun hevcDecoderThatListsNoProfilesStaysUnrestrictedForVideoRange() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/hevc", "video/avc", "audio/mp4a-latm"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            channels = mapOf("audio/mp4a-latm" to 2),
                        ),
                    )
                },
            )

        val profile = AndroidTvPlaybackDeviceProfileProvider(snapshotSource = source).deviceProfile()

        assertTrue(profile.directPlayProfiles.any { "hevc" in it.videoCodec })
        assertTrue(profile.codecProfiles.none { it.type == "Video" })
    }

    @Test
    fun hdrTvWithoutDolbyVisionDecoderDeclaresNoPureDolbyVisionButKeepsHdr10Fallbacks() {
        val source =
            AndroidRuntimePlaybackCapabilitySnapshotSource(
                codecEntries = {
                    listOf(
                        fakeCodec(
                            types = listOf("video/hevc", "video/avc"),
                            hardwareSupport = CapabilitySupport.SUPPORTED,
                            profiles = mapOf("video/hevc" to setOf(CodecProfileLevel.HEVCProfileMain10HDR10)),
                        ),
                    )
                },
                dolbyVisionDisplayProbe = DolbyVisionDisplayProbe { true },
            )

        val profile = AndroidTvPlaybackDeviceProfileProvider(snapshotSource = source).deviceProfile()

        val allowed =
            profile.codecProfiles
                .single { it.codec == "hevc" && it.type == "Video" }
                .conditions
                .single()
                .value
                .split('|')
        assertTrue("DOVIWithHDR10" in allowed)
        assertTrue("HDR10" in allowed)
        assertFalse("DOVI" in allowed)
    }

    private fun fakeCodec(
        types: List<String>,
        isEncoder: Boolean = false,
        isAlias: Boolean = false,
        hardwareSupport: CapabilitySupport = CapabilitySupport.UNSUPPORTED,
        channels: Map<String, Int> = emptyMap(),
        maxChannelFailure: Set<String> = emptySet(),
        profiles: Map<String, Set<Int>> = emptyMap(),
    ): AndroidRuntimeCodecEntry =
        object : AndroidRuntimeCodecEntry {
            override val isEncoder: Boolean = isEncoder
            override val isAlias: Boolean = isAlias
            override val hardwareSupport: CapabilitySupport = hardwareSupport

            override fun supportedTypes(): List<String> = types

            override fun maxInputChannelCount(mimeType: String): Int? {
                if (mimeType in maxChannelFailure) error("capabilities failed")
                return channels[mimeType]
            }

            override fun profiles(mimeType: String): Set<Int> = profiles[mimeType].orEmpty()
        }
}
