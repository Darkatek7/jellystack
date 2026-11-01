package dev.jellystack.design.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvAudioPlayerScreenTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun focusStartsOnPlayPauseAndTheTrackIsNamed() {
        show()

        composeRule.onNodeWithTag("tv-audio-play-pause").assertIsFocused()
        composeRule.onNodeWithText("Opening Theme").assertExists()
        composeRule.onNodeWithText("Example Band").assertExists()
        composeRule.onNodeWithText("First Light").assertExists()
    }

    @Test
    fun leftAndRightMoveBetweenPreviousPlayPauseAndNext() {
        show()

        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("tv-audio-previous").assertIsFocused()

        composeRule.onNodeWithTag("tv-audio-previous").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("tv-audio-play-pause").assertIsFocused()

        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("tv-audio-next").assertIsFocused()
    }

    @Test
    fun centerOnTheControlsPausesAndSkips() {
        show(hasNext = true)

        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("tv-audio-next").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("tv-audio-next").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("tv-audio-previous").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(listOf("pause:true", "next", "previous"), calls) }
    }

    @Test
    fun nextDoesNothingOnTheLastTrack() {
        show(hasNext = false)

        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("tv-audio-next").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test
    fun backLeavesThePlayer() {
        show()

        composeRule.onNodeWithTag("tv-audio-play-pause").performKeyInput { pressKey(Key.Back) }

        composeRule.runOnIdle { assertEquals(listOf("close"), calls) }
    }

    private fun show(hasNext: Boolean = true) {
        composeRule.setContent {
            JellystackTvTheme {
                TvAudioPlayerScreen(
                    state =
                        TvAudioPlayerState(
                            playback = playing(),
                            start = TvPlaybackStartState.Idle,
                            images = TvPlayerImageSource(null, null),
                            hasNext = hasNext,
                        ),
                    transport =
                        TvAudioTransport(
                            onSetPaused = { calls += "pause:$it" },
                            onPreviousTrack = { calls += "previous" },
                            onNextTrack = { calls += "next" },
                        ),
                    errors = TvAudioErrorActions({ calls += "retry" }, { calls += "retry-start" }, { calls += "close" }),
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                )
            }
        }
    }

    private fun playing(): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = "t1",
            deviceName = "test",
            stream =
                PlaybackStreamSelection(
                    sourceId = "source",
                    mode = PlaybackMode.DIRECT,
                    container = "flac",
                    videoCodec = null,
                    audioCodec = "flac",
                    videoBitrate = null,
                    audioTracks = emptyList(),
                    subtitleTracks = emptyList(),
                    maxBitrate = null,
                    qualityOptions = emptyList(),
                    selectedQualityId = PlaybackQualityOption.AUTO_ID,
                ),
            positionMs = 30_000L,
            durationMs = 180_000L,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source =
                ResolvedPlaybackSource(
                    url = "https://example.test/audio",
                    headers = emptyMap(),
                    mode = PlaybackMode.DIRECT,
                    mimeType = "audio/flac",
                    subtitles = emptyList(),
                    playSessionId = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                ),
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            metadata =
                PlaybackMetadata(
                    title = "Opening Theme",
                    seriesId = null,
                    seriesName = null,
                    episodeName = null,
                    artworkUrl = null,
                    primaryImageTag = null,
                    artist = "Example Band",
                    albumName = "First Light",
                ),
            mediaKind = PlaybackMediaKind.AUDIO,
        )
}
