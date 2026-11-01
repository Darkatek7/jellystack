package dev.jellystack.design.tv

import kotlin.math.abs

/** A display mode as the platform reports it (`Display.Mode`), reduced to what the selection needs. */
internal data class TvDisplayMode(
    val id: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
)

/**
 * The display mode to play a video of [videoFrameRate] fps in: it keeps the physical resolution of [current]
 * (so HDR and the picture size stay as they are) and has a refresh rate that is an integer multiple (1 to 5) of
 * the frame rate, so every video frame is shown equally long. Null when the frame rate is unknown or invalid or
 * no supported mode fits; the caller then leaves the display alone.
 *
 * The result can be [current] itself, which means no switch is needed.
 *
 * Ranking: modes that match the frame rate exactly (23.976 on 23.976, 47.952 or 119.88 Hz) win over modes that are
 * only close (23.976 on 24 Hz, 29.97 on 30 Hz). Within a class the lowest multiple wins, then the smaller error.
 */
internal fun selectMatchingDisplayMode(
    current: TvDisplayMode,
    supported: List<TvDisplayMode>,
    videoFrameRate: Float?,
): TvDisplayMode? {
    // NaN and infinity fall outside the range, so they count as unknown too.
    val frameRate = videoFrameRate?.takeIf { it in MIN_VIDEO_FRAME_RATE..MAX_VIDEO_FRAME_RATE } ?: return null
    return supported
        .asSequence()
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .mapNotNull { it.refreshMatch(frameRate) }
        .minWithOrNull(compareBy<RefreshMatch>({ !it.exact }, { it.multiple }, { it.relativeError }, { it.mode.id }))
        ?.mode
}

private class RefreshMatch(
    val mode: TvDisplayMode,
    val multiple: Int,
    val relativeError: Float,
) {
    val exact: Boolean get() = relativeError <= EXACT_TOLERANCE
}

private fun TvDisplayMode.refreshMatch(frameRate: Float): RefreshMatch? =
    if (refreshRate.isFinite() && refreshRate > 0f) {
        (1..MAX_REFRESH_MULTIPLE)
            .asSequence()
            .map { multiple -> RefreshMatch(this, multiple, abs(refreshRate / multiple - frameRate) / frameRate) }
            .firstOrNull { it.relativeError <= NEAR_TOLERANCE }
    } else {
        null
    }

private const val MIN_VIDEO_FRAME_RATE = 10f
private const val MAX_VIDEO_FRAME_RATE = 120f
private const val MAX_REFRESH_MULTIPLE = 5

/** Display rates are floats ("23.976025", "23.98"): relative error below which a rate counts as the exact multiple. */
private const val EXACT_TOLERANCE = 0.0005f

/** 24 Hz for 23.976 fps differs by 0.1%: a display without the NTSC modes is still a good fit. */
private const val NEAR_TOLERANCE = 0.002f
