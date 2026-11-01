package dev.jellystack.design.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvRowFocusKeeperTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun focusedCardMovedToTheFrontStaysOnScreen() {
        var itemIds by mutableStateOf((0 until 20).map { "item-$it" })
        lateinit var listState: LazyListState
        val requester = FocusRequester()
        composeRule.setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 8)
            val keeper = rememberTvRowFocusKeeper(listState, itemIds)
            LazyRow(state = listState, modifier = Modifier.width(600.dp)) {
                items(itemIds, key = { it }) { id ->
                    Box(
                        Modifier
                            .size(200.dp)
                            .testTag(id)
                            .then(if (id == FOCUSED_ITEM) Modifier.focusRequester(requester) else Modifier)
                            .onFocusChanged { if (it.isFocused) keeper.onFocused(id) else keeper.onBlurred(id) }
                            .focusable(),
                    )
                }
            }
        }
        composeRule.runOnIdle { requester.requestFocus() }
        composeRule.waitForIdle()
        // A card that is already on screen must not move the row.
        composeRule.runOnIdle { assertEquals(8, listState.firstVisibleItemIndex) }

        composeRule.runOnIdle { itemIds = listOf(FOCUSED_ITEM) + (itemIds - FOCUSED_ITEM) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FOCUSED_ITEM).assertIsDisplayed().assertIsFocused()
    }

    private companion object {
        const val FOCUSED_ITEM = "item-9"
    }
}
