package dev.jellystack.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.navigation.BackStackSnapshot
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.navigation.ShellBackAction
import dev.jellystack.design.navigation.nextBackAction
import dev.jellystack.design.navigation.rememberDestinationChangeDispatcher
import dev.jellystack.design.preview.JellystackPreviewFixture
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellAction
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellystackShellUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun compactShellShowsThreeLabeledDockDestinations() {
        composeRule.setContent {
            JellystackPreviewFixture(
                fixtureName = "home",
                modifier = Modifier.requiredSize(width = 411.dp, height = 891.dp),
            )
        }

        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Discover").assertExists()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_DOCK).assertExists()
    }

    @Test
    fun expandedShellUsesRailAndTwoPanes() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
                    JellystackShell(
                        state = JellystackShellState(paneMode = ShellPaneMode.ListDetail),
                        onAction = {},
                        primaryContent = {
                            Box(Modifier.fillMaxSize().testTag("expanded-primary-content"))
                        },
                        secondaryContent = {
                            Box(Modifier.fillMaxSize().testTag("expanded-secondary-content"))
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.NAVIGATION_RAIL).assertExists()
        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertExists()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertExists()
    }

    @Test
    fun compactDetailReplacesPrimaryPane() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
                    JellystackShell(
                        state = JellystackShellState(paneMode = ShellPaneMode.ListDetail),
                        onAction = {},
                        primaryContent = {
                            Box(Modifier.fillMaxSize().testTag("compact-primary-content"))
                        },
                        secondaryContent = {
                            Box(Modifier.fillMaxSize().testTag("compact-secondary-content"))
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("compact-primary-content").assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertDoesNotExist()
        composeRule.onNodeWithTag("compact-secondary-content").assertExists()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertExists()
    }

    @Test
    fun expandedSinglePaneIgnoresSecondaryContent() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
                    JellystackShell(
                        state = JellystackShellState(paneMode = ShellPaneMode.Single),
                        onAction = {},
                        primaryContent = {
                            Box(Modifier.fillMaxSize().testTag("single-primary-content"))
                        },
                        secondaryContent = {
                            Box(Modifier.fillMaxSize().testTag("single-secondary-content"))
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("single-primary-content").assertExists()
        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertExists()
        composeRule.onNodeWithTag("single-secondary-content").assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertDoesNotExist()
    }

    @Test
    fun expandedListDetailShowsLocalizedPromptWithoutSelection() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
                    JellystackShell(
                        state = JellystackShellState(paneMode = ShellPaneMode.ListDetail),
                        onAction = {},
                        primaryContent = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Select an item to view details").assertExists()
        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertExists()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertExists()
    }

    @Test
    fun shortDockDestinationsKeepAccessibleTouchTargets() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 411.dp, height = 479.dp)) {
                    JellystackShell(
                        state = JellystackShellState(),
                        onAction = {},
                        primaryContent = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Home")
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription("Library")
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription("Discover")
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun selectingDockDestinationDispatchesTypedAction() {
        val actions = mutableListOf<JellystackShellAction>()
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
                    JellystackShell(
                        state = JellystackShellState(),
                        onAction = actions::add,
                        primaryContent = { Box(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Library").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(JellystackShellAction.SelectPrimary(PrimaryDestination.Library)),
                actions,
            )
        }
    }

    @Test
    fun destinationChangeClearsFocusedSearch() {
        composeRule.setContent { navigationFocusHarness() }
        composeRule.onNodeWithTag("search").performClick().performTextInput("Dune")
        composeRule.onNodeWithTag("search").assertIsFocused()

        composeRule.onNodeWithText("Home").performClick()

        composeRule.onNodeWithTag("search", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun nestedDestinationCallbacksClearFocusedSearch() {
        composeRule.setContent { nestedDestinationFocusHarness() }

        listOf("library-child", "nested-detail", "onboarding-forward").forEach { destinationTag ->
            composeRule.onNodeWithTag("nested-search").performClick()
            composeRule.onNodeWithTag("nested-search").assertIsFocused()
            composeRule.onNodeWithTag(destinationTag).performClick()
            composeRule.onNodeWithTag("nested-search", useUnmergedTree = true).assertIsNotFocused()
        }

        composeRule.onNodeWithText("onboarding").assertExists()
    }

    @Test
    fun switchingPrimaryClearsDetailAndBackIgnoresHiddenLibraryDepth() {
        composeRule.setContent { crossPrimaryDetailHarness() }
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Dune").performClick()
        composeRule.onNodeWithText("Play").assertExists()

        composeRule.onNodeWithText("Discover").performClick()
        composeRule.onNodeWithText("Play").assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Home").assertIsSelected()
    }
}

@Composable
private fun navigationFocusHarness() {
    var primary by remember { mutableStateOf(PrimaryDestination.Library) }
    var query by remember { mutableStateOf("") }
    val dispatcher = rememberDestinationChangeDispatcher()

    JellystackTheme(isDarkTheme = true) {
        ProvideResponsiveProfile(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
            JellystackShell(
                state = JellystackShellState(primary = primary),
                onAction = { action ->
                    if (action is JellystackShellAction.SelectPrimary) {
                        dispatcher.dispatch { primary = action.destination }
                    }
                },
                primaryContent = { contentPadding ->
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.padding(contentPadding).testTag("search"),
                    )
                },
            )
        }
    }
}

@Composable
private fun nestedDestinationFocusHarness() {
    var query by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("root") }
    val dispatcher = rememberDestinationChangeDispatcher()
    val libraryChange = dispatcher.callback<String> { destination = it }
    val nestedDetail = dispatcher.action { destination = "detail" }
    val onboardingForward = dispatcher.action { destination = "onboarding" }

    Column {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.testTag("nested-search"),
        )
        Text("Library child", Modifier.testTag("library-child").clickable { libraryChange("library") })
        Text("Nested detail", Modifier.testTag("nested-detail").clickable(onClick = nestedDetail))
        Text("Onboarding forward", Modifier.testTag("onboarding-forward").clickable(onClick = onboardingForward))
        Text(destination)
    }
}

@Composable
private fun crossPrimaryDetailHarness() {
    var primary by remember { mutableStateOf(PrimaryDestination.Home) }
    var detailVisible by remember { mutableStateOf(false) }
    var hiddenLibraryDepth by remember { mutableStateOf(0) }
    val dispatcher = rememberDestinationChangeDispatcher()
    val backAction =
        nextBackAction(
            BackStackSnapshot(
                primary = primary,
                libraryDepth = hiddenLibraryDepth,
                detailDepth = if (detailVisible) 1 else 0,
            ),
        )

    fun selectPrimary(destination: PrimaryDestination) {
        dispatcher.dispatch {
            if (destination != primary) detailVisible = false
            primary = destination
            if (destination == PrimaryDestination.Library) hiddenLibraryDepth = 1
        }
    }

    fun dispatchBack() {
        dispatcher.dispatch {
            when (backAction) {
                ShellBackAction.PopDetail -> detailVisible = false
                ShellBackAction.PopLibrary -> hiddenLibraryDepth = 0
                ShellBackAction.SelectHome -> primary = PrimaryDestination.Home
                else -> Unit
            }
        }
    }

    platformBackHandler(enabled = backAction != ShellBackAction.ExitPlatform) { dispatchBack() }

    JellystackTheme(isDarkTheme = true) {
        ProvideResponsiveProfile(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
            JellystackShell(
                state =
                    JellystackShellState(
                        primary = primary,
                        paneMode = if (detailVisible) ShellPaneMode.ListDetail else ShellPaneMode.Single,
                    ),
                onAction = { action ->
                    if (action is JellystackShellAction.SelectPrimary) {
                        selectPrimary(action.destination)
                    }
                },
                primaryContent = { contentPadding ->
                    Box(Modifier.fillMaxSize().padding(contentPadding)) {
                        if (primary == PrimaryDestination.Library) {
                            Text(
                                "Dune",
                                modifier = Modifier.clickable { detailVisible = true }.padding(24.dp),
                            )
                        }
                    }
                },
                secondaryContent =
                    if (detailVisible) {
                        { contentPadding ->
                            Box(Modifier.fillMaxSize().padding(contentPadding)) { Text("Play") }
                        }
                    } else {
                        null
                    },
            )
        }
    }
}
