package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.players.PlaybackSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPlaybackScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controlsActionsAreBottomRightAboveTheTimelineAndExposeStableTags() {
        composeRule.setContent {
            val fallback = remember { FocusRequester() }
            val entry = remember { FocusRequester() }
            JellystackTvTheme {
                Column(Modifier.width(800.dp)) {
                    TvPlaybackActions(
                        actions = listOf(action("segment:intro", "Skip intro")),
                        fallbackFocusRequester = fallback,
                        entryFocusRequester = entry,
                        onAction = {},
                        modifier = Modifier.fillMaxWidth().testTag(TV_PLAYBACK_ACTIONS_CONTROLS_TAG),
                    )
                    Box(Modifier.fillMaxWidth().height(5.dp).testTag(TV_PLAYBACK_TIMELINE_TAG))
                }
            }
        }

        val actionBounds = composeRule.onNodeWithTag("segment:intro").getUnclippedBoundsInRoot()
        val timelineBounds = composeRule.onNodeWithTag(TV_PLAYBACK_TIMELINE_TAG).getUnclippedBoundsInRoot()

        assertTrue(actionBounds.bottom <= timelineBounds.top)
        assertTrue(actionBounds.right > timelineBounds.left + (timelineBounds.right - timelineBounds.left) / 2f)
        composeRule.onNodeWithContentDescription("Skip intro").assertExists()
    }

    @Test
    fun appearingStandaloneActionDoesNotStealFocus() {
        lateinit var showAction: () -> Unit
        composeRule.setContent {
            var actions by remember { mutableStateOf(emptyList<TvPlaybackActionModel>()) }
            val fallback = remember { FocusRequester() }
            val entry = remember { FocusRequester() }
            showAction = { actions = listOf(action("segment:intro", "Skip intro")) }
            JellystackTvTheme {
                Column {
                    TvPlaybackActions(actions, fallback, entry, {}, Modifier.fillMaxWidth())
                    TvActionButton(
                        "Stable control",
                        {},
                        modifier = Modifier.focusRequester(fallback).focusProperties { up = entry },
                    )
                }
                LaunchedEffect(Unit) { fallback.requestFocus() }
            }
        }
        composeRule.onNodeWithText("Stable control").assertIsFocused()

        composeRule.runOnIdle(showAction)

        composeRule.onNodeWithText("Stable control").assertIsFocused()
    }

    @Test
    fun actionIsDpadReachableActivatesAndFallsBackWhenItDisappears() {
        var actions by mutableStateOf(listOf(action("segment:intro", "Skip intro")))
        val activated = mutableListOf<String>()
        composeRule.setContent {
            val fallback = remember { FocusRequester() }
            val entry = remember { FocusRequester() }
            JellystackTvTheme {
                Column {
                    TvPlaybackActions(
                        actions = actions,
                        fallbackFocusRequester = fallback,
                        entryFocusRequester = entry,
                        onAction = { activated += it.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvActionButton(
                        "Stable control",
                        {},
                        modifier = Modifier.focusRequester(fallback).focusProperties { up = entry },
                    )
                }
            }
        }

        composeRule
            .onNodeWithText("Stable control")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule
            .onNodeWithContentDescription("Skip intro")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf("segment:intro"), activated) }

        composeRule
            .onNodeWithContentDescription("Skip intro")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Stable control").assertIsFocused()

        composeRule.onNodeWithContentDescription("Skip intro").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.runOnIdle { actions = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Stable control").assertIsFocused()
    }

    @Test
    fun segmentSettingsExplainServerMarkersAndDispatchAllFivePickers() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val events = mutableListOf<Pair<PlaybackSegmentType, SegmentSkipMode>>()
        composeRule.setContent {
            JellystackTvTheme {
                TvSegmentSkipSettings(
                    settings = AppSettings(),
                    strings = strings,
                    onModeSelected = { type, mode -> events += type to mode },
                )
            }
        }

        composeRule.onNodeWithText(strings.serverSegmentExplanation).assertExists()
        val expectedTypes =
            listOf(
                strings.introSegments to PlaybackSegmentType.INTRO,
                strings.recapSegments to PlaybackSegmentType.RECAP,
                strings.outroSegments to PlaybackSegmentType.OUTRO,
                strings.previewSegments to PlaybackSegmentType.PREVIEW,
                strings.commercialSegments to PlaybackSegmentType.COMMERCIAL,
            )
        expectedTypes.forEach { (title, _) ->
            composeRule.onNodeWithText(title).performClick()
            composeRule.onNodeWithText(strings.skipAutomatically).performClick()
        }

        composeRule.runOnIdle {
            assertEquals(expectedTypes.map { it.second to SegmentSkipMode.AUTO_SKIP }, events)
        }
    }

    private fun action(
        id: String,
        label: String,
    ) = TvPlaybackActionModel(id, TvPlaybackActionKind.SEGMENT_SKIP, label)
}
