package dev.jellystack.players

import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentDto
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSegmentCoordinatorTest {
    @Test
    fun activeRangeIncludesStartAndExcludesEnd() =
        runTest {
            val service = StaticSegmentService(available(segment("intro", "Intro", 1_000, 2_000)))
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode", 999))
            runCurrent()
            assertEquals(emptyList(), coordinator.state.value.actions)

            coordinator.onPlaybackState(active("episode", 1_000))
            assertEquals(
                listOf(PlaybackSegmentType.INTRO),
                coordinator.state.value.actions
                    .map { it.type },
            )

            coordinator.onPlaybackState(active("episode", 1_999))
            assertEquals(
                listOf(PlaybackSegmentType.INTRO),
                coordinator.state.value.actions
                    .map { it.type },
            )

            coordinator.onPlaybackState(active("episode", 2_000))
            assertEquals(emptyList(), coordinator.state.value.actions)
            assertEquals(1, service.requests)
        }

    @Test
    fun malformedUnknownMismatchedAndUnavailableSegmentsDoNotInterruptPlayback() =
        runTest {
            val service =
                StaticSegmentService(
                    available(
                        segment("valid", "Recap", 0, 3_000),
                        segment("negative", "Intro", -1, 1_000),
                        segment("backwards", "Outro", 2_000, 1_000),
                        segment("empty", "Preview", 1_000, 1_000),
                        segmentTicks("collapsed", "Preview", 1, 9_999),
                        segment("future", "FutureType", 0, 3_000),
                        segment("wrong-item", "Commercial", 0, 3_000, itemId = "other"),
                    ),
                )
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode", 500))
            runCurrent()

            assertFalse(coordinator.state.value.isLoading)
            assertEquals(
                listOf(PlaybackSegmentType.RECAP),
                coordinator.state.value.activeSegments
                    .map { it.type },
            )
            assertEquals(
                listOf(PlaybackSegmentType.RECAP),
                coordinator.state.value.actions
                    .map { it.type },
            )

            val unavailable = coordinator(StaticSegmentService(JellyfinMediaSegmentsResult.Unavailable))
            unavailable.onPlaybackState(active("episode", 500))
            runCurrent()
            assertFalse(unavailable.state.value.isLoading)
            assertTrue(
                unavailable.state.value.activeSegments
                    .isEmpty(),
            )

            val failing = coordinator(ThrowingSegmentService())
            failing.onPlaybackState(active("episode", 500))
            runCurrent()
            assertFalse(failing.state.value.isLoading)
            assertTrue(
                failing.state.value.activeSegments
                    .isEmpty(),
            )
        }

    @Test
    fun mediaChangeCancelsOldLoadRejectsItsStaleResultAndLoadsEachItemOnce() =
        runTest {
            val service = SupersededSegmentService()
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode-1", 500))
            runCurrent()
            coordinator.onPlaybackState(active("episode-1", 600))
            runCurrent()
            assertEquals(listOf("episode-1"), service.requests)

            coordinator.onPlaybackState(active("episode-2", 500))
            runCurrent()
            assertTrue(service.firstCancelled.await())
            assertEquals(listOf("episode-1", "episode-2"), service.requests)

            service.second.complete(available(segment("second", "Recap", 0, 1_000, "episode-2")))
            runCurrent()
            assertEquals("episode-2", coordinator.state.value.mediaId)
            assertEquals(
                listOf(PlaybackSegmentType.RECAP),
                coordinator.state.value.actions
                    .map { it.type },
            )

            service.firstAfterCancellation.complete(available(segment("stale", "Intro", 0, 1_000, "episode-1")))
            runCurrent()
            assertEquals("episode-2", coordinator.state.value.mediaId)
            assertEquals(
                listOf(PlaybackSegmentType.RECAP),
                coordinator.state.value.actions
                    .map { it.type },
            )

            coordinator.onPlaybackState(PlaybackState.Stopped)
            assertEquals(PlaybackSegmentState(), coordinator.state.value)
        }

    @Test
    fun seekingIntoAutoSegmentSeeksToItsExactEndOnlyOnceAndResetsForANewSession() =
        runTest {
            val seeks = mutableListOf<Long>()
            val service = StaticSegmentService(available(segment("intro", "Intro", 1_000, 2_000)))
            val coordinator = coordinator(service, mode = { SegmentSkipMode.AUTO_SKIP }, seeks = seeks)

            coordinator.onPlaybackState(active("episode", 500))
            runCurrent()
            assertTrue(seeks.isEmpty())

            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()
            coordinator.onPlaybackState(active("episode", 1_600))
            runCurrent()
            assertEquals(listOf(2_000L), seeks)

            coordinator.onPlaybackState(PlaybackState.Stopped)
            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()
            assertEquals(listOf(2_000L, 2_000L), seeks)
            assertEquals(2, service.requests)
        }

    @Test
    fun overlappingAutoSegmentsSeekToFurthestEndAndConsumeEveryActiveMarker() =
        runTest {
            val seeks = mutableListOf<Long>()
            val coordinator =
                coordinator(
                    StaticSegmentService(
                        available(
                            segment("intro", "Intro", 1_000, 2_500),
                            segment("recap", "Recap", 1_200, 3_000),
                        ),
                    ),
                    mode = { SegmentSkipMode.AUTO_SKIP },
                    seeks = seeks,
                )

            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()
            coordinator.onPlaybackState(active("episode", 1_600))
            runCurrent()

            assertEquals(listOf(3_000L), seeks)
        }

    @Test
    fun overlappingButtonSegmentsRemainDistinctAndManualSkipUsesInjectedAdapter() =
        runTest {
            val seeks = mutableListOf<Long>()
            val coordinator =
                coordinator(
                    StaticSegmentService(
                        available(
                            segment("intro", "Intro", 1_000, 2_500),
                            segment("recap", "Recap", 1_200, 3_000),
                            segment("outro", "Outro", 1_300, 4_000),
                        ),
                    ),
                    mode = { type -> if (type == PlaybackSegmentType.OUTRO) SegmentSkipMode.OFF else SegmentSkipMode.SHOW_BUTTON },
                    seeks = seeks,
                )

            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()

            assertEquals(
                listOf(PlaybackSegmentType.INTRO, PlaybackSegmentType.RECAP, PlaybackSegmentType.OUTRO),
                coordinator.state.value.activeSegments
                    .map { it.type },
            )
            assertEquals(
                listOf(PlaybackSegmentType.INTRO, PlaybackSegmentType.RECAP),
                coordinator.state.value.actions
                    .map { it.type },
            )

            coordinator.skip(
                coordinator.state.value.actions
                    .last(),
            )
            runCurrent()
            assertEquals(listOf(3_000L), seeks)
        }

    @Test
    fun nonVideoPlaybackNeverLoadsSegmentsAndClearsSameIdVideoActions() =
        runTest {
            val service = StaticSegmentService(available(segment("intro", "Intro", 0, 2_000)))
            val coordinator = coordinator(service)
            coordinator.onPlaybackState(active("episode", 1_000))
            runCurrent()
            assertTrue(
                coordinator.state.value.actions
                    .isNotEmpty(),
            )

            coordinator.onPlaybackState(active("episode", 1_000, mediaKind = PlaybackMediaKind.AUDIO))

            assertTrue(
                coordinator.state.value.actions
                    .isEmpty(),
            )
            assertTrue(
                coordinator.state.value.activeSegments
                    .isEmpty(),
            )
            assertEquals(1, service.requests)
        }

    @Test
    fun sameIdPreparingAndEndedReplayCreateFreshAutoSkipSessionsWithoutStopped() =
        runTest {
            val seeks = mutableListOf<Long>()
            val service = StaticSegmentService(available(segment("intro", "Intro", 1_000, 2_000)))
            val coordinator = coordinator(service, mode = { SegmentSkipMode.AUTO_SKIP }, seeks = seeks)

            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()
            assertEquals(listOf(2_000L), seeks)

            coordinator.onPlaybackState(preparing("episode"))
            coordinator.onPlaybackState(preparing("episode"))
            runCurrent()
            coordinator.onPlaybackState(active("episode", 1_500))
            runCurrent()
            assertEquals(listOf(2_000L, 2_000L), seeks)

            coordinator.onPlaybackState(active("episode", 2_000, phase = PlaybackPhase.Ended))
            coordinator.onPlaybackState(active("episode", 1_500, phase = PlaybackPhase.Ready))
            runCurrent()

            assertEquals(listOf(2_000L, 2_000L, 2_000L), seeks)
            assertEquals(3, service.requests)
        }

    @Test
    fun sameIdNonVideoToVideoTransitionLoadsSegments() =
        runTest {
            val service = StaticSegmentService(available(segment("intro", "Intro", 0, 2_000)))
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode", 1_000, mediaKind = PlaybackMediaKind.AUDIO))
            runCurrent()
            assertEquals(0, service.requests)

            coordinator.onPlaybackState(active("episode", 1_000))
            runCurrent()

            assertEquals(1, service.requests)
            assertEquals(
                listOf(PlaybackSegmentType.INTRO),
                coordinator.state.value.actions
                    .map { it.type },
            )
        }

    @Test
    fun localToCastHandoffDoesNotStartANewSegmentSession() =
        runTest {
            val service = StaticSegmentService(available(segment("intro", "Intro", 0, 2_000)))
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode", 500))
            runCurrent()
            coordinator.onPlaybackState(castConnecting("episode", 600))
            runCurrent()

            assertEquals(1, service.requests)
            assertEquals(
                listOf(PlaybackSegmentType.INTRO),
                coordinator.state.value.actions
                    .map { it.type },
            )
        }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        service: JellyfinMediaSegmentsService,
        mode: (PlaybackSegmentType) -> SegmentSkipMode = { SegmentSkipMode.SHOW_BUTTON },
        seeks: MutableList<Long> = mutableListOf(),
    ): PlaybackSegmentCoordinator =
        PlaybackSegmentCoordinator(
            scope = this,
            segmentService = service,
            modeProvider = PlaybackSegmentModeProvider(mode),
            seekAdapter = PlaybackSeekAdapter(seeks::add),
        )

    private fun segment(
        id: String,
        type: String,
        startMs: Long,
        endMs: Long,
        itemId: String = "episode",
    ) = JellyfinMediaSegmentDto(
        id = id,
        itemId = itemId,
        type = type,
        startTicks = startMs * 10_000,
        endTicks = endMs * 10_000,
    )

    private fun segmentTicks(
        id: String,
        type: String,
        startTicks: Long,
        endTicks: Long,
        itemId: String = "episode",
    ) = JellyfinMediaSegmentDto(
        id = id,
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )

    private fun available(vararg segments: JellyfinMediaSegmentDto) = JellyfinMediaSegmentsResult.Available(segments.toList())

    private fun active(
        mediaId: String,
        positionMs: Long,
        mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
        phase: PlaybackPhase = PlaybackPhase.Ready,
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
                    seriesId = "series",
                    seriesName = "Series",
                    episodeName = "Episode",
                    artworkUrl = null,
                    primaryImageTag = null,
                ),
            mediaKind = mediaKind,
            phase = phase,
        )

    private fun preparing(mediaId: String) =
        PlaybackState.Preparing(
            mediaId = mediaId,
            metadata = null,
            mediaKind = PlaybackMediaKind.VIDEO,
        )

    private fun castConnecting(
        mediaId: String,
        positionMs: Long,
    ): PlaybackState.Active =
        PlaybackState.CastConnecting(
            mediaId = mediaId,
            localDeviceName = "local",
            targetDeviceName = "cast",
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
                    seriesId = "series",
                    seriesName = "Series",
                    episodeName = "Episode",
                    artworkUrl = null,
                    primaryImageTag = null,
                ),
            phase = PlaybackPhase.Ready,
        )

    private class StaticSegmentService(
        private val result: JellyfinMediaSegmentsResult,
    ) : JellyfinMediaSegmentsService {
        var requests = 0

        override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult {
            requests += 1
            return result
        }
    }

    private class ThrowingSegmentService : JellyfinMediaSegmentsService {
        override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult = error("optional endpoint failed")
    }

    private class SupersededSegmentService : JellyfinMediaSegmentsService {
        val requests = mutableListOf<String>()
        val firstCancelled = CompletableDeferred<Boolean>()
        val firstAfterCancellation = CompletableDeferred<JellyfinMediaSegmentsResult>()
        val second = CompletableDeferred<JellyfinMediaSegmentsResult>()

        override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult {
            requests += itemId
            return when (itemId) {
                "episode-1" ->
                    try {
                        CompletableDeferred<Nothing>().await()
                    } catch (_: CancellationException) {
                        firstCancelled.complete(true)
                        withContext(NonCancellable) { firstAfterCancellation.await() }
                    }
                "episode-2" -> second.await()
                else -> error("Unexpected item $itemId")
            }
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
