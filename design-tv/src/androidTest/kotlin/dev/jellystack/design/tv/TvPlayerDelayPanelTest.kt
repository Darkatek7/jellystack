package dev.jellystack.design.tv

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPlayerDelayPanelTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    private val strings = TvStrings.current(AppLanguage.ENGLISH)
    private val delays = TvPlaybackDelays()

    @Test
    fun leftAndRightStepTheFocusedDelayAndCenterResetsIt() {
        setPanel(subtitleOn = true)
        val audio = composeRule.onNodeWithContentDescription("${strings.player.sync.audioDelay}, 0 ms")
        audio.assertIsFocused()

        audio.performKeyInput { pressKey(Key.DirectionRight) }
        audio.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(100, delays.audioMs) }

        composeRule
            .onNodeWithContentDescription(
                "${strings.player.sync.audioDelay}, +100 ms",
            ).performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(50, delays.audioMs) }

        composeRule
            .onNodeWithContentDescription(
                "${strings.player.sync.audioDelay}, +50 ms",
            ).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(0, delays.audioMs) }
    }

    @Test
    fun subtitleRowIsSkippedByTheDpadWhileNoSubtitleIsOn() {
        setPanel(subtitleOn = false)
        composeRule.onNodeWithContentDescription("${strings.player.sync.audioDelay}, 0 ms").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
        }

        composeRule.onNodeWithContentDescription(strings.player.sync.resetDelays).assertIsFocused()
    }

    private fun setPanel(subtitleOn: Boolean) {
        composeRule.setContent {
            val first = remember { FocusRequester() }
            JellystackTvTheme {
                LazyColumn {
                    tvPlayerDelayRows(delays, subtitleOn, strings, Modifier.focusRequester(first))
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
    }
}
