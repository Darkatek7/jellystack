package dev.jellystack.players

/** A chapter of the playing item; [index] addresses its chapter image on the server. */
data class PlaybackChapter(
    val index: Int,
    val name: String?,
    val startPositionMs: Long,
    val imageTag: String?,
)

/** Chapter that contains [positionMs], or null before the first chapter. */
fun List<PlaybackChapter>.chapterAt(positionMs: Long): PlaybackChapter? = lastOrNull { it.startPositionMs <= positionMs }

/** Start of the next chapter after [positionMs], or null in the last chapter. */
fun List<PlaybackChapter>.nextChapterStart(positionMs: Long): Long? = firstOrNull { it.startPositionMs > positionMs }?.startPositionMs

/**
 * Start for a "previous chapter" press: early in a chapter it goes to the chapter before, otherwise it
 * restarts the current chapter, like a CD player's back button.
 */
fun List<PlaybackChapter>.previousChapterStart(
    positionMs: Long,
    restartThresholdMs: Long = CHAPTER_RESTART_THRESHOLD_MS,
): Long? {
    val currentIndex = indexOfLast { it.startPositionMs <= positionMs }
    if (currentIndex < 0) return null
    val current = this[currentIndex]
    return if (positionMs - current.startPositionMs > restartThresholdMs || currentIndex == 0) {
        current.startPositionMs
    } else {
        this[currentIndex - 1].startPositionMs
    }
}

/** Wall-clock time playback ends at, accounting for the playback speed; null without a known duration. */
fun playbackEndsAtMs(
    nowMs: Long,
    positionMs: Long,
    durationMs: Long?,
    playbackSpeed: Float,
): Long? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    val remaining = (duration - positionMs).coerceAtLeast(0L)
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    return nowMs + (remaining / speed).toLong()
}

/** Seek bar marks: chapter starts as fractions, segments (intro, credits, …) as fractional ranges. */
data class PlaybackTimelineMarkers(
    val chapterStarts: List<Float> = emptyList(),
    val segments: List<PlaybackTimelineRange> = emptyList(),
)

data class PlaybackTimelineRange(
    val type: PlaybackSegmentType,
    val start: Float,
    val end: Float,
)

/** Marks for a [durationMs] timeline; a chapter at the very start adds no tick. */
fun playbackTimelineMarkers(
    durationMs: Long?,
    chapters: List<PlaybackChapter>,
    segments: List<PlaybackSegment>,
): PlaybackTimelineMarkers {
    val duration = durationMs?.takeIf { it > 0L } ?: return PlaybackTimelineMarkers()

    fun fraction(positionMs: Long) = (positionMs.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
    return PlaybackTimelineMarkers(
        chapterStarts =
            chapters
                .map { fraction(it.startPositionMs) }
                .filter { it > EDGE_FRACTION && it < 1f - EDGE_FRACTION }
                .distinct(),
        segments =
            segments
                .map { PlaybackTimelineRange(it.type, fraction(it.startPositionMs), fraction(it.endPositionMs)) }
                .filter { it.end > it.start },
    )
}

private const val CHAPTER_RESTART_THRESHOLD_MS = 3_000L
private const val EDGE_FRACTION = 0.005f
