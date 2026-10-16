package dev.jellystack.design.tv

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

@Immutable
internal data class TvSafeInsets(
    val horizontal: Dp = 48.dp,
    val vertical: Dp = 27.dp,
)

@Immutable
internal data class TvLayoutBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal object TvLayoutTokens {
    val SafeInsets = TvSafeInsets()
    val ExpandedRailWidth = 228.dp

    // The navigation rail hides while the content has focus, so content starts at the safe inset.
    val ContentStart = SafeInsets.horizontal
    val MinimumActionSize = 48.dp
    val LandscapeArtworkWidth = 176.dp
    val LandscapeArtworkHeight = 99.dp
    val CardSpacing = 16.dp
    val FocusHaloPadding = 8.dp
    const val FOCUS_SCALE = 1.055f
    val FocusLightRing = Color.White
    val FocusDarkRing = Color.Black
}

internal fun tvSafeBounds(
    widthDp: Float,
    heightDp: Float,
    insets: TvSafeInsets = TvLayoutTokens.SafeInsets,
): TvLayoutBounds =
    TvLayoutBounds(
        left = insets.horizontal.value,
        top = insets.vertical.value,
        right = widthDp - insets.horizontal.value,
        bottom = heightDp - insets.vertical.value,
    )

/**
 * Lets a horizontal row draw into the [start] and [end] padding of its parent, so cards scroll to the screen
 * edge instead of being cut off at the safe inset. Pair it with [tvBleedRowPadding] so the first card still
 * lines up with the content.
 */
internal fun Modifier.tvRowBleed(
    start: Dp = TvLayoutTokens.ContentStart,
    end: Dp = TvLayoutTokens.SafeInsets.horizontal,
): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val startPx = start.roundToPx()
        val width = constraints.maxWidth + startPx + end.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-startPx, 0) }
    }

/** Content padding for a row widened by [tvRowBleed]; [edge] is the room for the focused card's halo. */
internal fun tvBleedRowPadding(
    edge: Dp,
    start: Dp = TvLayoutTokens.ContentStart,
    end: Dp = TvLayoutTokens.SafeInsets.horizontal,
): PaddingValues = PaddingValues(start = start + edge, end = end + edge, top = edge, bottom = edge)

@Suppress("FunctionOnlyReturningConstant", "UNUSED_PARAMETER")
internal fun tvContentOffsetForRail(expanded: Boolean): Float = 0f

internal fun tvSettingsColumnCount(
    availableWidthDp: Float,
    fontScale: Float,
): Int {
    val safeFontScale = fontScale.coerceAtLeast(1f)
    val minimumTileWidth = 260f * (1f + ((safeFontScale - 1f) * 0.7f))
    val columns =
        floor(
            (availableWidthDp + TvLayoutTokens.CardSpacing.value) /
                (minimumTileWidth + TvLayoutTokens.CardSpacing.value),
        )
    return columns.toInt().coerceIn(1, 3)
}

internal fun tvContrastRatio(
    foreground: Color,
    background: Color,
): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
