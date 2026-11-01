@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon

private const val DISABLED_ROW_ALPHA = 0.45f

/** Direction of a Left/Right press on a delay row in delay steps, or null for any other key. */
internal fun tvDelayStepFor(keyCode: Int): Int? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -1
        KeyEvent.KEYCODE_DPAD_RIGHT -> 1
        else -> null
    }

/**
 * Rows of the audio and subtitle delay panel. Left/Right change a row by one step per key event, so holding the
 * key repeats; Center resets that row. The subtitle row stays visible but cannot be focused while no subtitle is on.
 */
internal fun LazyListScope.tvPlayerDelayRows(
    delays: TvPlaybackDelays,
    subtitleOn: Boolean,
    strings: TvStrings,
    firstFocus: Modifier,
) {
    item {
        TvPlayerOptionRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = strings.player.sync.audioDelay,
            summary = formatDelayMs(delays.audioMs),
            selected = delays.audioMs != 0,
            onClick = delays::resetAudio,
            modifier = firstFocus.delayKeys(delays::stepAudio),
            trailing = { DelayArrows() },
        )
    }
    item {
        val rowModifier =
            if (subtitleOn) Modifier.delayKeys(delays::stepSubtitle) else Modifier.alpha(DISABLED_ROW_ALPHA)
        TvPlayerOptionRow(
            icon = Icons.Default.Subtitles,
            title = strings.player.sync.subtitleDelay,
            summary =
                if (subtitleOn) {
                    formatDelayMs(delays.subtitleMs)
                } else {
                    strings.player.sync.subtitleDelayNeedsSubtitle
                },
            selected = subtitleOn && delays.subtitleMs != 0,
            onClick = delays::resetSubtitle,
            modifier = rowModifier,
            enabled = subtitleOn,
            trailing = { if (subtitleOn) DelayArrows() },
        )
    }
    item {
        TvPlayerOptionRow(
            icon = Icons.Default.RestartAlt,
            title = strings.player.sync.resetDelays,
            summary = null,
            selected = false,
            onClick = delays::resetAll,
            trailing = {},
        )
    }
}

private fun Modifier.delayKeys(onStep: (Int) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val step = tvDelayStepFor(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
        // Every repeated ACTION_DOWN of a held key is one more step; the matching ACTION_UP does nothing.
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) onStep(step)
        true
    }

@Composable
private fun DelayArrows() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(24.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(24.dp))
    }
}
