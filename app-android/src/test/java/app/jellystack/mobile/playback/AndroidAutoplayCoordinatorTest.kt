package app.jellystack.mobile.playback

import dev.jellystack.core.preferences.AutoplayNextMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAutoplayCoordinatorTest {
    @Test
    fun completedEpisodeEmitsOneCountdownAndStartsExactlyOnce() =
        runTest {
            var resolutions = 0
            var plays = 0
            val coordinator =
                coordinator(
                    resolve = {
                        resolutions += 1
                        target { plays += 1 }
                    },
                )

            coordinator.onPlaybackCompleted("episode-1", "series")
            coordinator.onPlaybackCompleted("episode-1", "series")
            runCurrent()

            assertIs<AndroidAutoplayState.Countdown>(coordinator.state.value)
            assertEquals(1, resolutions)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, plays)
            assertEquals(AndroidAutoplayState.Idle, coordinator.state.value)
        }

    @Test
    fun cancellationPreventsLaunch() =
        runTest {
            var played = false
            val coordinator = coordinator(resolve = { target { played = true } })
            coordinator.onPlaybackCompleted("episode-1", "series")
            runCurrent()

            coordinator.cancel()
            advanceTimeBy(20_000L)
            runCurrent()

            assertTrue(!played)
        }

    @Test
    fun backgroundPausesAndForegroundResumesCountdown() =
        runTest {
            var plays = 0
            val coordinator = coordinator(resolve = { target { plays += 1 } })
            coordinator.onPlaybackCompleted("episode-1", "series")
            runCurrent()
            coordinator.setForeground(false)
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals(0, plays)

            coordinator.setForeground(true)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun immediateModeStartsWithoutCountdownAndMissingTargetStopsNormally() =
        runTest {
            var plays = 0
            val immediate = coordinator(mode = AutoplayNextMode.IMMEDIATE, resolve = { target { plays += 1 } })
            immediate.onPlaybackCompleted("episode-1", "series")
            runCurrent()
            assertEquals(1, plays)
            assertEquals(AndroidAutoplayState.Idle, immediate.state.value)

            val missing = coordinator(resolve = { null })
            missing.onPlaybackCompleted("episode-2", "series")
            runCurrent()
            assertEquals(AndroidAutoplayState.Idle, missing.state.value)
        }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        mode: AutoplayNextMode = AutoplayNextMode.COUNTDOWN,
        resolve: suspend () -> AndroidAutoplayTarget?,
    ): AndroidAutoplayCoordinator =
        AndroidAutoplayCoordinator(
            scope = this,
            modeProvider = { mode },
            resolveNext = { _, _ -> resolve() },
        )

    private fun target(onPlay: suspend () -> Unit): AndroidAutoplayTarget =
        AndroidAutoplayTarget(mediaId = "episode-2", title = "Next", onPlay = onPlay)
}
