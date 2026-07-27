package dev.jellystack.core.playback

import kotlinx.serialization.Serializable

@Serializable
sealed interface OfflinePlaybackEvent {
    val mediaId: String
    val timestampMs: Long

    @Serializable
    data class Progress(
        override val mediaId: String,
        val positionMs: Long,
        val durationMs: Long?,
        override val timestampMs: Long,
    ) : OfflinePlaybackEvent

    @Serializable
    data class Completed(
        override val mediaId: String,
        override val timestampMs: Long,
    ) : OfflinePlaybackEvent
}

interface OfflinePlaybackEventStore {
    fun read(): List<OfflinePlaybackEvent>

    fun write(events: List<OfflinePlaybackEvent>)
}

interface OfflinePlaybackProgressReporter {
    suspend fun reportOfflineProgress(
        mediaId: String,
        positionMs: Long,
    )

    suspend fun markOfflinePlaybackCompleted(mediaId: String)
}

interface OfflineProgressSyncer {
    suspend fun onProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long?,
    )

    suspend fun onCompleted(mediaId: String)

    suspend fun flush()
}

object NoopOfflineProgressSyncer : OfflineProgressSyncer {
    override suspend fun onProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long?,
    ) = Unit

    override suspend fun onCompleted(mediaId: String) = Unit

    override suspend fun flush() = Unit
}
