@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The Live TV route: the channel list, which starts a channel through the playback launcher. */
@Composable
internal fun TvLiveTvRoute(
    coordinator: TvLiveTvCoordinator,
    context: TvLiveTvContext,
    launcher: TvPlaybackLauncher,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator) { coordinator.show() }
    val actions =
        remember(coordinator, launcher) {
            TvLiveTvActions(
                onPlay = { channel -> launcher.play(channel.toPlayableItem()) },
                onRetry = coordinator::retry,
                onVisibleChannels = coordinator::setVisible,
            )
        }
    TvLiveTvScreen(state, context, actions)
}
