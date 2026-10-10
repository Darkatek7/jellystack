package dev.jellystack.core.jellyfin

import kotlin.math.ceil

/** Watch progress of a partly played item, as shown on Continue Watching and Next Up cards. */
data class JellyfinItemProgress(
    val fraction: Float,
    val remainingMinutes: Int?,
)

/**
 * Progress from the resume position and runtime, falling back to the server's played percentage.
 * Returns null for unstarted or finished items, where a progress bar would only add noise.
 */
fun JellyfinItem.watchProgress(): JellyfinItemProgress? {
    val runtime = runTimeTicks?.takeIf { it > 0L }
    val position = positionTicks?.takeIf { it > 0L }
    val fraction =
        (
            if (runtime != null && position != null) {
                position.toDouble() / runtime
            } else {
                playedPercentage?.div(PERCENT)
            }
        )?.takeIf { it > 0.0 && it < 1.0 } ?: return null
    val remainingMinutes =
        runtime?.let {
            val remainingTicks = it - (position ?: (it * fraction).toLong())
            ceil(remainingTicks.toDouble() / TICKS_PER_MINUTE).toInt().coerceAtLeast(1)
        }
    return JellyfinItemProgress(fraction = fraction.toFloat(), remainingMinutes = remainingMinutes)
}

private const val PERCENT = 100.0
private const val TICKS_PER_MINUTE = 600_000_000L
