package dev.jellystack.players

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class IosPlayerEngine : PlayerEngine {
    private val positions = MutableSharedFlow<Long>(replay = 1)
    private val playerEvents = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)

    override val positionUpdates: Flow<Long> = positions.asSharedFlow()
    override val events: Flow<PlayerEvent> = playerEvents.asSharedFlow()

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        positions.tryEmit(startPositionMs)
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        positions.tryEmit(positionMs)
    }

    override fun setAudioTrack(track: AudioTrack?) = Unit

    override fun setSubtitleTrack(track: SubtitleTrack?) = Unit

    override fun setVideoQuality(maxBitrate: Int?) = Unit

    override fun release() = Unit
}
