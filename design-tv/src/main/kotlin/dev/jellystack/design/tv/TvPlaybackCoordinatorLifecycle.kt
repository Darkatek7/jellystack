package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    isForeground: Boolean,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
): TvPlaybackCoordinators {
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(isForeground, coordinators.continuation) {
        coordinators.continuation.setForeground(isForeground)
    }
    DisposableEffect(coordinators) {
        onDispose {
            coordinators.segment.release()
            coordinators.continuation.release()
        }
    }

    return coordinators
}
