package dev.jellystack.players

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Sizes Media3's byte buffer to the app's heap.
 *
 * The buffer lives on the Java heap. Media3's default for video (about 140 MB) risks an OutOfMemoryError on
 * devices with a 128 to 256 MB heap, such as 1 GB Fire TV sticks, when a high-bitrate 4K stream is buffered.
 * Time-based limits keep Media3's defaults.
 */
internal object PlaybackBufferSizing {
    private const val BYTES_PER_MB = 1024L * 1024L

    /** Roughly what Media3 buffers by default for a 4K stream; a budget at or above it needs no cap. */
    private const val MEDIA3_DEFAULT_BUFFER_MB = 144L

    /** Enough for the default 2.5 s start-up buffer of a very high bitrate remux. */
    private const val MIN_BUFFER_MB = 32L

    /** Share of the heap the buffer may take, as percent: lower on low-RAM devices, where everything else is tight. */
    private const val LOW_RAM_HEAP_SHARE_PERCENT = 25L
    private const val DEFAULT_HEAP_SHARE_PERCENT = 40L

    /** Byte buffer limit for a heap of [memoryClassMb], or null to keep Media3's default sizing. */
    fun targetBufferBytes(
        memoryClassMb: Int,
        isLowRamDevice: Boolean,
    ): Int? {
        val sharePercent = if (isLowRamDevice) LOW_RAM_HEAP_SHARE_PERCENT else DEFAULT_HEAP_SHARE_PERCENT
        val budgetMb = memoryClassMb.coerceAtLeast(0) * sharePercent / PERCENT
        if (budgetMb >= MEDIA3_DEFAULT_BUFFER_MB) return null
        return (budgetMb.coerceAtLeast(MIN_BUFFER_MB) * BYTES_PER_MB).toInt()
    }

    @UnstableApi
    fun loadControl(context: Context): DefaultLoadControl {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val builder = DefaultLoadControl.Builder()
        val targetBytes =
            activityManager?.let { targetBufferBytes(it.memoryClass, it.isLowRamDevice) }
        if (targetBytes != null) builder.setTargetBufferBytes(targetBytes)
        return builder.build()
    }

    private const val PERCENT = 100L
}
