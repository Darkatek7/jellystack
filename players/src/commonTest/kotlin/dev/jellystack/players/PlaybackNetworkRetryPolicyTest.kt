package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackNetworkRetryPolicyTest {
    private val policy = PlaybackNetworkRetryPolicy(backoffMs = listOf(100L, 200L), progressToRefillMs = 10_000L)

    @Test
    fun backsOffInStepsAndThenGivesUp() {
        assertEquals(100L, policy.nextBackoffMs(positionMs = 5_000L))
        assertEquals(200L, policy.nextBackoffMs(positionMs = 5_000L))
        assertNull(policy.nextBackoffMs(positionMs = 5_000L))
    }

    @Test
    fun refillsOnlyAfterPlaybackAdvancedPastTheLastRetry() {
        policy.nextBackoffMs(positionMs = 5_000L)
        policy.nextBackoffMs(positionMs = 5_000L)

        // Reaching READY again without getting anywhere must not restart the budget.
        policy.onPosition(5_000L)
        policy.onPosition(14_999L)
        assertNull(policy.nextBackoffMs(positionMs = 5_000L))

        policy.onPosition(15_000L)
        assertEquals(100L, policy.nextBackoffMs(positionMs = 15_000L))
    }

    @Test
    fun resetStartsFreshForANewItem() {
        policy.nextBackoffMs(positionMs = 0L)
        policy.nextBackoffMs(positionMs = 0L)

        policy.reset()

        assertEquals(100L, policy.nextBackoffMs(positionMs = 0L))
    }
}
