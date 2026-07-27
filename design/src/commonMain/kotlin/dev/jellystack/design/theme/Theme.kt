package dev.jellystack.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object JellystackThemeColors {
    val lightBackground = Color(0xFFF8F7FC)
    val lightOnBackground = Color(0xFF191821)
    val darkBackground = Color(0xFF08090F)
    val darkOnBackground = Color(0xFFE8E4EF)

    val light =
        lightColorScheme(
            primary = Color(0xFF6650A4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE8DEFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF45618A),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD7E3FF),
            onTertiaryContainer = Color(0xFF001B3E),
            background = lightBackground,
            onBackground = lightOnBackground,
            surface = Color(0xFFFFFBFF),
            onSurface = Color(0xFF191821),
            surfaceVariant = Color(0xFFE7E1EC),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceContainer = Color(0xFFF1EFF7),
            surfaceContainerHigh = Color(0xFFEAE7F0),
            surfaceContainerHighest = Color(0xFFE3E0E9),
            outline = Color(0xFF79747E),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        )

    val dark =
        darkColorScheme(
            primary = Color(0xFFC7B7FF),
            onPrimary = Color(0xFF33216B),
            primaryContainer = Color(0xFF4B3783),
            onPrimaryContainer = Color(0xFFE8DEFF),
            secondary = Color(0xFFCBC2DB),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFAFC6FF),
            onTertiary = Color(0xFF15305B),
            tertiaryContainer = Color(0xFF2D4773),
            onTertiaryContainer = Color(0xFFD7E3FF),
            background = darkBackground,
            onBackground = darkOnBackground,
            surface = Color(0xFF0E1018),
            onSurface = Color(0xFFE8E4EF),
            surfaceVariant = Color(0xFF262431),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainer = Color(0xFF141620),
            surfaceContainerHigh = Color(0xFF1A1C27),
            surfaceContainerHighest = Color(0xFF232532),
            outline = Color(0xFF938F99),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
}

private val jellystackTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
            ),
        headlineMedium =
            TextStyle(
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
        headlineSmall =
            TextStyle(
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            ),
        titleLarge =
            TextStyle(
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleSmall =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        labelMedium =
            TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
    )

private val jellystackShapes =
    Shapes(
        extraSmall =
            androidx.compose.foundation.shape
                .RoundedCornerShape(8.dp),
        small =
            androidx.compose.foundation.shape
                .RoundedCornerShape(10.dp),
        medium =
            androidx.compose.foundation.shape
                .RoundedCornerShape(14.dp),
        large =
            androidx.compose.foundation.shape
                .RoundedCornerShape(20.dp),
        extraLarge =
            androidx.compose.foundation.shape
                .RoundedCornerShape(28.dp),
    )

@Suppress("ktlint:standard:function-naming")
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Suppress("FunctionName")
@Composable
fun JellystackTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) JellystackThemeColors.dark else JellystackThemeColors.light
    CompositionLocalProvider(LocalIsDarkTheme provides isDarkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = jellystackTypography,
            shapes = jellystackShapes,
            content = content,
        )
    }
}
