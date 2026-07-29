package app.jellystack.mobile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSession
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlaybackSurfaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun videoIsImmersiveWithoutChangingOrientationAndBarsRestoreOnClose() {
        val originalOrientation = composeRule.activity.requestedOrientation
        showPlayback(PlaybackMediaKind.VIDEO)

        composeRule.onNodeWithContentDescription("Video playback surface").assertExists()
        composeRule.waitUntil(5_000L) { !systemBarsVisible() }
        assertEquals(originalOrientation, composeRule.activity.requestedOrientation)

        composeRule.onNodeWithContentDescription("Exit playback").performClick()
        composeRule.waitUntil(5_000L) { systemBarsVisible() }
        assertTrue(systemBarsVisible())
    }

    @Test
    fun audioDoesNotCreateVideoSurfaceOrHideBars() {
        showPlayback(PlaybackMediaKind.AUDIO)

        composeRule.onNodeWithContentDescription("Video playback surface").assertDoesNotExist()
        composeRule.waitUntil(5_000L) { systemBarsVisible() }
        assertTrue(systemBarsVisible())
    }

    @Test
    fun pausedPlaybackSuppressesAutoHide() {
        showPlayback(PlaybackMediaKind.VIDEO)
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(5_000L)

        composeRule.onNodeWithContentDescription("Play").assertExists()
    }

    @Test
    fun hiddenVideoControlsCanBeRevealedSemantically() {
        showPlayback(PlaybackMediaKind.VIDEO)
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.onNodeWithContentDescription("Pause").assertDoesNotExist()

        composeRule
            .onNodeWithContentDescription("Video playback surface")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithContentDescription("Pause").assertExists()
    }

    private fun showPlayback(mediaKind: PlaybackMediaKind) {
        val environment = composeRule.activity.playbackEnvironment ?: error("Playback environment unavailable")
        val stream =
            PlaybackStreamSelection(
                sourceId = "source-1",
                mode = PlaybackMode.DIRECT,
                container = "mp4",
                videoCodec = "h264",
                audioCodec = "aac",
                videoBitrate = 1_000_000,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                maxBitrate = null,
                qualityOptions = emptyList(),
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
            )
        val source =
            ResolvedPlaybackSource(
                url = "https://example.invalid/video.mp4",
                headers = emptyMap(),
                mode = PlaybackMode.DIRECT,
                mimeType = if (mediaKind == PlaybackMediaKind.AUDIO) "audio/mp4" else "video/mp4",
                subtitles = emptyList(),
                playSessionId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        val request = PlaybackRequest(mediaId = "media-1", mediaSources = emptyList(), mediaKind = mediaKind)
        val session =
            PlaybackSession(
                request = request,
                mediaId = "media-1",
                stream = stream,
                positionMs = 10_000L,
                durationMs = 120_000L,
                audioTrack = null,
                subtitleTrack = null,
                isPaused = false,
                source = source,
                qualityOptions = emptyList(),
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
                phase = PlaybackPhase.Ready,
            )
        val state =
            PlaybackState.LocalPlayback(
                mediaId = "media-1",
                deviceName = "Test device",
                stream = stream,
                positionMs = 10_000L,
                durationMs = 120_000L,
                audioTrack = null,
                subtitleTrack = null,
                isPaused = false,
                source = source,
                qualityOptions = emptyList(),
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
                mediaKind = mediaKind,
                phase = PlaybackPhase.Ready,
            )
        composeRule.runOnIdle {
            val stateField =
                environment.controller::class.java
                    .getDeclaredField("_state")
                    .apply { isAccessible = true }
            val sessionField =
                environment.controller::class.java
                    .getDeclaredField("session")
                    .apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val stateFlow = stateField.get(environment.controller) as MutableStateFlow<PlaybackState>
            sessionField.set(environment.controller, session)
            stateFlow.value = state
        }
        composeRule.waitForIdle()
    }

    private fun systemBarsVisible(): Boolean {
        var visible = false
        composeRule.activityRule.scenario.onActivity { activity ->
            val insets = activity.window.decorView.rootWindowInsets
            visible =
                insets != null &&
                WindowInsetsCompat.toWindowInsetsCompat(insets, activity.window.decorView).let { compat ->
                    compat.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                        compat.isVisible(WindowInsetsCompat.Type.navigationBars())
                }
        }
        return visible
    }
}
