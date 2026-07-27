package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.GoogleCastSessionManager

@Suppress("FunctionName")
@Composable
actual fun BindCastSnapshotProvider(
    controller: PlaybackController,
    castSessionManager: CastSessionManager,
) {
    val googleCastManager = castSessionManager as? GoogleCastSessionManager
    DisposableEffect(googleCastManager, controller) {
        if (googleCastManager != null) {
            googleCastManager.setSnapshotProvider { controller.currentCastSnapshot() }
        }
        onDispose {
            googleCastManager?.setSnapshotProvider(null)
        }
    }
}
