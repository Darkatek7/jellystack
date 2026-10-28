@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** What the player route shows instead of the video. */
internal enum class TvPlayerOverlay {
    NONE,

    /** The server is still answering, or the controller is preparing the stream. */
    PREPARING,

    /** The controller reported a playback failure; Retry goes through the controller. */
    PLAYBACK_ERROR,

    /** The start failed before the controller took over; Retry starts the same item again. */
    START_ERROR,
}

internal fun tvPlayerOverlay(
    playback: PlaybackState,
    start: TvPlaybackStartState,
): TvPlayerOverlay =
    when {
        // Playing something already (for example an episode switch that failed to load): the video stays.
        playback is PlaybackState.Active -> TvPlayerOverlay.NONE
        playback is PlaybackState.Preparing || start is TvPlaybackStartState.Starting -> TvPlayerOverlay.PREPARING
        playback is PlaybackState.PlaybackError -> TvPlayerOverlay.PLAYBACK_ERROR
        start is TvPlaybackStartState.Failed -> TvPlayerOverlay.START_ERROR
        else -> TvPlayerOverlay.NONE
    }

/**
 * The player route has nothing left to show: playback ended outside the player (Home button, media
 * stop, SyncPlay stop, ...) and no start or error panel is waiting. The route then leaves instead of
 * presenting a black screen.
 */
internal fun shouldPopPlayerRoute(
    playback: PlaybackState,
    start: TvPlaybackStartState,
): Boolean = playback is PlaybackState.Stopped && start is TvPlaybackStartState.Idle

/**
 * Playback briefly reads as stopped between two items (stop, then prepare the next one). The route only
 * leaves when it stays stopped for this long.
 */
internal const val TV_PLAYER_STOPPED_POP_GRACE_MS = 400L

/** Calls [onPop] when a `true` value was not replaced within [graceMs]. */
internal suspend fun Flow<Boolean>.popAfterGrace(
    graceMs: Long,
    onPop: () -> Unit,
) = collectLatest { shouldPop ->
    if (shouldPop) {
        delay(graceMs)
        onPop()
    }
}

/** Leaves the player route when playback stopped for good; see [shouldPopPlayerRoute]. */
@Composable
internal fun TvPlayerRoutePopEffect(
    onPlayerRoute: Boolean,
    playback: PlaybackState,
    start: TvPlaybackStartState,
    onPop: () -> Unit,
) {
    val shouldPop = onPlayerRoute && shouldPopPlayerRoute(playback, start)
    val currentShouldPop by rememberUpdatedState(shouldPop)
    val currentOnPop by rememberUpdatedState(onPop)
    LaunchedEffect(Unit) {
        snapshotFlow { currentShouldPop }.popAfterGrace(TV_PLAYER_STOPPED_POP_GRACE_MS) { currentOnPop() }
    }
}

internal enum class TvPlayerBackAction { CLOSE_PANEL, CLOSE_SCRUB, HIDE_CONTROLS, CLOSE_PLAYER }

/**
 * Back in the player. Without a running video there are no controls to hide, so Back leaves at once;
 * that is also how the user cancels a start that is still preparing.
 */
internal fun tvPlayerBackAction(
    videoActive: Boolean,
    panelOpen: Boolean,
    controlsVisible: Boolean,
    hiddenScrubVisible: Boolean,
): TvPlayerBackAction =
    when {
        !videoActive -> TvPlayerBackAction.CLOSE_PLAYER
        panelOpen -> TvPlayerBackAction.CLOSE_PANEL
        // Back closes the hidden-controls seek bar first; during a hold it also drops the pending seek.
        !controlsVisible && hiddenScrubVisible -> TvPlayerBackAction.CLOSE_SCRUB
        controlsVisible -> TvPlayerBackAction.HIDE_CONTROLS
        else -> TvPlayerBackAction.CLOSE_PLAYER
    }
