package dev.jellystack.players

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

/** Device profile tuned for TV playback from runtime codec evidence only. */
class AndroidTvPlaybackDeviceProfileProvider(
    private val snapshotSource: PlaybackCapabilitySnapshotSource = AndroidRuntimePlaybackCapabilitySnapshotSource(),
) : PlaybackDeviceProfileProvider {
    override fun requiresServerSelectedAudioForVideo(): Boolean = true

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
}

internal class AndroidRuntimePlaybackCapabilitySnapshotSource(
    private val codecEntries: () -> List<AndroidRuntimeCodecEntry> = ::platformCodecEntries,
) : PlaybackCapabilitySnapshotSource {
    override fun snapshot(): PlaybackCapabilitySnapshot {
        val entries = runCatching(codecEntries).getOrElse { return PlaybackCapabilitySnapshot.failed() }
        var inspectionComplete = true
        val observedVideoSupport = mutableSetOf<PlaybackVideoCodec>()
        val observedVideoHardwareSupport = mutableMapOf<PlaybackVideoCodec, CapabilitySupport>()
        val observedAudioSupport = mutableSetOf<PlaybackAudioCodec>()
        val observedMaxAudioChannels = mutableMapOf<PlaybackAudioCodec, Int>()

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
        }
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
            else -> null
        }
