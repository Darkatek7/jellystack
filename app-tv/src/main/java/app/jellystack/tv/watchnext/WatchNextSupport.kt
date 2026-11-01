package app.jellystack.tv.watchnext

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.jellystack.core.logging.JellystackLog

/**
 * Whether the system has a Watch Next row this app may fill. Fire TV is excluded on purpose: its home screen
 * does not read the Android TV provider, so writing to it would only leave unused data behind.
 */
internal object WatchNextSupport {
    private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
    private const val TV_PROVIDER_AUTHORITY = "android.media.tv"

    fun isSupported(
        sdkInt: Int,
        isFireTv: Boolean,
        isLeanback: Boolean,
        providerAvailable: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.O && !isFireTv && isLeanback && providerAvailable

    fun detect(context: Context): Boolean =
        runCatching {
            val packageManager = context.packageManager
            isSupported(
                sdkInt = Build.VERSION.SDK_INT,
                isFireTv = packageManager.hasSystemFeature(FIRE_TV_FEATURE),
                isLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
                providerAvailable = packageManager.resolveContentProvider(TV_PROVIDER_AUTHORITY, 0) != null,
            )
        }.getOrElse { failure ->
            JellystackLog.w("Watch Next support check failed: ${failure::class.java.simpleName}")
            false
        }
}
