package dev.jellystack.players

enum class CapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

enum class PlaybackCapabilityInspection {
    COMPLETE,
    PARTIAL,
    FAILED,
}

/** One immutable observation of the active device and output path. */
class PlaybackCapabilitySnapshot(
    videoSupport: Map<PlaybackVideoCodec, CapabilitySupport>,
    videoHardwareSupport: Map<PlaybackVideoCodec, CapabilitySupport>,
    audioSupport: Map<PlaybackAudioCodec, CapabilitySupport>,
    maxAudioChannelCounts: Map<PlaybackAudioCodec, Int>,
    val inspectionCompleteness: PlaybackCapabilityInspection,
) {
    val videoSupport: Map<PlaybackVideoCodec, CapabilitySupport> = videoSupport.toMap()
    val videoHardwareSupport: Map<PlaybackVideoCodec, CapabilitySupport> = videoHardwareSupport.toMap()
    val audioSupport: Map<PlaybackAudioCodec, CapabilitySupport> = audioSupport.toMap()
    val maxAudioChannelCounts: Map<PlaybackAudioCodec, Int> = maxAudioChannelCounts.toMap()

    fun videoSupport(codec: PlaybackVideoCodec): CapabilitySupport = videoSupport[codec] ?: CapabilitySupport.UNKNOWN

    fun videoHardwareSupport(codec: PlaybackVideoCodec): CapabilitySupport = videoHardwareSupport[codec] ?: CapabilitySupport.UNKNOWN

    fun audioSupport(codec: PlaybackAudioCodec): CapabilitySupport = audioSupport[codec] ?: CapabilitySupport.UNKNOWN

    companion object {
        fun failed(): PlaybackCapabilitySnapshot =
            PlaybackCapabilitySnapshot(
                videoSupport = emptyMap(),
                videoHardwareSupport = emptyMap(),
                audioSupport = emptyMap(),
                maxAudioChannelCounts = emptyMap(),
                inspectionCompleteness = PlaybackCapabilityInspection.FAILED,
            )
    }
}

fun interface PlaybackCapabilitySnapshotSource {
    fun snapshot(): PlaybackCapabilitySnapshot
}

/**
 * Converts runtime evidence into Jellyfin negotiation capabilities. Modern video codecs require
 * both decoder and hardware evidence. Incomplete or contradictory inspection is never promoted to
 * Direct Play; it only enables the interoperable H.264/AAC server-transcode fallback.
 */
fun selectTvDecoderCapabilities(snapshot: PlaybackCapabilitySnapshot): PlaybackDecoderCapabilities {
    fun advancedVideoSupported(codec: PlaybackVideoCodec): Boolean =
        snapshot.videoSupport(codec) == CapabilitySupport.SUPPORTED &&
            snapshot.videoHardwareSupport(codec) == CapabilitySupport.SUPPORTED

    val directVideoCodecs =
        buildSet {
            if (advancedVideoSupported(PlaybackVideoCodec.AV1)) add(PlaybackVideoCodec.AV1)
            if (advancedVideoSupported(PlaybackVideoCodec.HEVC)) add(PlaybackVideoCodec.HEVC)
            if (advancedVideoSupported(PlaybackVideoCodec.VP9)) add(PlaybackVideoCodec.VP9)
            if (snapshot.videoSupport(PlaybackVideoCodec.H264) == CapabilitySupport.SUPPORTED) {
                add(PlaybackVideoCodec.H264)
            }
        }
    val directAudioCodecs =
        PlaybackAudioCodec.entries.filterTo(mutableSetOf()) {
            snapshot.audioSupport(it) == CapabilitySupport.SUPPORTED
        }

    val hasContradictoryEvidence =
        ADVANCED_VIDEO_CODECS.any { codec ->
            val support = snapshot.videoSupport(codec)
            val hardwareSupport = snapshot.videoHardwareSupport(codec)
            (support == CapabilitySupport.UNSUPPORTED && hardwareSupport == CapabilitySupport.SUPPORTED) ||
                (support == CapabilitySupport.UNKNOWN && hardwareSupport != CapabilitySupport.UNKNOWN)
        }
    val hasUnknownEvidence =
        PlaybackVideoCodec.entries.any { snapshot.videoSupport(it) == CapabilitySupport.UNKNOWN } ||
            PlaybackAudioCodec.entries.any { snapshot.audioSupport(it) == CapabilitySupport.UNKNOWN }
    val needsInteroperableFallback =
        snapshot.inspectionCompleteness != PlaybackCapabilityInspection.COMPLETE ||
            hasContradictoryEvidence ||
            hasUnknownEvidence

    val transcodeVideoCodecs =
        buildSet {
            if (PlaybackVideoCodec.HEVC in directVideoCodecs) add(PlaybackVideoCodec.HEVC)
            if (
                PlaybackVideoCodec.H264 in directVideoCodecs ||
                (needsInteroperableFallback && snapshot.videoSupport(PlaybackVideoCodec.H264) != CapabilitySupport.UNSUPPORTED)
            ) {
                add(PlaybackVideoCodec.H264)
            }
        }
    val transcodeAudioCodecs =
        buildSet {
            addAll(directAudioCodecs)
            if (
                needsInteroperableFallback &&
                snapshot.audioSupport(PlaybackAudioCodec.AAC) != CapabilitySupport.UNSUPPORTED
            ) {
                add(PlaybackAudioCodec.AAC)
            }
        }

    val maxAudioChannelCounts =
        directAudioCodecs.associateWith { codec ->
            val reported = snapshot.maxAudioChannelCounts[codec]?.takeIf { it > 0 }
            minOf(reported ?: CONSERVATIVE_UNKNOWN_CHANNEL_COUNT, codec.safeMaximumChannelCount)
        }

    return PlaybackDecoderCapabilities(
        videoCodecs = directVideoCodecs,
        audioCodecs = directAudioCodecs,
        maxAacChannelCount = maxAudioChannelCounts[PlaybackAudioCodec.AAC],
        maxAudioChannelCounts = maxAudioChannelCounts,
        maxStreamingBitrate = 120_000_000,
        transcodingVideoCodecs = transcodeVideoCodecs,
        transcodingAudioCodecs = transcodeAudioCodecs,
        maxTranscodingAudioChannelCount =
            if (PlaybackAudioCodec.AAC in transcodeAudioCodecs) {
                maxAudioChannelCounts[PlaybackAudioCodec.AAC] ?: CONSERVATIVE_UNKNOWN_CHANNEL_COUNT
            } else {
                transcodeAudioCodecs.mapNotNull(maxAudioChannelCounts::get).maxOrNull()
            },
    )
}

private val ADVANCED_VIDEO_CODECS =
    setOf(
        PlaybackVideoCodec.AV1,
        PlaybackVideoCodec.HEVC,
        PlaybackVideoCodec.VP9,
    )

private const val CONSERVATIVE_UNKNOWN_CHANNEL_COUNT = 2

private val PlaybackAudioCodec.safeMaximumChannelCount: Int
    get() =
        when (this) {
            PlaybackAudioCodec.AAC,
            PlaybackAudioCodec.MP3,
            -> 2

            PlaybackAudioCodec.AC3,
            PlaybackAudioCodec.EAC3,
            -> 6

            PlaybackAudioCodec.OPUS,
            PlaybackAudioCodec.VORBIS,
            PlaybackAudioCodec.FLAC,
            -> 8
        }
