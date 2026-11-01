package dev.jellystack.design.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** Which card of a row has focus. Only the keeper's effect reads it, so a focus move does not recompose the row. */
internal class TvRowFocusKeeper {
    private var focusedItemId by mutableStateOf<String?>(null)

    fun onFocused(itemId: String) {
        focusedItemId = itemId
    }

    fun onBlurred(itemId: String) {
        if (focusedItemId == itemId) focusedItemId = null
    }

    fun focusedIndexIn(itemIds: List<String>): Int? = focusedItemId?.let(itemIds::indexOf)?.takeIf { it >= 0 }
}

/**
 * Keeps the focused card on screen when the row's items change around it. A lazy list keeps focus on its item but its
 * scroll position on the first visible one, so a card moved to the front, as the one just played is in Continue
 * Watching, would hold focus out of view, whether it moves before or after focus returns to it.
 */
@Composable
internal fun rememberTvRowFocusKeeper(
    listState: LazyListState,
    itemIds: List<String>,
): TvRowFocusKeeper {
    val keeper = remember { TvRowFocusKeeper() }
    val currentItemIds by rememberUpdatedState(itemIds)
    LaunchedEffect(listState, keeper) {
        snapshotFlow { keeper.focusedIndexIn(currentItemIds) }.collectLatest { index ->
            if (index == null) return@collectLatest
            // One frame to lay out a new order, one for a focus move to start scrolling its card into view. That
            // scroll has to finish first: it is what normally brings the card on screen.
            repeat(2) { withFrameNanos {} }
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        }
    }
    return keeper
}
