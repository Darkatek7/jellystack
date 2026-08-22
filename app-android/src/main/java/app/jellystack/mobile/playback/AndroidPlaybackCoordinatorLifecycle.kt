package app.jellystack.mobile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.CoroutineScope

internal data class AndroidJellyfinPlaybackIdentity(
    val serverKey: String,
    val userId: String,
)

internal data class AndroidPlaybackCoordinators(
    val segment: PlaybackSegmentCoordinator,
    val continuation: PlaybackContinuationCoordinator,
)

internal data class AndroidPlaybackRootBindings(
    val segmentState: PlaybackSegmentState,
    val continuationState: PlaybackContinuationState,
    val onSkipSegment: (PlaybackSegmentAction) -> Unit,
    val onPlayNext: () -> Unit,
)

@Composable
// ktlint prefers this single-parameter signature on one line even though it exceeds 120 chars.
@Suppress("MaxLineLength")
internal fun rememberAndroidPlaybackRootBindings(coordinators: AndroidPlaybackCoordinators): AndroidPlaybackRootBindings {
    val segmentState by coordinators.segment.state.collectAsStateWithLifecycle()
    val continuationState by coordinators.continuation.state.collectAsStateWithLifecycle()
    return remember(coordinators, segmentState, continuationState) {
        AndroidPlaybackRootBindings(
            segmentState = segmentState,
            continuationState = continuationState,
            onSkipSegment = coordinators.segment::skip,
            onPlayNext = coordinators.continuation::playNext,
        )
    }
}

@Composable
internal fun rememberAndroidPlaybackCoordinators(
    identity: AndroidJellyfinPlaybackIdentity?,
    playbackState: PlaybackState,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
): AndroidPlaybackCoordinators {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinators =
        remember(identity) {
            AndroidPlaybackCoordinators(
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
