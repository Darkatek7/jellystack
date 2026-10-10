package dev.jellystack.design.tv

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import org.junit.Rule
import org.junit.Test

class TvLatestNavEntryProviderTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun routeContentShowsValuesFromTheLatestCompositionWhileTheBackStackStays() {
        var homeLabel by mutableStateOf("loading")
        composeRule.setContent {
            // A plain value captured by the entry, as the TV root passes the home state to the home route.
            val label = homeLabel
            NavDisplay(
                backStack = listOf("home"),
                onBack = {},
                entryProvider = rememberLatestNavEntryProvider { route -> NavEntry(route) { BasicText(label) } },
            )
        }
        composeRule.onNodeWithText("loading").assertExists()

        composeRule.runOnIdle { homeLabel = "loaded" }

        composeRule.onNodeWithText("loaded").assertExists()
    }
}
