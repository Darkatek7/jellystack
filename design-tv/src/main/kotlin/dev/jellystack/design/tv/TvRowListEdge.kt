package dev.jellystack.design.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades out rows cut by the top edge of a browse list under the stage. The focused row starts at that edge, but the
 * last row cannot scroll that far, so the row above it would stay visible as a hard sliver under the stage text.
 * A row is gone once it has moved [fadeDistance] past the edge, so rows scrolling out fade instead of being cut.
 */
internal fun Modifier.tvFadeRowsCutAtTop(
    listState: LazyListState,
    fadeDistance: Dp = TV_ROWS_TOP_FADE_DISTANCE,
): Modifier =
    graphicsLayer {
        // Erasing rows needs a layer of its own; a list resting on its first row has nothing to erase.
        compositingStrategy =
            if (listState.canScrollBackward) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val fadePx = fadeDistance.toPx()
        listState.layoutInfo.visibleItemsInfo.forEach { row ->
            val visibleBottom = (row.offset + row.size).toFloat()
            if (row.offset < 0 && visibleBottom > 0f) {
                drawRect(
                    color = Color.Black,
                    size = Size(size.width, visibleBottom),
                    alpha = (-row.offset / fadePx).coerceIn(0f, 1f),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
    }

private val TV_ROWS_TOP_FADE_DISTANCE = 48.dp
