package dev.jellystack.design.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.jellystack.design.theme.JellystackLayoutTokens

internal enum class JellystackWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal enum class JellystackNavigationMode {
    Dock,
    Rail,
}

internal data class ResponsiveProfile(
    val widthClass: JellystackWidthClass,
    val isShortHeight: Boolean,
) {
    val isCompact: Boolean
        get() = widthClass == JellystackWidthClass.Compact

    val isExpanded: Boolean
        get() = widthClass == JellystackWidthClass.Expanded

    val horizontalContentPadding: Dp
        get() =
            when (widthClass) {
                JellystackWidthClass.Compact -> JellystackLayoutTokens.screenPaddingCompact
                JellystackWidthClass.Medium -> JellystackLayoutTokens.screenPaddingMedium
                JellystackWidthClass.Expanded -> JellystackLayoutTokens.screenPaddingExpanded
            }

    val navigationMode: JellystackNavigationMode
        get() =
            if (isExpanded) {
                JellystackNavigationMode.Rail
            } else {
                JellystackNavigationMode.Dock
            }

    val dockShowsAllLabels: Boolean
        get() = !isShortHeight
}

internal fun responsiveProfile(
    widthDp: Float,
    heightDp: Float,
): ResponsiveProfile =
    ResponsiveProfile(
        widthClass =
            when {
                widthDp < 600f -> JellystackWidthClass.Compact
                widthDp < 840f -> JellystackWidthClass.Medium
                else -> JellystackWidthClass.Expanded
            },
        isShortHeight = heightDp < 480f,
    )

internal val LocalResponsiveProfile =
    staticCompositionLocalOf {
        ResponsiveProfile(
            widthClass = JellystackWidthClass.Compact,
            isShortHeight = false,
        )
    }

@Composable
@Suppress("FunctionName")
internal fun ProvideResponsiveProfile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val profile = responsiveProfile(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalResponsiveProfile provides profile) {
            content()
        }
    }
}
