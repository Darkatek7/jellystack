package dev.jellystack.players

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class PlaybackRuntimeStats(
    val playbackMode: PlaybackMode? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoBitrate: Int? = null,
    val frameRate: Float? = null,
    val hdr: String? = null,
    val bufferedDurationMs: Long? = null,
    val droppedFrames: Int? = null,
)

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

    data class SubtitleTrackSelectionApplied(
        val trackId: String?,
    ) : PlayerEvent

    data class SubtitleTrackSelectionUnavailable(
        val trackId: String?,
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

enum class SubtitleTrackSelectionResult {
    APPLIED,
    PENDING,
    UNAVAILABLE,
}

interface PlayerEngine {
    val positionUpdates: Flow<Long>
    val events: Flow<PlayerEvent>
    val runtimeStats: Flow<PlaybackRuntimeStats>
        get() = emptyFlow()

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

    fun setSubtitleTrack(track: SubtitleTrack?): SubtitleTrackSelectionResult

    fun setVideoQuality(maxBitrate: Int?)

    fun setPlaybackSpeed(speed: Float) = Unit

    fun release()
}

class NoopPlayerEngine : PlayerEngine {
    override val positionUpdates: Flow<Long> = emptyFlow()
    override val events: Flow<PlayerEvent> = emptyFlow()
    override val runtimeStats: Flow<PlaybackRuntimeStats> = emptyFlow()

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

    override fun setSubtitleTrack(track: SubtitleTrack?): SubtitleTrackSelectionResult =
        SubtitleTrackSelectionResult.APPLIED

    override fun setVideoQuality(maxBitrate: Int?) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun release() = Unit
}
