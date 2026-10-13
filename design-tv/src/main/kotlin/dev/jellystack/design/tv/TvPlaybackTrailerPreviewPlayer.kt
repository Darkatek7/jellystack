package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

internal class TvPlaybackTrailerPreviewPlayer(
    private val controller: PlaybackController,
    private val engine: AndroidPlayerEngine,
    private val environmentProvider: JellyfinEnvironmentProvider,
) : TvTrailerPreviewPlayer {
    override val events: Flow<TvTrailerPreviewPlayerEvent> =
        merge(
            engine.events.trailerPreviewEvents(),
            controller.state.filterIsInstance<PlaybackState.PlaybackError>().map { TvTrailerPreviewPlayerEvent.Failed },
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

/**
 * The preview events of a trailer's engine events. A trailer that keeps buffering for [stallTimeoutMillis] fails,
 * so a stalled stream hands the stage back to the artwork instead of freezing on one frame with the UI hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<PlayerEvent>.trailerPreviewEvents(
    stallTimeoutMillis: Long = TV_TRAILER_STALL_TIMEOUT_MS,
): Flow<TvTrailerPreviewPlayerEvent> =
    filter { it == PlayerEvent.Buffering || it == PlayerEvent.Ready || it.toTrailerPreviewEvent() != null }
        .transformLatest { event ->
            if (event == PlayerEvent.Buffering) {
                delay(stallTimeoutMillis)
                emit(TvTrailerPreviewPlayerEvent.Failed)
            } else {
                event.toTrailerPreviewEvent()?.let { emit(it) }
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
