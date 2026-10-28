package app.jellystack.tv

import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvKeepScreenOnPolicyTest {
    private val input = MutableSharedFlow<TvScreenActivity>()

    @Test
    fun activityFollowsThePlaybackState() {
        assertEquals(TvScreenActivity.IDLE, PlaybackState.Stopped.screenActivity())
        assertEquals(TvScreenActivity.IDLE, PlaybackState.PlaybackError("boom").screenActivity())
        assertEquals(TvScreenActivity.PLAYING, PlaybackState.Preparing("movie", null).screenActivity())
        assertEquals(TvScreenActivity.PLAYING, screenActivity(isPaused = false, phase = PlaybackPhase.Buffering))
        assertEquals(TvScreenActivity.PAUSED, screenActivity(isPaused = true, phase = PlaybackPhase.Ready))
        assertEquals(TvScreenActivity.PAUSED, screenActivity(isPaused = false, phase = PlaybackPhase.Ended))
    }

    @Test
    fun screenStaysOnWhilePlayingAndReleasesWhenPlaybackStops() =
        runTest {
            val (events, job) = collect()

            input.emit(TvScreenActivity.PLAYING)
            input.emit(TvScreenActivity.PLAYING)
            input.emit(TvScreenActivity.IDLE)
            runCurrent()

            assertEquals(listOf(true, false), events)
            job.cancel()
        }

    @Test
    fun aLongPauseReleasesTheScreenAfterTheTimeout() =
        runTest {
            val (events, job) = collect()

            input.emit(TvScreenActivity.PAUSED)
            advanceTimeBy(TIMEOUT_MS - 1)
            runCurrent()
            assertEquals(listOf(true), events)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(true, false), events)
            job.cancel()
        }

    @Test
    fun resumingBeforeTheTimeoutKeepsTheScreenAndTheNextPauseStartsANewCountdown() =
        runTest {
            val (events, job) = collect()

            input.emit(TvScreenActivity.PAUSED)
            advanceTimeBy(TIMEOUT_MS - 1)
            input.emit(TvScreenActivity.PLAYING)
            advanceTimeBy(TIMEOUT_MS * 2)
            runCurrent()
            assertEquals(listOf(true), events)

            input.emit(TvScreenActivity.PAUSED)
            advanceTimeBy(TIMEOUT_MS - 1)
            runCurrent()
            assertEquals(listOf(true), events)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(true, false), events)
            job.cancel()
        }

    @Test
    fun playingAfterAReleasedPauseTurnsTheScreenOnAgain() =
        runTest {
            val (events, job) = collect()

            input.emit(TvScreenActivity.PAUSED)
            advanceTimeBy(TIMEOUT_MS)
            runCurrent()
            input.emit(TvScreenActivity.PLAYING)
            runCurrent()

            assertEquals(listOf(true, false, true), events)
            job.cancel()
        }

    private fun TestScope.collect(): Pair<List<Boolean>, Job> {
        val events = mutableListOf<Boolean>()
        val job = launch { input.keepScreenOn(TIMEOUT_MS).collect { events += it } }
        runCurrent()
        return events to job
    }

    private companion object {
        const val TIMEOUT_MS = 10 * 60_000L
    }
}
