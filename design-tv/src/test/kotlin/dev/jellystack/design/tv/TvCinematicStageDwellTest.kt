package dev.jellystack.design.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvCinematicStageDwellTest {
    private fun card(id: String) = TvCinematicCard(id = id, title = id)

    @Test
    fun stagesCardsOnlyAfterFocusRestsAndSkipsPassedOnes() =
        runTest {
            val focused = MutableStateFlow<TvCinematicCard?>(card("a"))
            var staged: TvCinematicCard? = null
            val job = launch { focused.followStagedCard(200, { staged?.id }) { staged = it } }
            runCurrent()
            assertEquals("a", staged?.id)

            focused.value = card("b")
            runCurrent()
            advanceTimeBy(150)
            focused.value = card("c")
            runCurrent()
            advanceTimeBy(150)
            assertEquals("a", staged?.id)
            advanceTimeBy(60)
            assertEquals("c", staged?.id)
            job.cancel()
        }

    @Test
    fun sameCardUpdateAndLeavingTheRowsAreStagedAtOnce() =
        runTest {
            val focused = MutableStateFlow<TvCinematicCard?>(card("a"))
            var staged: TvCinematicCard? = null
            val job = launch { focused.followStagedCard(200, { staged?.id }) { staged = it } }
            runCurrent()

            focused.value = card("a").copy(played = true)
            runCurrent()
            assertEquals(true, staged?.played)

            focused.value = null
            runCurrent()
            assertEquals(null, staged)
            job.cancel()
        }
}
