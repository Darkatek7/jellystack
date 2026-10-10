package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.datetime.Clock

/** How long the app may sit in the background before "Who's watching?" asks again. */
internal const val TV_PROFILE_GATE_GRACE_MILLIS = 60_000L

/**
 * A household with several profiles picks again when the app comes back after more than a short break,
 * so the next person never lands in the previous person's profile. Short trips (voice input, a quick
 * look at another app) keep the active profile.
 */
internal fun tvProfileGateOnReturn(
    profileCount: Int,
    backgroundedAtMillis: Long?,
    nowMillis: Long,
): Boolean =
    profileCount > 1 &&
        backgroundedAtMillis != null &&
        nowMillis - backgroundedAtMillis >= TV_PROFILE_GATE_GRACE_MILLIS

/** Calls [onGate] when the app returns to the foreground and [tvProfileGateOnReturn] asks for the picker. */
@Composable
internal fun TvProfileGateEffect(
    enabled: Boolean,
    profileCount: Int,
    onGate: () -> Unit,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentProfileCount by rememberUpdatedState(profileCount)
    val currentOnGate by rememberUpdatedState(onGate)
    val currentNow by rememberUpdatedState(nowMillis)
    DisposableEffect(lifecycleOwner) {
        var backgroundedAt: Long? = null
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> backgroundedAt = currentNow()
                    Lifecycle.Event.ON_START -> {
                        val gate = tvProfileGateOnReturn(currentProfileCount, backgroundedAt, currentNow())
                        backgroundedAt = null
                        if (currentEnabled && gate) currentOnGate()
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
