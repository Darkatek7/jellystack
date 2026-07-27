package dev.jellystack.design

import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.design.components.ModalFocusScope
import dev.jellystack.design.jellyseerr.RequestConfiguration
import dev.jellystack.design.preview.JellystackPreviewFixture
import dev.jellystack.design.theme.JellystackTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class JellystackAccessibilityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun everyClickableNodeInProductionFixtureIsAtLeast48Dp() {
        composeRule.setContent {
            JellystackPreviewFixture(fixtureName = "requests", darkTheme = false)
        }
        composeRule.waitForIdle()
        val minimumPx = with(composeRule.density) { 48.dp.toPx() }

        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                assertTrue(
                    "Clickable node ${node.config} width was ${node.boundsInRoot.width}px",
                    node.boundsInRoot.width >= minimumPx,
                )
                assertTrue(
                    "Clickable node ${node.config} height was ${node.boundsInRoot.height}px",
                    node.boundsInRoot.height >= minimumPx,
                )
            }
    }

    @Test
    fun modalFocusEntersModalAndReturnsToInvoker() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                RequestModalFocusHarness()
            }
        }

        composeRule.onNodeWithText("Configure request").performClick()
        waitUntilFocused("Server default")
        composeRule.onNodeWithText("Server default").assertIsFocused()
        composeRule.onNodeWithText("Close").performClick()
        waitUntilFocused("Configure request")
        composeRule.onNodeWithText("Configure request").assertIsFocused()
    }

    @Test
    fun requestActionRemainsReachableAtTwoHundredPercentInEnglish() {
        assertRequestActionReachable(Locale.ENGLISH, "Submit request")
    }

    @Test
    fun requestActionRemainsReachableAtTwoHundredPercentInGerman() {
        assertRequestActionReachable(Locale.GERMAN, "Anfrage senden")
    }

    private fun assertRequestActionReachable(
        locale: Locale,
        action: String,
    ) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            composeRule.setContent {
                WithFontScale(2f) {
                    JellystackPreviewFixture(fixtureName = "requests", darkTheme = false)
                }
            }
            composeRule.onNodeWithText(action).performScrollTo()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun waitUntilFocused(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule
                .onAllNodes(hasText(text) and isFocused())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}

@Composable
private fun RequestModalFocusHarness() {
    var open by remember { mutableStateOf(false) }
    val invokerFocus = remember { FocusRequester() }
    Button(
        onClick = { open = true },
        modifier = Modifier.focusRequester(invokerFocus).focusable(),
    ) {
        Text("Configure request")
    }
    if (open) {
        ModalFocusScope(
            onDismissRequest = { open = false },
            returnFocusRequester = invokerFocus,
        ) { initialFocusModifier ->
            RequestConfigurationFixture(
                initialFocusModifier = initialFocusModifier,
                onClose = { open = false },
            )
        }
    }
}

@Composable
private fun RequestConfigurationFixture(
    initialFocusModifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    RequestConfiguration(
        item = accessibilityItem(),
        profiles = emptyList(),
        availableSeasons = listOf(1, 2),
        selected = JellyseerrRequestProfileSelection.ServerDefault,
        seasonSelection = JellyseerrCreateSelection.AllSeasons,
        initialFocusModifier = initialFocusModifier,
        modifier = Modifier.fillMaxSize(),
        onSelect = {},
        onSelectSeasons = {},
        onSubmit = {},
        onClose = onClose,
    )
}

@Composable
private fun WithFontScale(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val baseConfiguration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val configuration = remember(baseConfiguration, fontScale) { Configuration(baseConfiguration).apply { this.fontScale = fontScale } }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalDensity provides Density(baseDensity.density, fontScale),
        content = content,
    )
}

private fun accessibilityItem(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = 1,
        mediaType = JellyseerrMediaType.TV,
        title = "Accessibility fixture",
        overview = null,
        releaseYear = null,
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )
