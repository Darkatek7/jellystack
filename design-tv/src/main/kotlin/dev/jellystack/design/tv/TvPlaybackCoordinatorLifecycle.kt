package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.CoroutineScope

internal data class TvJellyfinPlaybackIdentity(
    val serverKey: String,
    val userId: String,
)

internal data class TvPlaybackCoordinators(
    val segment: PlaybackSegmentCoordinator,
    val continuation: PlaybackContinuationCoordinator,
)

@Composable
internal fun rememberTvPlaybackCoordinators(
    identity: TvJellyfinPlaybackIdentity?,
    playbackState: PlaybackState,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
): TvPlaybackCoordinators {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinators =
        remember(identity) {
            TvPlaybackCoordinators(
                segment = createSegmentCoordinator(scope),
                continuation = createContinuationCoordinator(scope),
            )
        }

    LaunchedEffect(playbackState, coordinators) {
        coordinators.segment.onPlaybackState(playbackState)
        coordinators.continuation.onPlaybackState(playbackState)
    }
    DisposableEffect(lifecycleOwner, coordinators) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    -> coordinators.continuation.setForeground(true)

                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                    -> coordinators.continuation.setForeground(false)

                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        coordinators.continuation.setForeground(
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycle.removeObserver(observer)
            coordinators.continuation.setForeground(false)
            coordinators.segment.release()
            coordinators.continuation.release()
        }
    }

    return coordinators
}
