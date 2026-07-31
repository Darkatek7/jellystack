package dev.jellystack.core.playback

import dev.jellystack.core.jellyfin.JellyfinBrowseRepository

data class StreamingProgressContext(
    val mediaId: String,
    val mediaSourceId: String?,
    val playSessionId: String?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val strategy: StreamingPlayStrategy,
)

enum class StreamingPlayStrategy {
    DIRECT,
    TRANSCODED,
}

interface StreamingProgressReporter {
    suspend fun onStart(
        context: StreamingProgressContext,
        positionMs: Long,
    )

    suspend fun onProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    )

    suspend fun onStop(
        context: StreamingProgressContext,
        positionMs: Long?,
        completed: Boolean,
    )
}

object NoopStreamingProgressReporter : StreamingProgressReporter {
    override suspend fun onStart(
        context: StreamingProgressContext,
        positionMs: Long,
    ) = Unit

    override suspend fun onProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    ) = Unit

    override suspend fun onStop(
        context: StreamingProgressContext,
        positionMs: Long?,
        completed: Boolean,
    ) = Unit
}

class JellyfinStreamingProgressReporter(
    private val repository: JellyfinBrowseRepository,
) : StreamingProgressReporter {
    override suspend fun onStart(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        repository.startStreamingPlayback(context, positionMs)
    }

    override suspend fun onProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        repository.reportStreamingProgress(context, positionMs)
    }

    override suspend fun onStop(
        context: StreamingProgressContext,
        positionMs: Long?,
        completed: Boolean,
    ) {
        repository.stopStreamingPlayback(context, positionMs, completed)
    }
}
