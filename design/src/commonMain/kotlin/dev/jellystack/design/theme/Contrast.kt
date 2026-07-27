package dev.jellystack.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal fun contrastRatio(
    foreground: Color,
    background: Color,
): Double {
    val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
    val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
    return (lighter + 0.05) / (darker + 0.05)
}
