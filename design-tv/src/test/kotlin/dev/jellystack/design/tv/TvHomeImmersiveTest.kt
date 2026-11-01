package dev.jellystack.design.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeImmersiveTest {
    @Test
    fun trailerTakesTheScreenAfterTheRemoteRestedAndEveryKeyRestartsTheWait() =
        runTest {
            val interactions = TvInteractionCounter()
            val immersive = mutableListOf<Boolean>()
            val job = launch { interactions.runImmersiveWaits(trailerPlaying = true) { immersive += it } }

            advanceTimeBy(TV_HOME_IMMERSIVE_DELAY_MS - 1)
            runCurrent()
            assertEquals(false, immersive.last())

            interactions.record()
            runCurrent()
            advanceTimeBy(TV_HOME_IMMERSIVE_DELAY_MS - 1)
            runCurrent()
            assertEquals(false, immersive.last())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(true, immersive.last())

            interactions.record()
            runCurrent()
            assertEquals(false, immersive.last())
            job.cancel()
        }

    @Test
    fun uiStaysVisibleWithoutATrailerNoMatterHowLongTheRemoteRests() =
        runTest {
            val interactions = TvInteractionCounter()
            val immersive = mutableListOf<Boolean>()
            val job = launch { interactions.runImmersiveWaits(trailerPlaying = false) { immersive += it } }

            advanceTimeBy(TV_HOME_IMMERSIVE_DELAY_MS * 2)
            runCurrent()

            assertEquals(listOf(false), immersive)
            job.cancel()
        }
}
