package dev.jellystack.players.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.cast.CastConnectionState.Connected
import dev.jellystack.players.cast.CastConnectionState.Connecting
import dev.jellystack.players.cast.CastConnectionState.Error
import dev.jellystack.players.cast.CastConnectionState.Idle
import dev.jellystack.players.cast.CastMediaNotificationManager.Controls
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import dev.jellystack.players.cast.CastSessionSnapshot as Snapshot

private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
private const val DEFAULT_DEVICE_NAME = "Chromecast"
private const val CAST_SUSPENSION_REASON_NETWORK = 2
private const val PROGRESS_LOG_INTERVAL_MS = 5_000L

class GoogleCastSessionManager(
    context: Context,
    private val castContext: CastContext = CastContext.getSharedInstance(context.applicationContext),
    private val diagnosticsSink: CastDiagnosticsSink? = null,
) : CastSessionManager {
    private val appContext = context.applicationContext
    private val sessionManager: SessionManager = castContext.sessionManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionStateInternal =
        MutableSharedFlow<CastConnectionState>(replay = 1).apply {
            tryEmit(Idle)
        }
    private val remoteProgressInternal = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 4)

    override val connectionState: SharedFlow<CastConnectionState> = connectionStateInternal.asSharedFlow()
    override val remoteProgress: SharedFlow<Long> = remoteProgressInternal.asSharedFlow()

    private val notificationManager =
        CastMediaNotificationManager(
            context = appContext,
            scope = scope,
            controls =
                object : Controls {
                    override suspend fun onPlay() {
                        play()
                    }

                    override suspend fun onPause() {
                        pause()
                    }

                    override suspend fun onStop() {
                        stop()
                    }

                    override suspend fun onSeek(positionMs: Long) {
                        seek(positionMs)
                    }
                },
        )
    private var snapshotProvider: (() -> Snapshot?)? = null
    private var currentSnapshot: Snapshot? = null
    private var currentSession: CastSession? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    private val castPlayer =
        CastPlayer(
            clientProvider = { remoteMediaClient },
            onLoadResult = { status ->
                if (!status.isSuccess) {
                    val message = "Cast load failed (${status.statusCode})"
                    Napier.w(tag = "Cast", message = message)
                    diagnosticsSink?.onLastError(message)
                } else {
                    Napier.d(tag = "Cast", message = "Cast load succeeded")
                }
            },
            onSubtitleFallback = { message ->
                diagnosticsSink?.onLastError(message)
            },
        )
    private var lastProgressLogMs: Long = 0L

    private val progressListener =
        RemoteMediaClient.ProgressListener { progressMs, durationMs ->
            if (progressMs < 0) return@ProgressListener
            currentSnapshot =
                currentSnapshot
                    ?.copy(
                        positionMs = progressMs,
                        durationMs = durationMs.takeIf { it > 0 } ?: currentSnapshot?.durationMs,
                        isPaused = false,
                        phase = PlaybackPhase.Ready,
                    )
            remoteProgressInternal.tryEmit(progressMs)
            notificationManager.onProgress(progressMs)
            val now = System.currentTimeMillis()
            if (now - lastProgressLogMs >= PROGRESS_LOG_INTERVAL_MS) {
                lastProgressLogMs = now
                Napier.d(tag = "Cast", message = "Remote progress: ${progressMs}ms")
            }
        }

    private val mediaClientListener =
        object : RemoteMediaClient.Listener {
            override fun onStatusUpdated() {
                val client = remoteMediaClient ?: return
                val status = client.mediaStatus ?: return
                val snapshot = status.toSnapshot() ?: return
                currentSnapshot = snapshot
                postConnected(snapshot)
                when (status.playerState) {
                    MediaStatus.PLAYER_STATE_IDLE -> handleIdleStatus(status)
                    MediaStatus.PLAYER_STATE_BUFFERING -> Unit
                    MediaStatus.PLAYER_STATE_PLAYING -> Unit
                    MediaStatus.PLAYER_STATE_UNKNOWN -> Unit
                    MediaStatus.PLAYER_STATE_PAUSED -> Unit
                }
            }

            override fun onMetadataUpdated() {
                val client = remoteMediaClient ?: return
                client.mediaStatus?.toSnapshot()?.let {
                    currentSnapshot = it
                    postConnected(it)
                }
            }

            override fun onQueueStatusUpdated() = Unit

            override fun onPreloadStatusUpdated() = Unit

            override fun onAdBreakStatusUpdated() = Unit

            override fun onSendingRemoteMediaRequest() = Unit
        }

    private val sessionListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                currentSession = session
                Napier.i(tag = "Cast", message = "Session starting: ${session.deviceNameOrDefault()}")
                postState(Connecting(session.deviceNameOrDefault()))
            }

            override fun onSessionStarted(
                session: CastSession,
                sessionId: String,
            ) {
                Napier.i(tag = "Cast", message = "Session started: ${session.deviceNameOrDefault()} ($sessionId)")
                onSessionConnected(session)
            }

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int,
            ) {
                Napier.w(tag = "Cast", message = "Session start failed (code=$error)")
                val message = "Cast session start failed ($error)"
                diagnosticsSink?.onLastError(message)
                postState(Error(RuntimeException(message)))
            }

            override fun onSessionEnding(session: CastSession) {
                Napier.d(tag = "Cast", message = "Session ending for ${session.deviceNameOrDefault()}")
            }

            override fun onSessionEnded(
                session: CastSession,
                error: Int,
            ) {
                if (error != 0) {
                    Napier.w(tag = "Cast", message = "Session ended with error code $error")
                    diagnosticsSink?.onLastError("Cast session ended ($error)")
                }
                detachClient()
                currentSession = null
                currentSnapshot = null
                postState(Idle)
            }

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String,
            ) {
                currentSession = session
                Napier.i(tag = "Cast", message = "Session resuming: ${session.deviceNameOrDefault()} ($sessionId)")
                postState(Connecting(session.deviceNameOrDefault()))
            }

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean,
            ) {
                Napier.i(tag = "Cast", message = "Session resumed: ${session.deviceNameOrDefault()} (suspended=$wasSuspended)")
                onSessionConnected(session)
            }

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int,
            ) {
                Napier.w(tag = "Cast", message = "Session resume failed (code=$error)")
                val message = "Cast session resume failed ($error)"
                diagnosticsSink?.onLastError(message)
                postState(Error(RuntimeException(message)))
            }

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int,
            ) {
                Napier.i(tag = "Cast", message = "Session suspended (reason=$reason)")
                if (reason == CAST_SUSPENSION_REASON_NETWORK) {
                    val message = "Cast session suspended due to network loss"
                    diagnosticsSink?.onLastError(message)
                    postState(Error(RuntimeException(message)))
                    scope.launch {
                        sessionManager.endCurrentSession(true)
                    }
                }
            }
        }

    init {
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.sessionManager.currentCastSession?.let { active ->
            onSessionConnected(active)
        }
    }

    fun setSnapshotProvider(provider: (() -> Snapshot?)?) {
        snapshotProvider = provider
    }

    override suspend fun play() {
        val snapshot = snapshotForControl() ?: return
        diagnosticsSink?.onLoadRequest(snapshot.toLoadRequestSummary(autoplay = true))
        val loadResult = castPlayer.ensureLoaded(snapshot)
        castPlayer.play()
        currentSnapshot =
            snapshot.copy(
                isPaused = false,
                selectedSubtitleTrackId = loadResult.selectedTrackId,
                phase = PlaybackPhase.Buffering,
            )
        postConnected(currentSnapshot ?: snapshot)
    }

    override suspend fun pause() {
        val snapshot = snapshotForControl() ?: return
        castPlayer.pause()
        currentSnapshot = snapshot.copy(isPaused = true, phase = PlaybackPhase.Ready)
        postConnected(currentSnapshot ?: snapshot)
    }

    override suspend fun seek(positionMs: Long) {
        val snapshot = snapshotForControl() ?: return
        diagnosticsSink?.onLoadRequest(snapshot.toLoadRequestSummary(autoplay = !snapshot.isPaused, positionMs = positionMs))
        val loadResult = castPlayer.ensureLoaded(snapshot)
        castPlayer.seek(positionMs)
        currentSnapshot =
            snapshot.copy(
                positionMs = positionMs,
                selectedSubtitleTrackId = loadResult.selectedTrackId,
            )
        postConnected(currentSnapshot ?: snapshot)
    }

    override suspend fun stop() {
        castPlayer.stop()
        currentSnapshot = currentSnapshot?.copy(isPaused = true)
        postState(Idle)
    }

    override suspend fun selectSubtitleTrack(trackId: String?) {
        val snapshot = snapshotForControl() ?: return
        val result = castPlayer.selectSubtitleTrack(snapshot, trackId)
        currentSnapshot =
            snapshot.copy(
                selectedSubtitleTrackId = result.selectedTrackId,
            )
        postConnected(currentSnapshot ?: snapshot)
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.Main) {
            sessionManager.endCurrentSession(true)
        }
    }

    fun release() {
        sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        detachClient()
        notificationManager.release()
        scope.cancel()
        snapshotProvider = null
    }

    private fun onSessionConnected(session: CastSession) {
        currentSession = session
        attachClient(session.remoteMediaClient)
        val client = remoteMediaClient
        val deviceName = session.deviceNameOrDefault()
        val statusSnapshot = client?.mediaStatus?.toSnapshot()
        when {
            statusSnapshot != null -> {
                currentSnapshot = statusSnapshot
                postConnected(statusSnapshot)
            }
            snapshotProvider != null -> {
                val snapshot = snapshotProvider?.invoke()
                if (snapshot != null) {
                    scope.launch {
                        diagnosticsSink?.onLoadRequest(snapshot.toLoadRequestSummary(autoplay = !snapshot.isPaused))
                        val loadResult = castPlayer.ensureLoaded(snapshot)
                        val adjustedSnapshot = snapshot.copy(selectedSubtitleTrackId = loadResult.selectedTrackId)
                        if (!adjustedSnapshot.isPaused) {
                            castPlayer.play()
                        }
                        currentSnapshot = adjustedSnapshot
                        postConnected(adjustedSnapshot)
                    }
                } else {
                    postState(Connected(deviceName, defaultSnapshot(deviceName)))
                }
            }
            else -> {
                postState(Connected(deviceName, defaultSnapshot(deviceName)))
            }
        }
    }

    private fun attachClient(client: RemoteMediaClient?) {
        if (client == null) return
        if (remoteMediaClient === client) return
        detachClient()
        remoteMediaClient = client
        client.addListener(mediaClientListener)
        client.addProgressListener(progressListener, PROGRESS_UPDATE_INTERVAL_MS)
    }

    private fun detachClient() {
        remoteMediaClient?.let { client ->
            client.removeListener(mediaClientListener)
            client.removeProgressListener(progressListener)
        }
        remoteMediaClient = null
    }

    private fun handleIdleStatus(status: MediaStatus) {
        when (status.idleReason) {
            MediaStatus.IDLE_REASON_FINISHED -> {
                currentSnapshot =
                    currentSnapshot?.copy(
                        positionMs = status.streamPosition,
                        isPaused = true,
                        phase = PlaybackPhase.Ended,
                    )
                currentSnapshot?.let(::postConnected) ?: postState(Idle)
            }
            MediaStatus.IDLE_REASON_CANCELED -> {
                postState(Idle)
            }
            MediaStatus.IDLE_REASON_ERROR -> {
                diagnosticsSink?.onLastError("Cast playback error")
                postState(Error(RuntimeException("Cast playback error")))
            }
            MediaStatus.IDLE_REASON_NONE -> Unit
            MediaStatus.IDLE_REASON_INTERRUPTED -> {
                postState(Idle)
            }
        }
    }

    private fun snapshotForControl(): Snapshot? =
        currentSnapshot
            ?: snapshotProvider?.invoke().also { provided ->
                currentSnapshot = provided
            }

    private fun postState(state: CastConnectionState) {
        notificationManager.updateState(state)
        diagnosticsSink?.onSessionState(state)
        scope.launch {
            connectionStateInternal.emit(state)
        }
    }

    private fun postConnected(snapshot: Snapshot) {
        val deviceName = currentSession?.deviceNameOrDefault() ?: DEFAULT_DEVICE_NAME
        val state = Connected(deviceName, snapshot)
        currentSnapshot = snapshot
        notificationManager.updateState(state)
        diagnosticsSink?.onSessionState(state)
        scope.launch {
            connectionStateInternal.emit(state)
        }
    }

    private fun MediaStatus.toSnapshot(): Snapshot? {
        val info = mediaInfo ?: return null
        val mediaId =
            info.customData?.optString("mediaId")?.takeIf { !it.isNullOrBlank() }
                ?: info.contentId
                ?: return null
        val metadata = info.metadata
        val artwork =
            metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
        val durationMs =
            info.streamDuration
                .takeIf { it > 0 }
                ?.let { (it * 1000).toLong() }
        val positionMs = streamPosition
        val isPaused = playerState == MediaStatus.PLAYER_STATE_PAUSED
        val phase =
            when (playerState) {
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_UNKNOWN -> PlaybackPhase.Buffering
                MediaStatus.PLAYER_STATE_IDLE ->
                    if (idleReason == MediaStatus.IDLE_REASON_FINISHED) PlaybackPhase.Ended else PlaybackPhase.Ready
                else -> PlaybackPhase.Ready
            }
        val streamType =
            when (info.streamType) {
                MediaInfo.STREAM_TYPE_LIVE -> CastStreamType.LIVE
                else -> CastStreamType.BUFFERED
            }
        val subtitleTracks =
            info.mediaTracks
                ?.filter { it.type == MediaTrack.TYPE_TEXT }
                ?.map { track ->
                    val appTrackId =
                        track
                            .customDataOrNull()
                            ?.optString("appTrackId")
                            ?.takeIf { !it.isNullOrBlank() }
                            ?: track.id.toString()
                    CastSubtitleTrack(
                        id = appTrackId,
                        url = track.contentId ?: "",
                        mimeType = track.contentType ?: "text/vtt",
                        language = track.language,
                        label = track.name,
                        isForced = track.subtype == MediaTrack.SUBTYPE_SUBTITLES,
                    )
                } ?: emptyList()
        val selectedSubtitleId =
            activeTrackIds
                ?.firstOrNull()
                ?.let { activeTrackId ->
                    info.mediaTracks
                        ?.firstOrNull { track -> track.id == activeTrackId }
                        ?.let { selectedTrack ->
                            selectedTrack
                                .customDataOrNull()
                                ?.optString("appTrackId")
                                ?.takeIf { !it.isNullOrBlank() }
                                ?: selectedTrack.id.toString()
                        }
                }
        return Snapshot(
            mediaId = mediaId,
            title = metadata?.getString(MediaMetadata.KEY_TITLE),
            seriesName = metadata?.getString(MediaMetadata.KEY_SERIES_TITLE),
            episodeName = metadata?.getString(MediaMetadata.KEY_SUBTITLE),
            artworkUrl = artwork,
            streamUrl = info.contentId ?: "",
            positionMs = positionMs,
            durationMs = durationMs,
            isPaused = isPaused,
            contentType = info.contentType,
            streamType = streamType,
            subtitleTracks = subtitleTracks,
            selectedSubtitleTrackId = selectedSubtitleId,
            phase = phase,
        )
    }

    private fun defaultSnapshot(deviceName: String): Snapshot =
        Snapshot(
            mediaId = "cast-$deviceName",
            title = "Ready to cast",
            seriesName = null,
            episodeName = null,
            artworkUrl = null,
            streamUrl = "",
            positionMs = 0L,
            durationMs = null,
            isPaused = true,
            contentType = null,
            streamType = CastStreamType.BUFFERED,
            subtitleTracks = emptyList(),
            selectedSubtitleTrackId = null,
            phase = PlaybackPhase.Ready,
        )

    private fun CastSession.deviceNameOrDefault(): String = castDevice?.friendlyName ?: DEFAULT_DEVICE_NAME

    private val MediaInfo.customData: JSONObject?
        get() =
            try {
                customData
            } catch (ignored: Exception) {
                null
            }

    private fun MediaTrack.customDataOrNull(): JSONObject? =
        try {
            customData
        } catch (ignored: Exception) {
            null
        }

    private fun Snapshot.toLoadRequestSummary(
        autoplay: Boolean,
        positionMs: Long = this.positionMs,
    ): CastLoadRequestSummary =
        CastLoadRequestSummary(
            mediaId = mediaId,
            streamUrl = streamUrl,
            contentType = contentType,
            streamType = streamType,
            subtitleCount = subtitleTracks.size,
            positionMs = positionMs,
            autoplay = autoplay,
        )
}
