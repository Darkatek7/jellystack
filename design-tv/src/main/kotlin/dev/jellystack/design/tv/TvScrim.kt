package dev.jellystack.design.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The scrims behind full-screen artwork. Evaluating full-screen gradient shaders every frame is what limits this UI on
 * weak TV GPUs: on a Fire TV 4K Max it tripled the frame time while rows move. Each scrim is drawn once into a small
 * bitmap and stretched over the screen, which only samples a texture.
 */
internal enum class TvScrim(
    val draw: DrawScope.() -> Unit,
) {
    HOME_BACKDROP({
        drawRect(Brush.horizontalGradient(colorStops = TV_HOME_BACKDROP_SIDE_SCRIM))
        drawRect(Brush.verticalGradient(colorStops = TV_HOME_BACKDROP_BOTTOM_SCRIM))
        // Keeps the clock and the page dots readable when the artwork is bright in that corner.
        drawRect(
            Brush.radialGradient(
                colors = listOf(TvBackground.copy(alpha = 0.62f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.minDimension * 0.75f,
            ),
        )
    }),
    DETAIL_HERO({
        drawRect(
            Brush.horizontalGradient(listOf(TvBackground.copy(0.96f), TvBackground.copy(0.68f), Color.Transparent)),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.08f), TvBackground.copy(alpha = 0.12f), TvBackground),
            ),
        )
    }),
}

/** Draws [scrim] over this element's whole area. */
internal fun Modifier.tvBakedScrim(scrim: TvScrim): Modifier =
    drawWithCache {
        val baked = bakedScrim(scrim, size, this, layoutDirection)
        val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
        onDrawBehind { drawImage(baked, dstSize = target, filterQuality = FilterQuality.Low) }
    }

private data class TvBakedScrimKey(
    val scrim: TvScrim,
    val width: Int,
    val height: Int,
)

// Shared by every screen with a backdrop; a scrim exists in very few sizes, usually one, so this stays small.
private val bakedScrims = HashMap<TvBakedScrimKey, ImageBitmap>()

private fun bakedScrim(
    scrim: TvScrim,
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val width = ceil(size.width / TV_SCRIM_BAKE_DIVISOR).toInt().coerceAtLeast(1)
    val height = ceil(size.height / TV_SCRIM_BAKE_DIVISOR).toInt().coerceAtLeast(1)
    return bakedScrims.getOrPut(TvBakedScrimKey(scrim, width, height)) {
        ImageBitmap(width, height).also { bitmap ->
            val bakedSize = Size(width.toFloat(), height.toFloat())
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), bakedSize, scrim.draw)
        }
    }
}

// Scrims are smooth, so a quarter of the screen size stays invisible once stretched, at a sixteenth of the memory.
private const val TV_SCRIM_BAKE_DIVISOR = 4f

// Dark behind the hero text and the open rail, clear on the right where the artwork shows.
private val TV_HOME_BACKDROP_SIDE_SCRIM =
    arrayOf(
        0f to TvBackground.copy(alpha = 0.96f),
        0.3f to TvBackground.copy(alpha = 0.84f),
        0.58f to TvBackground.copy(alpha = 0.38f),
        0.82f to Color.Transparent,
    )

// A light veil under the clock, then dark from the rows down so row titles stay readable on bright artwork.
private val TV_HOME_BACKDROP_BOTTOM_SCRIM =
    arrayOf(
        0f to TvBackground.copy(alpha = 0.34f),
        0.22f to Color.Transparent,
        0.5f to TvBackground.copy(alpha = 0.3f),
        0.64f to TvBackground.copy(alpha = 0.88f),
        1f to TvBackground,
    )
