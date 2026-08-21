package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.SegmentSkipMode
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
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackSegmentType
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import dev.jellystack.players.syncplay.SyncPlayUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPlaybackReviewIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realScreenStartsStandaloneWindowWhenControlsHideAndKeepsControlActionPersistent() {
        composeRule.mainClock.autoAdvance = false
        val resources = ScreenResources()
        composeRule.setContent {
            JellystackTvTheme {
                ReviewPlaybackScreen(
                    resources = resources,
                    playbackState = activePlayback(),
                    segmentState = introState(),
                    continuationState = PlaybackContinuationState(),
                )
            }
        }

        composeRule.onNodeWithTag(TV_PLAYBACK_ACTIONS_CONTROLS_TAG).assertExists()
        // The 5-second coroutine deadline is applied on the next Compose frame.
        composeRule.mainClock.advanceTimeBy(5_100L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TV_PLAYBACK_ACTIONS_STANDALONE_TAG).assertExists()

        composeRule.mainClock.advanceTimeBy(7_900L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Skip intro").assertDoesNotExist()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TV_PLAYBACK_ACTIONS_CONTROLS_TAG).assertExists()
        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun realScreenKeepsSecondActionFocusedAcrossFirstRemovalThenUsesPlaybackFallback() {
        val resources = ScreenResources()
        var segmentState by mutableStateOf(outroState(showSkipAction = true))
        var continuationState by mutableStateOf(preparedContinuation())
        composeRule.setContent {
            JellystackTvTheme {
                ReviewPlaybackScreen(
                    resources = resources,
                    playbackState = activePlayback(positionMs = 85_000L),
                    segmentState = segmentState,
                    continuationState = continuationState,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Play next episode")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle { segmentState = outroState(showSkipAction = false) }
        composeRule.onNodeWithContentDescription("Play next episode").assertIsFocused()

        composeRule.runOnIdle { continuationState = PlaybackContinuationState(mediaId = "episode") }
        composeRule.onNodeWithContentDescription("Pause").assertIsFocused()
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun realScreenCallbacksUseLiveSyncPlaySelectionForBothSimultaneousActions() {
        val resources = ScreenResources()
        var syncPlayActive = false
        val events = mutableListOf<String>()
        val router =
            TvPlaybackCommandRouter(
                isSyncPlayActive = { syncPlayActive },
                requestSyncSeek = { events += "sync-seek:$it" },
                requestLocalSeek = { events += "local-seek:$it" },
                requestSyncNext = { events += "sync-next" },
            )
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            JellystackTvTheme {
                ReviewPlaybackScreen(
                    resources = resources,
                    playbackState = activePlayback(positionMs = 85_000L),
                    segmentState = outroState(showSkipAction = true),
                    continuationState = preparedContinuation(),
                    onSkipSegment = { router.seekTo(it.endPositionMs) },
                    onPlayNext = { scope.launch { router.playNext { events += "local-next" } } },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Skip credits").performClick()
        composeRule.runOnIdle { syncPlayActive = true }
        composeRule.onNodeWithContentDescription("Play next episode").performClick()
        composeRule.waitUntil { events.size == 2 }

        composeRule.runOnIdle {
            assertEquals(listOf("local-seek:90000", "sync-next"), events)
            resources.release()
        }
    }

    @Test
    fun endedScreenWithOutroAndPreparedTargetShowsOnlySharedCompletionPrompt() {
        val resources = ScreenResources()
        val continuation = preparedContinuation(countdownSeconds = 10)
        composeRule.setContent {
            JellystackTvTheme {
                Box {
                    ReviewPlaybackScreen(
                        resources = resources,
                        playbackState = activePlayback(positionMs = 90_000L, phase = PlaybackPhase.Ended),
                        segmentState = outroState(showSkipAction = true),
                        continuationState = continuation,
                    )
                    TvPlaybackCompletionPrompt(
                        continuationState = continuation,
                        strings = strings,
                        onPlayNow = {},
                        onCancel = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tv-player-action:segment:outro:outro-1").assertDoesNotExist()
        composeRule.onNodeWithTag("tv-player-action:play-next:episode-2").assertDoesNotExist()
        composeRule.onNodeWithText("Episode 2").assertExists()
        composeRule.onNodeWithText(strings.playingInSeconds.format(10)).assertExists()
        composeRule.runOnIdle(resources::release)
    }

    @Test
    fun environmentAccountReplacementRejectsSameMediaStaleWorkAndReleasesEveryInstance() {
        val firstIdentity = TvJellyfinPlaybackIdentity("server-a", "user-a")
        val secondIdentity = TvJellyfinPlaybackIdentity("server-b", "user-b")
        val staleSegments = CompletableDeferred<JellyfinMediaSegmentsResult>()
        val staleTarget = CompletableDeferred<PlaybackContinuationTarget?>()
        val observed = linkedMapOf<TvJellyfinPlaybackIdentity, TvPlaybackCoordinators>()
        var identity by mutableStateOf(firstIdentity)
        var showHost by mutableStateOf(true)

        composeRule.setContent {
            if (showHost) {
                PlaybackCoordinatorLifecycleHost(
                    identity = identity,
                    staleIdentity = firstIdentity,
                    staleSegments = staleSegments,
                    staleTarget = staleTarget,
                    onObserved = { observed[identity] = it },
                )
            }
        }

        composeRule.waitUntil { observed[firstIdentity]?.segment?.state?.value?.isLoading == true }
        val first = observed.getValue(firstIdentity)
        composeRule.runOnIdle { identity = secondIdentity }
        composeRule.waitUntil {
            observed[secondIdentity]?.let { pair ->
                pair.segment.state.value.actions.singleOrNull()?.segmentId == "outro-fresh" &&
                    pair.continuation.state.value.nextTarget?.mediaId == "episode-fresh"
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
            assertEquals("outro-fresh", second.segment.state.value.actions.single().segmentId)
            assertEquals("episode-fresh", second.continuation.state.value.nextTarget?.mediaId)
            showHost = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(PlaybackSegmentState(), second.segment.state.value)
            assertEquals(PlaybackContinuationState(), second.continuation.state.value)
        }
    }

    @Composable
    private fun ReviewPlaybackScreen(
        resources: ScreenResources,
        playbackState: PlaybackState,
        segmentState: PlaybackSegmentState,
        continuationState: PlaybackContinuationState,
        onSkipSegment: (PlaybackSegmentAction) -> Unit = {},
        onPlayNext: () -> Unit = {},
    ) {
        val context = LocalContext.current
        val engine = remember { AndroidPlayerEngine(context) }
        SideEffect { resources.engine = engine }
        TvPlaybackScreen(
            controller = resources.controller,
            engine = engine,
            syncPlay = resources.syncPlay,
            playbackState = playbackState,
            syncState = SyncPlayUiState(),
            segmentState = segmentState,
            continuationState = continuationState,
            onSkipSegment = onSkipSegment,
            onPlayNext = onPlayNext,
            strings = strings,
            stopPlayback = {},
            onClose = {},
            modifier = Modifier,
        )
    }

    @Composable
    private fun PlaybackCoordinatorLifecycleHost(
        identity: TvJellyfinPlaybackIdentity,
        staleIdentity: TvJellyfinPlaybackIdentity,
        staleSegments: CompletableDeferred<JellyfinMediaSegmentsResult>,
        staleTarget: CompletableDeferred<PlaybackContinuationTarget?>,
        onObserved: (TvPlaybackCoordinators) -> Unit,
    ) {
        val coordinators =
            rememberTvPlaybackCoordinators(
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
        val syncPlay =
            SyncPlayCoordinator(
                environmentProvider = JellyfinEnvironmentProvider { null },
                playbackController = controller,
                playItem = { _, _ -> },
            )
        var engine: AndroidPlayerEngine? = null

        fun release() {
            syncPlay.close()
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

    private fun introState() =
        PlaybackSegmentState(
            mediaId = "episode",
            activeSegments = listOf(PlaybackSegment("intro-1", PlaybackSegmentType.INTRO, 0L, 20_000L)),
            actions = listOf(PlaybackSegmentAction("episode", "intro-1", PlaybackSegmentType.INTRO, 20_000L)),
        )

    private fun outroState(showSkipAction: Boolean) =
        PlaybackSegmentState(
            mediaId = "episode",
            activeSegments = listOf(PlaybackSegment("outro-1", PlaybackSegmentType.OUTRO, 80_000L, 90_000L)),
            actions =
                if (showSkipAction) {
                    listOf(PlaybackSegmentAction("episode", "outro-1", PlaybackSegmentType.OUTRO, 90_000L))
                } else {
                    emptyList()
                },
        )

    private fun preparedContinuation(countdownSeconds: Int? = null) =
        PlaybackContinuationState(
            mediaId = "episode",
            nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
            countdownSecondsRemaining = countdownSeconds,
        )

    private fun activePlayback(
        positionMs: Long = 10_000L,
        phase: PlaybackPhase = PlaybackPhase.Ready,
    ): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = "episode",
            deviceName = "test",
            stream = testStream,
            positionMs = positionMs,
            durationMs = 90_000L,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source = testSource,
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            metadata = PlaybackMetadata("Episode", "series", "Series", "Episode", null, null),
            mediaKind = PlaybackMediaKind.VIDEO,
            phase = phase,
        )

    private companion object {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
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

        fun availableSegment(
            id: String,
            type: String,
        ) = JellyfinMediaSegmentsResult.Available(
            listOf(
                JellyfinMediaSegmentDto(
                    id = id,
                    itemId = "episode",
                    type = type,
                    startTicks = 0L,
                    endTicks = 200_000_000L,
                ),
            ),
        )
    }
}
