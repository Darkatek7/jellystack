package app.jellystack.mobile

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.jellystack.design.TestTags
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSession
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import dev.jellystack.players.SubtitleFormat
import dev.jellystack.players.SubtitleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        composeRule.activityRule.scenario.onActivity { activity ->
            listOf(
                "jellystack_prefs",
                "jellystack_playback",
                "jellystack_downloads",
                "jellystack_cast_permissions",
            ).forEach { name ->
                activity
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun onboardingFlowShowsWelcomeAndJellyfinSetup() {
        dismissWhatsNewIfPresent()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("Welcome to Jellystack")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithText("Welcome to Jellystack").onFirst().assertExists()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onAllNodesWithText("Connect Jellyfin").onFirst().assertExists()
        composeRule.onNodeWithText("Server URL").assertExists()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onAllNodesWithText("Welcome to Jellystack").onFirst().assertExists()
    }

    @Test
    fun homeScreenShowsNavigationTabs() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Discover").assertExists()
    }

    @Test
    fun backFromLibraryReturnsHomeBeforePlatformExit() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithTag(TestTags.PRIMARY_LIBRARY).performClick().assertIsSelected()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun themeTogglePropagatesAcrossScreens() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("Light").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("Light").assertIsSelected()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
    }

    @Test
    fun connectionsSectionShowsServerControls() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Connections").performScrollTo().performClick()
        composeRule
            .onNodeWithText("Connect server Jellyfin")
            .performScrollTo()
            .assertExists()
            .assertIsEnabled()
        composeRule
            .onNodeWithText("Connect server Seerr")
            .performScrollTo()
            .assertExists()
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("Close settings").performClick()
    }

    @Test
    fun rotationKeepsHomeScreen() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithText("Jellystack").assertExists()
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        composeRule.onNodeWithText("Jellystack").assertExists()
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun rotationKeepsLibrarySelection() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithTag(TestTags.PRIMARY_LIBRARY).performClick().assertIsSelected()
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        composeRule.onNodeWithTag(TestTags.PRIMARY_LIBRARY).assertIsSelected()
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        composeRule.onNodeWithTag(TestTags.PRIMARY_LIBRARY).assertIsSelected()
    }

    @Test
    fun playbackStateSurvivesRotation() {
        dismissOnboardingIfPresent()
        val activity = composeRule.activity
        val environment = activity.playbackEnvironment ?: error("Playback environment unavailable")
        val controller = environment.controller

        composeRule.runOnIdle {
            val audioTrack =
                AudioTrack(
                    id = "audio-eng",
                    language = Locale.ENGLISH.language,
                    title = "English",
                    codec = "aac",
                    isDefault = true,
                    streamIndex = 0,
                )
            val streamSelection =
                PlaybackStreamSelection(
                    maxBitrate = null,
                    qualityOptions = emptyList(),
                    selectedQualityId = PlaybackQualityOption.AUTO_ID,
                    sourceId = "source1",
                    mode = PlaybackMode.DIRECT,
                    container = "mp4",
                    videoCodec = "h264",
                    audioCodec = audioTrack.codec,
                    videoBitrate = 1_000_000,
                    audioTracks = listOf(audioTrack),
                    subtitleTracks = emptyList(),
                )
            val source =
                ResolvedPlaybackSource(
                    url = "https://example.com/stream.mp4",
                    headers = emptyMap(),
                    mode = PlaybackMode.DIRECT,
                    mimeType = "video/mp4",
                    subtitles = emptyList(),
                    playSessionId = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                )
            val session =
                PlaybackSession(
                    request = PlaybackRequest(mediaId = "media1", mediaSources = emptyList()),
                    mediaId = "media1",
                    stream = streamSelection,
                    positionMs = 0L,
                    durationMs = 120_000L,
                    audioTrack = audioTrack,
                    subtitleTrack = null,
                    isPaused = false,
                    source = source,
                    qualityOptions = emptyList(),
                    selectedQualityId = PlaybackQualityOption.AUTO_ID,
                )
            val stateField =
                PlaybackController::class.java
                    .getDeclaredField("_state")
                    .apply { isAccessible = true }
            val sessionField =
                PlaybackController::class.java
                    .getDeclaredField("session")
                    .apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val stateFlow = stateField.get(controller) as MutableStateFlow<PlaybackState>
            sessionField.set(controller, session)
            stateFlow.value =
                PlaybackState.LocalPlayback(
                    mediaId = session.mediaId,
                    deviceName = "Test Device",
                    stream = session.stream,
                    positionMs = session.positionMs,
                    durationMs = session.durationMs,
                    audioTrack = session.audioTrack,
                    subtitleTrack = session.subtitleTrack,
                    isPaused = session.isPaused,
                    source = session.source,
                    qualityOptions = session.qualityOptions,
                    selectedQualityId = session.selectedQualityId,
                )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithContentDescription("Exit playback")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        composeRule.onNodeWithContentDescription("Exit playback").assertExists()
        val playingState = controller.state.value as? PlaybackState.LocalPlayback
        assertTrue(
            "Playback state should remain active after rotation",
            playingState?.mediaId == "media1",
        )
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun whatsNewModalCanBeReopened() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("What’s new").performScrollTo().performClick()
        composeRule.onNodeWithText("What's new in Jellystack").assertExists()
        composeRule.onNodeWithText("Close").performClick()
    }

    @Test
    fun whatsNewShowsCanonical014Highlights() {
        dismissOnboardingIfPresent()
        openWhatsNew()
        composeRule.onAllNodesWithText("Version 0.14.2").onLast().assertExists()
        composeRule
            .onNodeWithText(
                "Significantly reduced Android permissions with clearer, contextual requests.",
                substring = true,
            ).assertExists()
        composeRule
            .onNodeWithText(
                "New permission and privacy explanations directly in Settings.",
                substring = true,
            ).assertExists()
        composeRule
            .onNodeWithText(
                "Jellystack is now fully open source under AGPL-3.0.",
                substring = true,
            ).assertExists()
    }

    @Test
    fun settingsGuideDoesNotRewriteCompletedOnboardingAcrossRecreation() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("Run setup guide").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Welcome to Jellystack").onFirst().assertExists()

        assertCompletedOnboardingPreferences()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Server URL").performTextInput("https://guide.invalid")
        assertCompletedOnboardingPreferences()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onAllNodesWithText("Welcome to Jellystack").onFirst().assertExists()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onAllNodesWithText("About").onFirst().assertExists()
        composeRule.onNodeWithText("Continue").assertMissing()
        assertCompletedOnboardingPreferences()

        composeRule.onNodeWithContentDescription("Close settings").performClick()
        composeRule.onNodeWithText("Connections").performScrollTo().performClick()
        composeRule
            .onNodeWithText("Connect server Jellyfin")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("https://guide.invalid").assertMissing()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Continue").assertMissing()
        assertCompletedOnboardingPreferences()
    }

    @Test
    fun castRoutePickerButtonAppearsOnHomeScreen() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithTag(ROUTE_PICKER_TAG).assertExists()
    }

    @Test
    fun castDisconnectButtonHiddenWhenNotCasting() {
        dismissOnboardingIfPresent()
        composeRule.onNodeWithTag(CAST_DISCONNECT_BUTTON_TAG).assertMissing()
    }

    @Test
    fun localPlaybackSurfaceShowsWithoutCastDisconnectButton() {
        dismissOnboardingIfPresent()
        val environment = composeRule.activity.playbackEnvironment ?: error("Playback environment not initialised")
        environment.controller.forcePlayingStateForTests()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Exit playback").assertExists()
        composeRule.onNodeWithTag(CAST_DISCONNECT_BUTTON_TAG).assertMissing()
    }

    @Test
    fun preparingPlaybackShowsTitleAndProgress() {
        dismissOnboardingIfPresent()
        composeRule.activity.playbackEnvironment?.controller?.forceStateForTests(
            PlaybackState.Preparing(
                mediaId = "episode-3",
                metadata = episodeMetadata(),
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Test Series").assertExists()
        composeRule.onNodeWithText("Preparing playback").assertExists()
        composeRule.onNodeWithContentDescription("Exit playback").assertExists()
    }

    @Test
    fun playbackErrorOffersRetryAndClose() {
        dismissOnboardingIfPresent()
        composeRule.activity.playbackEnvironment?.controller?.forceStateForTests(
            PlaybackState.PlaybackError(
                message = "The stream could not be opened.",
                mediaId = "episode-3",
                metadata = episodeMetadata(),
                canRetry = true,
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The stream could not be opened.").assertExists()
        composeRule.onNodeWithText("Try again").assertExists()
        composeRule.onNodeWithText("Close").assertExists()
    }

    @Test
    fun activePlayerUsesWideTimelineAndAdaptiveOptions() {
        dismissOnboardingIfPresent()
        val environment = composeRule.activity.playbackEnvironment ?: error("Playback environment not initialised")
        environment.controller.forcePlayingStateForTests()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PLAYER_TIMELINE_TAG).assertExists()
        composeRule.onNodeWithText("Test Series").assertExists()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithTag(PLAYER_OPTIONS_TAG).assertExists()
        composeRule.onNodeWithTag(PLAYER_AUDIO_SELECTOR_TAG).performClick()
        composeRule.onNodeWithText("Japanese · ja · AAC").performClick()
        composeRule.onNodeWithTag(PLAYER_AUDIO_SELECTOR_TAG).assertTextContains("Japanese · ja · AAC")

        composeRule.onNodeWithTag(PLAYER_SUBTITLE_SELECTOR_TAG).performClick()
        composeRule.onNodeWithText("English · en · VTT").performClick()
        composeRule.onNodeWithTag(PLAYER_SUBTITLE_SELECTOR_TAG).assertTextContains("English · en · VTT")

        composeRule.onNodeWithTag(PLAYER_QUALITY_SELECTOR_TAG).performClick()
        composeRule.onNodeWithText("4.0 Mbps · 720p · Adaptive").assertExists()
        composeRule.onNodeWithText("Cast diagnostics").assertDoesNotExist()
        composeRule.onNodeWithTag(PLAYER_OPTIONS_TAG).assertExists()
    }

    @Test
    fun playerChromeIconsRemainBrightOnTheBlackOverlay() {
        val environment = composeRule.activity.playbackEnvironment ?: error("Playback environment not initialised")
        environment.controller.forcePlayingStateForTests()
        composeRule.waitForIdle()

        assertHasBrightPixels(
            captureWithPixelCopyRetry(composeRule.onNodeWithContentDescription("Exit playback")),
            "exit icon",
        )
        assertHasBrightPixels(
            captureWithPixelCopyRetry(composeRule.onNodeWithTag(PLAYER_CAST_TAG)),
            "cast icon",
        )
        assertHasBrightPixels(
            captureWithPixelCopyRetry(composeRule.onNodeWithContentDescription("More options")),
            "options icon",
        )
    }

    private fun captureWithPixelCopyRetry(node: SemanticsNodeInteraction): ImageBitmap {
        var lastPixelCopyFailure: AssertionError? = null
        repeat(PIXEL_COPY_ATTEMPTS) {
            try {
                return node.captureToImage()
            } catch (failure: AssertionError) {
                if (!failure.message.orEmpty().contains("PixelCopy")) throw failure
                lastPixelCopyFailure = failure
                composeRule.waitForIdle()
                SystemClock.sleep(PIXEL_COPY_RETRY_DELAY_MS)
            }
        }
        throw checkNotNull(lastPixelCopyFailure)
    }

    private fun rotateTo(orientation: Int) {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        composeRule.waitForIdle()
    }

    private fun PlaybackController.forcePlayingStateForTests() {
        val audioTrack =
            AudioTrack(
                id = "audio-1",
                language = Locale.ENGLISH.language,
                title = "Stereo",
                codec = "AAC",
                isDefault = true,
                streamIndex = 0,
            )
        val japaneseAudioTrack =
            AudioTrack(
                id = "audio-2",
                language = Locale.JAPANESE.language,
                title = "Japanese",
                codec = "AAC",
                isDefault = false,
                streamIndex = 1,
            )
        val englishSubtitle =
            SubtitleTrack(
                id = "subtitle-1",
                language = Locale.ENGLISH.language,
                title = "English",
                format = SubtitleFormat.VTT,
                isDefault = false,
                isForced = false,
                streamIndex = 2,
            )
        val qualityOptions =
            listOf(
                PlaybackQualityOption(
                    id = PlaybackQualityOption.AUTO_ID,
                    label = "",
                    mode = PlaybackMode.DIRECT,
                    sourceId = "source-1",
                    maxBitrate = null,
                    isAuto = true,
                ),
                PlaybackQualityOption(
                    id = "quality-hls-source-1-4000000",
                    label = "4.0 Mbps · 720p",
                    mode = PlaybackMode.HLS,
                    sourceId = "source-1",
                    maxBitrate = 4_000_000,
                    maxHeight = 720,
                    isAuto = false,
                ),
            )
        val streamSelection =
            PlaybackStreamSelection(
                sourceId = "source-1",
                mode = PlaybackMode.DIRECT,
                container = "mp4",
                videoCodec = "h264",
                audioCodec = audioTrack.codec,
                videoBitrate = 1_000_000,
                audioTracks = listOf(audioTrack, japaneseAudioTrack),
                subtitleTracks = listOf(englishSubtitle),
                maxBitrate = null,
                qualityOptions = qualityOptions,
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
            )
        val source =
            ResolvedPlaybackSource(
                url = "https://example.com/stream.mp4",
                headers = emptyMap(),
                mode = PlaybackMode.DIRECT,
                mimeType = "video/mp4",
                subtitles = emptyList(),
                playSessionId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        val session =
            PlaybackSession(
                request =
                    PlaybackRequest(
                        mediaId = "media1",
                        mediaSources = emptyList(),
                        metadata = episodeMetadata(),
                    ),
                mediaId = "media1",
                stream = streamSelection,
                positionMs = 0L,
                durationMs = 120_000L,
                audioTrack = audioTrack,
                subtitleTrack = null,
                isPaused = false,
                source = source,
                qualityOptions = qualityOptions,
                selectedQualityId = PlaybackQualityOption.AUTO_ID,
            )
        val stateField =
            PlaybackController::class.java
                .getDeclaredField("_state")
                .apply { isAccessible = true }
        val sessionField =
            PlaybackController::class.java
                .getDeclaredField("session")
                .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(this) as MutableStateFlow<PlaybackState>
        sessionField.set(this, session)
        stateFlow.value =
            PlaybackState.LocalPlayback(
                mediaId = session.mediaId,
                deviceName = "Test Device",
                stream = session.stream,
                positionMs = session.positionMs,
                durationMs = session.durationMs,
                audioTrack = session.audioTrack,
                subtitleTrack = session.subtitleTrack,
                isPaused = session.isPaused,
                source = session.source,
                qualityOptions = session.qualityOptions,
                selectedQualityId = session.selectedQualityId,
                metadata = session.request.metadata,
            )
    }

    private fun PlaybackController.forceStateForTests(state: PlaybackState) {
        val stateField =
            PlaybackController::class.java
                .getDeclaredField("_state")
                .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(this) as MutableStateFlow<PlaybackState>
        stateFlow.value = state
    }

    private fun episodeMetadata(): PlaybackMetadata =
        PlaybackMetadata(
            title = "Episode title",
            seriesId = "series-1",
            seriesName = "Test Series",
            episodeName = "Episode title",
            artworkUrl = null,
            primaryImageTag = null,
            seasonNumber = 1,
            episodeNumber = 3,
        )

    companion object {
        private const val ROUTE_PICKER_TAG = "cast_route_picker_top_bar"
        private const val CAST_DISCONNECT_BUTTON_TAG = "cast_disconnect_button"
        private const val PLAYER_TIMELINE_TAG = "player_timeline"
        private const val PLAYER_OPTIONS_TAG = "player_options_panel"
        private const val PLAYER_AUDIO_SELECTOR_TAG = "player_audio_selector"
        private const val PLAYER_SUBTITLE_SELECTOR_TAG = "player_subtitle_selector"
        private const val PLAYER_QUALITY_SELECTOR_TAG = "player_quality_selector"
        private const val PLAYER_CAST_TAG = "cast_route_picker_player"
        private const val PIXEL_COPY_ATTEMPTS = 3
        private const val PIXEL_COPY_RETRY_DELAY_MS = 250L
    }

    private fun dismissOnboardingIfPresent() {
        composeRule.waitForIdle()
        dismissWhatsNewIfPresent()
        composeRule.waitForIdle()
        val tutorialNodes = composeRule.onAllNodesWithText("Welcome to Jellystack")
        if (tutorialNodes.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity
                    .getSharedPreferences("jellystack_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("onboarding.last_whats_new_version", BuildConfig.VERSION_NAME)
                    .putString("onboarding.tutorial.step", "Explore")
                    .putBoolean("onboarding.tutorial.completed", true)
                    .commit()
            }
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            dismissWhatsNewIfPresent()
        }
    }

    private fun dismissWhatsNewIfPresent() {
        composeRule.waitForIdle()
        val whatsNewNodes = composeRule.onAllNodesWithText("What's new in Jellystack")
        if (whatsNewNodes.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("View changelog").assertExists()
            composeRule.onNodeWithText("Close").performClick()
        }
    }

    private fun assertCompletedOnboardingPreferences() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("jellystack_prefs", Context.MODE_PRIVATE)
            assertEquals("Explore", preferences.getString("onboarding.tutorial.step", null))
            assertTrue(preferences.getBoolean("onboarding.tutorial.completed", false))
        }
    }

    private fun openWhatsNew() {
        if (
            composeRule
                .onAllNodesWithText("What's new in Jellystack")
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            return
        }
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("What’s new").performScrollTo().performClick()
    }
}

private fun SemanticsNodeInteraction.assertExists(): SemanticsNodeInteraction {
    assertTrue("Expected node to exist.", runCatching { fetchSemanticsNode() }.isSuccess)
    return this
}

private fun SemanticsNodeInteraction.assertMissing(): SemanticsNodeInteraction {
    assertTrue("Expected node to not exist.", runCatching { fetchSemanticsNode() }.isFailure)
    return this
}

private fun assertHasBrightPixels(
    image: ImageBitmap,
    label: String,
) {
    val pixels = image.toPixelMap()
    var brightPixelCount = 0
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.alpha > 0.5f && color.red + color.green + color.blue > 2.4f) {
                brightPixelCount++
            }
        }
    }
    assertTrue("Expected $label to contain bright foreground pixels.", brightPixelCount >= 8)
}
