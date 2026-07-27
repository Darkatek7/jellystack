package dev.jellystack.design.jellyfin

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotlightAutoAdvanceTest {
    @Test
    fun slowProgressUpdatesDoNotExtendAdvanceDeadline() =
        runBlocking {
            var progressUpdaterStarted = false
            val progressValues = mutableListOf<Float>()

            runSpotlightAutoAdvanceCycle(
                cycleDurationMillis = 6_000L,
                onProgress = progressValues::add,
                progressDelay = {
                    progressUpdaterStarted = true
                    awaitCancellation()
                },
                deadlineDelay = {
                    while (!progressUpdaterStarted) {
                        yield()
                    }
                },
            )

            assertTrue(progressUpdaterStarted)
            assertEquals(0f, progressValues.first())
            assertEquals(1f, progressValues.last())
        }
}
