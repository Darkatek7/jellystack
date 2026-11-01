package dev.jellystack.players

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto

class AndroidPlaybackDeviceProfileProvider(
    private val decoderTypes: () -> Set<String> = {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .map(String::lowercase)
            .toSet()
    },
) : PlaybackDeviceProfileProvider {
    override fun deviceProfile(): JellyfinDeviceProfileDto {
        val types = decoderTypes()
        val videoCodecs =
            buildSet {
                if (MIME_TYPE_AV1 in types) add(PlaybackVideoCodec.AV1)
                if (MIME_TYPE_HEVC in types) add(PlaybackVideoCodec.HEVC)
                if (MIME_TYPE_VP9 in types) add(PlaybackVideoCodec.VP9)
                if (MIME_TYPE_H264 in types) add(PlaybackVideoCodec.H264)
            }
        val audioCodecs =
            buildSet {
                if (MIME_TYPE_AAC in types || MIME_TYPE_AAC_ADTS in types) add(PlaybackAudioCodec.AAC)
                if (MIME_TYPE_MP3 in types) add(PlaybackAudioCodec.MP3)
                if (MIME_TYPE_AC3 in types) add(PlaybackAudioCodec.AC3)
                if (MIME_TYPE_EAC3 in types || MIME_TYPE_EAC3_JOC in types) add(PlaybackAudioCodec.EAC3)
                if (MIME_TYPE_OPUS in types) add(PlaybackAudioCodec.OPUS)
                if (MIME_TYPE_VORBIS in types) add(PlaybackAudioCodec.VORBIS)
                if (MIME_TYPE_FLAC in types) add(PlaybackAudioCodec.FLAC)
            }

        return PlaybackDeviceProfileFactory.create(
            "Jellystack Android",
            PlaybackDecoderCapabilities(
                videoCodecs = videoCodecs,
                audioCodecs = audioCodecs,
            ),
        )
    }

    private companion object {
        private const val MIME_TYPE_AV1 = "video/av01"
        private const val MIME_TYPE_HEVC = "video/hevc"
        private const val MIME_TYPE_VP9 = "video/x-vnd.on2.vp9"
        private const val MIME_TYPE_H264 = "video/avc"
        private const val MIME_TYPE_AAC = "audio/mp4a-latm"
        private const val MIME_TYPE_AAC_ADTS = "audio/aac"
        private const val MIME_TYPE_MP3 = "audio/mpeg"
        private const val MIME_TYPE_AC3 = "audio/ac3"
        private const val MIME_TYPE_EAC3 = "audio/eac3"
        private const val MIME_TYPE_EAC3_JOC = "audio/eac3-joc"
        private const val MIME_TYPE_OPUS = "audio/opus"
        private const val MIME_TYPE_VORBIS = "audio/vorbis"
        private const val MIME_TYPE_FLAC = "audio/flac"
    }
}

/**
 * Device profile tuned for TV playback from runtime evidence only: decoders, the audio output route and the
 * display. The profile is rebuilt on every [deviceProfile] call, so a soundbar or TV plugged in between two
 * playbacks changes the next negotiation. Only the application context is kept.
 */
class AndroidTvPlaybackDeviceProfileProvider(
    private val snapshotSource: PlaybackCapabilitySnapshotSource,
) : PlaybackDeviceProfileProvider {
    constructor(
        context: Context,
        softwareAudioDecoderProbe: SoftwareAudioDecoderProbe = SoftwareAudioDecoderProbe.NONE,
    ) : this(
        AndroidRuntimePlaybackCapabilitySnapshotSource(
            audioPassthroughProbe = Media3AudioPassthroughProbe(context.applicationContext),
            dolbyVisionDisplayProbe = AndroidDolbyVisionDisplayProbe(context.applicationContext),
            softwareAudioDecoderProbe = softwareAudioDecoderProbe,
        ),
    )

    override fun usesBestAvailableAudioTranscodeTarget(): Boolean = true

    override fun deviceProfile(): JellyfinDeviceProfileDto =
        PlaybackDeviceProfileFactory.create(
            name = "Jellystack TV",
            capabilities = selectTvDecoderCapabilities(snapshotSource.snapshot()),
        )
}

internal interface AndroidRuntimeCodecEntry {
    val isEncoder: Boolean
    val isAlias: Boolean
    val hardwareSupport: CapabilitySupport

    fun supportedTypes(): List<String>

    fun maxInputChannelCount(mimeType: String): Int?

    /** `CodecProfileLevel.profile` of every profile the decoder lists for [mimeType]; empty when it lists none. */
    fun profiles(mimeType: String): Set<Int> = emptySet()
}

internal class AndroidRuntimePlaybackCapabilitySnapshotSource(
    private val codecEntries: () -> List<AndroidRuntimeCodecEntry> = ::platformCodecEntries,
    private val audioPassthroughProbe: AudioPassthroughProbe = AudioPassthroughProbe { AudioPassthroughSupport.NONE },
    private val dolbyVisionDisplayProbe: DolbyVisionDisplayProbe = DolbyVisionDisplayProbe { false },
    private val softwareAudioDecoderProbe: SoftwareAudioDecoderProbe = SoftwareAudioDecoderProbe.NONE,
) : PlaybackCapabilitySnapshotSource {
    override fun snapshot(): PlaybackCapabilitySnapshot {
        val entries = runCatching(codecEntries).getOrElse { return PlaybackCapabilitySnapshot.failed() }
        var inspectionComplete = true
        val observedVideoSupport = mutableSetOf<PlaybackVideoCodec>()
        val observedVideoHardwareSupport = mutableMapOf<PlaybackVideoCodec, CapabilitySupport>()
        val observedAudioSupport = mutableSetOf<PlaybackAudioCodec>()
        val observedMaxAudioChannels = mutableMapOf<PlaybackAudioCodec, Int>()
        val observedHdr = mutableMapOf<PlaybackVideoCodec, MutableSet<PlaybackHdrFormat>>()
        val observedDolbyVisionProfiles = mutableSetOf<Int>()

        entries.forEach { entry ->
            val isEncoder =
                runCatching { entry.isEncoder }.getOrElse {
                    inspectionComplete = false
                    return@forEach
                }
            val isAlias =
                runCatching { entry.isAlias }.getOrElse {
                    inspectionComplete = false
                    return@forEach
                }
            if (isEncoder || isAlias) return@forEach

            val types =
                runCatching { entry.supportedTypes() }.getOrElse {
                    inspectionComplete = false
                    return@forEach
                }
            val hardwareSupport =
                runCatching { entry.hardwareSupport }.getOrElse {
                    inspectionComplete = false
                    CapabilitySupport.UNKNOWN
                }

            types.forEach { rawMimeType ->
                val mimeType = rawMimeType.lowercase()
                mimeType.videoCodec?.let { codec ->
                    observedVideoSupport += codec
                    observedVideoHardwareSupport[codec] =
                        mergeHardwareSupport(
                            observedVideoHardwareSupport[codec],
                            hardwareSupport,
                        )
                }
                // Only hardware decoders count as evidence for HDR and Dolby Vision, like for the codec itself.
                if (hardwareSupport == CapabilitySupport.SUPPORTED && (mimeType.hdrCodec != null || mimeType == MIME_DOLBY_VISION)) {
                    val profiles =
                        runCatching { entry.profiles(rawMimeType) }.getOrElse {
                            inspectionComplete = false
                            emptySet()
                        }
                    if (mimeType == MIME_DOLBY_VISION) {
                        profiles.mapNotNullTo(observedDolbyVisionProfiles, ::dolbyVisionProfileNumber)
                    } else if (profiles.isNotEmpty()) {
                        mimeType.hdrCodec?.let { codec ->
                            observedHdr.getOrPut(codec) { mutableSetOf() } += hdrFormats(codec, profiles)
                        }
                    }
                }
                mimeType.audioCodec?.let { codec ->
                    observedAudioSupport += codec
                    val maxChannels =
                        runCatching { entry.maxInputChannelCount(rawMimeType) }.getOrElse {
                            inspectionComplete = false
                            null
                        }
                    if (maxChannels == null) {
                        inspectionComplete = false
                    } else if (maxChannels > 0) {
                        observedMaxAudioChannels[codec] =
                            maxOf(observedMaxAudioChannels[codec] ?: 0, maxChannels)
                    }
                }
            }
        }

        // The output route decides what can be passed through, whatever the decoders say.
        runCatching(audioPassthroughProbe::probe)
            .getOrElse {
                inspectionComplete = false
                null
            }?.maxChannelsByCodec
            ?.forEach { (codec, channels) ->
                observedAudioSupport += codec
                observedMaxAudioChannels[codec] = maxOf(observedMaxAudioChannels[codec] ?: 0, channels)
            }
        val displayShowsDolbyVision = runCatching(dolbyVisionDisplayProbe::displayListsDolbyVision).getOrDefault(false)
        val softwareAudioChannelCounts = softwareAudioChannelCounts(softwareAudioDecoderProbe, observedAudioSupport)

        val missingSupport =
            if (inspectionComplete) {
                CapabilitySupport.UNSUPPORTED
            } else {
                CapabilitySupport.UNKNOWN
            }
        return PlaybackCapabilitySnapshot(
            videoSupport =
                PlaybackVideoCodec.entries.associateWith {
                    if (it in
                        observedVideoSupport
                    ) {
                        CapabilitySupport.SUPPORTED
                    } else {
                        missingSupport
                    }
                },
            videoHardwareSupport =
                PlaybackVideoCodec.entries.associateWith { codec ->
                    observedVideoHardwareSupport[codec]
                        ?: if (codec in observedVideoSupport) CapabilitySupport.UNKNOWN else missingSupport
                },
            audioSupport =
                PlaybackAudioCodec.entries.associateWith {
                    if (it in
                        observedAudioSupport
                    ) {
                        CapabilitySupport.SUPPORTED
                    } else {
                        missingSupport
                    }
                },
            maxAudioChannelCounts = observedMaxAudioChannels.toMap(),
            inspectionCompleteness =
                if (inspectionComplete) {
                    PlaybackCapabilityInspection.COMPLETE
                } else {
                    PlaybackCapabilityInspection.PARTIAL
                },
            hdrSupport = observedHdr,
            dolbyVisionProfiles = observedDolbyVisionProfiles,
            displaySupportsDolbyVision = displayShowsDolbyVision,
            softwareAudioChannelCounts = softwareAudioChannelCounts,
        )
    }
}

private fun mergeHardwareSupport(
    current: CapabilitySupport?,
    observed: CapabilitySupport,
): CapabilitySupport =
    when {
        current == CapabilitySupport.SUPPORTED || observed == CapabilitySupport.SUPPORTED -> CapabilitySupport.SUPPORTED
        current == CapabilitySupport.UNKNOWN || observed == CapabilitySupport.UNKNOWN -> CapabilitySupport.UNKNOWN
        else -> CapabilitySupport.UNSUPPORTED
    }

private fun platformCodecEntries(): List<AndroidRuntimeCodecEntry> =
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map { codecInfo ->
        object : AndroidRuntimeCodecEntry {
            override val isEncoder: Boolean
                get() = codecInfo.isEncoder

            override val isAlias: Boolean
                get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias

            override val hardwareSupport: CapabilitySupport
                get() =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (codecInfo.isHardwareAccelerated) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED
                    } else {
                        CapabilitySupport.UNKNOWN
                    }

            override fun supportedTypes(): List<String> = codecInfo.supportedTypes.toList()

            override fun maxInputChannelCount(mimeType: String): Int? =
                codecInfo.getCapabilitiesForType(mimeType).audioCapabilities?.maxInputChannelCount

            override fun profiles(mimeType: String): Set<Int> =
                codecInfo
                    .getCapabilitiesForType(mimeType)
                    .profileLevels
                    .mapTo(mutableSetOf()) { it.profile }
        }
    }

private const val MIME_DOLBY_VISION = "video/dolby-vision"

private val String.hdrCodec: PlaybackVideoCodec?
    get() = videoCodec?.takeIf { it == PlaybackVideoCodec.HEVC || it == PlaybackVideoCodec.AV1 }

/**
 * Decoders list `Main10` for 10-bit decoding and the HDR10 flavours explicitly. A `Main10` decoder is taken to
 * decode HDR10 and HLG bitstreams as well: it is the same 10-bit decode, and the device tone-maps for displays
 * that cannot show it. A decoder that lists only 8-bit profiles supports no HDR.
 */
private fun hdrFormats(
    codec: PlaybackVideoCodec,
    profiles: Set<Int>,
): Set<PlaybackHdrFormat> {
    val tenBitProfiles =
        if (codec == PlaybackVideoCodec.HEVC) {
            setOf(
                CodecProfileLevel.HEVCProfileMain10,
                CodecProfileLevel.HEVCProfileMain10HDR10,
                CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
        } else {
            setOf(
                CodecProfileLevel.AV1ProfileMain10,
                CodecProfileLevel.AV1ProfileMain10HDR10,
                CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            )
        }
    return if (profiles.any { it in tenBitProfiles }) {
        setOf(PlaybackHdrFormat.HDR10, PlaybackHdrFormat.HLG)
    } else {
        emptySet()
    }
}

/** Dolby Vision profile number (as in `dvhe.05`) for a `CodecProfileLevel.DolbyVisionProfile*` constant. */
private fun dolbyVisionProfileNumber(profile: Int): Int? =
    when (profile) {
        CodecProfileLevel.DolbyVisionProfileDvheDtr -> 4
        CodecProfileLevel.DolbyVisionProfileDvheStn -> 5
        CodecProfileLevel.DolbyVisionProfileDvheDtb -> 7
        CodecProfileLevel.DolbyVisionProfileDvheSt -> 8
        CodecProfileLevel.DolbyVisionProfileDvavSe -> 9
        CodecProfileLevel.DolbyVisionProfileDvav110 -> 10
        else -> null
    }

private val String.videoCodec: PlaybackVideoCodec?
    get() =
        when (this) {
            "video/av01" -> PlaybackVideoCodec.AV1
            "video/hevc" -> PlaybackVideoCodec.HEVC
            "video/x-vnd.on2.vp9" -> PlaybackVideoCodec.VP9
            "video/avc" -> PlaybackVideoCodec.H264
            else -> null
        }

private val String.audioCodec: PlaybackAudioCodec?
    get() =
        when (this) {
            "audio/mp4a-latm", "audio/aac" -> PlaybackAudioCodec.AAC
            "audio/mpeg" -> PlaybackAudioCodec.MP3
            "audio/ac3" -> PlaybackAudioCodec.AC3
            "audio/eac3", "audio/eac3-joc" -> PlaybackAudioCodec.EAC3
            "audio/opus" -> PlaybackAudioCodec.OPUS
            "audio/vorbis" -> PlaybackAudioCodec.VORBIS
            "audio/flac" -> PlaybackAudioCodec.FLAC
            "audio/vnd.dts", "audio/vnd.dts.hd" -> PlaybackAudioCodec.DTS
            "audio/true-hd" -> PlaybackAudioCodec.TRUEHD
            else -> null
        }
