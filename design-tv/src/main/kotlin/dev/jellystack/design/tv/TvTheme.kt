@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

internal val TvPurple = Color(0xFFB9A2FF)
internal val TvPurpleStrong = Color(0xFF7C51E8)
internal val TvBackground = Color(0xFF080910)
internal val TvSurface = Color(0xFF171824)
internal val TvSurfaceRaised = Color(0xFF222334)
internal val TvText = Color(0xFFF4F1FF)
internal val TvTextMuted = Color(0xFFB8B4C6)

@Composable
fun JellystackTvTheme(content: @Composable () -> Unit) {
    val tvColors =
        darkColorScheme(
            primary = TvPurple,
            onPrimary = Color(0xFF24134E),
            background = TvBackground,
            onBackground = TvText,
            surface = TvSurface,
            onSurface = TvText,
            surfaceVariant = TvSurfaceRaised,
            onSurfaceVariant = TvTextMuted,
        )
    androidx.compose.material3.MaterialTheme(
        colorScheme =
            androidx.compose.material3.darkColorScheme(
                primary = TvPurple,
                onPrimary = Color(0xFF24134E),
                background = TvBackground,
                onBackground = TvText,
                surface = TvSurface,
                onSurface = TvText,
                surfaceVariant = TvSurfaceRaised,
                onSurfaceVariant = TvTextMuted,
            ),
    ) {
        MaterialTheme(colorScheme = tvColors) {
            CompositionLocalProvider(LocalContentColor provides TvText, content = content)
        }
    }
}
