package dev.jellystack.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SuspendCatchingTest {
    @Test
    fun valuesAndFailuresBecomeResults() =
        runTest {
            assertEquals(42, runSuspendCatching { 42 }.getOrNull())
            assertIs<IllegalStateException>(runSuspendCatching { error("boom") }.exceptionOrNull())
        }

    @Test
    fun cancellationIsRethrownInsteadOfCaptured() =
        runTest {
            assertFailsWith<CancellationException> {
                runSuspendCatching { throw CancellationException("cancelled") }
            }
        }
}
