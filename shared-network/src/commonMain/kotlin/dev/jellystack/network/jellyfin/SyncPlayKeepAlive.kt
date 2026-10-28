package dev.jellystack.network.jellyfin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Answers Jellyfin's `ForceKeepAlive` with `KeepAlive` messages.
 *
 * The server announces how many seconds a socket may stay silent; clients are expected to ping at about half of
 * that. A socket that stays silent is dropped by the server, which ends SyncPlay without any visible error.
 */
internal class SyncPlayKeepAlive(
    private val scope: CoroutineScope,
    private val send: suspend () -> Unit,
) {
    private var job: Job? = null
    private var intervalMillis: Long? = null

    /** Handles a `ForceKeepAlive` message: replies now and pings every half of [timeoutSeconds] from then on. */
    fun onForceKeepAlive(timeoutSeconds: Long?) {
        val interval = keepAliveIntervalMillis(timeoutSeconds)
        if (job?.isActive == true && intervalMillis == interval) {
            scope.launch { send() }
            return
        }
        job?.cancel()
        intervalMillis = interval
        job =
            scope.launch {
                send()
                while (true) {
                    delay(interval)
                    send()
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/** Half of the silence the server tolerates, kept inside sane bounds; a missing value falls back to the default. */
internal fun keepAliveIntervalMillis(timeoutSeconds: Long?): Long {
    val timeoutMillis = timeoutSeconds?.takeIf { it > 0L }?.times(MILLIS_PER_SECOND) ?: DEFAULT_TIMEOUT_MILLIS
    return (timeoutMillis / 2).coerceIn(MIN_INTERVAL_MILLIS, MAX_INTERVAL_MILLIS)
}

internal const val SYNC_PLAY_KEEP_ALIVE_MESSAGE = """{"MessageType":"KeepAlive"}"""
internal const val SYNC_PLAY_FORCE_KEEP_ALIVE = "ForceKeepAlive"
private const val MILLIS_PER_SECOND = 1_000L
private const val DEFAULT_TIMEOUT_MILLIS = 60_000L
private const val MIN_INTERVAL_MILLIS = 5_000L
private const val MAX_INTERVAL_MILLIS = 60_000L
