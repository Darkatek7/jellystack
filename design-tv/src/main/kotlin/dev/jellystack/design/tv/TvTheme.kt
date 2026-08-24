@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dev.jellystack.core.preferences.MotionPreference

internal val TvPurple = Color(0xFFB9A2FF)
internal val TvPurpleStrong = Color(0xFF7C51E8)
internal val TvBackground = Color(0xFF080910)
internal val TvSurface = Color(0xFF171824)
internal val TvSurfaceRaised = Color(0xFF222334)
internal val TvText = Color(0xFFF4F1FF)
internal val TvTextMuted = Color(0xFFB8B4C6)

@Immutable
internal data class TvFocusAppearance(
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val scale: Float = TvLayoutTokens.FOCUS_SCALE,
    val ringWidthDp: Float = 5f,
)

internal val LocalTvFocusAppearance = staticCompositionLocalOf { TvFocusAppearance() }

@Composable
fun JellystackTvTheme(
    motionPreference: MotionPreference = MotionPreference.SYSTEM,
    highContrastFocus: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemAnimationsEnabled = tvSystemAnimationsEnabled()
    val motion =
        tvCinematicMotion(
            reducedMotion = tvMotionReduced(motionPreference, systemAnimationsEnabled),
            highContrastFocus = highContrastFocus,
        )
    val focusAppearance =
        TvFocusAppearance(
            reducedMotion = motion.reducedMotion,
            highContrast = motion.highContrastFocus,
            scale = motion.focusScale,
            ringWidthDp = motion.focusRingWidthDp,
        )
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
            CompositionLocalProvider(
                LocalContentColor provides TvText,
                LocalTvFocusAppearance provides focusAppearance,
                content = content,
            )
        }
    }
}

@Composable
private fun tvSystemAnimationsEnabled(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled()
    val context = LocalContext.current
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}
