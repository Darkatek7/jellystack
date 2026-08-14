package dev.jellystack.players

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.COLOR_TRANSFER_HLG
import androidx.media3.common.C.COLOR_TRANSFER_ST2084
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleTextSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class AndroidPlayerEngine(
    context: Context,
    preferHighestSupportedBitrate: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PlayerEngine {
    private val mediaAudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    private val appContext = context.applicationContext
    private var pendingAudioTrack: AudioTrack? = null
    private var pendingAudioSelectionConfirmationId: String? = null
    private var pendingSubtitleTrack: SubtitleTrack? = null
    private var pendingSubtitleSelectionConfirmationId: String? = null
    private var videoSurface: PlayerView? = null
    private var subtitleTextSize: SubtitleTextSize = SubtitleTextSize.SYSTEM
    private var subtitleBackground: SubtitleBackground = SubtitleBackground.SYSTEM
    private var firstFrameRendered = false
    private var firstFrameWatchdog: Job? = null
    private var expectsAudioOutput = false
    private var audioOutputUnavailableReported = false
    private var audioOutputWatchdog: Job? = null

    private val exoPlayer =
        ExoPlayer
            .Builder(context)
            .build()
            .apply {
                if (preferHighestSupportedBitrate) {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setForceHighestSupportedBitrate(true)
                            .build()
                }
                setAudioAttributes(
                    mediaAudioAttributes,
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> eventFlow.tryEmit(PlayerEvent.Buffering)
                                Player.STATE_READY -> {
                                    eventFlow.tryEmit(PlayerEvent.Ready)
                                    scheduleFirstFrameWatchdog()
                                    scheduleAudioOutputWatchdog()
                                }
                                Player.STATE_ENDED -> eventFlow.tryEmit(PlayerEvent.Completed)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            firstFrameWatchdog?.cancel()
                            audioOutputWatchdog?.cancel()
                            scope.launch { eventFlow.emit(PlayerEvent.Error(error)) }
                        }

                        override fun onRenderedFirstFrame() {
                            firstFrameRendered = true
                            firstFrameWatchdog?.cancel()
                        }

                        override fun onPlayWhenReadyChanged(
                            playWhenReady: Boolean,
                            reason: Int,
                        ) {
                            if (playWhenReady) scheduleFirstFrameWatchdog() else firstFrameWatchdog?.cancel()
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            scheduleFirstFrameWatchdog()
                            if (tracks.hasSelectedAudioTrack()) {
                                audioOutputWatchdog?.cancel()
                            } else {
                                scheduleAudioOutputWatchdog()
                            }
                            val audioResult = applyAudioTrack(pendingAudioTrack)
                            val subtitleResult = applySubtitleTrack(pendingSubtitleTrack)
                            val pendingTrackId = pendingAudioSelectionConfirmationId
                            if (pendingTrackId != null) {
                                when (audioResult) {
                                    AudioTrackSelectionResult.APPLIED -> {
                                        pendingAudioSelectionConfirmationId = null
                                        eventFlow.tryEmit(PlayerEvent.AudioTrackSelectionApplied(pendingTrackId))
                                    }
                                    AudioTrackSelectionResult.UNAVAILABLE -> {
                                        pendingAudioSelectionConfirmationId = null
                                        eventFlow.tryEmit(PlayerEvent.AudioTrackSelectionUnavailable(pendingTrackId))
                                    }
                                    AudioTrackSelectionResult.PENDING -> Unit
                                }
                            }
                            val pendingSubtitleId = pendingSubtitleSelectionConfirmationId
                            if (pendingSubtitleId != null) {
                                when (subtitleResult) {
                                    SubtitleTrackSelectionResult.APPLIED -> {
                                        pendingSubtitleSelectionConfirmationId = null
                                        eventFlow.tryEmit(PlayerEvent.SubtitleTrackSelectionApplied(pendingSubtitleId))
                                    }
                                    SubtitleTrackSelectionResult.UNAVAILABLE -> {
                                        pendingSubtitleSelectionConfirmationId = null
                                        eventFlow.tryEmit(PlayerEvent.SubtitleTrackSelectionUnavailable(pendingSubtitleId))
                                    }
                                    SubtitleTrackSelectionResult.PENDING -> Unit
                                }
                            }
                        }
                    },
                )
            }

    private val positionFlow = MutableSharedFlow<Long>(replay = 1)
    private val eventFlow = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    private val runtimeStatsFlow = MutableStateFlow(PlaybackRuntimeStats())
    private var positionJob =
        scope.launch {
            while (isActive) {
                positionFlow.emit(exoPlayer.currentPosition)
                runtimeStatsFlow.value = currentRuntimeStats()
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }

    override val positionUpdates: SharedFlow<Long> = positionFlow.asSharedFlow()
    override val events: SharedFlow<PlayerEvent> = eventFlow.asSharedFlow()
    override val runtimeStats: StateFlow<PlaybackRuntimeStats> = runtimeStatsFlow.asStateFlow()

    fun setAudioOutputEnabled(enabled: Boolean) {
        exoPlayer.volume = if (enabled) 1f else 0f
        exoPlayer.setAudioAttributes(mediaAudioAttributes, enabled)
    }

    fun createVideoSurface(context: Context): View =
        PlayerView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            keepScreenOn = true
            // Compose owns TV remote focus. A focusable PlayerView can silently consume the first
            // D-pad event and leave the visible controls without a selected action.
            isFocusable = false
            isFocusableInTouchMode = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            updateVideoSurface(
                view = this,
            )
        }

    fun updateVideoSurface(view: View) {
        val playerView = view as? PlayerView ?: return
        playerView.useController = false
        if (playerView.player !== exoPlayer) {
            playerView.player = exoPlayer
        }
        videoSurface = playerView
        applySubtitleAppearance(playerView)
    }

    fun releaseVideoSurface(view: View) {
        val playerView = view as? PlayerView ?: return
        if (playerView.player === exoPlayer) {
            playerView.player = null
        }
        if (videoSurface === playerView) videoSurface = null
    }

    fun setSubtitleAppearance(
        textSize: SubtitleTextSize,
        background: SubtitleBackground,
    ) {
        subtitleTextSize = textSize
        subtitleBackground = background
        videoSurface?.let(::applySubtitleAppearance)
    }

    private fun applySubtitleAppearance(playerView: PlayerView) {
        val subtitleView = playerView.subtitleView ?: return
        when (subtitleTextSize) {
            SubtitleTextSize.SYSTEM -> subtitleView.setUserDefaultTextSize()
            SubtitleTextSize.SMALL -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            SubtitleTextSize.MEDIUM -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            SubtitleTextSize.LARGE -> subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        }
        when (subtitleBackground) {
            SubtitleBackground.SYSTEM -> subtitleView.setUserDefaultStyle()
            SubtitleBackground.NONE -> subtitleView.setStyle(subtitleStyle(Color.TRANSPARENT))
            SubtitleBackground.TRANSLUCENT -> subtitleView.setStyle(subtitleStyle(0x99000000.toInt()))
            SubtitleBackground.DARK -> subtitleView.setStyle(subtitleStyle(Color.BLACK))
        }
    }

    private fun subtitleStyle(backgroundColor: Int): CaptionStyleCompat =
        CaptionStyleCompat(
            Color.WHITE,
            backgroundColor,
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            null,
        )

    @UnstableApi
    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        firstFrameWatchdog?.cancel()
        audioOutputWatchdog?.cancel()
        firstFrameRendered = false
        expectsAudioOutput = audioTrack != null || source.audioStreamIndex != null
        audioOutputUnavailableReported = false
        pendingAudioTrack = audioTrack
        pendingSubtitleTrack = subtitleTrack
        withContext(Dispatchers.Main) {
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(Uri.parse(source.url))
                    .apply {
                        val mimeType =
                            source.mimeType
                                ?: when (source.mode) {
                                    PlaybackMode.DIRECT -> MimeTypes.VIDEO_MP4
                                    PlaybackMode.HLS -> MimeTypes.APPLICATION_M3U8
                                    PlaybackMode.LOCAL -> MimeTypes.VIDEO_MP4
                                }
                        setMimeType(mimeType)
                        if (source.subtitles.isNotEmpty()) {
                            val configurations =
                                source.subtitles.map { subtitle ->
                                    SubtitleConfiguration
                                        .Builder(Uri.parse(subtitle.url))
                                        .setMimeType(subtitle.mimeType)
                                        .setLanguage(subtitle.language)
                                        .setLabel(subtitle.label ?: subtitle.language ?: subtitle.trackId)
                                        .setRoleFlags(
                                            if (subtitle.isForced) {
                                                C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE
                                            } else {
                                                C.ROLE_FLAG_SUBTITLE
                                            },
                                        ).setId(subtitle.trackId)
                                        .build()
                                }
                            setSubtitleConfigurations(configurations)
                        }
                    }.build()

            val mediaSource =
                when (source.mode) {
                    PlaybackMode.DIRECT ->
                        ProgressiveMediaSource
                            .Factory(
                                DefaultHttpDataSource
                                    .Factory()
                                    .setDefaultRequestProperties(source.headers),
                            ).createMediaSource(mediaItem)

                    PlaybackMode.HLS ->
                        HlsMediaSource
                            .Factory(
                                DefaultHttpDataSource
                                    .Factory()
                                    .setDefaultRequestProperties(source.headers),
                            ).createMediaSource(mediaItem)

                    PlaybackMode.LOCAL ->
                        ProgressiveMediaSource
                            .Factory(DefaultDataSource.Factory(appContext))
                            .createMediaSource(mediaItem)
                }

            exoPlayer.stop()
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.seekTo(startPositionMs)
            applyAudioTrack(audioTrack)
            applySubtitleTrack(subtitleTrack)
        }
    }

    override fun play() {
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun stop() {
        firstFrameWatchdog?.cancel()
        audioOutputWatchdog?.cancel()
        expectsAudioOutput = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun setAudioTrack(track: AudioTrack?): AudioTrackSelectionResult {
        pendingAudioTrack = track
        val result = applyAudioTrack(track)
        pendingAudioSelectionConfirmationId =
            track
                ?.id
                ?.takeIf { result == AudioTrackSelectionResult.PENDING }
        return result
    }

    override fun setSubtitleTrack(track: SubtitleTrack?): SubtitleTrackSelectionResult {
        pendingSubtitleTrack = track
        val result = applySubtitleTrack(track)
        pendingSubtitleSelectionConfirmationId =
            track
                ?.id
                ?.takeIf { result == SubtitleTrackSelectionResult.PENDING }
        return result
    }

    override fun setVideoQuality(maxBitrate: Int?) {
        val builder =
            exoPlayer
                .trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

        if (maxBitrate != null && maxBitrate > 0) {
            builder.setMaxVideoBitrate(maxBitrate)
        } else {
            builder.setMaxVideoBitrate(Int.MAX_VALUE)
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    override fun release() {
        firstFrameWatchdog?.cancel()
        audioOutputWatchdog?.cancel()
        positionJob.cancel()
        exoPlayer.release()
        scope.cancel()
    }

    internal fun media3Player(): Player = exoPlayer

    private fun scheduleFirstFrameWatchdog() {
        if (firstFrameRendered || !exoPlayer.playWhenReady || exoPlayer.videoFormat == null) return
        firstFrameWatchdog?.cancel()
        firstFrameWatchdog =
            scope.launch {
                delay(FIRST_FRAME_TIMEOUT_MS)
                if (!firstFrameRendered && exoPlayer.playWhenReady && exoPlayer.videoFormat != null) {
                    eventFlow.emit(PlayerEvent.VideoOutputStalled)
                }
            }
    }

    private fun scheduleAudioOutputWatchdog() {
        if (
            !expectsAudioOutput ||
            audioOutputUnavailableReported ||
            !exoPlayer.playWhenReady ||
            exoPlayer.audioFormat != null ||
            exoPlayer.currentTracks.hasSelectedAudioTrack()
        ) {
            return
        }
        audioOutputWatchdog?.cancel()
        audioOutputWatchdog =
            scope.launch {
                delay(AUDIO_OUTPUT_TIMEOUT_MS)
                if (
                    expectsAudioOutput &&
                    !audioOutputUnavailableReported &&
                    exoPlayer.playWhenReady &&
                    exoPlayer.playbackState == Player.STATE_READY &&
                    exoPlayer.audioFormat == null &&
                    !exoPlayer.currentTracks.hasSelectedAudioTrack()
                ) {
                    audioOutputUnavailableReported = true
                    eventFlow.emit(PlayerEvent.AudioOutputUnavailable)
                }
            }
    }

    private fun currentRuntimeStats(): PlaybackRuntimeStats {
        val video = exoPlayer.videoFormat
        val audio = exoPlayer.audioFormat
        val transfer = video?.colorInfo?.colorTransfer
        return runtimeStatsFlow.value.copy(
            videoCodec = video?.codecs ?: video?.sampleMimeType,
            audioCodec = audio?.codecs ?: audio?.sampleMimeType,
            width = video?.width?.takeIf { it > 0 },
            height = video?.height?.takeIf { it > 0 },
            videoBitrate = video?.bitrate?.takeIf { it > 0 },
            frameRate = video?.frameRate?.takeIf { it > 0f },
            hdr =
                when (transfer) {
                    COLOR_TRANSFER_ST2084 -> "HDR10/PQ"
                    COLOR_TRANSFER_HLG -> "HLG"
                    else -> null
                },
            bufferedDurationMs = exoPlayer.totalBufferedDuration.coerceAtLeast(0L),
            droppedFrames = exoPlayer.videoDecoderCounters?.droppedBufferCount,
        )
    }

    private fun applyAudioTrack(track: AudioTrack?): AudioTrackSelectionResult {
        val builder =
            exoPlayer
                .trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)

        if (track?.language != null) {
            builder.setPreferredAudioLanguages(track.language)
        } else {
            builder.setPreferredAudioLanguages()
        }

        val override = findAudioOverride(track)
        override?.let {
            builder.addOverride(override)
        }

        exoPlayer.trackSelectionParameters = builder.build()
        return when {
            track == null -> AudioTrackSelectionResult.APPLIED
            override != null -> AudioTrackSelectionResult.APPLIED
            exoPlayer.currentTracks.groups.none { it.type == C.TRACK_TYPE_AUDIO } ->
                AudioTrackSelectionResult.PENDING
            else -> AudioTrackSelectionResult.UNAVAILABLE
        }
    }

    private fun applySubtitleTrack(track: SubtitleTrack?): SubtitleTrackSelectionResult {
        val currentParameters = exoPlayer.trackSelectionParameters
        val override = track?.let(::findSubtitleOverride)
        val builder =
            currentParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (track == null) {
            builder.setPreferredTextLanguages()
            builder.setPreferredTextRoleFlags(0)
            builder.setSelectUndeterminedTextLanguage(false)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            if (track.language != null) {
                builder.setPreferredTextLanguages(track.language)
                builder.setSelectUndeterminedTextLanguage(false)
            } else {
                builder.setPreferredTextLanguages()
                builder.setSelectUndeterminedTextLanguage(true)
            }
            val roleFlags =
                if (track.isForced) {
                    C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_CAPTION
                } else {
                    C.ROLE_FLAG_SUBTITLE
                }
            builder.setPreferredTextRoleFlags(roleFlags)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            override?.let { selectedOverride ->
                builder.addOverride(selectedOverride)
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
        return when {
            track == null -> SubtitleTrackSelectionResult.APPLIED
            override != null -> SubtitleTrackSelectionResult.APPLIED
            exoPlayer.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT } ->
                SubtitleTrackSelectionResult.PENDING
            else -> SubtitleTrackSelectionResult.UNAVAILABLE
        }
    }

    private fun findAudioOverride(track: AudioTrack?): TrackSelectionOverride? {
        if (track == null) return null
        val candidates =
            exoPlayer.currentTracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter(group::isTrackSupported)
                        .map { index -> AudioCandidate(group, index) }
                }.toList()
        val matched =
            candidates.firstOrNull { candidate ->
                val id = candidate.formatId
                id?.equals(track.id, ignoreCase = true) == true ||
                    (
                        track.streamIndex != null &&
                            id?.equals(track.streamIndex.toString(), ignoreCase = true) == true
                    )
            } ?: track.audioIndex?.let(candidates::getOrNull)
                ?: candidates.firstOrNull { candidate ->
                    candidate.languageMatches(track.language) &&
                        candidate.labelMatches(track.title) &&
                        candidate.codecMatches(track.codec)
                } ?: candidates
                .filter { it.languageMatches(track.language) }
                .singleOrNull()
        return matched?.let {
            TrackSelectionOverride(it.group.mediaTrackGroup, it.trackIndex)
        }
    }

    private data class AudioCandidate(
        val group: Tracks.Group,
        val trackIndex: Int,
    ) {
        private val format
            get() = group.getTrackFormat(trackIndex)

        val formatId: String?
            get() = format.id

        fun languageMatches(language: String?): Boolean = language != null && format.language?.equals(language, ignoreCase = true) == true

        fun labelMatches(label: String?): Boolean = label == null || format.label?.equals(label, ignoreCase = true) == true

        fun codecMatches(codec: String?): Boolean {
            if (codec == null) return true
            val mime = format.sampleMimeType.orEmpty().lowercase()
            return when (codec.lowercase()) {
                "aac" -> mime == MimeTypes.AUDIO_AAC
                "mp3" -> mime == MimeTypes.AUDIO_MPEG
                "ac3" -> mime == MimeTypes.AUDIO_AC3
                "eac3", "eac3_joc" -> mime == MimeTypes.AUDIO_E_AC3 || mime == MimeTypes.AUDIO_E_AC3_JOC
                "opus" -> mime == MimeTypes.AUDIO_OPUS
                "vorbis" -> mime == MimeTypes.AUDIO_VORBIS
                "flac" -> mime == MimeTypes.AUDIO_FLAC
                else -> true
            }
        }
    }

    private fun findSubtitleOverride(track: SubtitleTrack): TrackSelectionOverride? {
        val candidates =
            exoPlayer.currentTracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter(group::isTrackSupported)
                        .map { index -> SubtitleCandidate(group, index) }
                }.toList()
        val matched =
            candidates.firstOrNull { candidate ->
                candidate.formatId?.equals(track.id, ignoreCase = true) == true ||
                    (
                        track.streamIndex != null &&
                            candidate.formatId?.equals(track.streamIndex.toString(), ignoreCase = true) == true
                    )
            } ?: candidates.firstOrNull { candidate ->
                candidate.languageMatches(track.language) &&
                    candidate.labelMatches(track.title) &&
                    candidate.formatMatches(track.format) &&
                    candidate.forcedMatches(track.isForced)
            } ?: candidates
                .filter { it.languageMatches(track.language) }
                .singleOrNull()
        return matched?.let { TrackSelectionOverride(it.group.mediaTrackGroup, it.trackIndex) }
    }

    private data class SubtitleCandidate(
        val group: Tracks.Group,
        val trackIndex: Int,
    ) {
        private val format
            get() = group.getTrackFormat(trackIndex)

        val formatId: String?
            get() = format.id

        fun languageMatches(language: String?): Boolean = language != null && format.language?.equals(language, ignoreCase = true) == true

        fun labelMatches(label: String?): Boolean = label != null && format.label?.equals(label, ignoreCase = true) == true

        fun forcedMatches(isForced: Boolean): Boolean = (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) == isForced

        fun formatMatches(subtitleFormat: SubtitleFormat): Boolean {
            val mime = format.sampleMimeType.orEmpty().lowercase()
            return when (subtitleFormat) {
                SubtitleFormat.SRT -> mime == MimeTypes.APPLICATION_SUBRIP
                SubtitleFormat.VTT -> mime == MimeTypes.TEXT_VTT
                SubtitleFormat.ASS,
                SubtitleFormat.SSA,
                -> mime == MimeTypes.TEXT_SSA
                SubtitleFormat.PGS,
                SubtitleFormat.SUP,
                -> mime == MimeTypes.APPLICATION_PGS
                SubtitleFormat.UNKNOWN -> true
            }
        }
    }

    private companion object {
        private const val AUDIO_OUTPUT_TIMEOUT_MS = 1_500L
        private const val FIRST_FRAME_TIMEOUT_MS = 8_000L
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }
}

private fun Tracks.hasSelectedAudioTrack(): Boolean = groups.any { group -> group.type == C.TRACK_TYPE_AUDIO && group.isSelected }
