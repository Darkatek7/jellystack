package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvPlayerRouteTest {
    private val idle = TvPlaybackStartState.Idle
    private val stopped = PlaybackState.Stopped
    private val preparing = PlaybackState.Preparing("next", null)
    private val error = PlaybackState.PlaybackError("boom", canRetry = true)
    private val starting = TvPlaybackStartState.Starting(item())
    private val failed = TvPlaybackStartState.Failed(item())

    private fun back(
        videoActive: Boolean,
        panelOpen: Boolean = false,
        controlsVisible: Boolean = false,
        scrubVisible: Boolean = false,
    ) = tvPlayerBackAction(videoActive, panelOpen, controlsVisible, scrubVisible)

    @Test
    fun routeLeavesOnlyWhenPlaybackStoppedWithoutAStartOrErrorToShow() {
        assertTrue(shouldPopPlayerRoute(PlaybackState.Stopped, idle))

        assertFalse(shouldPopPlayerRoute(active(), idle))
        assertFalse(shouldPopPlayerRoute(preparing, idle))
        assertFalse(shouldPopPlayerRoute(error, idle))
        // Between pressing Play and the controller taking over the state is still Stopped.
        assertFalse(shouldPopPlayerRoute(stopped, starting))
        assertFalse(shouldPopPlayerRoute(stopped, failed))
    }

    @Test
    fun overlayFollowsTheControllerAndThePendingStart() {
        assertEquals(TvPlayerOverlay.NONE, tvPlayerOverlay(active(), idle))
        assertEquals(TvPlayerOverlay.PREPARING, tvPlayerOverlay(preparing, idle))
        assertEquals(TvPlayerOverlay.PREPARING, tvPlayerOverlay(stopped, starting))
        assertEquals(TvPlayerOverlay.PLAYBACK_ERROR, tvPlayerOverlay(error, idle))
        assertEquals(TvPlayerOverlay.START_ERROR, tvPlayerOverlay(stopped, failed))
        // A start that failed behind a video that is still playing leaves the video alone.
        assertEquals(TvPlayerOverlay.NONE, tvPlayerOverlay(active(), failed))
        assertEquals(TvPlayerOverlay.NONE, tvPlayerOverlay(stopped, idle))
    }

    @Test
    fun stoppingOutsideThePlayerPopsAfterTheGraceTime() =
        runTest {
            var pops = 0
            val job = launch { flowOf(false, true).popAfterGrace(GRACE_MS) { pops += 1 } }

            runCurrent()
            assertEquals(0, pops)
            advanceTimeBy(GRACE_MS)
            runCurrent()

            assertEquals(1, pops)
            job.cancel()
        }

    @Test
    fun nextItemTransitionThroughStoppedDoesNotPop() =
        runTest {
            val states = MutableSharedFlow<Boolean>()
            var pops = 0
            val job = launch { states.popAfterGrace(GRACE_MS) { pops += 1 } }
            runCurrent()

            states.emit(true)
            advanceTimeBy(GRACE_MS / 2)
            states.emit(false)
            advanceTimeBy(GRACE_MS * 2)
            runCurrent()

            assertEquals(0, pops)
            job.cancel()
        }

    @Test
    fun backWithoutAVideoLeavesAtOnceInsteadOfHidingControls() {
        // Preparing, a failed start and the error panel have no controls: one Back cancels the start.
        assertEquals(TvPlayerBackAction.CLOSE_PLAYER, back(videoActive = false, controlsVisible = true))
        assertEquals(TvPlayerBackAction.HIDE_CONTROLS, back(videoActive = true, controlsVisible = true))
        assertEquals(TvPlayerBackAction.CLOSE_PANEL, back(videoActive = true, panelOpen = true, controlsVisible = true))
        assertEquals(TvPlayerBackAction.CLOSE_SCRUB, back(videoActive = true, scrubVisible = true))
        assertEquals(TvPlayerBackAction.CLOSE_PLAYER, back(videoActive = true))
    }

    @Test
    fun preparingShowsTheChosenItemFirstAndTheControllerMetadataOtherwise() {
        val images = TvPlayerImageSource("https://example.com", "token")
        val episode =
            item().copy(
                seriesName = "Show",
                name = "Pilot",
                parentIndexNumber = 1,
                indexNumber = 2,
                primaryImageTag = "tag",
            )

        val fromItem = tvPlayerPreparingModel(PlaybackState.Stopped, TvPlaybackStartState.Starting(episode), images)
        assertEquals("Show", fromItem.title)
        assertEquals("S1 E2 · Pilot", fromItem.subtitle)
        assertTrue(fromItem.artworkUrl.orEmpty().startsWith("https://example.com/Items/movie/Images/Primary"))

        val metadata = PlaybackMetadata("Movie", null, null, null, null, null)
        val fromController = tvPlayerPreparingModel(PlaybackState.Preparing("movie", metadata), idle, images)
        assertEquals("Movie", fromController.title)
        assertNull(fromController.subtitle)
        assertNull(fromController.artworkUrl)

        assertEquals(TvPlayerPreparingModel(null, null, null), tvPlayerPreparingModel(stopped, idle, images))
    }

    private fun active(): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = "movie",
            deviceName = "test",
            stream =
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
                ),
            positionMs = 0L,
            durationMs = 60_000L,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source =
                ResolvedPlaybackSource(
                    url = "https://example.com/video",
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
        )

    private fun item() =
        JellyfinItem(
            id = "movie",
            libraryId = null,
            name = "Movie",
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

    private companion object {
        const val GRACE_MS = TV_PLAYER_STOPPED_POP_GRACE_MS
    }
}
