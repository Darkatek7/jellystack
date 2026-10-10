@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * While a trailer plays on the home stage and the remote rests for [TV_HOME_IMMERSIVE_DELAY_MS], the home UI fades
 * out and the trailer has the screen. [interaction] counts key presses: each one brings the UI back at once and
 * starts the wait again, and the key still acts as usual.
 *
 * Returns how much of the home UI shows: 1 normally, 0 while the trailer has the screen.
 */
@Composable
internal fun rememberTvHomeUiAlpha(
    trailerPlaying: Boolean,
    interaction: Int,
): State<Float> {
    var immersive by remember { mutableStateOf(false) }
    LaunchedEffect(trailerPlaying, interaction) {
        immersive = false
        if (trailerPlaying) {
            delay(TV_HOME_IMMERSIVE_DELAY_MS)
            immersive = true
        }
    }
    val fadeMs =
        when {
            LocalTvFocusAppearance.current.reducedMotion -> 0
            immersive -> TV_HOME_UI_FADE_OUT_MS
            else -> TV_HOME_UI_FADE_IN_MS
        }
    return animateFloatAsState(if (immersive) 0f else 1f, tween(fadeMs), label = "tv-home-ui-alpha")
}

/** Draws [content] at [alpha] and leaves it out while it is fully transparent. */
@Composable
internal fun TvHomeFadeLayer(
    alpha: State<Float>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shown by remember(alpha) { derivedStateOf { alpha.value > 0f } }
    if (shown) Box(modifier.graphicsLayer { this.alpha = alpha.value }, content = content)
}

internal const val TV_HOME_IMMERSIVE_DELAY_MS = 3_000L

// Out slowly so the change feels calm, back quickly so a key press shows the UI before the next one.
private const val TV_HOME_UI_FADE_OUT_MS = 600
private const val TV_HOME_UI_FADE_IN_MS = 150
