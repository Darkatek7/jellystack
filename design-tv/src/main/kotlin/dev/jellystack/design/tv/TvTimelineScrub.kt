package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * D-pad scrubbing on the seek bar. Each press moves a preview position from the last preview (or the
 * playback position) and speeds up while the key is held. With [liveSeeks] the player follows the
 * preview at most every [TV_SCRUB_COMMIT_INTERVAL_MS]; otherwise only the release seeks.
 */
internal class TvTimelineScrub(
    private val liveSeeks: Boolean,
    private val nowMs: () -> Long,
) {
    var previewPositionMs by mutableStateOf<Long?>(null)
        private set

    private var lastLiveSeekMs = Long.MIN_VALUE

    /** A direction press or key repeat; returns a position to seek to right away, if any. */
    fun press(
        basePositionMs: Long,
        durationMs: Long?,
        stepMs: Long,
        repeatCount: Int,
    ): Long? {
        val origin = previewPositionMs ?: basePositionMs
        val upperBound = durationMs?.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (origin + stepMs * tvScrubAcceleration(repeatCount)).coerceIn(0L, upperBound)
        previewPositionMs = target
        val now = nowMs()
        val throttled = lastLiveSeekMs != Long.MIN_VALUE && now - lastLiveSeekMs < TV_SCRUB_COMMIT_INTERVAL_MS
        val seekNow = liveSeeks && !throttled
        if (seekNow) lastLiveSeekMs = now
        return target.takeIf { seekNow }
    }

    /** The key was released: the final position to seek to. The preview stays until [clear]. */
    fun release(): Long? = previewPositionMs

    fun clear() {
        previewPositionMs = null
        lastLiveSeekMs = Long.MIN_VALUE
    }
}

/** Step multiplier for a held direction key: steady for a moment, then faster the longer it is held. */
internal fun tvScrubAcceleration(repeatCount: Int): Int =
    when {
        repeatCount < SCRUB_STEADY_REPEATS -> 1
        repeatCount < SCRUB_FAST_REPEATS -> 2
        repeatCount < SCRUB_FASTER_REPEATS -> 4
        else -> SCRUB_MAX_ACCELERATION
    }

private const val SCRUB_STEADY_REPEATS = 5
private const val SCRUB_FAST_REPEATS = 15
private const val SCRUB_FASTER_REPEATS = 30
private const val SCRUB_MAX_ACCELERATION = 8
