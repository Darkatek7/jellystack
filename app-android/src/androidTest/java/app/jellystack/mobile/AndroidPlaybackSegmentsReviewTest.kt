package app.jellystack.mobile

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jellystack.mobile.playback.AndroidJellyfinPlaybackIdentity
import app.jellystack.mobile.playback.AndroidPlaybackCommandRouter
import app.jellystack.mobile.playback.AndroidPlaybackCoordinators
import app.jellystack.mobile.playback.androidAutoplayPromptModel
import app.jellystack.mobile.playback.rememberAndroidPlaybackCoordinators
import app.jellystack.mobile.ui.AndroidPlaybackSurface
import app.jellystack.mobile.ui.AndroidPlaybackTags
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentDto
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackSegmentType
import dev.jellystack.players.PlaybackSession
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionSnapshot
import dev.jellystack.players.cast.testing.CastSessionManagerFake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlaybackSegmentsReviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun standaloneActionStartsWhenControlsHideRemainsTouchableAndDisappearsWithSegment() {
        composeRule.mainClock.autoAdvance = false
        val resources = ScreenResources()
        val taps = mutableListOf<String>()
        var segmentState by mutableStateOf(introState())
        setPlaybackState(resources.controller, activePlayback())
        setTestContent {
            ReviewPlaybackSurface(
                resources = resources,
                playbackState = activePlayback(),
                segmentState = segmentState,
                onSkipSegment = { taps += it.segmentId },
            )
        }
        composeRule.mainClock.advanceTimeByFrame()

        val actionsBounds = composeRule.onNodeWithTag(AndroidPlaybackTags.ACTIONS_CONTROLS).fetchSemanticsNode().boundsInRoot
        val timelineBounds = composeRule.onNodeWithTag(AndroidPlaybackTags.TIMELINE).fetchSemanticsNode().boundsInRoot
        assertTrue(actionsBounds.bottom <= timelineBounds.top)

        composeRule.mainClock.advanceTimeBy(3_600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AndroidPlaybackTags.ACTIONS_STANDALONE).assertExists()
        composeRule.mainClock.advanceTimeBy(7_900L)
        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.onNodeWithContentDescription("Skip intro").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("Video playback surface")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
        composeRule.onNodeWithContentDescription("Skip intro").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("intro-1"), taps) }
        composeRule.runOnIdle { segmentState = PlaybackSegmentState(mediaId = "episode") }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Skip intro").assertDoesNotExist()
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun overlappingActionsKeepDistinctButtonsAndCreditsCanAppearWithPlayNext() {
        val resources = ScreenResources()
        val taps = mutableListOf<String>()
        var segmentState by mutableStateOf(overlappingState())
        setTestContent {
            ReviewPlaybackSurface(
                resources = resources,
                playbackState = activePlayback(positionMs = 85_000L),
                segmentState = segmentState,
                continuationState = preparedContinuation(),
                onSkipSegment = { taps += it.segmentId },
                onPlayNext = { taps += "next" },
            )
        }

        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
        composeRule.onNodeWithContentDescription("Skip recap").assertExists()
        composeRule.onNodeWithContentDescription("Skip credits").performTouchInput { click() }
        composeRule.onNodeWithContentDescription("Play next episode").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf("outro-1", "next"), taps) }

        composeRule.runOnIdle {
            segmentState =
                segmentState.copy(
                    activeSegments = segmentState.activeSegments.filterNot { it.type == PlaybackSegmentType.INTRO },
                    actions = segmentState.actions.filterNot { it.type == PlaybackSegmentType.INTRO },
                )
        }
        composeRule.onNodeWithContentDescription("Skip intro").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Skip recap").assertExists()
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun endedPlaybackShowsOnlySharedCompletionPrompt() {
        val resources = ScreenResources()
        val continuation = preparedContinuation(countdownSeconds = 10)
        setTestContent {
            JellystackTheme(isDarkTheme = true) {
                ReviewPlaybackSurface(
                    resources = resources,
                    playbackState = activePlayback(positionMs = 90_000L, phase = PlaybackPhase.Ended),
                    segmentState = overlappingState(),
                    continuationState = continuation,
                )
                androidAutoplayPromptModel(continuation)?.let { prompt ->
                    AutoplayNextPrompt(prompt, onCancel = {}, onPlayNow = {})
                }
            }
        }

        composeRule.onNodeWithTag("phone-player-action:segment:outro:outro-1").assertDoesNotExist()
        composeRule.onNodeWithTag("phone-player-action:play-next:episode-2").assertDoesNotExist()
        composeRule.onAllNodesWithText("Episode 2").assertCountEquals(1)
        composeRule.onAllNodesWithText("Playing in 10 seconds").assertCountEquals(1)
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun identityReplacementReplaysSameMediaAndRejectsStaleWork() {
        val firstIdentity = AndroidJellyfinPlaybackIdentity("server-a", "user-a")
        val secondIdentity = AndroidJellyfinPlaybackIdentity("server-b", "user-b")
        val staleSegments = CompletableDeferred<JellyfinMediaSegmentsResult>()
        val staleTarget = CompletableDeferred<PlaybackContinuationTarget?>()
        val observed = linkedMapOf<AndroidJellyfinPlaybackIdentity, AndroidPlaybackCoordinators>()
        var identity by mutableStateOf(firstIdentity)
        var showHost by mutableStateOf(true)

        setTestContent {
            if (showHost) {
                CoordinatorLifecycleHost(
                    identity = identity,
                    staleIdentity = firstIdentity,
                    staleSegments = staleSegments,
                    staleTarget = staleTarget,
                    onObserved = { observed[identity] = it },
                )
            }
        }

        composeRule.waitUntil {
            observed[firstIdentity]
                ?.segment
                ?.state
                ?.value
                ?.isLoading == true
        }
        val first = observed.getValue(firstIdentity)
        composeRule.runOnIdle { identity = secondIdentity }
        composeRule.waitUntil {
            observed[secondIdentity]?.let { pair ->
                pair.segment.state.value.actions
                    .singleOrNull()
                    ?.segmentId == "outro-fresh" &&
                    pair.continuation.state.value.nextTarget
                        ?.mediaId == "episode-fresh"
            } == true
        }
        val second = observed.getValue(secondIdentity)

        composeRule.runOnIdle {
            assertEquals(PlaybackSegmentState(), first.segment.state.value)
            assertEquals(PlaybackContinuationState(), first.continuation.state.value)
            staleSegments.complete(availableSegment("intro-stale", "Intro"))
            staleTarget.complete(PlaybackContinuationTarget("episode-stale", "Stale") {})
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(PlaybackSegmentState(), first.segment.state.value)
            assertEquals(PlaybackContinuationState(), first.continuation.state.value)
            assertEquals(
                "outro-fresh",
                second.segment.state.value.actions
                    .single()
                    .segmentId,
            )
            assertEquals(
                "episode-fresh",
                second.continuation.state.value.nextTarget
                    ?.mediaId,
            )
            showHost = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(PlaybackSegmentState(), second.segment.state.value)
            assertEquals(PlaybackContinuationState(), second.continuation.state.value)
        }
    }

    @Test
    fun commandRouterUsesLiveCastControllerAndSyncPlayPaths() {
        val cast = CastSessionManagerFake()
        val controller = PlaybackController(castSessionManager = cast)
        setPlaybackState(controller, activePlayback(), includeSession = true)
        val syncEvents = mutableListOf<String>()
        var syncPlayActive = false
        val router =
            AndroidPlaybackCommandRouter(
                isSyncPlayActive = { syncPlayActive },
                requestSyncSeek = { syncEvents += "seek:$it" },
                requestPlaybackSeek = controller::seekTo,
                requestSyncNext = { syncEvents += "next" },
            )
        runBlocking { cast.emitState(CastConnectionState.Connected("Living Room", castSnapshot())) }
        composeRule.waitUntil { controller.state.value is PlaybackState.CastPlayback }
        cast.clearCommands()

        router.seekTo(42_000L)
        composeRule.waitUntil {
            cast.commands.contains(CastSessionManagerFake.Command.Seek(42_000L))
        }
        syncPlayActive = true
        router.seekTo(84_000L)
        runBlocking { router.playNext { syncEvents += "playback-next" } }
        syncPlayActive = false
        runBlocking { router.playNext { syncEvents += "playback-next" } }

        assertEquals(listOf("seek:84000", "next", "playback-next"), syncEvents)
        assertEquals(listOf(CastSessionManagerFake.Command.Seek(42_000L)), cast.commands)
        controller.release()
    }

    private fun setTestContent(content: @Composable () -> Unit) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent { content() }
        }
    }

    @Composable
    private fun ReviewPlaybackSurface(
        resources: ScreenResources,
        playbackState: PlaybackState.Active,
        segmentState: PlaybackSegmentState,
        continuationState: PlaybackContinuationState = PlaybackContinuationState(),
        onSkipSegment: (PlaybackSegmentAction) -> Unit = {},
        onPlayNext: () -> Unit = {},
    ) {
        val context = LocalContext.current
        val engine = remember { AndroidPlayerEngine(context) }
        SideEffect {
            resources.engine = engine
            setPlaybackState(resources.controller, playbackState)
        }
        JellystackTheme(isDarkTheme = true) {
            AndroidPlaybackSurface(
                controller = resources.controller,
                playerEngine = engine,
                castSessionManager = resources.cast,
                castRouteButton = {},
                segmentState = segmentState,
                continuationState = continuationState,
                onSkipSegment = onSkipSegment,
                onPlayNext = onPlayNext,
            )
        }
    }

    @Composable
    private fun CoordinatorLifecycleHost(
        identity: AndroidJellyfinPlaybackIdentity,
        staleIdentity: AndroidJellyfinPlaybackIdentity,
        staleSegments: CompletableDeferred<JellyfinMediaSegmentsResult>,
        staleTarget: CompletableDeferred<PlaybackContinuationTarget?>,
        onObserved: (AndroidPlaybackCoordinators) -> Unit,
    ) {
        val coordinators =
            rememberAndroidPlaybackCoordinators(
                identity = identity,
                playbackState = activePlayback(),
                isForeground = true,
                createSegmentCoordinator = { scope ->
                    PlaybackSegmentCoordinator(
                        scope = scope,
                        segmentService =
                            if (identity == staleIdentity) {
                                CancellationResistantSegmentService(staleSegments)
                            } else {
                                StaticSegmentService(availableSegment("outro-fresh", "Outro"))
                            },
                        modeProvider = PlaybackSegmentModeProvider { SegmentSkipMode.SHOW_BUTTON },
                        seekAdapter = PlaybackSeekAdapter {},
                    )
                },
                createContinuationCoordinator = { scope ->
                    PlaybackContinuationCoordinator(
                        scope = scope,
                        modeProvider = { AutoplayNextMode.OFF },
                        resolveNext = { _, _ ->
                            if (identity == staleIdentity) {
                                try {
                                    staleTarget.await()
                                } catch (_: CancellationException) {
                                    withContext(NonCancellable) { staleTarget.await() }
                                }
                            } else {
                                PlaybackContinuationTarget("episode-fresh", "Fresh") {}
                            }
                        },
                    )
                },
            )
        SideEffect { onObserved(coordinators) }
    }

    private class ScreenResources {
        val controller = PlaybackController()
        val cast = CastSessionManagerFake()
        var engine: AndroidPlayerEngine? = null

        fun release() {
            controller.release()
            engine?.release()
        }
    }

    private class CancellationResistantSegmentService(
        private val deferred: CompletableDeferred<JellyfinMediaSegmentsResult>,
    ) : JellyfinMediaSegmentsService {
        override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult =
            try {
                deferred.await()
            } catch (_: CancellationException) {
                withContext(NonCancellable) { deferred.await() }
            }
    }

    private class StaticSegmentService(
        private val result: JellyfinMediaSegmentsResult,
    ) : JellyfinMediaSegmentsService {
        override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult = result
    }

    private companion object {
        val stream =
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
        val source =
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

        fun activePlayback(
            positionMs: Long = 10_000L,
            phase: PlaybackPhase = PlaybackPhase.Ready,
        ): PlaybackState.Active =
            PlaybackState.LocalPlayback(
                mediaId = "episode",
                deviceName = "test",
                stream = stream,
                positionMs = positionMs,
                durationMs = 90_000L,
                audioTrack = null,
                subtitleTrack = null,
                isPaused = false,
                source = source,
                qualityOptions = emptyList(),
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
                metadata = PlaybackMetadata("Episode", "series", "Series", "Episode", null, null),
                mediaKind = PlaybackMediaKind.VIDEO,
                phase = phase,
            )

        fun introState() =
            PlaybackSegmentState(
                mediaId = "episode",
                activeSegments = listOf(PlaybackSegment("intro-1", PlaybackSegmentType.INTRO, 0L, 20_000L)),
                actions = listOf(PlaybackSegmentAction("episode", "intro-1", PlaybackSegmentType.INTRO, 20_000L)),
            )

        fun overlappingState() =
            PlaybackSegmentState(
                mediaId = "episode",
                activeSegments =
                    listOf(
                        PlaybackSegment("intro-1", PlaybackSegmentType.INTRO, 0L, 90_000L),
                        PlaybackSegment("recap-1", PlaybackSegmentType.RECAP, 0L, 90_000L),
                        PlaybackSegment("outro-1", PlaybackSegmentType.OUTRO, 80_000L, 90_000L),
                    ),
                actions =
                    listOf(
                        PlaybackSegmentAction("episode", "intro-1", PlaybackSegmentType.INTRO, 90_000L),
                        PlaybackSegmentAction("episode", "recap-1", PlaybackSegmentType.RECAP, 90_000L),
                        PlaybackSegmentAction("episode", "outro-1", PlaybackSegmentType.OUTRO, 90_000L),
                    ),
            )

        fun preparedContinuation(countdownSeconds: Int? = null) =
            PlaybackContinuationState(
                mediaId = "episode",
                nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
                countdownSecondsRemaining = countdownSeconds,
            )

        fun availableSegment(
            id: String,
            type: String,
        ) = JellyfinMediaSegmentsResult.Available(
            listOf(JellyfinMediaSegmentDto(id, "episode", type, 0L, 200_000_000L)),
        )

        fun castSnapshot() =
            CastSessionSnapshot(
                mediaId = "episode",
                title = "Episode",
                seriesName = "Series",
                episodeName = "Episode",
                artworkUrl = null,
                streamUrl = source.url,
                positionMs = 10_000L,
                durationMs = 90_000L,
                isPaused = false,
            )

        fun setPlaybackState(
            controller: PlaybackController,
            state: PlaybackState.Active,
            includeSession: Boolean = false,
        ) {
            val stateField = controller::class.java.getDeclaredField("_state").apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val stateFlow = stateField.get(controller) as kotlinx.coroutines.flow.MutableStateFlow<PlaybackState>
            stateFlow.value = state
            if (includeSession) {
                val sessionField = controller::class.java.getDeclaredField("session").apply { isAccessible = true }
                sessionField.set(
                    controller,
                    PlaybackSession(
                        request =
                            PlaybackRequest(
                                "episode",
                                emptyList(),
                                PlaybackMediaKind.VIDEO,
                                metadata = state.metadata,
                            ),
                        mediaId = "episode",
                        stream = stream,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        audioTrack = null,
                        subtitleTrack = null,
                        isPaused = false,
                        source = source,
                        qualityOptions = emptyList(),
                        selectedQualityId = PlaybackQualityOption.AUTO_ID,
                        phase = state.phase,
                    ),
                )
            }
        }
    }
}
