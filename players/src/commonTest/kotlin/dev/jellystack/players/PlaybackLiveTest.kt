package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.core.playback.StreamingProgressReporter
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackMediaSourceDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLiveTest {
    @Test
    fun aChannelOpensItsLiveStreamAndPlaysTheServersHlsUrl() =
        runTest {
            val service = LivePlaybackInfoService(liveResponse())
            val resolver = JellyfinPlaybackSourceResolver(service, FixedDeviceProfile)
            val request = liveRequest()

            val source =
                resolver.resolve(
                    request,
                    PlaybackStreamSelector().select(request.mediaSources),
                    environment(),
                    startPositionMs = 0,
                    options = PlaybackSourceOptions(),
                )

            val sent = requireNotNull(service.lastRequest)
            assertEquals("channel-1", service.lastItemId)
            assertEquals(true, sent.autoOpenLiveStream)
            assertFalse(sent.enableDirectPlay)
            assertFalse(sent.enableDirectStream)
            assertTrue(sent.enableTranscoding)
            assertNull(sent.mediaSourceId)
            assertEquals("https://example.test/videos/channel-1/master.m3u8?LiveStreamId=live-1", source.url)
            assertEquals(PlaybackMode.HLS, source.mode)
            assertTrue(source.isLive)
            assertEquals("live-1", source.liveStreamId)
            assertEquals("play-1", source.playSessionId)
            // Recovery would resolve again and open a second stream beside this one.
            assertEquals(false, source.supportsTranscoding)
        }

    @Test
    fun aChannelWithoutAPlaybackUrlFailsInsteadOfGuessingOne() =
        runTest {
            val response =
                JellyfinPlaybackInfoResponseDto("play-1", listOf(JellyfinPlaybackMediaSourceDto(id = "s", liveStreamId = "live-1")))
            val resolver = JellyfinPlaybackSourceResolver(LivePlaybackInfoService(response), FixedDeviceProfile)
            val request = liveRequest()

            assertFailsWith<IllegalStateException> {
                resolver.resolve(request, PlaybackStreamSelector().select(request.mediaSources), environment(), 0, PlaybackSourceOptions())
            }
        }

    @Test
    fun aLiveChannelStartsAtTheLiveEdgeWhateverPositionWasSavedAndAsksNothingAboutResuming() =
        runTest {
            val store = InMemoryPlaybackProgressStore().apply { write(PlaybackProgress("channel-1", 600_000)) }
            val harness = harness(store)

            harness.controller.play(
                liveRequest().copy(resumePositionTicks = 6_000_000_000, startPolicy = PlaybackStartPolicy.RESUME),
                environment(),
            )

            assertEquals(0L, harness.engine.lastStartPositionMs)
            assertEquals(
                0L,
                assertIs<PlaybackState.Active>(harness.controller.state.value).positionMs,
            )
            harness.controller.release()
        }

    @Test
    fun seekingALiveChannelDoesNothing() =
        runTest {
            val harness = harness()
            harness.controller.play(liveRequest(), environment())

            harness.controller.seekTo(30_000)

            assertEquals(emptyList(), harness.engine.seekPositions)
            assertEquals(0L, assertIs<PlaybackState.Active>(harness.controller.state.value).positionMs)
            harness.controller.release()
        }

    @Test
    fun watchingAChannelLeavesNoResumePointAndReportsLiveWithoutWatchedState() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val harness = harness(store)
            harness.controller.play(liveRequest(), environment())
            harness.engine.emitEvent(PlayerEvent.Ready)
            harness.engine.emitPosition(120_000)

            harness.controller.stop()

            assertNull(store.read("channel-1"))
            val start = harness.reporter.starts.single()
            assertTrue(start.isLive)
            assertEquals("live-1", start.liveStreamId)
            assertTrue(harness.reporter.progress.all { it.isLive && it.liveStreamId == "live-1" })
            val stop = harness.reporter.stops.single()
            assertEquals("live-1", stop.first.liveStreamId)
            assertFalse(stop.second, "a live stream is never completed")
            harness.controller.release()
        }

    @Test
    fun aStreamThatEndsIsReportedStoppedNotWatched() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val harness = harness(store)
            harness.controller.play(liveRequest(), environment())

            harness.engine.emitEvent(PlayerEvent.Completed)

            val stop = harness.reporter.stops.single()
            assertEquals("live-1", stop.first.liveStreamId)
            assertFalse(stop.second)
            harness.controller.release()
        }

    @Test
    fun aPlayerErrorOnALiveChannelEndsInAnErrorWithoutOpeningASecondStream() =
        runTest {
            val service = LivePlaybackInfoService(liveResponse())
            val harness = harness(resolver = JellyfinPlaybackSourceResolver(service, FixedDeviceProfile))
            harness.controller.play(liveRequest(), environment())

            harness.engine.emitEvent(PlayerEvent.Error(IllegalStateException("decoder"), PlayerErrorCategory.MEDIA))

            assertIs<PlaybackState.PlaybackError>(harness.controller.state.value)
            assertEquals(1, service.fetchCount)
            assertEquals(
                "live-1",
                harness.reporter.stops
                    .single()
                    .first.liveStreamId,
            )
            harness.controller.release()
        }

    private fun TestScope.harness(
        store: PlaybackProgressStore = InMemoryPlaybackProgressStore(),
        resolver: PlaybackSourceResolver = JellyfinPlaybackSourceResolver(LivePlaybackInfoService(liveResponse()), FixedDeviceProfile),
    ): LiveHarness {
        val engine = LiveRecordingEngine()
        val reporter = LiveRecordingReporter()
        val controller =
            PlaybackController(
                progressStore = store,
                playbackSourceResolver = resolver,
                playerEngine = engine,
                streamingProgressReporter = reporter,
                scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            )
        return LiveHarness(controller, engine, reporter)
    }

    private fun liveRequest() = PlaybackRequest.forLiveChannel(channelId = "channel-1", name = "Example One")

    private fun liveResponse() =
        JellyfinPlaybackInfoResponseDto(
            playSessionId = "play-1",
            mediaSources =
                listOf(
                    JellyfinPlaybackMediaSourceDto(
                        id = "mediasource-1",
                        liveStreamId = "live-1",
                        isInfiniteStream = true,
                        transcodingUrl = "/videos/channel-1/master.m3u8?LiveStreamId=live-1",
                        transcodingSubProtocol = "hls",
                    ),
                ),
        )

    private fun environment() =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://example.test",
            accessToken = "dummy-token",
            userId = "user",
            deviceId = "device-id",
            deviceName = "TestDevice",
        )
}

private class LiveHarness(
    val controller: PlaybackController,
    val engine: LiveRecordingEngine,
    val reporter: LiveRecordingReporter,
)

private val FixedDeviceProfile =
    PlaybackDeviceProfileProvider {
        PlaybackDeviceProfileFactory.create("Test", PlaybackDecoderCapabilities(setOf(PlaybackVideoCodec.H264)))
    }

private class LivePlaybackInfoService(
    private val response: JellyfinPlaybackInfoResponseDto,
) : JellyfinPlaybackInfoService {
    var lastRequest: JellyfinPlaybackInfoRequestDto? = null
    var lastItemId: String? = null
    var fetchCount = 0

    override suspend fun fetch(
        environment: JellyfinEnvironment,
        itemId: String,
        userId: String,
        request: JellyfinPlaybackInfoRequestDto,
    ): JellyfinPlaybackInfoResponseDto {
        fetchCount++
        lastItemId = itemId
        lastRequest = request
        return response
    }
}

private class LiveRecordingReporter : StreamingProgressReporter {
    val starts = mutableListOf<StreamingProgressContext>()
    val progress = mutableListOf<StreamingProgressContext>()
    val stops = mutableListOf<Pair<StreamingProgressContext, Boolean>>()

    override suspend fun onStart(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        starts += context
    }

    override suspend fun onProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        progress += context
    }

    override suspend fun onStop(
        context: StreamingProgressContext,
        positionMs: Long?,
        completed: Boolean,
    ) {
        stops += context to completed
    }
}

private class LiveRecordingEngine : PlayerEngine {
    private val positions = MutableSharedFlow<Long>()
    private val engineEvents = MutableSharedFlow<PlayerEvent>()
    override val positionUpdates: SharedFlow<Long> = positions
    override val events: SharedFlow<PlayerEvent> = engineEvents
    var lastStartPositionMs: Long? = null
    val seekPositions = mutableListOf<Long>()

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        lastStartPositionMs = startPositionMs
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        seekPositions += positionMs
    }

    override fun setAudioTrack(track: AudioTrack?) = AudioTrackSelectionResult.APPLIED

    override fun setSubtitleTrack(track: SubtitleTrack?) = SubtitleTrackSelectionResult.APPLIED

    override fun setVideoQuality(maxBitrate: Int?) = Unit

    override fun release() = Unit

    suspend fun emitPosition(positionMs: Long) = positions.emit(positionMs)

    suspend fun emitEvent(event: PlayerEvent) = engineEvents.emit(event)
}
