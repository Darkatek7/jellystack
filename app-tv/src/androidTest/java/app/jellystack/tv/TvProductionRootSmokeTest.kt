package app.jellystack.tv

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvProductionRootSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldLaunchAndRecreationKeepProductionRootFocusable() {
        composeRule.onNodeWithText("Connect Jellyfin").assertExists()
        composeRule.onNodeWithContentDescription("Quick Connect").assertIsFocused()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Connect Jellyfin").assertExists()
        composeRule.onNodeWithContentDescription("Quick Connect").assertIsFocused()
    }
}
