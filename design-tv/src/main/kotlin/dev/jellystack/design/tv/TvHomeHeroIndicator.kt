@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal const val TV_HOME_HERO_INDICATOR_TAG = "tv-home-hero-indicator"

/** Zero-based [index] of the shown spotlight among [count]. */
internal data class TvHomeHeroPage(
    val index: Int,
    val count: Int,
)

/**
 * Page dots of the home spotlight. Focusable above the hero actions: Left and Right page through the
 * spotlight, Down returns to Play. The stage around it stays non-focusable.
 */
@Composable
internal fun TvHomeHeroIndicator(
    page: TvHomeHeroPage,
    contentDescription: String,
    onMove: (TvHomeCarouselDirection) -> Unit,
    onVerticalMove: (TvHomeVerticalDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(TV_HOME_HERO_INDICATOR_TAG)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .onPreviewKeyEvent { event ->
                val isPress = event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isPress) onMove(TvHomeCarouselDirection.PREVIOUS)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isPress) onMove(TvHomeCarouselDirection.NEXT)
                        true
                    }
                    else -> false
                }
            }.tvHomeVerticalFocus(onVerticalMove)
            .tvFocusable(onClick = null, shape = TvShapes.Pill, focusTargetId = TV_HOME_HERO_TARGET)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(page.count) { index ->
            val selected = index == page.index
            Box(
                Modifier
                    .size(width = if (selected) 20.dp else 7.dp, height = 7.dp)
                    .background(if (selected) TvText else TvTextMuted.copy(alpha = 0.55f), TvShapes.Pill),
            )
        }
    }
}
