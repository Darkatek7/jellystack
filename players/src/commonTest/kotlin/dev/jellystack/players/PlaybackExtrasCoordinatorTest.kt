package dev.jellystack.players

import dev.jellystack.network.jellyfin.JellyfinChapterDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasResult
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasService
import dev.jellystack.network.jellyfin.JellyfinTrickplayInfoDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackExtrasCoordinatorTest {
    @Test
    fun loadsOncePerItemAndPicksTheManifestOfThePlayedSource() =
        runTest {
            val service = FakeExtrasService { JellyfinPlaybackExtrasResult.Available(extras("movie")) }
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(preparing("movie"))
            coordinator.onPlaybackState(active("movie", sourceId = "SOURCE-B"))
            coordinator.onPlaybackState(active("movie", sourceId = "SOURCE-B"))
            runCurrent()

            assertEquals(listOf("movie"), service.requests)
            val state = coordinator.state.value
            assertEquals(listOf(0L, 300_000L), state.chapters.map { it.startPositionMs })
            assertEquals("Opening", state.chapters.first().name)
            assertEquals("sourceb", state.trickplay?.mediaSourceId)
            assertEquals(320, state.trickplay?.manifest?.width)
        }

    @Test
    fun staleResultsOfAPreviousItemAreDropped() =
        runTest {
            val first = CompletableDeferred<JellyfinPlaybackExtrasResult>()
            val service =
                FakeExtrasService { itemId ->
                    if (itemId == "episode-1") first.await() else JellyfinPlaybackExtrasResult.Available(extras("episode-2"))
                }
            val coordinator = coordinator(service)

            coordinator.onPlaybackState(active("episode-1", sourceId = "sourceb"))
            runCurrent()
            coordinator.onPlaybackState(active("episode-2", sourceId = "sourceb"))
            runCurrent()
            first.complete(JellyfinPlaybackExtrasResult.Available(extras("episode-1")))
            runCurrent()

            assertEquals("episode-2", coordinator.state.value.mediaId)
            assertEquals(2, coordinator.state.value.chapters.size)
        }

    @Test
    fun unavailableOrFailingExtrasLeaveAnEmptyState() =
        runTest {
            val unavailable = coordinator(FakeExtrasService { JellyfinPlaybackExtrasResult.Unavailable })
            val failing = coordinator(FakeExtrasService { error("offline") })

            unavailable.onPlaybackState(active("movie", sourceId = "sourceb"))
            failing.onPlaybackState(active("movie", sourceId = "sourceb"))
            runCurrent()

            assertEquals(PlaybackExtrasState(mediaId = "movie"), unavailable.state.value)
            assertEquals(PlaybackExtrasState(mediaId = "movie"), failing.state.value)
        }

    @Test
    fun stoppingAndNonVideoPlaybackClearTheState() =
        runTest {
            val coordinator = coordinator(FakeExtrasService { JellyfinPlaybackExtrasResult.Available(extras("movie")) })

            coordinator.onPlaybackState(active("movie", sourceId = "sourceb"))
            runCurrent()
            coordinator.onPlaybackState(PlaybackState.Stopped)

            assertEquals(PlaybackExtrasState(), coordinator.state.value)
            coordinator.onPlaybackState(active("song", sourceId = "sourceb", mediaKind = PlaybackMediaKind.AUDIO))
            runCurrent()
            assertNull(coordinator.state.value.trickplay)
        }

    private fun TestScope.coordinator(service: JellyfinPlaybackExtrasService) = PlaybackExtrasCoordinator(this, service)

    private class FakeExtrasService(
        private val respond: suspend (String) -> JellyfinPlaybackExtrasResult,
    ) : JellyfinPlaybackExtrasService {
        val requests = mutableListOf<String>()

        override suspend fun fetchExtras(itemId: String): JellyfinPlaybackExtrasResult {
            requests += itemId
            return respond(itemId)
        }
    }

    private fun extras(itemId: String) =
        JellyfinPlaybackExtrasDto(
            id = itemId,
            chapters =
                listOf(
                    JellyfinChapterDto(startPositionTicks = 3_000_000_000L, name = "Middle"),
                    JellyfinChapterDto(startPositionTicks = 0L, name = "Opening"),
                ),
            trickplay =
                mapOf(
                    "sourcea" to mapOf("320" to trickplayInfo(320)),
                    "sourceb" to mapOf("240" to trickplayInfo(240), "320" to trickplayInfo(320)),
                ),
        )

    private fun trickplayInfo(width: Int) =
        JellyfinTrickplayInfoDto(
            width = width,
            height = width * 9 / 16,
            tileWidth = 10,
            tileHeight = 10,
            thumbnailCount = 100,
            interval = 10_000,
        )

    private fun preparing(mediaId: String) = PlaybackState.Preparing(mediaId = mediaId, metadata = null)

    private fun active(
        mediaId: String,
        sourceId: String,
        mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
    ): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = mediaId,
            deviceName = "test",
            stream =
                PlaybackStreamSelection(
                    sourceId = sourceId,
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
                ),
            positionMs = 0L,
            durationMs = 600_000L,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source =
                ResolvedPlaybackSource(
                    url = "https://example.test/video",
                    headers = emptyMap(),
                    mode = PlaybackMode.DIRECT,
                    mimeType = "video/mp4",
                    subtitles = emptyList(),
                    playSessionId = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                ),
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            mediaKind = mediaKind,
        )
}
