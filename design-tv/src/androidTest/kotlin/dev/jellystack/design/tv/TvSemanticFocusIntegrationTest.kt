package dev.jellystack.design.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvSemanticFocusIntegrationTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun stableDescriptorWinsOverLocalizedLegacyFocusCallback() {
        val routeKey = "home"
        val itemId = "item-1"
        val holder = TvAppStateHolder()

        composeRule.setContent {
            val coordinator = remember { TvFocusCoordinator<FocusRequester>() }
            val requester = remember { FocusRequester() }
            CompositionLocalProvider(
                LocalTvFocusContext provides TvFocusContext(coordinator, routeKey, holder.focusMemory),
            ) {
                JellystackTvTheme {
                    TvMediaCard(
                        title = "Movie",
                        imageUrl = null,
                        onClick = {},
                        onFocused = {
                            holder.focusMemory.remember(routeKey, "Weiter ansehen", itemId)
                        },
                        focusTargetId = tvHomeCardTargetId("continue-watching", itemId),
                        providedFocusRequester = requester,
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Movie")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(
                "continue-watching",
                holder.focusMemory
                    .restore(routeKey)
                    ?.anchor
                    ?.sectionId,
            )
        }
    }
}
