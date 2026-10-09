package dev.jellystack.core.jellyfin

/** Resolution class of a video, named the way people know it. */
enum class JellyfinVideoResolution(
    val label: String,
) {
    UHD_4K("4K"),
    FULL_HD("1080p"),
    HD("720p"),
    SD("SD"),
}

/**
 * Classifies by width or height so letterboxed and cropped encodes keep their class:
 * a 1920×800 scope film is 1080p, not "800p", and a 1440×1080 encode is 1080p, not 720p.
 */
fun jellyfinVideoResolution(
    width: Int?,
    height: Int?,
): JellyfinVideoResolution? {
    val w = width?.takeIf { it > 0 } ?: 0
    val h = height?.takeIf { it > 0 } ?: 0
    return when {
        w == 0 && h == 0 -> null
        w >= UHD_MIN_WIDTH || h >= UHD_MIN_HEIGHT -> JellyfinVideoResolution.UHD_4K
        w >= FULL_HD_MIN_WIDTH || h >= FULL_HD_MIN_HEIGHT -> JellyfinVideoResolution.FULL_HD
        w >= HD_MIN_WIDTH || h >= HD_MIN_HEIGHT -> JellyfinVideoResolution.HD
        else -> JellyfinVideoResolution.SD
    }
}

/** Resolution of the first video stream of the first media source, ignoring streams without dimensions. */
fun JellyfinItemDetail.primaryVideoResolution(): JellyfinVideoResolution? =
    mediaSources
        .firstOrNull()
        ?.streams
        ?.firstOrNull { it.type == JellyfinMediaStreamType.VIDEO && ((it.width ?: 0) > 0 || (it.height ?: 0) > 0) }
        ?.let { jellyfinVideoResolution(it.width, it.height) }

private const val UHD_MIN_WIDTH = 3200
private const val UHD_MIN_HEIGHT = 1800
private const val FULL_HD_MIN_WIDTH = 1700
private const val FULL_HD_MIN_HEIGHT = 1000
private const val HD_MIN_WIDTH = 1200
private const val HD_MIN_HEIGHT = 700
