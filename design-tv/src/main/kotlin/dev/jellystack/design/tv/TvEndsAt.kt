package dev.jellystack.design.tv

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Date

internal const val TV_RUNTIME_TICKS_PER_MINUTE = 600_000_000L
private const val MILLIS_PER_MINUTE = 60_000L

/** Minutes of [runTimeTicks] still to watch from [positionTicks]; null without a runtime, never negative. */
internal fun tvRemainingMinutes(
    runTimeTicks: Long?,
    positionTicks: Long?,
): Long? {
    val runtime = runTimeTicks?.takeIf { it > 0L } ?: return null
    val watched = (positionTicks ?: 0L).coerceIn(0L, runtime)
    return (runtime - watched) / TV_RUNTIME_TICKS_PER_MINUTE
}

/**
 * The wall-clock instant a title that plays now for [remainingMinutes] ends, cut to the minute so the label does not
 * flicker between recompositions. Pure so Home can show "Ends at" too.
 */
internal fun tvEndsAtMillis(
    nowMillis: Long,
    remainingMinutes: Long,
): Long = (nowMillis / MILLIS_PER_MINUTE + remainingMinutes) * MILLIS_PER_MINUTE

/** [tvEndsAtMillis] formatted with [format], which decides between 12 and 24 hour display. */
internal fun tvEndsAtClock(
    nowMillis: Long,
    remainingMinutes: Long,
    format: java.text.DateFormat,
): String = format.format(Date(tvEndsAtMillis(nowMillis, remainingMinutes)))

/**
 * "Ends at 10:41 PM" for a title with [remainingMinutes] left, in the device's 12/24-hour format
 * ([DateFormat.getTimeFormat] follows the system setting); null when the runtime is unknown or nothing is left.
 */
@Composable
internal fun rememberTvEndsAtLabel(
    remainingMinutes: Long?,
    strings: TvStrings,
): String? {
    val format = DateFormat.getTimeFormat(LocalContext.current)
    // The label is computed once per remaining time: a detail page is not open long enough for it to drift.
    return remember(remainingMinutes, format, strings) {
        remainingMinutes
            ?.takeIf { it > 0L }
            ?.let { strings.player.endsAt.format(tvEndsAtClock(System.currentTimeMillis(), it, format)) }
    }
}
