package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/** Minimum gap to the previous silent refresh when Home shows again (app return, back from details or the player). */
internal const val TV_LIVE_REFRESH_RETURN_GAP_MILLIS = 15_000L

/** How often visible Home rows re-read watched state and progress while nothing plays. */
internal const val TV_LIVE_REFRESH_INTERVAL_MILLIS = 60_000L

/** Milliseconds to wait so that refreshes stay at least [gapMillis] apart. */
internal fun tvLiveRefreshDelayMillis(
    lastRefreshAtMillis: Long,
    nowMillis: Long,
    gapMillis: Long,
): Long = (lastRefreshAtMillis + gapMillis - nowMillis).coerceIn(0L, gapMillis)

/**
 * Keeps watched state current while the app stays open: runs [refresh] shortly after Home becomes
 * visible again and then periodically, but only while [active] and the app is in the foreground.
 */
@Composable
internal fun TvLiveRefreshEffect(
    active: Boolean,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    refresh: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastRefreshAt = remember { mutableLongStateOf(nowMillis()) }
    val currentRefresh by rememberUpdatedState(refresh)
    val currentNow by rememberUpdatedState(nowMillis)
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var gapMillis = TV_LIVE_REFRESH_RETURN_GAP_MILLIS
            while (true) {
                delay(tvLiveRefreshDelayMillis(lastRefreshAt.longValue, currentNow(), gapMillis))
                currentRefresh()
                lastRefreshAt.longValue = currentNow()
                gapMillis = TV_LIVE_REFRESH_INTERVAL_MILLIS
            }
        }
    }
}
