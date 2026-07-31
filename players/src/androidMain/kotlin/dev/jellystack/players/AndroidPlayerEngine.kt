package dev.jellystack.players

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.C.COLOR_TRANSFER_HLG
import androidx.media3.common.C.COLOR_TRANSFER_ST2084
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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PlayerEngine {
    private val appContext = context.applicationContext
    private var pendingAudioTrack: AudioTrack? = null
    private var pendingAudioSelectionConfirmationId: String? = null
    private var pendingSubtitleTrack: SubtitleTrack? = null
    private var videoSurface: PlayerView? = null
    private var subtitleTextSize: SubtitleTextSize = SubtitleTextSize.SYSTEM
    private var subtitleBackground: SubtitleBackground = SubtitleBackground.SYSTEM

    private val exoPlayer =
        ExoPlayer
            .Builder(context)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> eventFlow.tryEmit(PlayerEvent.Buffering)
                                Player.STATE_READY -> eventFlow.tryEmit(PlayerEvent.Ready)
                                Player.STATE_ENDED -> eventFlow.tryEmit(PlayerEvent.Completed)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            scope.launch { eventFlow.emit(PlayerEvent.Error(error)) }
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            val audioResult =
                                applyTrackPreferences(
                                    audioTrack = pendingAudioTrack,
                                    subtitleTrack = pendingSubtitleTrack,
                                )
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

    fun createVideoSurface(context: Context): View =
        PlayerView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            keepScreenOn = true
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
            applyTrackPreferences(
                audioTrack = audioTrack,
                subtitleTrack = subtitleTrack,
            )
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

    override fun setSubtitleTrack(track: SubtitleTrack?) {
        pendingSubtitleTrack = track
        applySubtitleTrack(track)
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
        positionJob.cancel()
        exoPlayer.release()
        scope.cancel()
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

    private fun applySubtitleTrack(track: SubtitleTrack?) {
        val currentParameters = exoPlayer.trackSelectionParameters
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
            findSubtitleOverride(track)?.let { override ->
                builder.addOverride(override)
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun applyTrackPreferences(
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ): AudioTrackSelectionResult {
        val audioResult = applyAudioTrack(audioTrack)
        applySubtitleTrack(subtitleTrack)
        return audioResult
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
        val groups = exoPlayer.currentTracks.groups
        groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val idMatches =
                    format.id?.equals(track.id, ignoreCase = true) == true ||
                        (track.streamIndex != null && format.id?.equals(track.streamIndex.toString(), ignoreCase = true) == true)
                val languageMatches =
                    track.language != null &&
                        format.language?.equals(track.language, ignoreCase = true) == true
                val labelMatches = track.title != null && format.label == track.title
                if (idMatches || labelMatches || languageMatches) {
                    return TrackSelectionOverride(group.mediaTrackGroup, index)
                }
            }
        }
        return null
    }

    private companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }
}
