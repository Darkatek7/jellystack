@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jellystack.players.PlaybackTimelineMarkers

/** Segment ranges (intro, credits, …) are amber so they read as "skippable" against the purple progress. */
internal val TvSeekBarSegmentColor = Color(0xFFE8C66A)

/**
 * Seek bar of the player: track, skippable segments, chapter ticks, progress, and a knob that grows
 * while the bar is [active] (focused or scrubbing).
 */
@Composable
internal fun TvSeekBar(
    fraction: Float,
    markers: PlaybackTimelineMarkers,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(SEEK_BAR_HEIGHT_DP.dp)) {
        val trackHeight = (if (active) ACTIVE_TRACK_DP else TRACK_DP).dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, trackHeight), radius)
        markers.segments.forEach { range ->
            drawRect(
                color = TvSeekBarSegmentColor.copy(alpha = 0.8f),
                topLeft = Offset(size.width * range.start, top),
                size = Size(size.width * (range.end - range.start), trackHeight),
            )
        }
        val progress = fraction.coerceIn(0f, 1f)
        drawRoundRect(TvPurple, Offset(0f, top), Size(size.width * progress, trackHeight), radius)
        val tickHeight = trackHeight + TICK_OVERHANG_DP.dp.toPx() * 2
        markers.chapterStarts.forEach { start ->
            drawRect(
                color = TvText.copy(alpha = 0.9f),
                topLeft = Offset(size.width * start - 1.dp.toPx(), (size.height - tickHeight) / 2f),
                size = Size(2.dp.toPx(), tickHeight),
            )
        }
        val knobRadius = (if (active) ACTIVE_KNOB_DP else KNOB_DP).dp.toPx()
        drawCircle(TvText, knobRadius, Offset(size.width * progress, size.height / 2f))
    }
}

private const val SEEK_BAR_HEIGHT_DP = 22
private const val TRACK_DP = 5
private const val ACTIVE_TRACK_DP = 7
private const val TICK_OVERHANG_DP = 3
private const val KNOB_DP = 6
private const val ACTIVE_KNOB_DP = 9
