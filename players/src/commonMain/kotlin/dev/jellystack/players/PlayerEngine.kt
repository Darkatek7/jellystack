package dev.jellystack.players

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

sealed interface PlayerEvent {
    data object Buffering : PlayerEvent

    data object Ready : PlayerEvent

    data object Completed : PlayerEvent

    data class AudioTrackSelectionApplied(
        val trackId: String,
    ) : PlayerEvent

    data class AudioTrackSelectionUnavailable(
        val trackId: String,
    ) : PlayerEvent

    data class Error(
        val throwable: Throwable,
    ) : PlayerEvent
}

enum class AudioTrackSelectionResult {
    APPLIED,
    PENDING,
    UNAVAILABLE,
}

interface PlayerEngine {
    val positionUpdates: Flow<Long>
    val events: Flow<PlayerEvent>

    suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    )

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setAudioTrack(track: AudioTrack?): AudioTrackSelectionResult

    fun setSubtitleTrack(track: SubtitleTrack?)

    fun setVideoQuality(maxBitrate: Int?)

    fun release()
}

class NoopPlayerEngine : PlayerEngine {
    override val positionUpdates: Flow<Long> = emptyFlow()
    override val events: Flow<PlayerEvent> = emptyFlow()

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setAudioTrack(track: AudioTrack?): AudioTrackSelectionResult = AudioTrackSelectionResult.PENDING

    override fun setSubtitleTrack(track: SubtitleTrack?) = Unit

    override fun setVideoQuality(maxBitrate: Int?) = Unit

    override fun release() = Unit
}
