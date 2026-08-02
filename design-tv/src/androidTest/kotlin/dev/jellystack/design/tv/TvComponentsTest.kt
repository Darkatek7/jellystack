package dev.jellystack.design.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionButtonSupportsDpadAndTvMinimumTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton("Play", { clicks += 1 })
            }
        }

        composeRule
            .onNodeWithText("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
