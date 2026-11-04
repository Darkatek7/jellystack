@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The Live TV route: the channel list or the guide grid, which start a channel through the playback launcher. */
@Composable
internal fun TvLiveTvRoute(
    coordinator: TvLiveTvCoordinator,
    context: TvLiveTvContext,
    launcher: TvPlaybackLauncher,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val guideMode by coordinator.guideMode.collectAsStateWithLifecycle()
    LaunchedEffect(coordinator) { coordinator.show() }
    val actions =
        remember(coordinator, launcher) {
            TvLiveTvActions(
                onPlay = { channel -> launcher.play(channel.toPlayableItem()) },
                onRetry = coordinator::retry,
                onVisibleChannels = coordinator::setVisible,
                guide =
                    TvLiveTvGuideActions(
                        onWindow = coordinator::setGuideWindow,
                        onToggle = coordinator::toggleGuide,
                        memory = coordinator.guideMemory,
                    ),
            )
        }
    TvLiveTvScreen(state, context, actions, guideMode)
}
