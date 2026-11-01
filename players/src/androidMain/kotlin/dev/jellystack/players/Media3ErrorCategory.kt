package dev.jellystack.players

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/** Maps a Media3 failure to the category that decides how [PlaybackController] recovers. */
@UnstableApi
internal fun PlaybackException.toPlayerErrorCategory(): PlayerErrorCategory = media3ErrorCategory(errorCode, httpResponseCode())

/**
 * Connectivity failures and timeouts are worth a retry of the same source; HTTP failures follow
 * [PlayerErrorCategory.fromHttpStatus] (expired or rejected credentials stand out); everything else (decoder, format,
 * container, unspecified I/O) is left to the fallback chain.
 */
internal fun media3ErrorCategory(
    errorCode: Int,
    httpResponseCode: Int?,
): PlayerErrorCategory =
    when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlayerErrorCategory.NETWORK
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            httpResponseCode?.let(PlayerErrorCategory::fromHttpStatus) ?: PlayerErrorCategory.MEDIA
        else -> PlayerErrorCategory.MEDIA
    }

/**
 * A live stream the player fell behind (paused too long, or slow to load) is not a failure: the playlist has dropped the
 * segments it wanted. Media3 recovers by jumping to the live edge and preparing again. Bounded, so a stream that stays
 * behind ends in an error instead of looping; the count restarts once the player is ready.
 */
internal fun shouldRestartAtLiveEdge(
    errorCode: Int,
    restartsSinceReady: Int,
): Boolean = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && restartsSinceReady < MAX_LIVE_EDGE_RESTARTS

internal const val MAX_LIVE_EDGE_RESTARTS = 3

@UnstableApi
private fun Throwable.httpResponseCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}
