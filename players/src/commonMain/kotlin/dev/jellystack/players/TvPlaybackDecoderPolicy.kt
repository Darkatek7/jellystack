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
    hdrSupport: Map<PlaybackVideoCodec, Set<PlaybackHdrFormat>> = emptyMap(),
    dolbyVisionProfiles: Set<Int> = emptySet(),
    /** The connected display lists Dolby Vision; false when it does not or when that could not be read. */
    val displaySupportsDolbyVision: Boolean = false,
    softwareAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
) {
    val videoSupport: Map<PlaybackVideoCodec, CapabilitySupport> = videoSupport.toMap()
    val videoHardwareSupport: Map<PlaybackVideoCodec, CapabilitySupport> = videoHardwareSupport.toMap()
    val audioSupport: Map<PlaybackAudioCodec, CapabilitySupport> = audioSupport.toMap()
    val maxAudioChannelCounts: Map<PlaybackAudioCodec, Int> = maxAudioChannelCounts.toMap()

    /**
     * HDR formats the hardware decoders of a codec can decode. A codec without an entry has no evidence
     * (decoder reported no profiles), which leaves it unrestricted in the profile.
     */
    val hdrSupport: Map<PlaybackVideoCodec, Set<PlaybackHdrFormat>> = hdrSupport.mapValues { it.value.toSet() }

    /** Dolby Vision profile numbers (5, 7, 8, 10, ...) a hardware Dolby Vision decoder can decode. */
    val dolbyVisionProfiles: Set<Int> = dolbyVisionProfiles.toSet()

    /**
     * Audio codecs the app's own software decoder (FFmpeg) can play, each with the most channels it should be
     * asked for. They are Direct Play inputs on top of the platform evidence in [audioSupport], never server
     * transcode targets: the server picks those, and a software decode is the last resort, not the preference.
     */
    val softwareAudioChannelCounts: Map<PlaybackAudioCodec, Int> = softwareAudioChannelCounts.toMap()

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
    val platformAudioCodecs =
        PlaybackAudioCodec.entries.filterTo(mutableSetOf()) {
            snapshot.audioSupport(it) == CapabilitySupport.SUPPORTED
        }
    val directAudioCodecs = platformAudioCodecs + snapshot.softwareAudioChannelCounts.keys

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
    // Passthrough-only codecs cannot be an HLS encoder target: the server cannot encode them. Software-decoded
    // codecs are no target either, so the transcode lists only ever follow the platform evidence.
    val transcodeAudioCodecs =
        buildSet {
            addAll(platformAudioCodecs - PASSTHROUGH_ONLY_AUDIO_CODECS)
            if (
                needsInteroperableFallback &&
                snapshot.audioSupport(PlaybackAudioCodec.AAC) != CapabilitySupport.UNSUPPORTED
            ) {
                add(PlaybackAudioCodec.AAC)
            }
        }

    val maxAudioChannelCounts =
        directAudioCodecs.associateWith { codec ->
            val platformReported = snapshot.maxAudioChannelCounts[codec]?.takeIf { it > 0 }
            val softwareReported = snapshot.softwareAudioChannelCounts[codec]?.takeIf { it > 0 }
            val reported = listOfNotNull(platformReported, softwareReported).maxOrNull()
            minOf(reported ?: CONSERVATIVE_UNKNOWN_CHANNEL_COUNT, codec.safeMaximumChannelCount)
        }

    // Best target first: multichannel Dolby keeps the surround mix the TV can decode, AAC stays the
    // interoperable stereo fallback. Only codecs the device reported as playable are ever listed.
    val transcodeAudioPreference =
        TV_TRANSCODE_AUDIO_PREFERENCE.filter { it in transcodeAudioCodecs }
    val preferredTranscodeCodec = transcodeAudioPreference.firstOrNull()

    return PlaybackDecoderCapabilities(
        videoCodecs = directVideoCodecs,
        audioCodecs = directAudioCodecs,
        maxAacChannelCount =
            maxAudioChannelCounts[PlaybackAudioCodec.AAC]
                ?: CONSERVATIVE_UNKNOWN_CHANNEL_COUNT.takeIf { PlaybackAudioCodec.AAC in transcodeAudioCodecs },
        maxAudioChannelCounts = maxAudioChannelCounts,
        maxStreamingBitrate = 120_000_000,
        transcodingVideoCodecs = transcodeVideoCodecs,
        transcodingAudioCodecs = transcodeAudioCodecs,
        maxTranscodingAudioChannelCount =
            preferredTranscodeCodec?.let { codec ->
                maxAudioChannelCounts[codec] ?: CONSERVATIVE_UNKNOWN_CHANNEL_COUNT
            } ?: transcodeAudioCodecs.mapNotNull(maxAudioChannelCounts::get).maxOrNull(),
        transcodingAudioCodecPreference = transcodeAudioPreference,
        supportsPgsSubtitles = true,
        // Hardware AV1 plays a server remux as well as Direct Play, so an audio-only transcode keeps the video.
        hlsFmp4VideoCopyCodecs = directVideoCodecs.filterTo(mutableSetOf()) { it == PlaybackVideoCodec.AV1 },
        videoRanges = tvVideoRanges(snapshot, directVideoCodecs),
    )
}

private val ADVANCED_VIDEO_CODECS =
    setOf(
        PlaybackVideoCodec.AV1,
        PlaybackVideoCodec.HEVC,
        PlaybackVideoCodec.VP9,
    )

private const val CONSERVATIVE_UNKNOWN_CHANNEL_COUNT = 2

private val PASSTHROUGH_ONLY_AUDIO_CODECS = setOf(PlaybackAudioCodec.DTS, PlaybackAudioCodec.TRUEHD)

private val TV_TRANSCODE_AUDIO_PREFERENCE =
    listOf(
        PlaybackAudioCodec.EAC3,
        PlaybackAudioCodec.AC3,
        PlaybackAudioCodec.AAC,
        PlaybackAudioCodec.MP3,
    )

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
            PlaybackAudioCodec.DTS,
            PlaybackAudioCodec.TRUEHD,
            -> 8
        }
