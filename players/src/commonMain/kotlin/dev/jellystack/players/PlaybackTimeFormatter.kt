package dev.jellystack.players

fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}

fun formatPlaybackDuration(milliseconds: Long?): String = milliseconds?.takeIf { it >= 0L }?.let(::formatPlaybackTime) ?: "--:--"

private fun Long.twoDigits(): String = toString().padStart(2, '0')
