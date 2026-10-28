package dev.jellystack.players

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals

class Media3ErrorCategoryTest {
    @Test
    fun connectionFailuresAndTimeoutsAreNetworkErrors() {
        assertEquals(
            PlayerErrorCategory.NETWORK,
            media3ErrorCategory(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, httpResponseCode = null),
        )
        assertEquals(
            PlayerErrorCategory.NETWORK,
            media3ErrorCategory(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, httpResponseCode = null),
        )
    }

    @Test
    fun badHttpStatusFollowsTheResponseCode() {
        assertEquals(
            PlayerErrorCategory.UNAUTHORIZED,
            media3ErrorCategory(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpResponseCode = 401),
        )
        assertEquals(
            PlayerErrorCategory.NETWORK,
            media3ErrorCategory(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpResponseCode = 503),
        )
        assertEquals(
            PlayerErrorCategory.MEDIA,
            media3ErrorCategory(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpResponseCode = null),
        )
    }

    @Test
    fun decoderAndContainerFailuresStayWithTheFallbackChain() {
        listOf(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        ).forEach { code ->
            assertEquals(PlayerErrorCategory.MEDIA, media3ErrorCategory(code, httpResponseCode = null), "code $code")
        }
    }
}
