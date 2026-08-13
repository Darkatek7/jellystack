package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

internal class TvPlaybackTrailerPreviewPlayer(
    private val controller: PlaybackController,
    private val engine: AndroidPlayerEngine,
    private val environmentProvider: JellyfinEnvironmentProvider,
) : TvTrailerPreviewPlayer {
    override val events: Flow<TvTrailerPreviewPlayerEvent> =
        merge(
            engine.events.mapNotNull(PlayerEvent::toTrailerPreviewEvent),
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

internal fun PlayerEvent.toTrailerPreviewEvent(): TvTrailerPreviewPlayerEvent? =
    when (this) {
        PlayerEvent.Completed -> TvTrailerPreviewPlayerEvent.Completed
        PlayerEvent.VideoOutputStalled,
        -> TvTrailerPreviewPlayerEvent.Failed
        else -> null
    }

internal fun PlaybackState.toTrailerPreviewEvent(): TvTrailerPreviewPlayerEvent? =
    if (this is PlaybackState.PlaybackError) TvTrailerPreviewPlayerEvent.Failed else null
