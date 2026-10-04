package app.jellystack.tv

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvProductionRootSmokeTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** TV remotes do not use touch mode; enter key input mode before the activity launches. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DpadInputModeRule()).around(composeRule)

    @Test
    fun coldLaunchAndRecreationKeepProductionRootFocusable() {
        composeRule.onNodeWithText("Connect Jellyfin").assertExists()
        composeRule.onNodeWithContentDescription("Quick Connect").assertIsFocused()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Connect Jellyfin").assertExists()
        composeRule.onNodeWithContentDescription("Quick Connect").assertIsFocused()
    }
}

private class DpadInputModeRule : ExternalResource() {
    @Suppress("DEPRECATION")
    override fun before() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }
}
