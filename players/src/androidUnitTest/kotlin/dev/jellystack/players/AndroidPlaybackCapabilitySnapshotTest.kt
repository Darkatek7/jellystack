package dev.jellystack.players

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

    private fun fakeCodec(
        types: List<String>,
        isEncoder: Boolean = false,
        isAlias: Boolean = false,
        hardwareSupport: CapabilitySupport = CapabilitySupport.UNSUPPORTED,
        channels: Map<String, Int> = emptyMap(),
        maxChannelFailure: Set<String> = emptySet(),
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
        }
}
