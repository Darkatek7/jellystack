package dev.jellystack.players

private const val DOLBY_VISION_PROFILE_5 = 5
private const val DOLBY_VISION_PROFILE_7 = 7
private const val DOLBY_VISION_PROFILE_8 = 8
private const val DOLBY_VISION_PROFILE_10 = 10

/**
 * Which `VideoRangeType`s each direct-play codec may play without wrong colours. Only HEVC and AV1 carry HDR
 * or Dolby Vision on TV; other codecs stay unrestricted, as does a codec whose decoders reported no profiles.
 *
 * Media3 hands a Dolby Vision profile 4, 8 (HEVC) and 10 (AV1) track to the plain HEVC/AV1 decoder when no
 * Dolby Vision decoder exists, so those streams play their HDR10/HLG/SDR base layer. Profiles 5 and 7 have no
 * such fallback: without a Dolby Vision decoder they fail or render purple and green, so they are only offered
 * for direct play with a hardware Dolby Vision decoder on a display that lists Dolby Vision. HDR itself is not
 * tied to the display: a decoder that can decode HDR10 keeps direct play on an SDR display (the device
 * tone-maps).
 */
internal fun tvVideoRanges(
    snapshot: PlaybackCapabilitySnapshot,
    directVideoCodecs: Set<PlaybackVideoCodec>,
): Map<PlaybackVideoCodec, Set<PlaybackVideoRange>> =
    buildMap {
        listOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.AV1)
            .filter(directVideoCodecs::contains)
            .forEach { codec ->
                snapshot.hdrSupport[codec]?.let { hdr -> put(codec, videoRangesFor(codec, hdr, snapshot)) }
            }
    }

private fun videoRangesFor(
    codec: PlaybackVideoCodec,
    hdr: Set<PlaybackHdrFormat>,
    snapshot: PlaybackCapabilitySnapshot,
): Set<PlaybackVideoRange> {
    val isAv1 = codec == PlaybackVideoCodec.AV1
    val compatibleProfile = if (isAv1) DOLBY_VISION_PROFILE_10 else DOLBY_VISION_PROFILE_8
    val pureProfile = if (isAv1) DOLBY_VISION_PROFILE_10 else DOLBY_VISION_PROFILE_5
    val dolbyVision = snapshot.dolbyVisionProfiles
    val hasDolbyVisionDecoder = compatibleProfile in dolbyVision
    val displayShowsDolbyVision = snapshot.displaySupportsDolbyVision
    return buildSet {
        add(PlaybackVideoRange.SDR)
        add(PlaybackVideoRange.DOVI_WITH_SDR)
        if (PlaybackHdrFormat.HDR10 in hdr || hasDolbyVisionDecoder) {
            add(PlaybackVideoRange.HDR10)
            add(PlaybackVideoRange.HDR10_PLUS)
            add(PlaybackVideoRange.DOVI_WITH_HDR10)
            add(PlaybackVideoRange.DOVI_WITH_HDR10_PLUS)
        }
        if (PlaybackHdrFormat.HLG in hdr || hasDolbyVisionDecoder) {
            add(PlaybackVideoRange.HLG)
            add(PlaybackVideoRange.DOVI_WITH_HLG)
        }
        if (pureProfile in dolbyVision && displayShowsDolbyVision) add(PlaybackVideoRange.DOVI)
        if (!isAv1 && DOLBY_VISION_PROFILE_7 in dolbyVision && displayShowsDolbyVision) {
            add(PlaybackVideoRange.DOVI_WITH_EL)
            add(PlaybackVideoRange.DOVI_WITH_EL_HDR10_PLUS)
        }
    }
}
