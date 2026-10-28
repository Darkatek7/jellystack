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

@UnstableApi
private fun Throwable.httpResponseCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}
