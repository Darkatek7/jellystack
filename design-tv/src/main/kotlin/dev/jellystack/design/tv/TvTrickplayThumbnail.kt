@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import dev.jellystack.players.PlaybackTrickplay
import dev.jellystack.players.tileAt
import dev.jellystack.players.trickplaySheetUrl
import kotlin.math.roundToInt

/**
 * Trickplay preview for [positionMs]. Sheets load at their original size (the tile math depends on it)
 * in RGB_565, and at most two stay in memory because a sheet is several megabytes.
 * Nothing is drawn until the sheet is ready, so a slow server never shows an empty frame.
 */
@Composable
internal fun TvTrickplayThumbnail(
    trickplay: PlaybackTrickplay,
    positionMs: Long,
    imageBaseUrl: String,
    accessToken: String?,
    modifier: Modifier = Modifier,
) {
    val tile = trickplay.manifest.tileAt(positionMs) ?: return
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val sheets = remember(trickplay) { mutableStateMapOf<Int, ImageBitmap>() }
    LaunchedEffect(trickplay, tile.sheetIndex) {
        if (tile.sheetIndex in sheets) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(trickplaySheetUrl(imageBaseUrl, trickplay, tile.sheetIndex, accessToken))
                .size(Size.ORIGINAL)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap = result.image.toBitmap().asImageBitmap()
        sheets.keys
            .filter { it != tile.sheetIndex }
            .sortedByDescending { kotlin.math.abs(it - tile.sheetIndex) }
            .take((sheets.size - 1).coerceAtLeast(0))
            .forEach(sheets::remove)
        sheets[tile.sheetIndex] = bitmap
    }
    val sheet = sheets[tile.sheetIndex] ?: return
    val shape = TvShapes.Badge
    Canvas(
        modifier
            .size(width = TRICKPLAY_WIDTH_DP.dp, height = (TRICKPLAY_WIDTH_DP * tile.height / tile.width.toFloat()).dp)
            .clip(shape)
            .border(2.dp, TvText, shape),
    ) {
        drawImage(
            image = sheet,
            srcOffset = IntOffset(tile.offsetX, tile.offsetY),
            srcSize = IntSize(tile.width, tile.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

internal const val TRICKPLAY_WIDTH_DP = 192
