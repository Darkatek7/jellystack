package dev.jellystack.players

import dev.jellystack.core.preferences.AutoplayNextMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackContinuationCoordinatorTest {
    @Test
    fun nextTargetIsPreparedDuringPlaybackAndReusedAtNaturalCompletion() =
        runTest {
            var resolutions = 0
            var plays = 0
            val coordinator =
                coordinator(
                    resolve = { _, _ ->
                        resolutions += 1
                        target { plays += 1 }
                    },
                )

            coordinator.onPlaybackState(active("episode-1", phase = PlaybackPhase.Ready))
            runCurrent()
            assertEquals("episode-2", assertNotNull(coordinator.state.value.nextTarget).mediaId)
            assertEquals(1, resolutions)

            coordinator.onPlaybackState(active("episode-1", positionMs = 20_000, phase = PlaybackPhase.Ready))
            coordinator.onPlaybackState(active("episode-1", positionMs = 60_000, phase = PlaybackPhase.Ended))
            runCurrent()
            assertEquals(1, resolutions)
            assertEquals(10, coordinator.state.value.countdownSecondsRemaining)

            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun lastEpisodeHasNoTargetAndResolutionFailureLeavesPlaybackUnaffected() =
        runTest {
            val lastEpisode = coordinator(resolve = { _, _ -> null })
            lastEpisode.onPlaybackState(active("last", phase = PlaybackPhase.Ready))
            runCurrent()
            lastEpisode.onPlaybackState(active("last", phase = PlaybackPhase.Ended))
            runCurrent()
            assertNull(lastEpisode.state.value.nextTarget)
            assertNull(lastEpisode.state.value.countdownSecondsRemaining)

            val failing = coordinator(resolve = { _, _ -> error("browse unavailable") })
            failing.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            assertNull(failing.state.value.nextTarget)
            assertNull(failing.state.value.countdownSecondsRemaining)
        }

    @Test
    fun manualPlayNextBypassesCountdownAndCannotReplayOnOldCompletion() =
        runTest {
            var plays = 0
            val coordinator = coordinator(resolve = { _, _ -> target { plays += 1 } })
            coordinator.onPlaybackState(active("episode-1", phase = PlaybackPhase.Ready))
            runCurrent()

            coordinator.playNext()
            runCurrent()
            assertEquals(1, plays)
            assertNull(coordinator.state.value.countdownSecondsRemaining)
            assertNull(coordinator.state.value.nextTarget)

            coordinator.onPlaybackState(active("episode-1", phase = PlaybackPhase.Ended))
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun completionModesRetainOffCountdownImmediateCancelAndForegroundSemantics() =
        runTest {
            var offPlays = 0
            val off = coordinator(mode = AutoplayNextMode.OFF, resolve = { _, _ -> target { offPlays += 1 } })
            off.onPlaybackState(active("off", phase = PlaybackPhase.Ready))
            runCurrent()
            off.onPlaybackState(active("off", phase = PlaybackPhase.Ended))
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, offPlays)
            assertNull(off.state.value.countdownSecondsRemaining)

            var immediatePlays = 0
            val immediate = coordinator(mode = AutoplayNextMode.IMMEDIATE, resolve = { _, _ -> target { immediatePlays += 1 } })
            immediate.onPlaybackState(active("immediate", phase = PlaybackPhase.Ready))
            runCurrent()
            immediate.onPlaybackState(active("immediate", phase = PlaybackPhase.Ended))
            runCurrent()
            assertEquals(1, immediatePlays)
            assertNull(immediate.state.value.countdownSecondsRemaining)

            var countdownPlays = 0
            val countdown = coordinator(resolve = { _, _ -> target { countdownPlays += 1 } })
            countdown.onPlaybackState(active("countdown", phase = PlaybackPhase.Ready))
            runCurrent()
            countdown.onPlaybackState(active("countdown", phase = PlaybackPhase.Ended))
            runCurrent()
            countdown.setForeground(false)
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, countdownPlays)
            assertEquals(10, countdown.state.value.countdownSecondsRemaining)
            countdown.setForeground(true)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(5, countdown.state.value.countdownSecondsRemaining)
            countdown.cancelAutoplay()
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, countdownPlays)
            assertNull(countdown.state.value.countdownSecondsRemaining)
        }

    @Test
    fun mediaChangeCancelsResolutionRejectsStaleTargetAndStopClearsState() =
        runTest {
            val resolver = SupersededTargetResolver()
            val coordinator =
                PlaybackContinuationCoordinator(
                    scope = this,
                    modeProvider = { AutoplayNextMode.COUNTDOWN },
                    resolveNext = resolver::resolve,
                )

            coordinator.onPlaybackState(active("episode-1", phase = PlaybackPhase.Ready))
            runCurrent()
            coordinator.onPlaybackState(active("episode-2", phase = PlaybackPhase.Ready))
            runCurrent()
            assertTrue(resolver.firstCancelled.await())

            resolver.second.complete(target {})
            runCurrent()
            assertEquals("episode-2", coordinator.state.value.mediaId)
            assertEquals(
                "episode-2",
                coordinator.state.value.nextTarget
                    ?.mediaId,
            )

            resolver.firstAfterCancellation.complete(target(mediaId = "stale") {})
            runCurrent()
            assertEquals(
                "episode-2",
                coordinator.state.value.nextTarget
                    ?.mediaId,
            )

            coordinator.onPlaybackState(PlaybackState.Stopped)
            assertEquals(PlaybackContinuationState(), coordinator.state.value)
        }

    @Test
    fun nonEpisodicPlaybackClearsPreparedTargetEvenWhenMediaIdIsUnchanged() =
        runTest {
            val coordinator = coordinator(resolve = { _, _ -> target {} })
            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            assertNotNull(coordinator.state.value.nextTarget)

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready, seriesId = null))

            assertNull(coordinator.state.value.nextTarget)
            assertNull(coordinator.state.value.countdownSecondsRemaining)
        }

    @Test
    fun sameIdPreparingAndEndedReplayResetPreparedStateAndOldCountdownWithoutStopped() =
        runTest {
            var resolutions = 0
            var plays = 0
            val coordinator =
                coordinator(
                    resolve = { _, _ ->
                        resolutions += 1
                        target(mediaId = "next-$resolutions") { plays += 1 }
                    },
                )

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ended))
            runCurrent()
            assertEquals(10, coordinator.state.value.countdownSecondsRemaining)

            coordinator.onPlaybackState(preparing("episode"))
            coordinator.onPlaybackState(preparing("episode"))
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, plays)
            assertNull(coordinator.state.value.nextTarget)
            assertNull(coordinator.state.value.countdownSecondsRemaining)

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            assertEquals(
                "next-2",
                coordinator.state.value.nextTarget
                    ?.mediaId,
            )
            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ended))
            runCurrent()
            assertEquals(10, coordinator.state.value.countdownSecondsRemaining)

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(3, resolutions)
            assertEquals(
                "next-3",
                coordinator.state.value.nextTarget
                    ?.mediaId,
            )
            assertNull(coordinator.state.value.countdownSecondsRemaining)
            assertEquals(0, plays)
        }

    @Test
    fun completionWhileResolutionIsPendingStartsCountdownWhenTargetArrives() =
        runTest {
            var plays = 0
            val pending = CompletableDeferred<PlaybackContinuationTarget?>()
            val coordinator = coordinator(resolve = { _, _ -> pending.await() })

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ended))
            runCurrent()
            assertNull(coordinator.state.value.countdownSecondsRemaining)

            pending.complete(target { plays += 1 })
            runCurrent()
            assertEquals(10, coordinator.state.value.countdownSecondsRemaining)

            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun pendingResolutionUsesModeSampledAtCompletionEvenIfProviderChanges() =
        runTest {
            var mode = AutoplayNextMode.IMMEDIATE
            var plays = 0
            val pending = CompletableDeferred<PlaybackContinuationTarget?>()
            val coordinator =
                PlaybackContinuationCoordinator(
                    scope = this,
                    modeProvider = { mode },
                    resolveNext = { _, _ -> pending.await() },
                )

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ended))
            runCurrent()

            mode = AutoplayNextMode.COUNTDOWN
            pending.complete(target { plays += 1 })
            runCurrent()

            assertEquals(1, plays)
            assertNull(coordinator.state.value.nextTarget)
            assertNull(coordinator.state.value.countdownSecondsRemaining)

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ended))
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun localToCastHandoffKeepsPreparedTargetWithoutResolvingAgain() =
        runTest {
            var resolutions = 0
            val coordinator =
                coordinator(
                    resolve = { _, _ ->
                        resolutions += 1
                        target {}
                    },
                )

            coordinator.onPlaybackState(active("episode", phase = PlaybackPhase.Ready))
            runCurrent()
            val prepared = coordinator.state.value.nextTarget
            coordinator.onPlaybackState(castConnecting("episode"))
            runCurrent()

            assertEquals(1, resolutions)
            assertEquals(prepared, coordinator.state.value.nextTarget)
        }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        mode: AutoplayNextMode = AutoplayNextMode.COUNTDOWN,
        resolve: suspend (String, String) -> PlaybackContinuationTarget?,
    ) = PlaybackContinuationCoordinator(
        scope = this,
        modeProvider = { mode },
        resolveNext = resolve,
    )

    private fun target(
        mediaId: String = "episode-2",
        play: suspend () -> Unit,
    ) = PlaybackContinuationTarget(mediaId = mediaId, title = "Next", play = play)

    private fun active(
        mediaId: String,
        positionMs: Long = 10_000,
        phase: PlaybackPhase,
        seriesId: String? = "series",
    ): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = mediaId,
            deviceName = "test",
            stream = testStream,
            positionMs = positionMs,
            durationMs = 60_000,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source = testSource,
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            metadata =
                PlaybackMetadata(
                    title = "Episode",
                    seriesId = seriesId,
                    seriesName = "Series",
                    episodeName = "Episode",
                    artworkUrl = null,
                    primaryImageTag = null,
                ),
            phase = phase,
        )

    private fun preparing(mediaId: String) =
        PlaybackState.Preparing(
            mediaId = mediaId,
            metadata = null,
            mediaKind = PlaybackMediaKind.VIDEO,
        )

    private fun castConnecting(mediaId: String): PlaybackState.Active =
        PlaybackState.CastConnecting(
            mediaId = mediaId,
            localDeviceName = "local",
            targetDeviceName = "cast",
            stream = testStream,
            positionMs = 20_000,
            durationMs = 60_000,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source = testSource,
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            metadata =
                PlaybackMetadata(
                    title = "Episode",
                    seriesId = "series",
                    seriesName = "Series",
                    episodeName = "Episode",
                    artworkUrl = null,
                    primaryImageTag = null,
                ),
            phase = PlaybackPhase.Ready,
        )

    private class SupersededTargetResolver {
        val firstCancelled = CompletableDeferred<Boolean>()
        val firstAfterCancellation = CompletableDeferred<PlaybackContinuationTarget?>()
        val second = CompletableDeferred<PlaybackContinuationTarget?>()

        suspend fun resolve(
            mediaId: String,
            seriesId: String,
        ): PlaybackContinuationTarget? =
            when (mediaId) {
                "episode-1" ->
                    try {
                        CompletableDeferred<Nothing>().await()
                    } catch (_: CancellationException) {
                        firstCancelled.complete(true)
                        withContext(NonCancellable) { firstAfterCancellation.await() }
                    }
                "episode-2" -> second.await()
                else -> error("Unexpected $mediaId in $seriesId")
            }
    }

    private companion object {
        val testStream =
            PlaybackStreamSelection(
                sourceId = "source",
                mode = PlaybackMode.DIRECT,
                container = "mp4",
                videoCodec = "h264",
                audioCodec = "aac",
                videoBitrate = null,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                maxBitrate = null,
                qualityOptions = emptyList(),
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
            )
        val testSource =
            ResolvedPlaybackSource(
                url = "https://example.test/video",
                headers = emptyMap(),
                mode = PlaybackMode.DIRECT,
                mimeType = "video/mp4",
                subtitles = emptyList(),
                playSessionId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
    }
}
