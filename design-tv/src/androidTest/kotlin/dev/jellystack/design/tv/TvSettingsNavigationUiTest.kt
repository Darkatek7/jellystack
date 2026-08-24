package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.server.ServerType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvSettingsNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun landingShowsEveryCategoryAndDispatchesTheSelectedRoute() {
        var selected: TvSettingsCategory? = null
        composeRule.setContent {
            JellystackTvTheme {
                TvSettingsLandingScreen(
                    strings = strings,
                    onOpenCategory = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText(strings.settingsAppearance).assertExists()
        composeRule.onNodeWithText(strings.settingsPlaybackCategory).assertExists()
        composeRule.onNodeWithText(strings.settingsAudioSubtitles).assertExists()
        composeRule.onNodeWithText(strings.segmentSkipping).assertExists()
        composeRule.onNodeWithText(strings.connections).assertExists()
        composeRule.onNodeWithText(strings.settingsPlaybackCategory).performClick()
        composeRule.runOnIdle { assertEquals(TvSettingsCategory.PLAYBACK, selected) }
    }

    @Test
    fun removeDialogMarksRemoveDestructiveAndFocusesCancelByDefault() {
        composeRule.setContent {
            JellystackTvTheme {
                TvRemoveServerDialog(
                    serverName = "Media server",
                    strings = strings,
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(strings.cancel).assertIsFocused()
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    TvDestructiveActionKey,
                    true,
                ),
            ).assertExists()
            .assertIsNotFocused()
    }

    @Test
    fun removingFocusedServerRestoresAnAttachedManageControl() {
        var removeServer: () -> Unit = {}
        composeRule.setContent {
            JellystackTvTheme {
                ConnectionsFocusRecoveryHarness { removeServer = it }
            }
        }

        composeRule.onNodeWithContentDescription("Remove test server").assertIsFocused()
        composeRule.runOnIdle(removeServer)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Manage Jellyfin").assertIsFocused()
    }

    @Test
    fun categoryNavigationRestoresLandingTileAndExactCategoryControl() {
        var navigateBack: () -> Unit = {}
        composeRule.setContent {
            SettingsRouteHarness(onNavigateBack = { navigateBack = it })
        }

        val appearance =
            composeRule.onNode(
                hasClickAction() and hasAnyDescendant(hasText(strings.settingsAppearance)),
                useUnmergedTree = true,
            )
        val playback =
            composeRule.onNode(
                hasClickAction() and hasAnyDescendant(hasText(strings.settingsPlaybackCategory)),
                useUnmergedTree = true,
            )
        appearance.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        playback.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }

        val quality =
            composeRule.onNode(
                hasClickAction() and hasAnyDescendant(hasText(strings.quality)),
                useUnmergedTree = true,
            )
        val autoplay =
            composeRule.onNode(
                hasClickAction() and hasAnyDescendant(hasText(strings.autoplay)),
                useUnmergedTree = true,
            )
        quality.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        autoplay.assertIsFocused()

        composeRule.runOnIdle(navigateBack)
        playback.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        autoplay.assertIsFocused()
    }

    @Test
    fun directConnectionsEntryFocusesItsAttachedFallback() {
        composeRule.setContent {
            SettingsRouteHarness(initialCategory = TvSettingsCategory.CONNECTIONS)
        }

        composeRule.onNodeWithContentDescription("Manage Jellyfin").assertIsFocused()
    }

    @Composable
    private fun ConnectionsFocusRecoveryHarness(onRemoveReady: (() -> Unit) -> Unit) {
        val routeKey = tvSettingsRoute(TvSettingsCategory.CONNECTIONS).focusRouteKey()
        val coordinator =
            remember {
                TvFocusCoordinator<FocusRequester>(awaitFocusFrame = { withFrameNanos { } })
            }
        val removeRequester = remember { FocusRequester() }
        var serverVisible by remember { mutableStateOf(true) }
        var recoveryRequest by remember { mutableStateOf<TvConnectionsFocusRecoveryRequest?>(null) }
        val remove = {
            serverVisible = false
            recoveryRequest = TvConnectionsFocusRecoveryRequest(1L, ServerType.JELLYFIN)
        }
        onRemoveReady(remove)
        TvRouteFocusScope(coordinator, routeKey) {
            TvConnectionsFocusRecovery(
                request = recoveryRequest,
                manageJellyfinTarget = "manage-jellyfin",
                manageSeerrTarget = "manage-seerr",
            )
            Column {
                if (serverVisible) {
                    TvActionButton(
                        label = "Remove test server",
                        onClick = remove,
                        focusTargetId = "remove-server",
                        focusRequester = removeRequester,
                    )
                }
                TvActionButton(
                    label = "Manage Jellyfin",
                    onClick = {},
                    focusTargetId = "manage-jellyfin",
                )
                TvActionButton(
                    label = "Manage Seerr",
                    onClick = {},
                    focusTargetId = "manage-seerr",
                )
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            removeRequester.requestFocus()
        }
    }

    @Composable
    private fun SettingsRouteHarness(
        initialCategory: TvSettingsCategory? = null,
        onNavigateBack: ((() -> Unit) -> Unit) = {},
    ) {
        val coordinator =
            remember {
                TvFocusCoordinator<FocusRequester>(awaitFocusFrame = { withFrameNanos { } })
            }
        val backStack =
            remember {
                mutableStateListOf<TvRoute>(TvRoute.Settings()).apply {
                    initialCategory?.let { add(tvSettingsRoute(it)) }
                }
            }
        val currentRoute = backStack.last()
        val navigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
        onNavigateBack(navigateBack)
        LaunchedEffect(currentRoute) {
            coordinator.restoreFocus(
                routeKey = currentRoute.focusRouteKey(),
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        }
        NavDisplay(
            backStack = backStack,
            onBack = navigateBack,
            entryProvider = { route ->
                NavEntry(route) {
                    TvRouteFocusScope(coordinator, route.focusRouteKey()) {
                        val category = (route as? TvRoute.Settings)?.section?.let(TvSettingsCategory::fromRouteSection)
                        when (category) {
                            null ->
                                TvSettingsLandingScreen(
                                    strings = strings,
                                    onOpenCategory = { backStack.add(tvSettingsRoute(it)) },
                                )
                            TvSettingsCategory.PLAYBACK ->
                                TvSettingsCategoryPage(
                                    category = category,
                                    title = strings.settingsPlaybackCategory,
                                    targetIds = tvSettingsControlKeys(category).map(::tvSettingsControlTargetId),
                                ) {
                                    TvSettingsGrid {
                                        TvSettingTile(
                                            title = strings.quality,
                                            value = "Auto",
                                            screenEntry = true,
                                            focusTargetId = tvSettingsControlTargetId("quality"),
                                            onClick = {},
                                        )
                                        TvSettingTile(
                                            title = strings.autoplay,
                                            value = strings.on,
                                            focusTargetId = tvSettingsControlTargetId("autoplay"),
                                            onClick = {},
                                        )
                                        TvSettingTile(
                                            title = strings.resume,
                                            value = strings.on,
                                            focusTargetId = tvSettingsControlTargetId("resume"),
                                            onClick = {},
                                        )
                                    }
                                }
                            TvSettingsCategory.CONNECTIONS ->
                                TvSettingsCategoryPage(
                                    category = category,
                                    title = strings.connections,
                                    targetIds = listOf("manage-jellyfin", "manage-seerr"),
                                ) {
                                    TvActionButton(
                                        label = "Manage Jellyfin",
                                        onClick = {},
                                        modifier =
                                            androidx.compose.ui.Modifier.tvScreenEntryFocus(
                                                focusTargetId = "manage-jellyfin",
                                            ),
                                        focusTargetId = "manage-jellyfin",
                                    )
                                    TvActionButton(
                                        label = "Manage Seerr",
                                        onClick = {},
                                        focusTargetId = "manage-seerr",
                                    )
                                }
                            else -> Unit
                        }
                    }
                }
            },
        )
    }
}
