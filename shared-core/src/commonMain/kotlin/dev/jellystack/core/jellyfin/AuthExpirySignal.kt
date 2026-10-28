package dev.jellystack.core.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Something outside the session probe saw the server reject the access token (a `401` on a browse call, a playback
 * failure with unauthorized credentials, a SyncPlay `401`). [accessToken] is the token that was rejected, or `null`
 * when the reporter only knows that the current session was.
 */
data class AuthExpiryReport(
    val accessToken: String? = null,
)

/**
 * Central, never-blocking channel for [AuthExpiryReport]s. Reporters only report; the UI that owns the signed-in
 * session decides what to do, normally through [AuthExpiryWatcher]. Reports without a collector are dropped, so the
 * phone app (which does not observe it yet) pays nothing.
 */
class AuthExpirySignal {
    private val mutableReports =
        MutableSharedFlow<AuthExpiryReport>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val reports: SharedFlow<AuthExpiryReport> = mutableReports.asSharedFlow()

    fun report(accessToken: String? = null) {
        mutableReports.tryEmit(AuthExpiryReport(accessToken))
    }
}

/**
 * Turns [AuthExpirySignal] reports into at most one [verifySession] call per burst.
 *
 * Several parallel `401`s (a Home screen loads many rows at once) collapse into one verification after
 * [burstWindowMillis]; a report about a token that is no longer the current one (a request that was in flight while
 * the profile switched) is ignored; and after a verification the watcher stays quiet for [cooldownMillis], so an
 * endpoint that answers `401` without the session being expired cannot turn into a request loop. A report that
 * arrives during the cooldown is verified once it ends.
 *
 * [verifySession] should ask the server whether the session is really gone and, if it is, start the sign-in
 * recovery; the verification is what separates "this endpoint said 401" from "the session expired".
 */
class AuthExpiryWatcher(
    private val signal: AuthExpirySignal,
    private val currentAccessToken: suspend () -> String?,
    private val verifySession: suspend () -> Unit,
    private val burstWindowMillis: Long = DEFAULT_BURST_WINDOW_MILLIS,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    suspend fun run() {
        coroutineScope {
            val pending = Channel<Unit>(Channel.CONFLATED)
            launch {
                signal.reports
                    .filter { report -> report.accessToken == null || report.accessToken == currentAccessToken() }
                    .collect { pending.trySend(Unit) }
            }
            for (trigger in pending) {
                delay(burstWindowMillis)
                // Reports that arrived during the window belong to the burst that is verified now.
                pending.tryReceive()
                try {
                    verifySession()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The verification reports its own outcome; a failure here must not end the watcher.
                }
                delay(cooldownMillis)
            }
        }
    }

    companion object {
        const val DEFAULT_BURST_WINDOW_MILLIS = 300L
        const val DEFAULT_COOLDOWN_MILLIS = 10_000L
    }
}
