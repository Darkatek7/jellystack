package dev.jellystack.players

/**
 * Range and step of the per-title audio and subtitle delay. A positive delay means the track plays later than
 * the picture (audio) or than its own timestamps (subtitles); a negative delay means earlier.
 */
object PlaybackDelay {
    const val STEP_MS = 50
    const val MAX_MS = 5_000

    fun clamp(delayMs: Int): Int = delayMs.coerceIn(-MAX_MS, MAX_MS)

    /** [delayMs] moved by [steps] times [STEP_MS], kept inside the allowed range. */
    fun adjusted(
        delayMs: Int,
        steps: Int,
    ): Int = clamp(delayMs + steps * STEP_MS)
}
