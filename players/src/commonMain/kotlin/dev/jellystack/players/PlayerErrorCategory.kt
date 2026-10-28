package dev.jellystack.players

/** Why a player failed, as far as the engine can tell. It decides how [PlaybackController] recovers. */
enum class PlayerErrorCategory {
    /** Connectivity problem or timeout: the same source is worth retrying before anything else. */
    NETWORK,

    /** The server rejected the credentials (HTTP 401/403): neither a retry nor a transcode can help. */
    UNAUTHORIZED,

    /** Decoder, format, container or unclassified failure: a different stream may play. */
    MEDIA,
    ;

    companion object {
        /** Category of a failed HTTP request for the given response status. */
        fun fromHttpStatus(status: Int): PlayerErrorCategory =
            when (status) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> UNAUTHORIZED
                HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS, HTTP_BAD_GATEWAY, HTTP_SERVICE_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT -> NETWORK
                else -> MEDIA
            }

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val HTTP_GATEWAY_TIMEOUT = 504
    }
}
