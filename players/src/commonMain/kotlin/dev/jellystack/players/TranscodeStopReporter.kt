package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.playback.StreamingPlayStrategy
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.core.playback.StreamingProgressReporter
import kotlinx.coroutines.CancellationException

/**
 * Progress reporter for sessions that must never touch the user's watch history, such as trailer previews.
 *
 * Start and progress are ignored. When the session ends, a server transcode it started is stopped through the
 * encoding endpoint, which, unlike a playback-stopped report, records no position or played state.
 */
class TranscodeStopReporter(
    private val playbackInfoService: JellyfinPlaybackInfoService,
    private val environmentProvider: JellyfinEnvironmentProvider,
) : StreamingProgressReporter {
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
    ) {
        if (context.strategy != StreamingPlayStrategy.TRANSCODED) return
        val playSessionId = context.playSessionId ?: return
        val environment = environmentProvider.current() ?: return
        try {
            playbackInfoService.stopEncoding(environment, playSessionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Best effort: the server also ends an abandoned transcode on its own after a while.
            JellystackLog.w("Stopping the preview transcode failed (${error.javaClass.simpleName})")
        }
    }
}
