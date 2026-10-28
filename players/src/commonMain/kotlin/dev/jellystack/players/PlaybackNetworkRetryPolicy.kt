package dev.jellystack.players

/**
 * How often one playback session re-prepares the same source after a network failure before the
 * transcode fallback chain takes over. The budget refills once playback has advanced
 * [progressToRefillMs] past the position of the last retry, so a connection that keeps breaking
 * without ever delivering media still ends in an error instead of looping forever.
 */
internal class PlaybackNetworkRetryPolicy(
    private val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
    private val progressToRefillMs: Long = DEFAULT_PROGRESS_TO_REFILL_MS,
) {
    private var used = 0
    private var lastRetryPositionMs = 0L

    /** Delay before the next retry resuming at [positionMs], or null when the budget is spent. */
    fun nextBackoffMs(positionMs: Long): Long? {
        val backoff = backoffMs.getOrNull(used) ?: return null
        used += 1
        lastRetryPositionMs = positionMs
        return backoff
    }

    /** Reports playback progress; refills the budget when a retry led to real playback. */
    fun onPosition(positionMs: Long) {
        if (used > 0 && positionMs - lastRetryPositionMs >= progressToRefillMs) used = 0
    }

    fun reset() {
        used = 0
        lastRetryPositionMs = 0L
    }

    companion object {
        val DEFAULT_BACKOFF_MS: List<Long> = listOf(1_000L, 2_500L, 5_000L)
        const val DEFAULT_PROGRESS_TO_REFILL_MS = 10_000L
    }
}
