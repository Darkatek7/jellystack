package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.cast.CastSessionManager

@Suppress("FunctionName")
@Composable
actual fun BindCastSnapshotProvider(
    controller: PlaybackController,
    castSessionManager: CastSessionManager,
) = Unit
