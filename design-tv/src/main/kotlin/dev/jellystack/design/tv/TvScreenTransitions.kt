package dev.jellystack.design.tv

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene

/**
 * The navigation library animates a screen change over 700 ms by default, which feels like waiting on a TV. Screens
 * cross-fade this fast instead, and not at all with reduced motion.
 */
internal const val TV_SCREEN_TRANSITION_MS = 180

internal typealias TvScreenTransition<T> = AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

internal fun <T : Any> tvScreenTransition(reducedMotion: Boolean): TvScreenTransition<T> =
    if (reducedMotion) {
        { ContentTransform(EnterTransition.None, ExitTransition.None) }
    } else {
        { fadeIn(tween(TV_SCREEN_TRANSITION_MS)) togetherWith fadeOut(tween(TV_SCREEN_TRANSITION_MS)) }
    }
