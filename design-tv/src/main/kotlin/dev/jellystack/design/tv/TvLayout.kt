package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val CollapsedRailWidth = 72.dp
    val MinimumActionSize = 48.dp
    val LandscapeArtworkWidth = 232.dp
    val LandscapeArtworkHeight = 131.dp
    val LandscapeMetadataBandHeight = 56.dp
    val CardSpacing = 16.dp
    const val FOCUS_SCALE = 1.055f
    val FocusLightRing = Color.White
    val FocusDarkRing = Color.Black
    val FocusAccentRing = TvPurple
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
