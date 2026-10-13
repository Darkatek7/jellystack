package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

internal class TvPlaybackTrailerPreviewPlayer(
    private val controller: PlaybackController,
    private val engine: AndroidPlayerEngine,
    private val environmentProvider: JellyfinEnvironmentProvider,
) : TvTrailerPreviewPlayer {
    override val events: Flow<TvTrailerPreviewPlayerEvent> =
        merge(
            engine.events.mapNotNull(PlayerEvent::toTrailerPreviewEvent),
            controller.state.filterIsInstance<PlaybackState.PlaybackError>().map { TvTrailerPreviewPlayerEvent.Failed },
            controller.state.trailerStallEvents(),
        )

    override suspend fun play(source: DetailTrailerSource.Local): Boolean {
        val environment = environmentProvider.current() ?: return false
        controller.play(
            PlaybackRequest.from(
                item = source.item,
                detail = source.detail,
                startPolicy = PlaybackStartPolicy.RESTART,
            ),
            environment,
        )
        return controller.state.value is PlaybackState.Active
    }

    override fun stop() {
        controller.stop(saveProgress = false)
    }

    override fun setSoundEnabled(enabled: Boolean) {
        engine.setAudioOutputEnabled(enabled)
    }
}

private fun Flow<PlaybackState>.trailerStallEvents(): Flow<TvTrailerPreviewPlayerEvent> =
    map(PlaybackState::trailerPlayingPositionMs).stalledTrailerEvents(TV_TRAILER_STALL_TIMEOUT_MS)

/** Position of a trailer that should be moving: active, not paused, and not at its end; null otherwise. */
internal fun PlaybackState.trailerPlayingPositionMs(): Long? =
    (this as? PlaybackState.Active)?.takeIf { !it.isPaused && it.phase != PlaybackPhase.Ended }?.positionMs

/**
 * Fails a trailer whose position stays put for [stallTimeoutMillis] while it should play. A stalled stream then
 * hands the stage back to the artwork instead of freezing on one frame with the UI hidden, whether the player
 * reports buffering or not. [this] is the playing position from [trailerPlayingPositionMs].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Long?>.stalledTrailerEvents(stallTimeoutMillis: Long): Flow<TvTrailerPreviewPlayerEvent> =
    distinctUntilChanged().transformLatest { positionMs ->
        if (positionMs != null) {
            delay(stallTimeoutMillis)
            emit(TvTrailerPreviewPlayerEvent.Failed)
        }
    }

internal fun PlayerEvent.toTrailerPreviewEvent(): TvTrailerPreviewPlayerEvent? =
    when (this) {
        PlayerEvent.Completed -> TvTrailerPreviewPlayerEvent.Completed
        PlayerEvent.VideoOutputStalled,
        -> TvTrailerPreviewPlayerEvent.Failed
        else -> null
    }

internal fun PlaybackState.toTrailerPreviewEvent(): TvTrailerPreviewPlayerEvent? =
    if (this is PlaybackState.PlaybackError) TvTrailerPreviewPlayerEvent.Failed else null

private const val TV_TRAILER_STALL_TIMEOUT_MS = 8_000L
