package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AutoplayNextMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvAutoplayCoordinatorTest {
    @Test
    fun countdownStartsTargetExactlyOnceAndCanBeCancelled() =
        runTest {
            var plays = 0
            val coordinator = coordinator { target { plays += 1 } }

            coordinator.onPlaybackCompleted("episode-1", "series")
            coordinator.onPlaybackCompleted("episode-1", "series")
            runCurrent()
            assertIs<TvAutoplayState.Countdown>(coordinator.state.value)

            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, plays)

            coordinator.onPlaybackCompleted("episode-2", "series")
            runCurrent()
            coordinator.cancel()
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun backgroundPausesCountdownAndImmediateModeSkipsIt() =
        runTest {
            var plays = 0
            val countdown = coordinator { target { plays += 1 } }
            countdown.onPlaybackCompleted("episode-1", "series")
            runCurrent()
            countdown.setForeground(false)
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals(0, plays)
            countdown.setForeground(true)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, plays)

            val immediate = coordinator(AutoplayNextMode.IMMEDIATE) { target { plays += 1 } }
            immediate.onPlaybackCompleted("episode-3", "series")
            runCurrent()
            assertEquals(2, plays)
            assertEquals(TvAutoplayState.Idle, immediate.state.value)
        }

    @Test
    fun nextEpisodeSelectionIsChronologicalAcrossSeasons() {
        val episodes =
            listOf(
                episode("s2e1", 2, 1),
                episode("s1e2", 1, 2),
                episode("s1e1", 1, 1),
            )

        assertEquals("s1e2", selectNextTvEpisode(episodes, "s1e1")?.id)
        assertEquals("s2e1", selectNextTvEpisode(episodes, "s1e2")?.id)
        assertNull(selectNextTvEpisode(episodes, "s2e1"))
        assertNull(selectNextTvEpisode(episodes, "missing"))
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        mode: AutoplayNextMode = AutoplayNextMode.COUNTDOWN,
        resolve: suspend () -> TvAutoplayTarget?,
    ) = TvAutoplayCoordinator(this, { mode }) { _, _ -> resolve() }

    private fun target(play: suspend () -> Unit) = TvAutoplayTarget("episode-2", "Next", play)

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = id,
        sortName = null,
        overview = null,
        type = "Episode",
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = "series",
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
        indexNumber = number,
        parentIndexNumber = season,
        seriesName = "Series",
        seasonId = null,
        episodeTitle = id,
        lastPlayed = null,
    )
}
