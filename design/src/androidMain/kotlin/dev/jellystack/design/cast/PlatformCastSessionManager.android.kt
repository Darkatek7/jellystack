package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.cast.framework.CastContext
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.GoogleCastSessionManager
import io.github.aakira.napier.Napier

@Composable
actual fun rememberPlatformCastSessionManager(): CastSessionManager? {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val castContext =
        remember(appContext) {
            runCatching { CastContext.getSharedInstance(appContext) }
                .onFailure { error ->
                    Napier.e(tag = "Cast", throwable = error) {
                        "Failed to obtain CastContext"
                    }
                }.getOrNull()
        }

    val castManager =
        remember(appContext, castContext) {
            castContext?.let {
                runCatching { GoogleCastSessionManager(appContext, it) }
                    .onFailure { error ->
                        Napier.e(tag = "Cast", throwable = error) { "Failed to create GoogleCastSessionManager" }
                    }.getOrNull()
            }
        }

    DisposableEffect(castManager) {
        onDispose {
            castManager?.release()
        }
    }

    return castManager
}
