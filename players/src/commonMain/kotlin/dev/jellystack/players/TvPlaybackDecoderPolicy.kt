package dev.jellystack.players

/** A decoder exposed by the platform codec registry. */
data class PlaybackDecoderDescriptor(
    val mimeType: String,
    val isHardwareAccelerated: Boolean,
    val isAlias: Boolean = false,
    val isEncoder: Boolean = false,
)

/**
 * TV devices frequently expose slow software decoders for modern video codecs. Advertising those
 * codecs to Jellyfin causes Direct Play to win even when the device cannot render the stream in
 * real time. H.264 remains safe as the platform baseline; newer codecs require hardware decode.
 */
fun selectTvDecoderCapabilities(
    decoders: List<PlaybackDecoderDescriptor>,
    allowSoftwareAdvancedVideo: Boolean = false,
): PlaybackDecoderCapabilities {
    val usable = decoders.filterNot { it.isEncoder || it.isAlias }

    fun supportsVideo(
        mimeType: String,
        requireHardware: Boolean,
    ): Boolean =
        usable.any {
            it.mimeType.equals(mimeType, ignoreCase = true) &&
                (!requireHardware || it.isHardwareAccelerated)
        }

    fun supportsAudio(vararg mimeTypes: String): Boolean =
        usable.any { decoder -> mimeTypes.any { decoder.mimeType.equals(it, ignoreCase = true) } }

    val audioCodecs =
        buildSet {
            if (supportsAudio("audio/mp4a-latm", "audio/aac")) add(PlaybackAudioCodec.AAC)
            if (supportsAudio("audio/mpeg")) add(PlaybackAudioCodec.MP3)
            if (supportsAudio("audio/ac3")) add(PlaybackAudioCodec.AC3)
            if (supportsAudio("audio/eac3", "audio/eac3-joc")) add(PlaybackAudioCodec.EAC3)
            if (supportsAudio("audio/opus")) add(PlaybackAudioCodec.OPUS)
            if (supportsAudio("audio/vorbis")) add(PlaybackAudioCodec.VORBIS)
            if (supportsAudio("audio/flac")) add(PlaybackAudioCodec.FLAC)
        }

    return PlaybackDecoderCapabilities(
        videoCodecs =
            buildSet {
                if (supportsVideo("video/av01", requireHardware = !allowSoftwareAdvancedVideo)) add(PlaybackVideoCodec.AV1)
                if (supportsVideo("video/hevc", requireHardware = !allowSoftwareAdvancedVideo)) add(PlaybackVideoCodec.HEVC)
                if (supportsVideo("video/x-vnd.on2.vp9", requireHardware = !allowSoftwareAdvancedVideo)) {
                    add(PlaybackVideoCodec.VP9)
                }
                if (supportsVideo("video/avc", requireHardware = false)) add(PlaybackVideoCodec.H264)
            },
        audioCodecs = audioCodecs,
        // TV codec registries often claim multichannel AAC support even when their platform
        // decoder crashes at runtime. Stereo AAC is the interoperable HLS fallback baseline.
        maxAacChannelCount = if (PlaybackAudioCodec.AAC in audioCodecs) 2 else null,
        // Keep Auto quality visually lossless on the big screen. Manual quality selections still
        // override this ceiling for an individual playback session.
        maxStreamingBitrate = 120_000_000,
    )
}
