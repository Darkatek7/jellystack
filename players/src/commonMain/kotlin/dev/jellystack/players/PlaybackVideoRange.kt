package dev.jellystack.players

/**
 * Jellyfin `VideoRangeType` values (`MediaStream.VideoRangeType`), named as the server sends them. The Dolby
 * Vision variants say which base layer the stream carries, which decides whether a decoder without Dolby Vision
 * can still show it with correct colours.
 */
enum class PlaybackVideoRange(
    val jellyfinName: String,
) {
    SDR("SDR"),
    HDR10("HDR10"),
    HDR10_PLUS("HDR10Plus"),
    HLG("HLG"),

    /** Pure Dolby Vision (profile 5 or AV1 profile 10.0): no backwards compatible base layer. */
    DOVI("DOVI"),
    DOVI_WITH_HDR10("DOVIWithHDR10"),
    DOVI_WITH_HDR10_PLUS("DOVIWithHDR10Plus"),
    DOVI_WITH_HLG("DOVIWithHLG"),
    DOVI_WITH_SDR("DOVIWithSDR"),

    /** Dolby Vision profile 7 with enhancement layer. */
    DOVI_WITH_EL("DOVIWithEL"),
    DOVI_WITH_EL_HDR10_PLUS("DOVIWithELHDR10Plus"),
}

/** Colour formats a hardware decoder can decode, independent of what the display can show. */
enum class PlaybackHdrFormat {
    HDR10,
    HLG,
}
