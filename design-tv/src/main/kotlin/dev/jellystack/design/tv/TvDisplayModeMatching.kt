@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import dev.jellystack.core.logging.JellystackLog

/**
 * Switches the TV display to a mode whose refresh rate fits the video while the player route is shown, and puts the
 * window's preferred mode back when it leaves. Does nothing at all while [enabled] is false.
 *
 * Fire TV runs Android 11 (API 30), where the Media3 frame-rate strategy needs API 31, so the mode is requested
 * through `WindowManager.LayoutParams.preferredDisplayModeId`. Media3's own strategy stays at its default.
 *
 * The decision is made once per [mediaId], from the first usable [frameRate] (Media3 `Format.frameRate`, known once
 * the player is ready and before the first frame), so one title never switches the display twice. Pass a null
 * [frameRate] while it is not trustworthy yet (buffering).
 */
@Composable
internal fun TvDisplayModeMatching(
    enabled: Boolean,
    mediaId: String?,
    frameRate: Float?,
) {
    val view = LocalView.current
    val switcher = remember(view) { view.context.findWindow()?.let(::TvDisplayModeSwitcher) }
    var decidedMediaId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(switcher, enabled) {
        if (enabled) switcher?.begin()
        onDispose {
            switcher?.restore()
            decidedMediaId = null
        }
    }
    LaunchedEffect(switcher, enabled, mediaId, frameRate) {
        val activeSwitcher = switcher?.takeIf { enabled && mediaId != decidedMediaId }
        if (activeSwitcher == null || mediaId == null || frameRate == null) return@LaunchedEffect
        decidedMediaId = mediaId
        activeSwitcher.matchTo(frameRate)
    }
}

/** Requests display modes for one window and remembers the preference it has to restore. */
internal class TvDisplayModeSwitcher(
    private val window: Window,
) {
    private var originalPreferredModeId: Int? = null

    fun begin() {
        if (originalPreferredModeId == null) originalPreferredModeId = window.attributes.preferredDisplayModeId
    }

    /** Asks for the best mode for [frameRate]; without one, the mode of before [begin] is requested again. */
    fun matchTo(frameRate: Float) {
        val original = originalPreferredModeId ?: return
        val display = window.decorView.display ?: return
        val current = display.mode.toTvDisplayMode()
        val supported = display.supportedModes.map(Display.Mode::toTvDisplayMode)
        val target = selectMatchingDisplayMode(current, supported, frameRate)
        when {
            target == null -> request(original)
            target.id != current.id -> {
                JellystackLog.d("Display mode switch to ${target.refreshRate} Hz for $frameRate fps")
                request(target.id)
            }
        }
    }

    fun restore() {
        originalPreferredModeId?.let(::request)
        originalPreferredModeId = null
    }

    private fun request(modeId: Int) {
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }
}

private fun Display.Mode.toTvDisplayMode() = TvDisplayMode(modeId, physicalWidth, physicalHeight, refreshRate)

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }
