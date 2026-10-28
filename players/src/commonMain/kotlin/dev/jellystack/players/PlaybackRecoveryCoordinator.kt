package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the recovery coordinator needs from the controller that owns the playback session. */
internal interface PlaybackRecoveryHost {
    /** Changes whenever playback is stopped or replaced; recovery attempts started under an older value are void. */
    val generation: Long
    val released: Boolean
    val session: PlaybackSession?
    val environment: JellyfinEnvironment?

    /** True while a Cast receiver, not the local player, owns playback. */
    val castOwnsPlayback: Boolean

    /** Stores [session] as the current one and publishes it. */
    fun commit(session: PlaybackSession)

    /** Ends playback with an error state. */
    fun fail(error: Throwable)
}

/**
 * Runs the fallback chain ([PlaybackFallbackState]) of ever more compatible streams after a player failure.
 * At most one attempt runs at a time, it is cancelled by [cancel], and it re-checks
 * [PlaybackRecoveryHost.generation] after every suspension so it can never resurrect a stopped item or overwrite
 * a newer one.
 */
internal class PlaybackRecoveryCoordinator(
    private val scope: CoroutineScope,
    private val engine: PlayerEngine,
    private val resolver: PlaybackSourceResolver,
    private val fallbackState: PlaybackFallbackState,
    private val host: PlaybackRecoveryHost,
) {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Starts the next fallback step; false when none is left (or none applies), so the caller must fail. */
    fun attemptFallback(
        originalError: Throwable? = null,
        audioOutputUnavailable: Boolean = false,
    ): Boolean {
        if (job?.isActive == true) return true
        val current = host.session ?: return false
        val environment = host.environment ?: return false
        if (current.stream.mode == PlaybackMode.LOCAL || current.source.supportsTranscoding != true) return false
        val options = fallbackState.nextFallback(current, originalError, audioOutputUnavailable) ?: return false
        val generation = host.generation
        launchAttempt(generation, current.mediaId, originalError) {
            val beforeResolve = currentSession(generation, current.mediaId) ?: return@launchAttempt
            val source =
                resolver.resolve(
                    request = beforeResolve.request,
                    selection = beforeResolve.stream,
                    environment = environment,
                    startPositionMs = beforeResolve.positionMs,
                    options = options,
                )
            val beforePrepare = currentSession(generation, current.mediaId) ?: return@launchAttempt
            val resolvedStream = beforePrepare.stream.withResolvedSource(source)
            val resolvedSource =
                source.copy(
                    audioStreamIndex = beforePrepare.audioTrack?.streamIndex,
                    subtitleStreamIndex = beforePrepare.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                )
            engine.prepare(
                source = resolvedSource,
                startPositionMs = beforePrepare.positionMs,
                audioTrack = beforePrepare.audioTrack,
                subtitleTrack = beforePrepare.subtitleTrack,
            )
            val prepared = currentSession(generation, current.mediaId) ?: return@launchAttempt
            if (options.forceAudioTranscoding) fallbackState.onCompatibilityAudioPrepared()
            resume(
                prepared.copy(
                    source = resolvedSource,
                    stream = resolvedStream,
                    qualityOptions = resolvedStream.qualityOptions,
                    selectedQualityId = resolvedStream.selectedQualityId,
                    phase = PlaybackPhase.Buffering,
                    runtimeStats =
                        prepared.runtimeStats.copy(
                            playbackMode = resolvedStream.mode,
                            container = resolvedStream.container,
                            videoCodec = resolvedStream.videoCodec,
                            audioCodec = resolvedStream.audioCodec,
                            videoBitrate = resolvedStream.videoBitrate,
                        ),
                ),
            )
        }
        return true
    }

    private fun launchAttempt(
        generation: Long,
        mediaId: String,
        originalError: Throwable?,
        block: suspend () -> Unit,
    ) {
        job =
            scope.launch {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (currentSession(generation, mediaId) != null) {
                        host.fail(originalError ?: error)
                    }
                }
            }
    }

    private fun currentSession(
        generation: Long,
        mediaId: String,
    ): PlaybackSession? {
        if (generation != host.generation || host.released) return null
        return host.session?.takeIf { it.mediaId == mediaId }
    }

    private fun resume(recovered: PlaybackSession) {
        host.commit(recovered)
        engine.setVideoQuality(recovered.stream.maxBitrate)
        engine.setPlaybackSpeed(recovered.playbackSpeed)
        if (recovered.isPaused || host.castOwnsPlayback) engine.pause() else engine.play()
    }
}
