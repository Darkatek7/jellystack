package dev.jellystack.players

/**
 * When to offer the next episode during playback: from the start of the credits when the server marks
 * them, otherwise in the last [UP_NEXT_FALLBACK_MS] of titles long enough to have credits at all.
 */
fun shouldOfferUpNext(
    positionMs: Long,
    durationMs: Long?,
    creditsActive: Boolean,
): Boolean {
    if (creditsActive) return true
    val duration = durationMs?.takeIf { it >= UP_NEXT_MIN_DURATION_MS } ?: return false
    val remaining = duration - positionMs
    return remaining in 1..UP_NEXT_FALLBACK_MS
}

private const val UP_NEXT_FALLBACK_MS = 30_000L
private const val UP_NEXT_MIN_DURATION_MS = 10 * 60_000L
