package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvBackDispatcherIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun topLevelBackRemainsUnconsumedForSystemOwner() {
        val holder = TvAppStateHolder()
        val systemExits = mutableIntStateOf(0)
        composeRule.setContent { BackHarness(holder, mutableIntStateOf(0), systemExits) }

        pressBack()

        composeRule.runOnIdle {
            assertEquals(listOf(TvRoute.Home), holder.state.backStack)
            assertEquals(1, systemExits.intValue)
            assertFalse(holder.state.railExpanded)
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

    @Composable
    private fun BackHarness(
        holder: TvAppStateHolder,
        pathDepth: MutableState<Int>,
        systemExits: MutableState<Int>,
        playerBacks: MutableState<Int>? = null,
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
        TvAppBackHandler(dispatcher)
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
