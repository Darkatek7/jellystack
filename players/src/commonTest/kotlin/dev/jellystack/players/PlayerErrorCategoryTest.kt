package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerErrorCategoryTest {
    @Test
    fun rejectedCredentialsAreUnauthorizedAndNeverRetried() {
        assertEquals(PlayerErrorCategory.UNAUTHORIZED, PlayerErrorCategory.fromHttpStatus(401))
        assertEquals(PlayerErrorCategory.UNAUTHORIZED, PlayerErrorCategory.fromHttpStatus(403))
    }

    @Test
    fun transientServerAndGatewayStatusesAreWorthARetry() {
        listOf(408, 429, 502, 503, 504).forEach { status ->
            assertEquals(PlayerErrorCategory.NETWORK, PlayerErrorCategory.fromHttpStatus(status), "status $status")
        }
    }

    @Test
    fun otherStatusesLeaveRecoveryToTheFallbackChain() {
        // A 404 or 500 on an HLS segment usually means a dead transcode, which a new stream fixes.
        listOf(400, 404, 500).forEach { status ->
            assertEquals(PlayerErrorCategory.MEDIA, PlayerErrorCategory.fromHttpStatus(status), "status $status")
        }
    }
}
