package dev.jellystack.design.tv

import androidx.compose.runtime.snapshots.Snapshot
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeStageDwellTest {
    private val dwell = 300L

    @Test
    fun fastMovesAlongARowSettleOnlyOnTheLastCard() =
        runTest {
            val focus = MutableSharedFlow<String?>(extraBufferCapacity = 8)
            val settled = mutableListOf<String>()
            val job = launch { focus.followAfterDwell(dwell, hasStaged = { true }) { settled += it } }
            runCurrent()

            listOf("a", "b", "c").forEach {
                focus.tryEmit(it)
                runCurrent()
                advanceTimeBy(dwell - 1)
            }
            assertEquals(emptyList(), settled)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("c"), settled)
            job.cancel()
        }

    @Test
    fun restingFocusSettlesAfterTheDwell() =
        runTest {
            val focus = MutableSharedFlow<String?>(extraBufferCapacity = 8)
            val settled = mutableListOf<String>()
            val job = launch { focus.followAfterDwell(dwell, hasStaged = { true }) { settled += it } }
            runCurrent()

            focus.tryEmit("a")
            runCurrent()
            advanceTimeBy(dwell - 1)
            assertEquals(emptyList(), settled)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("a"), settled)
            job.cancel()
        }

    @Test
    fun firstFocusSettlesAtOnceWhileNothingIsStaged() =
        runTest {
            val focus = MutableSharedFlow<String?>(extraBufferCapacity = 8)
            val settled = mutableListOf<String>()
            var staged = false
            val job =
                launch {
                    focus.followAfterDwell(dwell, hasStaged = { staged }) {
                        settled += it
                        staged = true
                    }
                }
            runCurrent()

            focus.tryEmit("a")
            runCurrent()
            assertEquals(listOf("a"), settled)

            focus.tryEmit("b")
            runCurrent()
            assertEquals(listOf("a"), settled)
            advanceTimeBy(dwell)
            runCurrent()
            assertEquals(listOf("a", "b"), settled)
            job.cancel()
        }

    @Test
    fun losingFocusDropsTheCardThatWasWaiting() =
        runTest {
            val focus = MutableSharedFlow<String?>(extraBufferCapacity = 8)
            val settled = mutableListOf<String>()
            val job = launch { focus.followAfterDwell(dwell, hasStaged = { true }) { settled += it } }
            runCurrent()

            focus.tryEmit("a")
            runCurrent()
            advanceTimeBy(dwell - 1)
            focus.tryEmit(null)
            runCurrent()
            advanceTimeBy(dwell * 2)

            assertEquals(emptyList(), settled)
            job.cancel()
        }

    @Test
    fun spotlightFocusUnstagesAtOnceAndDropsTheCardThatWasWaiting() =
        runTest {
            val dwellState = TvHomeStageDwell()
            val settled = mutableListOf<String>()
            val job = launch { dwellState.follow(dwell) { settled += it.item.id } }
            runCurrent()

            focusCard(dwellState, "a")
            assertEquals("a", dwellState.staged?.item?.id)

            focusCard(dwellState, "b")
            advanceTimeBy(dwell - 1)
            assertEquals("a", dwellState.staged?.item?.id)

            dwellState.onSpotlightFocused()
            assertNull(dwellState.staged)
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(dwell * 2)
            assertNull(dwellState.staged)
            assertEquals(listOf("a"), settled)

            // Staged again from the spotlight, the first card comes at once.
            focusCard(dwellState, "c")
            assertEquals("c", dwellState.staged?.item?.id)
            job.cancel()
        }

    @Test
    fun cardThatLostFocusIsNotStagedAndTheStageKeepsItsCard() =
        runTest {
            val dwellState = TvHomeStageDwell()
            val job = launch { dwellState.follow(dwell) {} }
            runCurrent()
            focusCard(dwellState, "a")

            focusCard(dwellState, "b")
            dwellState.onCardBlurred(card("b"), "row-b")
            Snapshot.sendApplyNotifications()
            runCurrent()
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(dwell * 2)

            assertEquals("a", dwellState.staged?.item?.id)
            job.cancel()
        }

    private fun TestScope.focusCard(
        state: TvHomeStageDwell,
        id: String,
    ) {
        state.onCardFocused(card(id), "row-$id")
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun card(id: String) =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = id,
            sortName = null,
            overview = null,
            type = "Movie",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = null,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = null,
            seriesName = null,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
        )
}
