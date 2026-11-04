@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.jellystack.core.server.ServerAddressValidation
import dev.jellystack.core.server.validateServerAddress
import kotlin.math.max

private const val QUIET_ZONE_MODULES = 4
private const val DARK_MODULE = 0xFF000000.toInt()
private const val LIGHT_MODULE = 0xFFFFFFFF.toInt()

/**
 * The server's Jellyfin web page where a signed-in user enters a Quick Connect code (`#/quickconnect`), or null when
 * [rawServerUrl] is not a usable address. The address is only encoded on the device and never logged.
 */
internal fun quickConnectPageUrl(rawServerUrl: String): String? {
    val valid = validateServerAddress(rawServerUrl) as? ServerAddressValidation.Valid ?: return null
    val base = valid.normalizedUrl.trimEnd('/').removeSuffix("/web")
    return "$base/web/#/quickconnect"
}

/** Encodes [content] to a square module grid including the quiet zone; `true` is a dark module. */
internal fun qrModules(content: String): Array<BooleanArray> {
    val hints =
        mapOf(
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    return Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
}

/** Dark modules on white with a quiet zone, generated locally and cached per [content]. */
@Composable
internal fun TvQrCode(
    content: String,
    size: Dp,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap =
        remember(content, targetPx) {
            val modules = qrModules(content)
            val scale = max(1, targetPx / modules.size)
            val pixels = modules.size * scale
            val colors = IntArray(pixels * pixels)
            for (y in 0 until pixels) {
                val row = modules[y / scale]
                for (x in 0 until pixels) colors[y * pixels + x] = if (row[x / scale]) DARK_MODULE else LIGHT_MODULE
            }
            Bitmap.createBitmap(colors, pixels, pixels, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size),
    )
}
