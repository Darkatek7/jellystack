package dev.jellystack.players

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

private val DOLBY_VISION_PROFILE = Regex("""^dv(?:he|h1|av|a1|ab)\.(\d{2})""", RegexOption.IGNORE_CASE)

/**
 * Stats-for-nerds label for the video's dynamic range. Dolby Vision is read from the track format (its mime type
 * and the `dvhe.05.06` style codec string) because the transfer function of a profile 5 stream says nothing
 * about it, and a profile 8 stream decoded by the plain HEVC decoder still is a Dolby Vision track.
 */
@UnstableApi
internal fun hdrStatsLabel(
    sampleMimeType: String?,
    codecs: String?,
    colorTransfer: Int?,
): String? =
    when {
        sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> {
            val profile =
                codecs?.let {
                    DOLBY_VISION_PROFILE
                        .find(it)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }
            if (profile == null) "Dolby Vision" else "Dolby Vision P$profile"
        }
        colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10/PQ"
        colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }
