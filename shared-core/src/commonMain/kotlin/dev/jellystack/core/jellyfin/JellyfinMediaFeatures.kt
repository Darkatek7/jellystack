package dev.jellystack.core.jellyfin

/** Audio channel layouts that have a common name; other channel counts have none. */
enum class JellyfinChannelLayout(
    val channels: Int,
) {
    MONO(1),
    STEREO(2),
    SURROUND_5_1(6),
    SURROUND_7_1(8),
}

fun jellyfinChannelLayout(channels: Int?): JellyfinChannelLayout? = JellyfinChannelLayout.entries.firstOrNull { it.channels == channels }

/** HDR format of a video stream, most specific first. */
enum class JellyfinDynamicRange {
    DOLBY_VISION,
    HDR10_PLUS,
    HDR10,
    HLG,
    HDR,
}

/** Object-based audio formats, as ffmpeg names them in an audio stream's profile. */
enum class JellyfinImmersiveAudio {
    DOLBY_ATMOS,
    DTS_X,
}

/**
 * What the first media source of an item offers. Audio describes the best track, not only the
 * default one, so a German 5.1 default next to an English Atmos track still shows Atmos and 7.1.
 */
data class JellyfinMediaFeatures(
    val resolution: JellyfinVideoResolution?,
    val dynamicRange: JellyfinDynamicRange?,
    val immersiveAudio: JellyfinImmersiveAudio?,
    val surround: JellyfinChannelLayout?,
    val hearingImpairedSubtitles: Boolean,
)

fun JellyfinItemDetail.mediaFeatures(): JellyfinMediaFeatures {
    val streams = mediaSources.firstOrNull()?.streams.orEmpty()
    val audio = streams.filter { it.type == JellyfinMediaStreamType.AUDIO }
    return JellyfinMediaFeatures(
        resolution = primaryVideoResolution(),
        dynamicRange = primaryVideoStream()?.let(::jellyfinDynamicRange),
        immersiveAudio = audio.mapNotNull { jellyfinImmersiveAudio(it.profile) }.minOrNull(),
        surround =
            audio
                .mapNotNull { jellyfinChannelLayout(it.channels) }
                .filter { it.channels > JellyfinChannelLayout.STEREO.channels }
                .maxByOrNull { it.channels },
        hearingImpairedSubtitles = streams.any { it.type == JellyfinMediaStreamType.SUBTITLE && it.isHearingImpaired },
    )
}

/**
 * Reads the server's `VideoRangeType`; older servers only send `VideoRange`, which still tells HDR
 * from SDR. `DOVIInvalid` is Dolby Vision metadata the server could not use, so it is not labelled as such.
 */
private fun jellyfinDynamicRange(stream: JellyfinMediaStream): JellyfinDynamicRange? {
    val type = stream.videoRangeType.orEmpty()
    return when {
        type.startsWith("DOVI", ignoreCase = true) && !type.equals("DOVIInvalid", ignoreCase = true) ->
            JellyfinDynamicRange.DOLBY_VISION
        type.equals("HDR10Plus", ignoreCase = true) -> JellyfinDynamicRange.HDR10_PLUS
        type.equals("HDR10", ignoreCase = true) -> JellyfinDynamicRange.HDR10
        type.equals("HLG", ignoreCase = true) -> JellyfinDynamicRange.HLG
        stream.videoRange.equals("HDR", ignoreCase = true) -> JellyfinDynamicRange.HDR
        else -> null
    }
}

/** ffmpeg profiles: "Dolby TrueHD + Dolby Atmos", "Dolby Digital Plus + Dolby Atmos", "DTS-HD MA + DTS:X". */
private fun jellyfinImmersiveAudio(profile: String?): JellyfinImmersiveAudio? =
    when {
        profile == null -> null
        profile.contains("Atmos", ignoreCase = true) -> JellyfinImmersiveAudio.DOLBY_ATMOS
        profile.contains("DTS:X", ignoreCase = true) -> JellyfinImmersiveAudio.DTS_X
        else -> null
    }
