package dev.jellystack.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppPlatformCapabilities
import dev.jellystack.core.preferences.ThemeMode
import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricLockState
import dev.jellystack.core.server.JellyfinQuickConnectSession
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ServerType
import dev.jellystack.design.components.InsecureHttpWarningTestTags
import dev.jellystack.design.components.JellyfinSignInMethodSelector
import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.onboarding.OnboardingAction
import dev.jellystack.design.onboarding.OnboardingScreen
import dev.jellystack.design.onboarding.OnboardingUiState
import dev.jellystack.design.onboarding.onboardingProgress
import dev.jellystack.design.onboarding.validateOnboarding
import dev.jellystack.design.settings.SettingsAction
import dev.jellystack.design.settings.SettingsConnectionHealth
import dev.jellystack.design.settings.SettingsConnectionUi
import dev.jellystack.design.settings.SettingsScreen
import dev.jellystack.design.settings.SettingsSection
import dev.jellystack.design.settings.SettingsTestTags
import dev.jellystack.design.settings.SettingsUiState
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.theme.JellystackTheme
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsOnboardingUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun settingsSummaryHidesUrlAndUsesTwoPanesWhenExpanded() {
        composeRule.setContent { expandedSettingsHarness() }

        composeRule.onNodeWithText("Connections").performClick()

        composeRule.onAllNodesWithText("Living Room").onLast().assertExists()
        composeRule.onNodeWithText("https://secret.example").assertDoesNotExist()
        composeRule.onNodeWithText("dummy-token-do-not-render").assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertExists()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertExists()
    }

    @Test
    fun closingSettingsReturnsToRequests() {
        composeRule.setContent { settingsOpenedFromRequestsHarness() }

        composeRule.onNodeWithContentDescription("Close settings").performClick()

        composeRule.onAllNodesWithText("Requests").assertCountEquals(2)
        composeRule.onNodeWithTag(TestTags.PRIMARY_DISCOVER).assertIsSelected()
    }

    @Test
    fun compactSettingsHubOpensPlaybackPreferences() {
        composeRule.setContent { compactSettingsHarness() }

        composeRule.onNodeWithText("Quick settings").assertIsDisplayed()
        composeRule.onNodeWithText("Playback").performClick()
        composeRule.onNodeWithText("Wi-Fi streaming quality").assertIsDisplayed()
        composeRule.onNodeWithText("Mobile streaming quality").assertIsDisplayed()
    }

    @Test
    fun compactSettingsHubOmitsConnectedBannerButKeepsConnections() {
        composeRule.setContent { compactSettingsHarness() }

        composeRule.onNodeWithText("Jellyfin · Connected").assertDoesNotExist()
        composeRule.onNodeWithText("Connections").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutExplainsPermissionsAndPrivacy() {
        composeRule.setContent { compactSettingsHarness() }

        composeRule.onNodeWithText("About").performScrollTo().performClick()

        composeRule.onNodeWithText("Permissions & privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Downloads").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Source code").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsSearchFindsOwningCategory() {
        composeRule.setContent { compactSettingsHarness() }

        composeRule.onNodeWithText("Search settings").performTextInput("subtitle")
        composeRule.onNodeWithText("Audio & subtitles").assertIsDisplayed()
        composeRule.onNodeWithText("Downloads").assertDoesNotExist()
    }

    @Test
    fun settingsCategoryTilesShareRowHeight() {
        composeRule.setContent { compactSettingsHarness() }

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance & language").assertDoesNotExist()
        val appearance =
            composeRule
                .onNodeWithTag(SettingsTestTags.sectionCard(SettingsSection.AppearanceLanguage))
                .fetchSemanticsNode()
                .boundsInRoot
        val downloads =
            composeRule
                .onNodeWithTag(SettingsTestTags.sectionCard(SettingsSection.Downloads))
                .fetchSemanticsNode()
                .boundsInRoot

        assertEquals(appearance.height, downloads.height, 0.5f)
    }

    @Test
    fun jellyfinSignInMethodsHaveEqualWidthAndHeight() {
        var selectorWidth = 0
        var selectorHeight = 0
        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                Box(Modifier.requiredSize(width = 208.dp, height = 80.dp)) {
                    JellyfinSignInMethodSelector(
                        selected = JellyfinSignInMethod.QUICK_CONNECT,
                        onSelected = {},
                        enabled = true,
                        modifier =
                            Modifier.onGloballyPositioned {
                                selectorWidth = it.size.width
                                selectorHeight = it.size.height
                            },
                    )
                }
            }
        }

        val quickConnect =
            composeRule
                .onNode(hasText("Quick Connect") and hasClickAction())
                .fetchSemanticsNode()
                .boundsInRoot
        val password =
            composeRule
                .onNode(hasText("Username & password") and hasClickAction())
                .fetchSemanticsNode()
                .boundsInRoot

        assertEquals(quickConnect.width, password.width, 1.5f)
        assertEquals(quickConnect.height, password.height, 0.5f)
        composeRule.runOnIdle {
            val expectedSelectorWidth =
                208f * composeRule.activity.resources.displayMetrics.density
            assertEquals(expectedSelectorWidth, selectorWidth.toFloat(), 2f)
            val expectedSelectorHeight =
                56f * composeRule.activity.resources.displayMetrics.density
            assertEquals(expectedSelectorHeight, selectorHeight.toFloat(), 2f)
            assertEquals(selectorWidth.toFloat(), quickConnect.width + password.width, 8f)
        }
    }

    @Test
    fun compactSettingsRowsAreInsetFromCardEdge() {
        composeRule.setContent { compactSettingsHarness() }

        val container =
            composeRule
                .onNodeWithTag(SettingsTestTags.COMPACT_CONTAINER)
                .fetchSemanticsNode()
                .boundsInRoot
        val securityRow =
            composeRule
                .onNodeWithTag(SettingsTestTags.compactRow(SettingsSection.Security))
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue("Compact row should be inset from the card edge", securityRow.left > container.left)
    }

    @Test
    fun onboardingShowsProgressAndFieldErrors() {
        composeRule.setContent { onboardingHarness(step = TutorialStep.ConnectJellyfin) }

        composeRule.onNodeWithText("Step 2 of 4").assertExists()
        composeRule
            .onNode(hasText("Connect Jellyfin") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText(
                "Start the address with http:// or https://, " +
                    "for example http://192.168.1.20:8096.",
            ).assertExists()
    }

    @Test
    fun onboardingUsesQuickConnectByDefaultAndKeepsPasswordFallbackVisible() {
        composeRule.setContent { onboardingHarness(step = TutorialStep.ConnectJellyfin) }

        composeRule.onNodeWithText("Quick Connect").assertIsSelected()
        composeRule.onNodeWithText("Username & password").assertIsDisplayed()
        composeRule.onNodeWithText("Username").assertDoesNotExist()
    }

    @Test
    fun onboardingShowsAccessibleHttpWarningCardAndConfirmation() {
        composeRule.setContent {
            onboardingHarness(
                step = TutorialStep.ConnectJellyfin,
                baseUrl = "http://192.168.1.20:8096",
            )
        }

        composeRule
            .onNodeWithTag(InsecureHttpWarningTestTags.CARD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Unencrypted connection").assertIsDisplayed()
        composeRule
            .onNodeWithTag(InsecureHttpWarningTestTags.CONFIRMATION)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                ),
            )
    }

    @Test
    fun onboardingPasswordFieldIsMarkedSensitiveForAccessibility() {
        composeRule.setContent { onboardingHarness(step = TutorialStep.ConnectJellyfin) }

        composeRule.onNodeWithText("Username & password").performClick()
        composeRule
            .onNodeWithText("Password")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun onboardingQuickConnectCodeIsReadableAndCanSwitchToPassword() {
        composeRule.setContent {
            onboardingHarness(
                step = TutorialStep.ConnectJellyfin,
                quickConnectState =
                    JellyfinQuickConnectState.Waiting(
                        JellyfinQuickConnectSession(
                            code = "123456",
                            expiresAt = Instant.fromEpochMilliseconds(600_000),
                        ),
                    ),
            )
        }

        composeRule.onNodeWithText("123456").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for approval…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Use username & password").performScrollTo().performClick()
        composeRule.onNodeWithText("Username").assertIsDisplayed()
    }

    @Test
    fun readyStageShowsStartExploring() {
        composeRule.setContent { onboardingHarness(step = TutorialStep.Explore) }

        composeRule.onNodeWithText("Start exploring").performScrollTo().assertIsEnabled()
    }

    @Test
    fun germanLargeTextKeepsFinalActionScrollable() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            composeRule.setContent {
                onboardingHarness(
                    step = TutorialStep.Explore,
                    fontScale = 2f,
                )
            }

            composeRule
                .onNode(hasText("Jetzt entdecken") and hasClickAction())
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}

@Composable
private fun expandedSettingsHarness() {
    var state by remember { mutableStateOf(settingsState()) }
    JellystackTheme(isDarkTheme = false) {
        Box(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
            SettingsScreen(
                state = state,
                onAction = { action ->
                    if (action is SettingsAction.SelectSection) {
                        state = state.copy(selectedSection = action.section)
                    }
                },
            )
        }
    }
}

@Composable
private fun settingsOpenedFromRequestsHarness() {
    var settingsOpen by remember { mutableStateOf(true) }
    JellystackTheme(isDarkTheme = false) {
        Box(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
            JellystackShell(
                state =
                    JellystackShellState(
                        primary = PrimaryDestination.Discover,
                        discover = DiscoverDestination.Requests,
                    ),
                onAction = {},
                topBar = { Text("Requests") },
                primaryContent = { Text("Requests") },
            )
            if (settingsOpen) {
                SettingsScreen(
                    state = settingsState().copy(selectedSection = null),
                    onAction = { action ->
                        if (action == SettingsAction.Close) settingsOpen = false
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun compactSettingsHarness() {
    var state by remember { mutableStateOf(settingsState().copy(selectedSection = null)) }
    JellystackTheme(isDarkTheme = false) {
        Box(Modifier.requiredSize(width = 411.dp, height = 891.dp)) {
            SettingsScreen(
                state = state,
                onAction = { action ->
                    if (action is SettingsAction.SelectSection) state = state.copy(selectedSection = action.section)
                },
            )
        }
    }
}

private fun settingsState(): SettingsUiState =
    SettingsUiState(
        selectedSection = SettingsSection.AppearanceLanguage,
        themeMode = ThemeMode.SYSTEM,
        platformCapabilities = AppPlatformCapabilities.Android,
        appLockEnabled = false,
        appLockState = BiometricLockState.Disabled,
        appLockCapability =
            BiometricCapability(
                status = BiometricCapability.Status.AVAILABLE,
                secureCredentialAvailable = true,
            ),
        connections =
            listOf(
                SettingsConnectionUi(
                    id = "server-1",
                    type = ServerType.JELLYFIN,
                    name = "Living Room",
                    isActive = true,
                    health = SettingsConnectionHealth.Ready,
                ),
            ),
        appVersion = "0.14.3",
    )

@Composable
private fun onboardingHarness(
    step: TutorialStep,
    fontScale: Float = 1f,
    quickConnectState: JellyfinQuickConnectState? = null,
    baseUrl: String? = null,
) {
    val baseConfiguration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val configuration =
        remember(baseConfiguration, fontScale) {
            Configuration(baseConfiguration).apply {
                this.fontScale = fontScale
            }
        }
    var state by
        remember(step) {
            mutableStateOf(
                OnboardingUiState(
                    step = step,
                    progress = onboardingProgress(step),
                    form =
                        ServerFormState(
                            type =
                                if (step == TutorialStep.ConnectJellyseerr) {
                                    ServerFormType.SEERR
                                } else {
                                    ServerFormType.JELLYFIN
                                },
                            baseUrl =
                                baseUrl
                                    ?: if (step == TutorialStep.ConnectJellyfin) {
                                        "not-a-url"
                                    } else {
                                        ""
                                    },
                        ),
                    fieldErrors = emptyMap(),
                    manualSeerrCredentialsRequired = false,
                    isSaving = false,
                    serviceErrorDetail = null,
                    canStartExploring = true,
                    quickConnectState = quickConnectState,
                ),
            )
        }

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalDensity provides Density(baseDensity.density, fontScale),
    ) {
        JellystackTheme(isDarkTheme = false) {
            Box(Modifier.requiredSize(width = 411.dp, height = 480.dp)) {
                OnboardingScreen(
                    state = state,
                    onAction = { action ->
                        state =
                            when (action) {
                                is OnboardingAction.FormChanged ->
                                    state.copy(form = action.form, fieldErrors = emptyMap())
                                is OnboardingAction.SignInMethodChanged ->
                                    state.copy(
                                        form =
                                            state.form.copy(
                                                jellyfinSignInMethod = action.method,
                                            ),
                                        quickConnectState = null,
                                        isSaving = false,
                                    )
                                OnboardingAction.RestartQuickConnect -> state
                                OnboardingAction.CancelQuickConnect ->
                                    state.copy(quickConnectState = null, isSaving = false)
                                OnboardingAction.Continue ->
                                    state.copy(
                                        fieldErrors =
                                            validateOnboarding(
                                                step = state.step,
                                                form = state.form,
                                                manualSeerrCredentialsRequired =
                                                    state.manualSeerrCredentialsRequired,
                                            ),
                                    )
                                OnboardingAction.Back,
                                OnboardingAction.SkipSeerr,
                                OnboardingAction.StartExploring,
                                -> state
                            }
                    },
                )
            }
        }
    }
}
