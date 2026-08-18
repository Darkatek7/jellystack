package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvNavDisplayFocusScopeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transitionOverlapKeepsOutgoingAndIncomingFocusRegistrationsRouteScoped() {
        val backStack = mutableStateListOf<TvRoute>(TvRoute.Home)
        composeRule.setContent { OverlappingRoutes(backStack) }
        composeRule.onNodeWithTag("home-entry").assertIsFocused()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { backStack += TvRoute.Search }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        composeRule.onAllNodes(hasTestTag("home-entry")).assertCountEquals(1)
        composeRule.onAllNodes(hasTestTag("search-entry")).assertCountEquals(1)
        composeRule.onNodeWithTag("search-entry").assertIsFocused()
    }

    @Composable
    private fun OverlappingRoutes(backStack: MutableList<TvRoute>) {
        val coordinator =
            remember {
                TvFocusCoordinator<FocusRequester>(
                    awaitFocusFrame = { withFrameNanos { } },
                )
            }
        val currentRoute = backStack.last()
        LaunchedEffect(currentRoute) {
            coordinator.restoreFocus(
                routeKey = currentRoute.focusRouteKey(),
                preferredTargetId = SHARED_ENTRY_TARGET,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        }
        JellystackTvTheme {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { route ->
                        NavEntry(route) {
                            TvRouteFocusScope(
                                focusCoordinator = coordinator,
                                routeKey = route.focusRouteKey(),
                            ) {
                                when (route) {
                                    TvRoute.Home ->
                                        TvActionButton(
                                            label = "Home entry",
                                            onClick = {},
                                            modifier =
                                                Modifier
                                                    .tvScreenEntryFocus(
                                                        focusTargetId = SHARED_ENTRY_TARGET,
                                                    ).testTag("home-entry"),
                                            focusTargetId = SHARED_ENTRY_TARGET,
                                        )
                                    TvRoute.Search ->
                                        TvActionButton(
                                            label = "Search entry",
                                            onClick = {},
                                            modifier =
                                                Modifier
                                                    .tvScreenEntryFocus(
                                                        focusTargetId = SHARED_ENTRY_TARGET,
                                                    ).testTag("search-entry"),
                                            focusTargetId = SHARED_ENTRY_TARGET,
                                        )
                                    else -> Unit
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val SHARED_ENTRY_TARGET = "route-entry:test-control"
    }
}
