package dev.jellystack.players.cast

import dev.jellystack.players.PlaybackPhase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface CastConnectionState {
    data object Idle : CastConnectionState

    data class Connecting(
        val deviceName: String?,
    ) : CastConnectionState

    data class Connected(
        val deviceName: String,
        val snapshot: CastSessionSnapshot,
    ) : CastConnectionState

    data class Error(
        val cause: Throwable?,
    ) : CastConnectionState
}

enum class CastStreamType {
    LIVE,
    BUFFERED,
}

data class CastSubtitleTrack(
    val id: String,
    val url: String,
    val mimeType: String,
    val language: String?,
    val label: String?,
    val isForced: Boolean,
)

data class CastSessionSnapshot(
    val mediaId: String,
    val title: String?,
    val seriesName: String?,
    val episodeName: String?,
    val artworkUrl: String?,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long?,
    val isPaused: Boolean,
    val contentType: String? = null,
    val streamType: CastStreamType = CastStreamType.BUFFERED,
    val subtitleTracks: List<CastSubtitleTrack> = emptyList(),
    val selectedSubtitleTrackId: String? = null,
    val phase: PlaybackPhase = PlaybackPhase.Ready,
)

interface CastSessionManager {
    val connectionState: SharedFlow<CastConnectionState>
    val remoteProgress: SharedFlow<Long>

    suspend fun play()

    suspend fun pause()

    suspend fun seek(positionMs: Long)

    suspend fun stop()

    suspend fun selectSubtitleTrack(trackId: String?)

    suspend fun disconnect()
}

object NoopCastSessionManager : CastSessionManager {
    private val idleState =
        MutableSharedFlow<CastConnectionState>(replay = 1).apply {
            tryEmit(CastConnectionState.Idle)
        }
    private val emptyProgress = MutableSharedFlow<Long>(extraBufferCapacity = 0)

    override val connectionState: SharedFlow<CastConnectionState> = idleState
    override val remoteProgress: SharedFlow<Long> = emptyProgress

    override suspend fun play() = Unit

    override suspend fun pause() = Unit

    override suspend fun seek(positionMs: Long) = Unit

    override suspend fun stop() = Unit

    override suspend fun selectSubtitleTrack(trackId: String?) = Unit

    override suspend fun disconnect() = Unit
}
