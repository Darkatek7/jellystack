package dev.jellystack.players

import dev.jellystack.core.currentPlatform
import dev.jellystack.core.downloads.OfflineMediaStore
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.playback.NoopOfflineProgressSyncer
import dev.jellystack.core.playback.NoopStreamingProgressReporter
import dev.jellystack.core.playback.OfflineProgressSyncer
import dev.jellystack.core.playback.StreamingPlayStrategy
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.core.playback.StreamingProgressReporter
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.CastSessionSnapshot
import dev.jellystack.players.cast.CastStreamType
import dev.jellystack.players.cast.CastSubtitleTrack
import dev.jellystack.players.cast.NoopCastSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class PlaybackController(
    private val progressStore: PlaybackProgressStore = InMemoryPlaybackProgressStore(),
    private val streamSelector: PlaybackStreamSelector = PlaybackStreamSelector(),
    private val playbackSourceResolver: PlaybackSourceResolver = JellyfinPlaybackSourceResolver(),
    private val playerEngine: PlayerEngine = NoopPlayerEngine(),
    private val offlineMediaStore: OfflineMediaStore? = null,
    private val offlineSourceResolver: OfflinePlaybackSourceResolver = NoOfflinePlaybackSourceResolver,
    private val offlineProgressSyncer: OfflineProgressSyncer = NoopOfflineProgressSyncer,
    private val streamingProgressReporter: StreamingProgressReporter = NoopStreamingProgressReporter,
    private val subtitlePreferenceStore: SubtitlePreferenceStore = NoopSubtitlePreferenceStore,
    private val playbackPreferencesProvider: PlaybackPreferencesProvider = DefaultPlaybackPreferencesProvider,
    private val playbackNetworkClassifier: PlaybackNetworkClassifier = UnknownPlaybackNetworkClassifier,
    private val castSessionManager: CastSessionManager = NoopCastSessionManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val state: StateFlow<PlaybackState> = _state
    private val _notices = MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<PlaybackNotice> = _notices.asSharedFlow()

    private val deviceName = currentPlatform().name
    private var session: PlaybackSession? = null
    private var lastPersisted: PlaybackProgress? = null
    private var progressJob: Job? = null
    private var eventsJob: Job? = null
    private var runtimeStatsJob: Job? = null
    private var lastEnvironment: JellyfinEnvironment? = null
    private var castState: CastConnectionState = CastConnectionState.Idle
    private var castStateJob: Job? = null
    private var castProgressJob: Job? = null
    private var remoteDeviceName: String? = null
    private var handoffPhase: HandoffPhase = HandoffPhase.LOCAL
    private var stopRequestedByController: Boolean = false
    private var recoveryPromotionJob: Job? = null
    private var retryContext: RetryContext? = null
    private var qualitySwitchJob: Job? = null
    private var qualitySwitchGeneration = 0L
    private var audioSwitchJob: Job? = null
    private var audioSwitchGeneration = 0L
    private var subtitleSwitchJob: Job? = null
    private var subtitleSwitchGeneration = 0L
    private var playGeneration = 0L
    private var directFallbackAttempted = false
    private var hlsForcedTranscodeFallbackAttempted = false
    private var hlsContainerFallbackAttempted = false
    private val streamingReportMutex = Mutex()
    private var finalStreamingReportJob: Job? = null
    private var released = false

    init {
        startCastObservers()
    }

    val castManager: CastSessionManager
        get() = castSessionManager

    fun flushOfflineProgress() {
        scope.launch { offlineProgressSyncer.flush() }
    }

    fun resolveSubtitlePreference(
        item: JellyfinItem,
        tracks: List<SubtitleTrack>,
    ): SubtitlePreferenceResolution? =
        if (playbackPreferencesProvider.currentSettings().rememberSeriesTracks) {
            subtitlePreferenceStore
                .read(item.subtitlePreferenceScopeKey())
                ?.let { tracks.resolveSubtitlePreference(it) }
        } else {
            null
        }

    fun saveSubtitlePreference(
        item: JellyfinItem,
        track: SubtitleTrack?,
    ) {
        if (!playbackPreferencesProvider.currentSettings().rememberSeriesTracks) return
        subtitlePreferenceStore.write(
            scopeKey = item.subtitlePreferenceScopeKey(),
            preference = track?.toPreference() ?: disabledSubtitlePreference(),
        )
    }

    suspend fun play(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
    ) {
        check(!released) { "PlaybackController has been released" }
        stopInternal(saveProgress = true, clearRetryContext = false)
        directFallbackAttempted = false
        hlsForcedTranscodeFallbackAttempted = false
        hlsContainerFallbackAttempted = false
        val attemptGeneration = playGeneration
        retryContext = RetryContext(request, environment)
        _state.value =
            PlaybackState.Preparing(
                mediaId = request.mediaId,
                metadata = request.metadata,
                mediaKind = request.mediaKind,
            )
        stopRequestedByController = false
        handoffPhase = HandoffPhase.LOCAL
        lastEnvironment = environment
        try {
            offlineProgressSyncer.flush()
            ensureCurrentPlayAttempt(attemptGeneration)
            val appSettings = playbackPreferencesProvider.currentSettings()
            val preferenceResolver = PlaybackPreferenceResolver(appSettings, playbackNetworkClassifier.currentNetworkClass())
            val restart =
                request.startPolicy == PlaybackStartPolicy.RESTART ||
                    (request.startPolicy == PlaybackStartPolicy.INHERIT && appSettings.resumeMode == ResumeMode.RESTART)
            val savedStartingPosition =
                if (restart) {
                    0L
                } else {
                    progressStore.read(request.mediaId)?.positionMs
                        ?: ticksToMillis(request.resumePositionTicks)
                        ?: 0L
                }
            val offlineSource =
                offlineMediaStore
                    ?.read(request.mediaId)
                    ?.let { media ->
                        runCatching { offlineSourceResolver.resolve(media) }.getOrNull()
                    }
            val selection: PlaybackStreamSelection
            val source: ResolvedPlaybackSource
            val initialAudioTrack: AudioTrack?
            val requestedSubtitleTrack: SubtitleTrack?
            val durationMs = ticksToMillis(request.durationTicks)
            val startingPosition: Long

            if (offlineSource != null) {
                val offlineTracks = offlineTracksFor(request, offlineSource)
                selection =
                    PlaybackStreamSelection(
                        maxBitrate = null,
                        qualityOptions = emptyList(),
                        selectedQualityId = PlaybackQualityOption.AUTO_ID,
                        sourceId = "offline-${request.mediaId}",
                        mode = PlaybackMode.LOCAL,
                        container = null,
                        videoCodec = null,
                        audioCodec = null,
                        videoBitrate = null,
                        audioTracks = offlineTracks.audioTracks,
                        subtitleTracks = offlineTracks.subtitleTracks,
                    )
                initialAudioTrack = preferredAudioTrackFor(request, selection, preferenceResolver)
                requestedSubtitleTrack =
                    preferredSubtitleTrackFor(
                        request = request,
                        selection = selection,
                        preferenceResolver = preferenceResolver,
                        rememberSeriesTracks = appSettings.rememberSeriesTracks,
                    )
                source = offlineSource
                startingPosition = request.validPlaybackPosition(savedStartingPosition, selection.sourceId)
            } else {
                val automaticSelection = streamSelector.select(request.mediaSources, mediaKind = request.mediaKind)
                val preferredQuality = preferenceResolver.selectQualityOption(automaticSelection.qualityOptions)
                selection =
                    preferredQuality
                        ?.takeUnless { it.isAuto }
                        ?.let { streamSelector.select(request.mediaSources, preferred = it, mediaKind = request.mediaKind) }
                        ?: automaticSelection
                initialAudioTrack = preferredAudioTrackFor(request, selection, preferenceResolver)
                requestedSubtitleTrack =
                    preferredSubtitleTrackFor(
                        request = request,
                        selection = selection,
                        preferenceResolver = preferenceResolver,
                        rememberSeriesTracks = appSettings.rememberSeriesTracks,
                    )
                startingPosition = request.validPlaybackPosition(savedStartingPosition, selection.sourceId)
                source =
                    playbackSourceResolver.resolve(
                        request = request,
                        selection = selection,
                        environment = environment,
                        startPositionMs = startingPosition,
                        options =
                            PlaybackSourceOptions(
                                audioStreamIndex = initialAudioTrack?.streamIndex,
                                subtitleStreamIndex = requestedSubtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                            ),
                    )
            }
            ensureCurrentPlayAttempt(attemptGeneration)

            val negotiatedSelection = selection.withResolvedSource(source)
            val initialSubtitleTrack =
                requestedSubtitleTrack.reconciledWith(negotiatedSelection)
            val sourceWithPreferences =
                source.copy(
                    audioStreamIndex = initialAudioTrack?.streamIndex,
                    subtitleStreamIndex = initialSubtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                )

            val newSession =
                PlaybackSession(
                    request = request,
                    mediaId = request.mediaId,
                    stream = negotiatedSelection,
                    positionMs = startingPosition,
                    durationMs = durationMs,
                    audioTrack = initialAudioTrack,
                    subtitleTrack = initialSubtitleTrack,
                    isPaused = false,
                    source = sourceWithPreferences,
                    qualityOptions = negotiatedSelection.qualityOptions,
                    selectedQualityId = negotiatedSelection.selectedQualityId,
                    phase = PlaybackPhase.Buffering,
                    runtimeStats =
                        PlaybackRuntimeStats(
                            playbackMode = negotiatedSelection.mode,
                            container = negotiatedSelection.container,
                            videoCodec = negotiatedSelection.videoCodec,
                            audioCodec = negotiatedSelection.audioCodec,
                            videoBitrate = negotiatedSelection.videoBitrate,
                        ),
                )
            ensureCurrentPlayAttempt(attemptGeneration)
            playerEngine.prepare(
                source = sourceWithPreferences,
                startPositionMs = startingPosition,
                audioTrack = initialAudioTrack,
                subtitleTrack = initialSubtitleTrack,
            )
            ensureCurrentPlayAttempt(attemptGeneration)
            session = newSession
            startCollectors()
            playerEngine.setVideoQuality(negotiatedSelection.maxBitrate)
            playerEngine.setPlaybackSpeed(1f)
            publishCurrentState(newSession)
            if (isRemoteConnected()) {
                handoffPhase = HandoffPhase.CAST_ACTIVE
                scope.launch { castSessionManager.seek(startingPosition) }
                scope.launch { castSessionManager.play() }
                playerEngine.pause()
                publishCurrentState(newSession)
            } else {
                playerEngine.play()
            }
            streamingContext(newSession)?.let { context ->
                launchStreamingReport {
                    onStart(context, startingPosition)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (attemptGeneration == playGeneration && !released) {
                publishPlaybackError(error, request)
            }
        }
    }

    private suspend fun ensureCurrentPlayAttempt(attemptGeneration: Long) {
        currentCoroutineContext().ensureActive()
        if (attemptGeneration != playGeneration || released) {
            throw CancellationException("Playback attempt was superseded")
        }
    }

    fun pause() {
        session?.let {
            if (handoffPhase == HandoffPhase.RECOVERING) {
                cancelRecoveryPromotion()
                handoffPhase = HandoffPhase.LOCAL
            }
            if (isRemoteConnected()) {
                scope.launch { castSessionManager.pause() }
            } else if (handoffPhase != HandoffPhase.CAST_CONNECTING) {
                playerEngine.pause()
            } else {
                playerEngine.pause()
            }
            val updated = it.copy(isPaused = true)
            session = updated
            publishCurrentState(updated)
        }
    }

    fun resume() {
        session?.let {
            if (handoffPhase == HandoffPhase.RECOVERING) {
                cancelRecoveryPromotion()
                handoffPhase = HandoffPhase.LOCAL
            }
            val restarting = it.phase == PlaybackPhase.Ended
            val updated =
                it.copy(
                    positionMs = if (restarting) 0L else it.positionMs,
                    isPaused = false,
                    phase = if (restarting) PlaybackPhase.Buffering else it.phase,
                )
            session = updated
            publishCurrentState(updated)
            if (isRemoteConnected()) {
                if (restarting) scope.launch { castSessionManager.seek(0L) }
                scope.launch { castSessionManager.play() }
                playerEngine.pause()
            } else if (handoffPhase == HandoffPhase.CAST_CONNECTING) {
                if (restarting) playerEngine.seekTo(0L)
                playerEngine.pause()
            } else {
                if (restarting) playerEngine.seekTo(0L)
                playerEngine.play()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val current = session ?: return
        if (handoffPhase != HandoffPhase.LOCAL) return
        val normalized = PLAYBACK_SPEEDS.minByOrNull { candidate -> kotlin.math.abs(candidate - speed) } ?: 1f
        playerEngine.setPlaybackSpeed(normalized)
        val updated = current.copy(playbackSpeed = normalized)
        session = updated
        publishCurrentState(updated)
    }

    fun setStatsForNerdsEnabled(enabled: Boolean) {
        val current = session ?: return
        val updated = current.copy(statsForNerdsEnabled = enabled)
        session = updated
        publishCurrentState(updated)
    }

    fun stop(saveProgress: Boolean = true) {
        if (isRemoteConnected()) {
            stopRequestedByController = true
            scope.launch { castSessionManager.stop() }
        }
        stopInternal(saveProgress)
    }

    fun retry() {
        val context = retryContext ?: return
        scope.launch { play(context.request, context.environment) }
    }

    private fun stopInternal(
        saveProgress: Boolean,
        clearRetryContext: Boolean = true,
        terminalState: PlaybackState = PlaybackState.Stopped,
    ) {
        playGeneration += 1
        cancelQualitySwitch()
        cancelAudioSwitch()
        cancelSubtitleSwitch()
        cancelRecoveryPromotion()
        cancelCollectors()
        playerEngine.stop()
        handoffPhase = HandoffPhase.LOCAL
        val current = session
        if (saveProgress && current != null && current.phase != PlaybackPhase.Ended) {
            if (isNearCompletion(current)) {
                progressStore.clear(current.mediaId)
                if (current.stream.mode == PlaybackMode.LOCAL) {
                    scope.launch { offlineProgressSyncer.onCompleted(current.mediaId) }
                } else {
                    streamingContext(current)?.let { context ->
                        launchStreamingStop(
                            context = context,
                            positionMs = current.positionMs,
                            completed = true,
                        )
                    }
                }
            } else {
                persistProgress(current.mediaId, current.positionMs)
                if (current.stream.mode == PlaybackMode.LOCAL) {
                    scope.launch {
                        offlineProgressSyncer.onProgress(current.mediaId, current.positionMs, current.durationMs)
                    }
                } else {
                    streamingContext(current)?.let { context ->
                        launchStreamingStop(
                            context = context,
                            positionMs = current.positionMs,
                            completed = false,
                        )
                    }
                }
            }
        } else if (current != null && current.phase != PlaybackPhase.Ended && current.stream.mode != PlaybackMode.LOCAL) {
            streamingContext(current)?.let { context ->
                launchStreamingStop(
                    context = context,
                    positionMs = null,
                    completed = false,
                )
            }
        }
        session = null
        lastPersisted = null
        if (clearRetryContext) retryContext = null
        _state.value = terminalState
    }

    private fun publishPlaybackError(
        error: Throwable,
        request: PlaybackRequest? = retryContext?.request,
    ) {
        val failedRequest = request ?: return
        val errorState =
            PlaybackState.PlaybackError(
                message = error.message.orEmpty(),
                cause = error,
                mediaId = failedRequest.mediaId,
                metadata = failedRequest.metadata,
                canRetry = retryContext != null,
                mediaKind = failedRequest.mediaKind,
            )
        stopInternal(
            saveProgress = true,
            clearRetryContext = false,
            terminalState = errorState,
        )
    }

    private fun startCastObservers() {
        castStateJob?.cancel()
        castProgressJob?.cancel()
        castStateJob =
            scope.launch {
                castSessionManager.connectionState.collect { state ->
                    handleCastState(state)
                }
            }
        castProgressJob =
            scope.launch {
                castSessionManager.remoteProgress.collect { positionMs ->
                    handleRemoteProgress(positionMs)
                }
            }
    }

    private fun handleCastState(state: CastConnectionState) {
        castState = state
        when (state) {
            CastConnectionState.Idle -> onCastIdle()
            is CastConnectionState.Connecting -> onCastConnecting(state)
            is CastConnectionState.Connected -> onCastConnected(state)
            is CastConnectionState.Error -> onCastError(state)
        }
    }

    private fun onCastConnecting(state: CastConnectionState.Connecting) {
        remoteDeviceName = state.deviceName
        val current = session ?: return
        if (handoffPhase != HandoffPhase.CAST_CONNECTING && handoffPhase != HandoffPhase.CAST_ACTIVE) {
            handoffPhase = HandoffPhase.CAST_CONNECTING
            playerEngine.pause()
        }
        publishCurrentState(current)
    }

    private fun onCastConnected(state: CastConnectionState.Connected) {
        remoteDeviceName = state.deviceName
        cancelRecoveryPromotion()
        val shouldPauseLocal = handoffPhase != HandoffPhase.CAST_ACTIVE && handoffPhase != HandoffPhase.CAST_CONNECTING
        handoffPhase = HandoffPhase.CAST_ACTIVE
        val current = session
        if (current != null) {
            val subtitleFromRemote =
                if (state.snapshot.selectedSubtitleTrackId == null) {
                    null
                } else {
                    current.stream.subtitleTracks.firstOrNull { it.id == state.snapshot.selectedSubtitleTrackId }
                }
            val updated =
                current.copy(
                    positionMs = state.snapshot.positionMs,
                    durationMs = state.snapshot.durationMs ?: current.durationMs,
                    isPaused = state.snapshot.isPaused,
                    phase = state.snapshot.phase,
                    subtitleTrack =
                        if (state.snapshot.selectedSubtitleTrackId == null) {
                            null
                        } else {
                            subtitleFromRemote ?: current.subtitleTrack
                        },
                    source =
                        current.source.copy(
                            subtitleStreamIndex =
                                if (state.snapshot.selectedSubtitleTrackId == null) {
                                    null
                                } else {
                                    subtitleFromRemote?.streamIndex ?: current.source.subtitleStreamIndex
                                },
                        ),
                )
            session = updated
            publishCurrentState(updated, state.snapshot)
        }
        if (shouldPauseLocal) {
            playerEngine.pause()
        }
    }

    private fun onCastIdle() {
        remoteDeviceName = null
        if (stopRequestedByController) {
            stopRequestedByController = false
            handoffPhase = HandoffPhase.LOCAL
            return
        }
        recoverToLocal(reason = null)
    }

    private fun onCastError(state: CastConnectionState.Error) {
        remoteDeviceName = null
        if (stopRequestedByController) {
            stopRequestedByController = false
            handoffPhase = HandoffPhase.LOCAL
            return
        }
        recoverToLocal(reason = state.cause?.message.orEmpty())
    }

    private fun handleRemoteProgress(positionMs: Long) {
        if (!isRemoteConnected() && handoffPhase != HandoffPhase.CAST_CONNECTING) return
        val current = session ?: return
        if (current.phase == PlaybackPhase.Ended) return
        cancelRecoveryPromotion()
        handoffPhase = HandoffPhase.CAST_ACTIVE
        val updated = current.copy(positionMs = positionMs, isPaused = false, phase = PlaybackPhase.Ready)
        session = updated
        publishCurrentState(updated)
        persistProgressIfNeeded(updated)
    }

    private fun recoverToLocal(reason: String?) {
        val current =
            session ?: run {
                handoffPhase = HandoffPhase.LOCAL
                return
            }
        if (handoffPhase != HandoffPhase.CAST_ACTIVE && handoffPhase != HandoffPhase.CAST_CONNECTING) {
            return
        }
        cancelRecoveryPromotion()
        handoffPhase = HandoffPhase.RECOVERING
        val updated = current.copy(positionMs = current.positionMs)
        session = updated
        playerEngine.seekTo(updated.positionMs)
        if (updated.isPaused) {
            playerEngine.pause()
        } else {
            playerEngine.play()
        }
        publishCurrentState(updated, recoveryReason = reason)
        scheduleRecoveryPromotion(updated.mediaId)
    }

    private fun buildCastSnapshot(
        session: PlaybackSession,
        positionMs: Long = session.positionMs,
        isPaused: Boolean = session.isPaused,
    ): CastSessionSnapshot {
        val metadata = session.request.metadata
        val artworkFromMetadata = metadata?.artworkUrl ?: artworkUrlFor(session)
        val streamUrlForCast =
            applyPlaybackIndicesToUrl(
                url = session.source.url,
                audioStreamIndex = session.audioTrack?.streamIndex,
                subtitleStreamIndex = session.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
            )
        val subtitles =
            session.source.subtitles.map { subtitle ->
                CastSubtitleTrack(
                    id = subtitle.trackId,
                    url = subtitle.url,
                    mimeType = subtitle.mimeType,
                    language = subtitle.language,
                    label = subtitle.label,
                    isForced = subtitle.isForced,
                )
            }
        val streamType =
            if (session.durationMs == null && session.stream.mode == PlaybackMode.HLS) {
                CastStreamType.LIVE
            } else {
                CastStreamType.BUFFERED
            }
        return CastSessionSnapshot(
            mediaId = session.mediaId,
            title = metadata?.title,
            seriesName = metadata?.seriesName,
            episodeName = metadata?.episodeName,
            artworkUrl = artworkFromMetadata,
            streamUrl = streamUrlForCast,
            positionMs = positionMs,
            durationMs = session.durationMs,
            isPaused = isPaused,
            contentType = session.source.mimeType,
            streamType = streamType,
            subtitleTracks = subtitles,
            selectedSubtitleTrackId = session.subtitleTrack?.id,
            phase = session.phase,
        )
    }

    fun currentCastSnapshot(): CastSessionSnapshot? = session?.let { buildCastSnapshot(it) }

    fun seekTo(positionMs: Long) {
        if (positionMs < 0) return
        session?.let {
            if (handoffPhase == HandoffPhase.RECOVERING) {
                cancelRecoveryPromotion()
                handoffPhase = HandoffPhase.LOCAL
            }
            val updated = it.copy(positionMs = positionMs)
            session = updated
            publishCurrentState(updated)
            if (isRemoteConnected()) {
                scope.launch { castSessionManager.seek(positionMs) }
            } else if (handoffPhase != HandoffPhase.CAST_CONNECTING) {
                playerEngine.seekTo(positionMs)
            } else {
                playerEngine.seekTo(positionMs)
            }
            persistProgressIfNeeded(updated)
        }
    }

    fun updateProgress(positionMs: Long) {
        if (positionMs < 0) return
        session?.let {
            if (handoffPhase == HandoffPhase.RECOVERING) {
                cancelRecoveryPromotion()
                handoffPhase = HandoffPhase.LOCAL
            }
            val updated = it.copy(positionMs = positionMs)
            session = updated
            publishCurrentState(updated)
            persistProgressIfNeeded(updated)
        }
    }

    fun selectSubtitle(trackId: String?) {
        val current = session ?: return
        val subtitle =
            trackId?.let { id -> current.stream.subtitleTracks.find { it.id == id } } ?: run {
                if (trackId != null) return
                null
            }
        if (current.subtitleTrack?.id == subtitle?.id) return
        if (isRemoteConnected()) {
            confirmPendingSubtitleSelection(subtitle?.id)
            scope.launch { castSessionManager.selectSubtitleTrack(subtitle?.id) }
            return
        }
        if (qualitySwitchJob?.isActive == true) {
            applySubtitleTrackLocally(subtitle)
            return
        }
        if (current.source.mode == PlaybackMode.HLS) {
            switchHlsSubtitleTrack(current, subtitle)
            return
        }
        applySubtitleTrackLocally(subtitle)
    }

    private fun applySubtitleTrackLocally(subtitle: SubtitleTrack?) {
        val result =
            runCatching { playerEngine.setSubtitleTrack(subtitle) }
                .getOrDefault(SubtitleTrackSelectionResult.UNAVAILABLE)
        when (result) {
            SubtitleTrackSelectionResult.APPLIED -> confirmPendingSubtitleSelection(subtitle?.id)
            SubtitleTrackSelectionResult.PENDING -> Unit
            SubtitleTrackSelectionResult.UNAVAILABLE ->
                _notices.tryEmit(PlaybackNotice.SubtitleTrackSelectionFailed)
        }
    }

    private fun switchHlsSubtitleTrack(
        current: PlaybackSession,
        subtitle: SubtitleTrack?,
    ) {
        val environment = lastEnvironment ?: return
        cancelAudioSwitch()
        cancelSubtitleSwitch()
        val switchGeneration = subtitleSwitchGeneration
        val switchPlayGeneration = playGeneration
        subtitleSwitchJob =
            scope.launch {
                var prepareStarted = false
                try {
                    val resolved =
                        playbackSourceResolver.resolve(
                            request = current.request,
                            selection = current.stream,
                            environment = environment,
                            startPositionMs = current.positionMs,
                            options =
                                PlaybackSourceOptions(
                                    audioStreamIndex = current.audioTrack?.streamIndex,
                                    subtitleStreamIndex = subtitle?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                                    playSessionId = current.source.playSessionId,
                                ),
                        )
                    currentCoroutineContext().ensureActive()
                    val latest =
                        currentSessionForSubtitleSwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch
                    val selectedSubtitle = subtitle?.let { target -> latest.stream.subtitleTracks.firstOrNull { it.id == target.id } }
                    if (subtitle != null && selectedSubtitle == null) return@launch
                    val selectedAudio = latest.audioTrack.reconciledWith(latest.stream)
                    val position = latest.request.validPlaybackPosition(latest.positionMs, latest.stream.sourceId)
                    val preparedSource =
                        resolved.forQualitySwitch(
                            positionMs = position,
                            audioTrack = selectedAudio,
                            subtitleTrack = selectedSubtitle,
                        )
                    prepareStarted = true
                    playerEngine.prepare(
                        source = preparedSource,
                        startPositionMs = position,
                        audioTrack = selectedAudio,
                        subtitleTrack = selectedSubtitle,
                    )
                    currentCoroutineContext().ensureActive()
                    val confirmed =
                        currentSessionForSubtitleSwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch
                    playerEngine.setVideoQuality(confirmed.stream.maxBitrate)
                    playerEngine.setAudioTrack(selectedAudio)
                    if (confirmed.isPaused) playerEngine.pause() else playerEngine.play()
                    val updated =
                        confirmed.copy(
                            positionMs = position,
                            audioTrack = selectedAudio,
                            subtitleTrack = selectedSubtitle,
                            source = preparedSource,
                        )
                    session = updated
                    publishCurrentState(updated)
                    saveSubtitlePreference(updated, selectedSubtitle)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    if (
                        switchGeneration == subtitleSwitchGeneration &&
                        switchPlayGeneration == playGeneration
                    ) {
                        if (prepareStarted) restorePlaybackAfterTrackSwitchFailure(current)
                        _notices.emit(PlaybackNotice.SubtitleTrackSelectionFailed)
                    }
                }
            }
    }

    private fun currentSessionForSubtitleSwitch(
        switchGeneration: Long,
        switchPlayGeneration: Long,
        mediaId: String,
    ): PlaybackSession? {
        if (switchGeneration != subtitleSwitchGeneration || switchPlayGeneration != playGeneration || released) return null
        return session?.takeIf { it.mediaId == mediaId }
    }

    private fun cancelSubtitleSwitch() {
        subtitleSwitchGeneration += 1
        subtitleSwitchJob?.cancel()
        subtitleSwitchJob = null
    }

    fun resolveAudioPreference(
        item: JellyfinItem,
        tracks: List<AudioTrack>,
    ): AudioTrack? =
        if (playbackPreferencesProvider.currentSettings().rememberSeriesTracks) {
            subtitlePreferenceStore.readAudio(item.subtitlePreferenceScopeKey())?.let(tracks::resolveAudioPreference)
        } else {
            null
        }

    fun saveAudioPreference(
        item: JellyfinItem,
        track: AudioTrack,
    ) {
        if (!playbackPreferencesProvider.currentSettings().rememberSeriesTracks) return
        subtitlePreferenceStore.writeAudio(item.subtitlePreferenceScopeKey(), track.toPreference())
    }

    fun clearRememberedTrackPreferences() {
        subtitlePreferenceStore.clearAll()
    }

    fun selectAudioTrack(trackId: String) {
        val current = session ?: return
        val audio = current.stream.audioTracks.find { it.id == trackId } ?: return
        if (current.audioTrack?.id == audio.id) return

        if (qualitySwitchJob?.isActive == true) {
            when (playerEngine.setAudioTrack(audio)) {
                AudioTrackSelectionResult.APPLIED ->
                    confirmPendingAudioSelection(audio.id)
                AudioTrackSelectionResult.PENDING -> Unit
                AudioTrackSelectionResult.UNAVAILABLE ->
                    _notices.tryEmit(PlaybackNotice.AudioTrackSelectionFailed)
            }
            return
        }

        if (current.source.mode == PlaybackMode.HLS && !isRemoteConnected()) {
            cancelSubtitleSwitch()
            switchHlsAudioTrack(current, audio)
            return
        }

        when (playerEngine.setAudioTrack(audio)) {
            AudioTrackSelectionResult.APPLIED ->
                confirmPendingAudioSelection(audio.id)
            AudioTrackSelectionResult.PENDING -> Unit
            AudioTrackSelectionResult.UNAVAILABLE ->
                _notices.tryEmit(PlaybackNotice.AudioTrackSelectionFailed)
        }
    }

    private fun switchHlsAudioTrack(
        current: PlaybackSession,
        audio: AudioTrack,
    ) {
        val environment = lastEnvironment ?: return
        cancelAudioSwitch()
        val switchGeneration = audioSwitchGeneration
        val switchPlayGeneration = playGeneration
        audioSwitchJob =
            scope.launch {
                var prepareStarted = false
                try {
                    val resolved =
                        playbackSourceResolver.resolve(
                            request = current.request,
                            selection = current.stream,
                            environment = environment,
                            startPositionMs = current.positionMs,
                            options =
                                PlaybackSourceOptions(
                                    audioStreamIndex = audio.streamIndex,
                                    subtitleStreamIndex = current.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                                    playSessionId = current.source.playSessionId,
                                ),
                        )
                    currentCoroutineContext().ensureActive()
                    val latest =
                        currentSessionForAudioSwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch
                    val selectedAudio =
                        latest.stream.audioTracks.firstOrNull { it.id == audio.id }
                            ?: return@launch
                    val position =
                        latest.request.validPlaybackPosition(
                            latest.positionMs,
                            latest.stream.sourceId,
                        )
                    val selectedSubtitle = latest.subtitleTrack.reconciledWith(latest.stream)
                    val preparedSource =
                        resolved.forQualitySwitch(
                            positionMs = position,
                            audioTrack = selectedAudio,
                            subtitleTrack = selectedSubtitle,
                        )
                    prepareStarted = true
                    playerEngine.prepare(
                        source = preparedSource,
                        startPositionMs = position,
                        audioTrack = selectedAudio,
                        subtitleTrack = selectedSubtitle,
                    )
                    currentCoroutineContext().ensureActive()
                    val confirmed =
                        currentSessionForAudioSwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch
                    playerEngine.setVideoQuality(confirmed.stream.maxBitrate)
                    playerEngine.setAudioTrack(selectedAudio)
                    playerEngine.setSubtitleTrack(selectedSubtitle)
                    if (confirmed.isPaused) {
                        playerEngine.pause()
                    } else {
                        playerEngine.play()
                    }
                    val updated =
                        confirmed.copy(
                            positionMs = position,
                            audioTrack = selectedAudio,
                            subtitleTrack = selectedSubtitle,
                            source = preparedSource,
                        )
                    session = updated
                    publishCurrentState(updated)
                    saveAudioPreference(updated, selectedAudio)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    if (
                        switchGeneration == audioSwitchGeneration &&
                        switchPlayGeneration == playGeneration
                    ) {
                        if (prepareStarted) restorePlaybackAfterTrackSwitchFailure(current)
                        _notices.emit(PlaybackNotice.AudioTrackSelectionFailed)
                    }
                }
            }
    }

    private suspend fun restorePlaybackAfterTrackSwitchFailure(previous: PlaybackSession) {
        val latest = session ?: return
        if (latest.mediaId != previous.mediaId) return
        runCatching {
            playerEngine.prepare(
                source = latest.source,
                startPositionMs = latest.positionMs,
                audioTrack = latest.audioTrack,
                subtitleTrack = latest.subtitleTrack,
            )
            playerEngine.setVideoQuality(latest.stream.maxBitrate)
            if (latest.isPaused) playerEngine.pause() else playerEngine.play()
        }
    }

    private fun currentSessionForAudioSwitch(
        switchGeneration: Long,
        switchPlayGeneration: Long,
        mediaId: String,
    ): PlaybackSession? {
        if (switchGeneration != audioSwitchGeneration || switchPlayGeneration != playGeneration || released) {
            return null
        }
        return session?.takeIf { it.mediaId == mediaId }
    }

    private fun cancelAudioSwitch() {
        audioSwitchGeneration += 1
        audioSwitchJob?.cancel()
        audioSwitchJob = null
    }

    fun selectQuality(optionId: String) {
        val current = session ?: return
        val options = current.stream.qualityOptions
        if (options.isEmpty()) return
        val option = options.firstOrNull { it.id == optionId } ?: return
        cancelQualitySwitch()
        cancelAudioSwitch()
        cancelSubtitleSwitch()
        if (option.id == current.stream.selectedQualityId) return

        val preferred = option.takeUnless { it.isAuto }
        val selection =
            streamSelector.select(
                current.request.mediaSources,
                preferred,
                current.request.mediaKind,
            )

        val updatedAudio =
            current.audioTrack?.let { track -> selection.audioTracks.find { it.id == track.id } }
                ?: selection.defaultAudioTrack()
        val updatedSubtitle =
            if (current.subtitleTrack == null) {
                null
            } else {
                selection.subtitleTracks.find { it.id == current.subtitleTrack.id }
                    ?: selection.defaultSubtitleTrack()
            }

        val subtitleForPlayer = updatedSubtitle
        val audioForPlayer = updatedAudio
        val requestedSwitchPosition =
            current.request.validPlaybackPosition(current.positionMs, selection.sourceId)

        val staysOnDirectSource =
            selection.mode == PlaybackMode.DIRECT &&
                current.stream.mode == PlaybackMode.DIRECT &&
                selection.sourceId == current.stream.sourceId

        if (staysOnDirectSource) {
            playerEngine.setVideoQuality(selection.maxBitrate)
            playerEngine.setAudioTrack(audioForPlayer)
            playerEngine.setSubtitleTrack(subtitleForPlayer)
            val updatedSession =
                current.copy(
                    stream = selection,
                    audioTrack = audioForPlayer,
                    subtitleTrack = subtitleForPlayer,
                    source =
                        current.source.copy(
                            audioStreamIndex = audioForPlayer?.streamIndex,
                            subtitleStreamIndex = subtitleForPlayer?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                        ),
                    qualityOptions = selection.qualityOptions,
                    selectedQualityId = selection.selectedQualityId,
                )
            session = updatedSession
            publishCurrentState(updatedSession)
            return
        }

        val environment = lastEnvironment ?: return
        val switchGeneration = qualitySwitchGeneration
        val switchPlayGeneration = playGeneration
        qualitySwitchJob =
            scope.launch {
                try {
                    val resolved =
                        playbackSourceResolver.resolve(
                            request = current.request,
                            selection = selection,
                            environment = environment,
                            startPositionMs = requestedSwitchPosition,
                            options =
                                PlaybackSourceOptions(
                                    audioStreamIndex = audioForPlayer?.streamIndex,
                                    subtitleStreamIndex = subtitleForPlayer?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                                    playSessionId =
                                        current.source.playSessionId.takeIf {
                                            current.stream.mode == selection.mode
                                        },
                                ),
                        )
                    currentCoroutineContext().ensureActive()
                    val beforePrepare =
                        currentSessionForQualitySwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch

                    val negotiatedSelection = selection.withResolvedSource(resolved)
                    val audioBeforePrepare = beforePrepare.audioTrack.reconciledWith(negotiatedSelection)
                    val subtitleBeforePrepare = beforePrepare.subtitleTrack.reconciledWith(negotiatedSelection)
                    val beforePreparePosition =
                        beforePrepare.request.validPlaybackPosition(
                            beforePrepare.positionMs,
                            negotiatedSelection.sourceId,
                        )
                    val resolvedBeforePrepare =
                        resolved.forQualitySwitch(
                            positionMs = beforePreparePosition,
                            audioTrack = audioBeforePrepare,
                            subtitleTrack = subtitleBeforePrepare,
                        )
                    playerEngine.prepare(
                        source = resolvedBeforePrepare,
                        startPositionMs = beforePreparePosition,
                        audioTrack = audioBeforePrepare,
                        subtitleTrack = subtitleBeforePrepare,
                    )
                    currentCoroutineContext().ensureActive()
                    val latest =
                        currentSessionForQualitySwitch(
                            switchGeneration = switchGeneration,
                            switchPlayGeneration = switchPlayGeneration,
                            mediaId = current.mediaId,
                        ) ?: return@launch
                    val latestAudio = latest.audioTrack.reconciledWith(negotiatedSelection)
                    val latestSubtitle = latest.subtitleTrack.reconciledWith(negotiatedSelection)
                    val latestPosition =
                        latest.request.validPlaybackPosition(
                            latest.positionMs,
                            negotiatedSelection.sourceId,
                        )
                    val resolvedWithTracks =
                        resolved.forQualitySwitch(
                            positionMs = latestPosition,
                            audioTrack = latestAudio,
                            subtitleTrack = latestSubtitle,
                        )

                    playerEngine.setVideoQuality(negotiatedSelection.maxBitrate)
                    if (latestPosition != beforePreparePosition) {
                        playerEngine.seekTo(latestPosition)
                    }
                    playerEngine.setAudioTrack(latestAudio)
                    playerEngine.setSubtitleTrack(latestSubtitle)
                    if (latest.isPaused) {
                        playerEngine.pause()
                    } else {
                        playerEngine.play()
                    }

                    val updatedSession =
                        latest.copy(
                            positionMs = latestPosition,
                            stream = negotiatedSelection,
                            audioTrack = latestAudio,
                            subtitleTrack = latestSubtitle,
                            source = resolvedWithTracks,
                            qualityOptions = negotiatedSelection.qualityOptions,
                            selectedQualityId = negotiatedSelection.selectedQualityId,
                        )
                    session = updatedSession
                    publishCurrentState(updatedSession)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (
                        switchGeneration == qualitySwitchGeneration &&
                        switchPlayGeneration == playGeneration
                    ) {
                        publishPlaybackError(error, current.request)
                    }
                }
            }
    }

    private fun currentSessionForQualitySwitch(
        switchGeneration: Long,
        switchPlayGeneration: Long,
        mediaId: String,
    ): PlaybackSession? {
        if (switchGeneration != qualitySwitchGeneration || switchPlayGeneration != playGeneration || released) {
            return null
        }
        return session?.takeIf { it.mediaId == mediaId }
    }

    private fun AudioTrack?.reconciledWith(selection: PlaybackStreamSelection): AudioTrack? =
        this?.let { selected -> selection.audioTracks.firstOrNull { it.id == selected.id } }
            ?: selection.defaultAudioTrack()

    private fun SubtitleTrack?.reconciledWith(selection: PlaybackStreamSelection): SubtitleTrack? =
        this?.let { selected -> selection.subtitleTracks.firstOrNull { it.id == selected.id } }

    private fun ResolvedPlaybackSource.forQualitySwitch(
        positionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ): ResolvedPlaybackSource {
        val withTracks =
            applyPlaybackIndicesToUrl(
                url = url,
                audioStreamIndex = audioTrack?.streamIndex,
                subtitleStreamIndex = subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
            )
        val reconciledUrl =
            if (mode == PlaybackMode.HLS && !isFallbackHls) {
                withQueryParameter(withTracks, "StartTimeTicks", positionMs.coerceAtLeast(0L).toTicks().toString())
            } else {
                withTracks
            }
        return copy(
            url = reconciledUrl,
            audioStreamIndex = audioTrack?.streamIndex,
            subtitleStreamIndex = subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
        )
    }

    private fun PlaybackRequest.validPlaybackPosition(
        positionMs: Long,
        sourceId: String,
    ): Long {
        val safePosition = positionMs.coerceAtLeast(0L)
        val sourceDurationMs =
            mediaSources
                .firstOrNull { it.id == sourceId }
                ?.runTimeTicks
                ?.let(::ticksToMillis)
        val mediaDurationMs = sourceDurationMs ?: ticksToMillis(durationTicks)
        return if (mediaDurationMs != null && mediaDurationMs > 0L && safePosition >= mediaDurationMs) {
            0L
        } else {
            safePosition
        }
    }

    private fun cancelQualitySwitch() {
        qualitySwitchGeneration += 1
        qualitySwitchJob?.cancel()
        qualitySwitchJob = null
    }

    private fun PlaybackStreamSelection.withResolvedSource(resolved: ResolvedPlaybackSource): PlaybackStreamSelection {
        val resolvedSourceId = resolved.mediaSourceId ?: sourceId
        val autoSelected =
            selectedQualityId == PlaybackQualityOption.AUTO_ID ||
                resolved.supportsTranscoding == false
        val reconciledOptions =
            qualityOptions
                .map { option ->
                    if (option.isAuto && autoSelected) {
                        option.copy(
                            mode = resolved.mode,
                            sourceId = resolvedSourceId,
                        )
                    } else {
                        option
                    }
                }.let { options ->
                    if (resolved.supportsTranscoding == false) {
                        options.filter { it.isAuto }
                    } else {
                        options
                    }
                }
        return copy(
            sourceId = resolvedSourceId,
            mode = resolved.mode,
            qualityOptions = reconciledOptions,
            selectedQualityId =
                if (resolved.supportsTranscoding == false) {
                    PlaybackQualityOption.AUTO_ID
                } else {
                    selectedQualityId
                },
        )
    }

    fun clearProgress(mediaId: String) {
        progressStore.clear(mediaId)
        if (session?.mediaId == mediaId) {
            lastPersisted = null
        }
    }

    fun currentSession(): PlaybackSession? = session

    private fun artworkUrlFor(session: PlaybackSession): String? {
        val metadata = session.request.metadata ?: return null
        val explicitUrl = metadata.artworkUrl
        if (explicitUrl != null) return explicitUrl
        val primaryTag = metadata.primaryImageTag ?: return null
        val environment = lastEnvironment ?: return null
        return "${environment.baseUrl}/Items/${session.mediaId}/Images/Primary?tag=$primaryTag"
    }

    private fun isRemoteConnected(): Boolean = castState is CastConnectionState.Connected

    private fun currentDeviceName(): String = remoteDeviceName ?: deviceName

    fun release() {
        stopInternal(saveProgress = true)
        released = true
        _state.value = PlaybackState.Stopped
        playerEngine.release()
        runBlocking { offlineProgressSyncer.flush() }
        castStateJob?.cancel()
        castProgressJob?.cancel()
        val pendingFinalReport = finalStreamingReportJob
        if (pendingFinalReport?.isActive == true) {
            pendingFinalReport.invokeOnCompletion { scope.cancel() }
        } else {
            scope.cancel()
        }
    }

    private fun publishCurrentState(
        session: PlaybackSession,
        castSnapshotOverride: CastSessionSnapshot? = null,
        recoveryReason: String? = null,
    ) {
        _state.value =
            when (handoffPhase) {
                HandoffPhase.LOCAL ->
                    PlaybackState.LocalPlayback(
                        mediaId = session.mediaId,
                        deviceName = currentDeviceName(),
                        stream = session.stream,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        audioTrack = session.audioTrack,
                        subtitleTrack = session.subtitleTrack,
                        isPaused = session.isPaused,
                        source = session.source,
                        qualityOptions = session.qualityOptions,
                        selectedQualityId = session.selectedQualityId,
                        metadata = session.request.metadata,
                        mediaKind = session.request.mediaKind,
                        phase = session.phase,
                        playbackSpeed = session.playbackSpeed,
                        statsForNerdsEnabled = session.statsForNerdsEnabled,
                        runtimeStats = session.runtimeStats,
                    )

                HandoffPhase.CAST_CONNECTING ->
                    PlaybackState.CastConnecting(
                        mediaId = session.mediaId,
                        localDeviceName = deviceName,
                        targetDeviceName = remoteDeviceName,
                        stream = session.stream,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        audioTrack = session.audioTrack,
                        subtitleTrack = session.subtitleTrack,
                        isPaused = session.isPaused,
                        source = session.source,
                        qualityOptions = session.qualityOptions,
                        selectedQualityId = session.selectedQualityId,
                        metadata = session.request.metadata,
                        mediaKind = session.request.mediaKind,
                        phase = session.phase,
                        playbackSpeed = session.playbackSpeed,
                        statsForNerdsEnabled = session.statsForNerdsEnabled,
                        runtimeStats = session.runtimeStats,
                    )

                HandoffPhase.CAST_ACTIVE ->
                    PlaybackState.CastPlayback(
                        mediaId = session.mediaId,
                        localDeviceName = deviceName,
                        castDeviceName = remoteDeviceName ?: currentDeviceName(),
                        castSnapshot = castSnapshotOverride ?: buildCastSnapshot(session),
                        stream = session.stream,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        audioTrack = session.audioTrack,
                        subtitleTrack = session.subtitleTrack,
                        isPaused = session.isPaused,
                        source = session.source,
                        qualityOptions = session.qualityOptions,
                        selectedQualityId = session.selectedQualityId,
                        metadata = session.request.metadata,
                        mediaKind = session.request.mediaKind,
                        phase = session.phase,
                        playbackSpeed = session.playbackSpeed,
                        statsForNerdsEnabled = session.statsForNerdsEnabled,
                        runtimeStats = session.runtimeStats,
                    )

                HandoffPhase.RECOVERING ->
                    PlaybackState.RecoveringPlayback(
                        mediaId = session.mediaId,
                        localDeviceName = deviceName,
                        previousCastDeviceName = remoteDeviceName,
                        reason = recoveryReason,
                        stream = session.stream,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        audioTrack = session.audioTrack,
                        subtitleTrack = session.subtitleTrack,
                        isPaused = session.isPaused,
                        source = session.source,
                        qualityOptions = session.qualityOptions,
                        selectedQualityId = session.selectedQualityId,
                        metadata = session.request.metadata,
                        mediaKind = session.request.mediaKind,
                        phase = session.phase,
                        playbackSpeed = session.playbackSpeed,
                        statsForNerdsEnabled = session.statsForNerdsEnabled,
                        runtimeStats = session.runtimeStats,
                    )
            }
    }

    private fun persistProgressIfNeeded(session: PlaybackSession) {
        val progress = PlaybackProgress(session.mediaId, session.positionMs)
        val last = lastPersisted
        if (isNearCompletion(session)) {
            progressStore.clear(session.mediaId)
            lastPersisted = null
            return
        }
        if (last == null || abs(progress.positionMs - last.positionMs) >= PROGRESS_WRITE_INTERVAL_MS) {
            persist(progress)
            if (session.source.mode == PlaybackMode.LOCAL) {
                scope.launch {
                    offlineProgressSyncer.onProgress(session.mediaId, progress.positionMs, session.durationMs)
                }
            } else {
                streamingContext(session)?.let { context ->
                    launchStreamingReport {
                        onProgress(context, progress.positionMs)
                    }
                }
            }
        }
    }

    private fun persistProgress(
        mediaId: String,
        positionMs: Long,
    ) {
        persist(PlaybackProgress(mediaId, positionMs))
    }

    private fun persist(progress: PlaybackProgress) {
        progressStore.write(progress)
        lastPersisted = progress
    }

    private fun isNearCompletion(session: PlaybackSession): Boolean {
        val duration = session.durationMs ?: return false
        if (duration <= 0) return false
        return session.positionMs >= (duration * COMPLETION_THRESHOLD_PERCENT).toLong()
    }

    private fun streamingContext(session: PlaybackSession): StreamingProgressContext? {
        if (session.stream.mode == PlaybackMode.LOCAL) return null
        val playSessionId = session.source.playSessionId ?: return null
        val mediaSourceId = session.stream.sourceId ?: return null
        val strategy =
            when (session.stream.mode) {
                PlaybackMode.DIRECT -> StreamingPlayStrategy.DIRECT
                PlaybackMode.HLS -> StreamingPlayStrategy.TRANSCODED
                PlaybackMode.LOCAL -> return null
            }
        val audioIndex = session.audioTrack?.streamIndex ?: session.source.audioStreamIndex
        val subtitleIndex = session.subtitleTrack?.streamIndex ?: session.source.subtitleStreamIndex
        return StreamingProgressContext(
            mediaId = session.mediaId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
            strategy = strategy,
        )
    }

    private fun preferredAudioTrackFor(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        preferenceResolver: PlaybackPreferenceResolver? = null,
    ): AudioTrack? {
        val stored =
            if (playbackPreferencesProvider.currentSettings().rememberSeriesTracks) {
                request.metadata?.subtitlePreferenceScopeKey(request.mediaId)?.let(subtitlePreferenceStore::readAudio)
            } else {
                null
            }
        return request.preferredAudioTrackId
            ?.let { preferredId -> selection.audioTracks.firstOrNull { it.id == preferredId } }
            ?: stored?.let(selection.audioTracks::resolveAudioPreference)
            ?: preferenceResolver?.selectAudioTrack(selection.audioTracks)
            ?: selection.defaultAudioTrack()
    }

    private fun preferredSubtitleTrackFor(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        preferenceResolver: PlaybackPreferenceResolver? = null,
        rememberSeriesTracks: Boolean = true,
    ): SubtitleTrack? {
        val preferredId = request.preferredSubtitleTrackId
        val storedPreference =
            if (rememberSeriesTracks) {
                request.metadata
                    ?.subtitlePreferenceScopeKey(request.mediaId)
                    ?.let(subtitlePreferenceStore::read)
            } else {
                null
            }
        if (preferredId == null && storedPreference != null) {
            val resolution = selection.subtitleTracks.resolveSubtitlePreference(storedPreference)
            if (resolution.disabled) return null
            resolution.trackId?.let { id -> return selection.subtitleTracks.firstOrNull { it.id == id } }
        }
        return when {
            preferredId == null ->
                preferenceResolver?.selectSubtitleTrack(
                    tracks = selection.subtitleTracks,
                    audioLanguage = preferredAudioTrackFor(request, selection, preferenceResolver)?.language,
                )
            else -> selection.subtitleTracks.firstOrNull { it.id == preferredId }
        }
    }

    private fun saveSubtitlePreference(
        session: PlaybackSession,
        track: SubtitleTrack?,
    ) {
        if (!playbackPreferencesProvider.currentSettings().rememberSeriesTracks) return
        val scopeKey =
            session.request.metadata?.subtitlePreferenceScopeKey(session.mediaId)
                ?: "item:${session.mediaId}"
        subtitlePreferenceStore.write(
            scopeKey = scopeKey,
            preference = track?.toPreference() ?: disabledSubtitlePreference(),
        )
    }

    private fun saveAudioPreference(
        session: PlaybackSession,
        track: AudioTrack,
    ) {
        if (!playbackPreferencesProvider.currentSettings().rememberSeriesTracks) return
        val scopeKey = session.request.metadata?.subtitlePreferenceScopeKey(session.mediaId) ?: "item:${session.mediaId}"
        subtitlePreferenceStore.writeAudio(scopeKey, track.toPreference())
    }

    private fun offlineTracksFor(
        request: PlaybackRequest,
        source: ResolvedPlaybackSource,
    ): OfflineTrackSelection {
        val sourceTracks = request.mediaSources.flatMap { mediaSource -> mediaSource.streams }
        val audioTracks =
            sourceTracks
                .filter { it.type == dev.jellystack.core.jellyfin.JellyfinMediaStreamType.AUDIO }
                .mapIndexedNotNull { audioIndex, stream -> stream.toAudioTrack(audioIndex) }
                .distinctBy { it.id }
        val subtitlesFromSource = sourceTracks.mapNotNull { it.toSubtitleTrack() }.distinctBy { it.id }
        val subtitleTracks =
            if (subtitlesFromSource.isNotEmpty()) {
                subtitlesFromSource
            } else {
                source.subtitles.map { subtitle ->
                    SubtitleTrack(
                        id = subtitle.trackId,
                        language = subtitle.language,
                        title = subtitle.label ?: subtitle.language ?: subtitle.trackId,
                        format = subtitleMimeTypeToFormat(subtitle.mimeType),
                        isDefault = false,
                        isForced = subtitle.isForced,
                        streamIndex = subtitle.trackId.toIntOrNull(),
                    )
                }
            }
        return OfflineTrackSelection(audioTracks = audioTracks, subtitleTracks = subtitleTracks)
    }

    private fun subtitleMimeTypeToFormat(mimeType: String?): SubtitleFormat {
        val normalized = mimeType?.lowercase().orEmpty()
        return when {
            normalized.contains("vtt") -> SubtitleFormat.VTT
            normalized.contains("subrip") || normalized.contains("srt") -> SubtitleFormat.SRT
            normalized.contains("ass") -> SubtitleFormat.ASS
            normalized.contains("ssa") -> SubtitleFormat.SSA
            normalized.contains("pgs") -> SubtitleFormat.PGS
            normalized.contains("sup") -> SubtitleFormat.SUP
            else -> SubtitleFormat.UNKNOWN
        }
    }

    private fun applyPlaybackIndicesToUrl(
        url: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int? = null,
    ): String =
        withQueryParameter(
            withQueryParameter(url, "AudioStreamIndex", audioStreamIndex?.toString()),
            "SubtitleStreamIndex",
            subtitleStreamIndex?.toString(),
        )

    private fun withQueryParameter(
        url: String,
        key: String,
        value: String?,
    ): String {
        val fragment = url.substringAfter('#', missingDelimiterValue = "").takeIf { '#' in url }
        val parts = url.substringBefore('#').split('?', limit = 2)
        val path = parts[0]
        val query = parts.getOrNull(1).orEmpty()
        val pairs =
            query
                .split('&')
                .filter { it.isNotBlank() }
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) {
                        null
                    } else {
                        part.substring(0, idx) to part.substring(idx + 1)
                    }
                }.filterNot { (name, _) -> name.equals(key, ignoreCase = true) }
                .toMutableList()
        if (!value.isNullOrBlank()) {
            pairs += key to value
        }
        val rebuiltQuery = pairs.joinToString("&") { (name, entryValue) -> "$name=$entryValue" }
        return buildString {
            append(if (rebuiltQuery.isBlank()) path else "$path?$rebuiltQuery")
            fragment?.let {
                append('#')
                append(it)
            }
        }
    }

    private data class OfflineTrackSelection(
        val audioTracks: List<AudioTrack>,
        val subtitleTracks: List<SubtitleTrack>,
    )

    private fun startCollectors() {
        cancelCollectors()
        progressJob =
            scope.launch {
                playerEngine.positionUpdates.collect { positionMs ->
                    updateProgress(positionMs)
                }
            }
        eventsJob =
            scope.launch {
                playerEngine.events.collect { event ->
                    when (event) {
                        PlayerEvent.Buffering -> updatePlaybackPhase(PlaybackPhase.Buffering)
                        PlayerEvent.Ready -> updatePlaybackPhase(PlaybackPhase.Ready)
                        PlayerEvent.VideoOutputStalled -> {
                            if (!attemptPlaybackFallback()) {
                                publishPlaybackError(IllegalStateException("Video output did not start."))
                            }
                        }
                        is PlayerEvent.AudioTrackSelectionApplied ->
                            confirmPendingAudioSelection(event.trackId)
                        is PlayerEvent.AudioTrackSelectionUnavailable ->
                            _notices.tryEmit(PlaybackNotice.AudioTrackSelectionFailed)
                        is PlayerEvent.SubtitleTrackSelectionApplied ->
                            confirmPendingSubtitleSelection(event.trackId)
                        is PlayerEvent.SubtitleTrackSelectionUnavailable ->
                            _notices.tryEmit(PlaybackNotice.SubtitleTrackSelectionFailed)
                        PlayerEvent.Completed ->
                            session?.takeUnless { it.phase == PlaybackPhase.Ended }?.let {
                                progressStore.clear(it.mediaId)
                                lastPersisted = null
                                if (it.stream.mode == PlaybackMode.LOCAL) {
                                    scope.launch { offlineProgressSyncer.onCompleted(it.mediaId) }
                                } else {
                                    streamingContext(it)?.let { context ->
                                        launchStreamingStop(
                                            context = context,
                                            positionMs = it.positionMs,
                                            completed = true,
                                        )
                                    }
                                }
                                val ended = it.copy(isPaused = true, phase = PlaybackPhase.Ended)
                                session = ended
                                publishCurrentState(ended)
                            }
                        is PlayerEvent.Error -> {
                            if (!attemptPlaybackFallback(event.throwable)) {
                                publishPlaybackError(event.throwable)
                            }
                        }
                    }
                }
            }
        runtimeStatsJob =
            scope.launch {
                playerEngine.runtimeStats.collect { stats ->
                    val current = session ?: return@collect
                    val merged =
                        stats.copy(
                            playbackMode = stats.playbackMode ?: current.stream.mode,
                            container = stats.container ?: current.stream.container,
                            videoCodec = stats.videoCodec ?: current.stream.videoCodec,
                            audioCodec = stats.audioCodec ?: current.stream.audioCodec,
                            videoBitrate = stats.videoBitrate ?: current.stream.videoBitrate,
                        )
                    if (merged != current.runtimeStats) {
                        val updated = current.copy(runtimeStats = merged)
                        session = updated
                        publishCurrentState(updated)
                    }
                }
            }
    }

    private fun launchStreamingReport(report: suspend StreamingProgressReporter.() -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            streamingReportMutex.withLock {
                streamingProgressReporter.report()
            }
        }

    private fun launchStreamingStop(
        context: StreamingProgressContext,
        positionMs: Long?,
        completed: Boolean,
    ) {
        finalStreamingReportJob =
            launchStreamingReport {
                onStop(
                    context = context,
                    positionMs = positionMs,
                    completed = completed,
                )
            }
    }

    private fun updatePlaybackPhase(phase: PlaybackPhase) {
        val current = session ?: return
        if (current.phase == PlaybackPhase.Ended && phase != PlaybackPhase.Buffering) return
        val updated = current.copy(phase = phase)
        session = updated
        publishCurrentState(updated)
    }

    private fun attemptPlaybackFallback(originalError: Throwable? = null): Boolean {
        val current = session ?: return false
        val environment = lastEnvironment ?: return false
        if (current.stream.mode == PlaybackMode.LOCAL || current.source.supportsTranscoding != true) return false
        val fallbackOptions =
            when (current.stream.mode) {
                PlaybackMode.DIRECT -> {
                    if (directFallbackAttempted) return false
                    directFallbackAttempted = true
                    hlsForcedTranscodeFallbackAttempted = true
                    PlaybackSourceOptions(
                        audioStreamIndex = current.audioTrack?.streamIndex,
                        subtitleStreamIndex = current.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                        playSessionId = current.source.playSessionId,
                        forceTranscoding = true,
                    )
                }

                PlaybackMode.HLS -> {
                    if (!hlsForcedTranscodeFallbackAttempted) {
                        hlsForcedTranscodeFallbackAttempted = true
                        PlaybackSourceOptions(
                            audioStreamIndex = current.audioTrack?.streamIndex,
                            subtitleStreamIndex = current.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                            playSessionId = current.source.playSessionId,
                            forceTranscoding = true,
                        )
                    } else {
                        if (hlsContainerFallbackAttempted || current.source.segmentContainer.equals("mp4", true)) {
                            return false
                        }
                        hlsContainerFallbackAttempted = true
                        PlaybackSourceOptions(
                            audioStreamIndex = current.audioTrack?.streamIndex,
                            subtitleStreamIndex = current.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                            playSessionId = current.source.playSessionId,
                            forceTranscoding = true,
                            preferFmp4Hls = true,
                        )
                    }
                }

                PlaybackMode.LOCAL -> return false
            }
        scope.launch {
            try {
                val latest = session ?: return@launch
                val source =
                    playbackSourceResolver.resolve(
                        request = latest.request,
                        selection = latest.stream,
                        environment = environment,
                        startPositionMs = latest.positionMs,
                        options = fallbackOptions,
                    )
                val resolvedStream = latest.stream.withResolvedSource(source)
                val resolvedSource =
                    source.copy(
                        audioStreamIndex = latest.audioTrack?.streamIndex,
                        subtitleStreamIndex = latest.subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
                    )
                val updated =
                    latest.copy(
                        source = resolvedSource,
                        stream = resolvedStream,
                        qualityOptions = resolvedStream.qualityOptions,
                        selectedQualityId = resolvedStream.selectedQualityId,
                        phase = PlaybackPhase.Buffering,
                        runtimeStats =
                            latest.runtimeStats.copy(
                                playbackMode = resolvedStream.mode,
                                container = resolvedStream.container,
                                videoCodec = resolvedStream.videoCodec,
                                audioCodec = resolvedStream.audioCodec,
                                videoBitrate = resolvedStream.videoBitrate,
                            ),
                    )
                playerEngine.prepare(
                    source = resolvedSource,
                    startPositionMs = latest.positionMs,
                    audioTrack = latest.audioTrack,
                    subtitleTrack = latest.subtitleTrack,
                )
                session = updated
                playerEngine.setVideoQuality(resolvedStream.maxBitrate)
                playerEngine.setPlaybackSpeed(latest.playbackSpeed)
                publishCurrentState(updated)
                if (latest.isPaused) playerEngine.pause() else playerEngine.play()
            } catch (fallbackError: Throwable) {
                publishPlaybackError(originalError ?: fallbackError)
            }
        }
        return true
    }

    private fun confirmPendingAudioSelection(trackId: String) {
        val current = session ?: return
        val audio = current.stream.audioTracks.firstOrNull { it.id == trackId } ?: return
        if (current.audioTrack?.id == audio.id) return
        val updated =
            current.copy(
                audioTrack = audio,
                source = current.source.copy(audioStreamIndex = audio.streamIndex),
            )
        session = updated
        publishCurrentState(updated)
        saveAudioPreference(updated, audio)
    }

    private fun confirmPendingSubtitleSelection(trackId: String?) {
        val current = session ?: return
        val subtitle = trackId?.let { id -> current.stream.subtitleTracks.firstOrNull { it.id == id } }
        if (trackId != null && subtitle == null) return
        if (current.subtitleTrack?.id == subtitle?.id) return
        val updated =
            current.copy(
                subtitleTrack = subtitle,
                source = current.source.copy(subtitleStreamIndex = subtitle?.streamIndex ?: SUBTITLES_DISABLED_INDEX),
            )
        session = updated
        publishCurrentState(updated)
        saveSubtitlePreference(updated, subtitle)
    }

    private fun cancelCollectors() {
        progressJob?.cancel()
        eventsJob?.cancel()
        runtimeStatsJob?.cancel()
        progressJob = null
        eventsJob = null
        runtimeStatsJob = null
    }

    private fun scheduleRecoveryPromotion(mediaId: String) {
        recoveryPromotionJob?.cancel()
        recoveryPromotionJob =
            scope.launch {
                delay(1_000L)
                val current = session ?: return@launch
                if (handoffPhase == HandoffPhase.RECOVERING && current.mediaId == mediaId) {
                    handoffPhase = HandoffPhase.LOCAL
                    publishCurrentState(current)
                }
            }
    }

    private fun cancelRecoveryPromotion() {
        recoveryPromotionJob?.cancel()
        recoveryPromotionJob = null
    }

    private data class RetryContext(
        val request: PlaybackRequest,
        val environment: JellyfinEnvironment,
    )

    companion object {
        val PLAYBACK_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private const val PROGRESS_WRITE_INTERVAL_MS = 5_000L
        private const val COMPLETION_THRESHOLD_PERCENT = 0.97
        private const val SUBTITLES_DISABLED_INDEX = -1
    }
}

private enum class HandoffPhase {
    LOCAL,
    CAST_CONNECTING,
    CAST_ACTIVE,
    RECOVERING,
}
