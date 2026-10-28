package dev.jellystack.design.tv

import dev.jellystack.core.diagnostics.JellystackDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCoroutineSafetyTest {
    @Test
    fun forgottenCatchInTheAccountScopeIsRecordedAndDoesNotStopSiblingWork() =
        runTest {
            val generation = TvAccountGeneration(TvAuthenticatedEnvironmentIdentity("server", "user"), this)
            var siblingRan = false

            try {
                generation.scope.launch { error("network is down") }
                generation.scope.launch { siblingRan = true }
                advanceUntilIdle()
            } finally {
                generation.close()
            }

            assertTrue(siblingRan)
            val recorded =
                JellystackDiagnostics.logBuffer
                    .snapshot()
                    .filter { "Unhandled failure in the account session" in it }
            assertTrue(recorded.any { "network is down" in it }, recorded.toString())
        }

    @Test
    fun cancellationIsNotReportedAsAFailure() {
        val handler = tvCoroutineFailureHandler("cancelled owner")

        handler.handleException(EmptyCoroutineContext, CancellationException("left the screen"))

        assertFalse(JellystackDiagnostics.logBuffer.snapshot().any { "cancelled owner" in it })
    }
}
