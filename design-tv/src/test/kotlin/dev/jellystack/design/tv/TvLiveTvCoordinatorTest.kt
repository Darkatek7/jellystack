package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class TvLiveTvCoordinatorTest {
    private val start = Instant.parse("2026-11-01T18:00:00Z")
    private val channels = listOf(channel("c1"), channel("c2"), channel("c3"))
    private var channelResult: () -> List<LiveTvChannel> = { channels }
    private val programRequests = mutableListOf<List<String>>()

    @Test
    fun programsLoadForTheChannelsOnScreenOnly() =
        runTest {
            val coordinator = coordinator()
            coordinator.show()
            runCurrent()

            coordinator.setVisible(listOf("c1", "c2"))
            runCurrent()

            assertEquals(listOf(listOf("c1", "c2")), programRequests)
            val ready = coordinator.state.value as TvLiveTvState.Ready
            assertEquals("Show c1a", ready.nowAndNext("c1").now?.title)
            assertNull(ready.nowAndNext("c3").now)
            assertTrue("c3" !in ready.guide)
        }

    @Test
    fun scrollingOnlyAsksForTheChannelsThatHaveNoGuideYet() =
        runTest {
            val coordinator = coordinator()
            coordinator.show()
            runCurrent()
            coordinator.setVisible(listOf("c1", "c2"))
            runCurrent()

            coordinator.setVisible(listOf("c2", "c3"))
            runCurrent()

            assertEquals(listOf(listOf("c1", "c2"), listOf("c3")), programRequests)
        }

    @Test
    fun theGuideMovesOnWhenTheRunningProgramEndsWithoutPolling() =
        runTest {
            val coordinator = coordinator()
            coordinator.show()
            runCurrent()
            coordinator.setVisible(listOf("c1"))
            runCurrent()
            assertEquals("Show c1a", (coordinator.state.value as TvLiveTvState.Ready).nowAndNext("c1").now?.title)

            // Nothing happens while the first program runs: no timer fires before its end.
            advanceTimeBy(29.minutes)
            runCurrent()
            assertEquals("Show c1a", (coordinator.state.value as TvLiveTvState.Ready).nowAndNext("c1").now?.title)

            advanceTimeBy(2.minutes)
            runCurrent()

            val ready = coordinator.state.value as TvLiveTvState.Ready
            assertEquals("Show c1b", ready.nowAndNext("c1").now?.title)
            // The guide is re-evaluated at the boundary (plus a little slack), not at the end of the wait.
            assertEquals(start + 30.minutes + 500.milliseconds, ready.at)
        }

    @Test
    fun aGuideThatFailsToLoadLeavesTheChannelsPlayable() =
        runTest {
            val coordinator = coordinator(failPrograms = true)
            coordinator.show()
            runCurrent()

            coordinator.setVisible(listOf("c1"))
            runCurrent()

            val ready = coordinator.state.value as TvLiveTvState.Ready
            assertEquals(channels, ready.channels)
            assertNull(ready.nowAndNext("c1").now)
        }

    @Test
    fun noChannelsIsAnEmptyStateAndAFailedLoadAnErrorThatRetryRecoversFrom() =
        runTest {
            channelResult = { emptyList() }
            val coordinator = coordinator()
            coordinator.show()
            runCurrent()
            assertEquals(TvLiveTvState.Empty, coordinator.state.value)

            channelResult = { error("server unreachable") }
            coordinator.retry()
            runCurrent()
            assertEquals(TvLiveTvState.Failed, coordinator.state.value)

            channelResult = { channels }
            coordinator.retry()
            runCurrent()
            assertTrue(coordinator.state.value is TvLiveTvState.Ready)
        }

    @Test
    fun aFailedRefreshKeepsTheChannelsTheUserAlreadySees() =
        runTest {
            val coordinator = coordinator()
            coordinator.show()
            runCurrent()

            channelResult = { error("server unreachable") }
            coordinator.show()
            runCurrent()

            assertEquals(channels, (coordinator.state.value as TvLiveTvState.Ready).channels)
        }

    @Test
    fun theLiveTvLibraryIsOnlyOfferedWhenTheServerHasChannels() =
        runTest {
            val home = JellyfinHomeState(libraries = listOf(library("movies", "movies"), library("tv", "livetv")))
            val coordinator = coordinator()

            fun access(available: Boolean) = TvLiveTvAccess(coordinator, available)

            fun shown(available: Boolean) = access(available).libraries(home).libraries.map { it.id }

            assertEquals(listOf("movies"), shown(available = false))
            assertEquals(listOf("movies", "tv"), shown(available = true))
        }

    private fun TestScope.coordinator(failPrograms: Boolean = false) =
        TvLiveTvCoordinator(
            scope = this,
            loader =
                object : TvLiveTvLoader {
                    override suspend fun channels() = channelResult()

                    override suspend fun programs(
                        channelIds: List<String>,
                        at: Instant,
                    ): Map<String, List<LiveTvProgram>> {
                        programRequests += channelIds
                        check(!failPrograms) { "guide unavailable" }
                        return channelIds.associateWith { id -> programsOf(id) }
                    }
                },
            clock = TestClock(start, this),
        )

    private fun programsOf(channelId: String) =
        listOf(
            program(channelId, "a", from = 0.minutes, to = 30.minutes),
            program(channelId, "b", from = 30.minutes, to = 90.minutes),
            program(channelId, "c", from = 90.minutes, to = 150.minutes),
        )

    private fun program(
        channelId: String,
        suffix: String,
        from: Duration,
        to: Duration,
    ) = LiveTvProgram("$channelId-$suffix", channelId, "Show $channelId$suffix", null, null, start + from, start + to)

    private fun channel(id: String) = LiveTvChannel(id, number = null, name = "Channel $id", imageTag = null)

    private fun library(
        id: String,
        collectionType: String,
    ) = JellyfinLibrary(id = id, name = id, collectionType = collectionType, itemCount = null, primaryImageTag = null)
}

/** The wall clock of a test: [start] plus the virtual time that has passed. */
private class TestClock(
    private val start: Instant,
    private val scope: TestScope,
) : Clock {
    override fun now(): Instant = start + scope.testScheduler.currentTime.milliseconds
}
