package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvBackDispatcherIntegrationTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun navigation3BackUsesFolderBeforePreviousEntryThenClosesTopLevelRail() {
        val holder = TvAppStateHolder().apply { push(TvRoute.Library("shows")) }
        val pathDepth = mutableIntStateOf(1)
        val systemExits = mutableIntStateOf(0)
        composeRule.setContent { BackHarness(holder, pathDepth, systemExits) }

        pressBack()
        composeRule.runOnIdle {
            assertEquals(0, pathDepth.intValue)
            assertEquals(2, holder.state.backStack.size)
        }
        pressBack()
        composeRule.runOnIdle { assertEquals(listOf(TvRoute.Home), holder.state.backStack) }

        composeRule.runOnIdle { holder.openRail() }
        pressBack()
        composeRule.runOnIdle {
            assertFalse(holder.state.railExpanded)
            assertEquals(0, systemExits.intValue)
        }
    }

    @Test
    fun topLevelBackRequestsExitConfirmationBeforeReachingSystemOwner() {
        val holder = TvAppStateHolder()
        val systemExits = mutableIntStateOf(0)
        val exitRequests = mutableIntStateOf(0)
        composeRule.setContent { BackHarness(holder, mutableIntStateOf(0), systemExits, exitRequests = exitRequests) }

        pressBack()

        composeRule.runOnIdle {
            assertEquals(listOf(TvRoute.Home), holder.state.backStack)
            assertEquals(0, systemExits.intValue)
            assertEquals(1, exitRequests.intValue)
            assertFalse(holder.state.railExpanded)
        }
    }

    @Test
    fun discoverBackReturnsHomeBeforeRequestingExitConfirmation() {
        val holder = TvAppStateHolder().apply { selectTopLevel(TvRoute.Discover) }
        val systemExits = mutableIntStateOf(0)
        val exitRequests = mutableIntStateOf(0)
        composeRule.setContent { BackHarness(holder, mutableIntStateOf(0), systemExits, exitRequests = exitRequests) }

        pressBack()
        composeRule.runOnIdle {
            assertEquals(listOf(TvRoute.Home), holder.state.backStack)
            assertEquals(0, exitRequests.intValue)
            assertEquals(0, systemExits.intValue)
        }

        pressBack()
        composeRule.runOnIdle {
            assertEquals(1, exitRequests.intValue)
            assertEquals(0, systemExits.intValue)
        }
    }

    @Test
    fun playerLocalSystemBackOwnsTheEventBeforeNavigation3() {
        val holder = TvAppStateHolder().apply { push(TvRoute.Player) }
        val systemExits = mutableIntStateOf(0)
        val playerBacks = mutableIntStateOf(0)
        composeRule.setContent { BackHarness(holder, mutableIntStateOf(0), systemExits, playerBacks) }

        pressBack()

        composeRule.runOnIdle {
            assertEquals(1, playerBacks.intValue)
            assertEquals(TvRoute.Player, holder.state.currentRoute)
            assertEquals(0, systemExits.intValue)
        }
    }

    @Test
    fun exitConfirmationDefaultsToCancelAndRequiresExplicitConfirmation() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val confirmations = mutableIntStateOf(0)
        composeRule.setContent {
            JellystackTvTheme {
                TvExitConfirmationDialog(
                    strings = strings,
                    onConfirm = { confirmations.intValue += 1 },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(strings.cancel).assertIsFocused()
        composeRule.onNodeWithContentDescription(strings.exitApp).performClick()
        composeRule.runOnIdle { assertEquals(1, confirmations.intValue) }
    }

    @Composable
    private fun BackHarness(
        holder: TvAppStateHolder,
        pathDepth: MutableState<Int>,
        systemExits: MutableState<Int>,
        playerBacks: MutableState<Int>? = null,
        exitRequests: MutableState<Int>? = null,
    ) {
        BackHandler { systemExits.value += 1 }
        val dispatcher =
            TvAppBackDispatcher(
                holder = holder,
                libraryPathDepth = { pathDepth.value },
                selectedLibraryId = { "shows" },
                popLibraryPath = { pathDepth.value -= 1 },
                cancelFocusRestoration = {},
            )
        TvAppBackHandler(dispatcher) { exitRequests?.value = (exitRequests?.value ?: 0) + 1 }
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = holder.state.backStack,
                onBack = { dispatcher.dispatch() },
                entryProvider = { route ->
                    NavEntry(route) {
                        if (route == TvRoute.Player && playerBacks != null) {
                            TvPlayerBackHandler { playerBacks.value += 1 }
                        }
                    }
                },
            )
        }
    }
}
