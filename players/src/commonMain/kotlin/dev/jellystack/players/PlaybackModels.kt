package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.players.cast.CastSessionSnapshot

private const val TICKS_PER_MILLISECOND = 10_000L

internal fun Long.toMillisFromTicks(): Long = this / TICKS_PER_MILLISECOND

internal fun Long.toTicks(): Long = this * TICKS_PER_MILLISECOND

internal fun ticksToMillis(value: Long?): Long? = value?.toMillisFromTicks()

data class PlaybackRequest(
    val mediaId: String,
    val mediaSources: List<JellyfinMediaSource>,
    val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
    val resumePositionTicks: Long? = null,
    val durationTicks: Long? = null,
    val preferredAudioTrackId: String? = null,
    val preferredSubtitleTrackId: String? = null,
    val startPolicy: PlaybackStartPolicy = PlaybackStartPolicy.INHERIT,
    val metadata: PlaybackMetadata? = null,
) {
    companion object {
        fun from(
            item: JellyfinItem,
            detail: JellyfinItemDetail,
            preferredAudioTrackId: String? = null,
            preferredSubtitleTrackId: String? = null,
            startPolicy: PlaybackStartPolicy = PlaybackStartPolicy.INHERIT,
        ): PlaybackRequest =
            PlaybackRequest(
                mediaId = item.id,
                mediaSources = detail.mediaSources,
                mediaKind = item.playbackMediaKind(),
                resumePositionTicks = item.positionTicks,
                durationTicks =
                    detail.runTimeTicks
                        ?: detail.mediaSources
                            .asSequence()
                            .mapNotNull { it.runTimeTicks }
                            .maxOrNull(),
                preferredAudioTrackId = preferredAudioTrackId,
                preferredSubtitleTrackId = preferredSubtitleTrackId,
                startPolicy = startPolicy,
                metadata =
                    PlaybackMetadata(
                        title = detail.name ?: item.name,
                        seriesId = item.seriesId,
                        seriesName = item.seriesName,
                        episodeName = item.episodeTitle,
                        seasonNumber = item.parentIndexNumber,
                        episodeNumber = item.indexNumber,
                        artworkUrl = null,
                        primaryImageTag = detail.primaryImageTag ?: item.primaryImageTag,
                    ),
            )
    }
}

enum class PlaybackStartPolicy {
    INHERIT,
    RESUME,
    RESTART,
}

enum class PlaybackMediaKind {
    VIDEO,
    AUDIO,
}

enum class PlaybackPhase {
    Buffering,
    Ready,
    Ended,
}

private fun JellyfinItem.playbackMediaKind(): PlaybackMediaKind =
    if (
        mediaType.equals("Audio", ignoreCase = true) ||
        type.equals("Audio", ignoreCase = true) ||
        type.equals("AudioBook", ignoreCase = true)
    ) {
        PlaybackMediaKind.AUDIO
    } else {
        PlaybackMediaKind.VIDEO
    }

data class PlaybackMetadata(
    val title: String?,
    val seriesId: String?,
    val seriesName: String?,
    val episodeName: String?,
    val artworkUrl: String?,
    val primaryImageTag: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

enum class PlaybackMode {
    DIRECT,
    HLS,
    LOCAL,
}

data class AudioTrack(
    val id: String,
    val language: String?,
    val title: String?,
    val codec: String?,
    val isDefault: Boolean,
    val streamIndex: Int?,
    val audioIndex: Int? = null,
)

data class PlaybackQualityOption(
    val id: String,
    val label: String,
    val mode: PlaybackMode,
    val sourceId: String?,
    val maxBitrate: Int?,
    val maxHeight: Int? = null,
    val isAuto: Boolean,
) {
    companion object {
        const val AUTO_ID = "quality-auto"
    }
}

enum class SubtitleFormat {
    SRT,
    VTT,
    PGS,
    SUP,
    ASS,
    SSA,
    UNKNOWN,
}

data class SubtitleTrack(
    val id: String,
    val language: String?,
    val title: String?,
    val format: SubtitleFormat,
    val isDefault: Boolean,
    val isForced: Boolean,
    val streamIndex: Int?,
)

data class PlaybackStreamSelection(
    val sourceId: String,
    val mode: PlaybackMode,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoBitrate: Int?,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val maxBitrate: Int?,
    val qualityOptions: List<PlaybackQualityOption>,
    val selectedQualityId: String,
)

data class PlaybackSession(
    val request: PlaybackRequest,
    val mediaId: String,
    val stream: PlaybackStreamSelection,
    val positionMs: Long,
    val durationMs: Long?,
    val audioTrack: AudioTrack?,
    val subtitleTrack: SubtitleTrack?,
    val isPaused: Boolean,
    val source: ResolvedPlaybackSource,
    val qualityOptions: List<PlaybackQualityOption>,
    val selectedQualityId: String,
    val phase: PlaybackPhase = PlaybackPhase.Ready,
    val playbackSpeed: Float = 1f,
    val statsForNerdsEnabled: Boolean = false,
    val runtimeStats: PlaybackRuntimeStats = PlaybackRuntimeStats(),
)

data class PlaybackProgress(
    val mediaId: String,
    val positionMs: Long,
)

sealed interface PlaybackState {
    data object Stopped : PlaybackState

    data class Preparing(
        val mediaId: String,
        val metadata: PlaybackMetadata?,
        val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
    ) : PlaybackState

    data class PlaybackError(
        val message: String,
        val cause: Throwable? = null,
        val mediaId: String? = null,
        val metadata: PlaybackMetadata? = null,
        val canRetry: Boolean = false,
        val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
    ) : PlaybackState

    sealed interface Active : PlaybackState {
        val mediaId: String
        val stream: PlaybackStreamSelection
        val positionMs: Long
        val durationMs: Long?
        val audioTrack: AudioTrack?
        val subtitleTrack: SubtitleTrack?
        val isPaused: Boolean
        val source: ResolvedPlaybackSource
        val qualityOptions: List<PlaybackQualityOption>
        val selectedQualityId: String
        val sessionDeviceName: String
        val metadata: PlaybackMetadata?
        val mediaKind: PlaybackMediaKind
        val phase: PlaybackPhase
        val playbackSpeed: Float
        val statsForNerdsEnabled: Boolean
        val runtimeStats: PlaybackRuntimeStats
    }

    data class LocalPlayback(
        override val mediaId: String,
        val deviceName: String,
        override val stream: PlaybackStreamSelection,
        override val positionMs: Long,
        override val durationMs: Long?,
        override val audioTrack: AudioTrack?,
        override val subtitleTrack: SubtitleTrack?,
        override val isPaused: Boolean,
        override val source: ResolvedPlaybackSource,
        override val qualityOptions: List<PlaybackQualityOption>,
        override val selectedQualityId: String,
        override val metadata: PlaybackMetadata? = null,
        override val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
        override val phase: PlaybackPhase = PlaybackPhase.Ready,
        override val playbackSpeed: Float = 1f,
        override val statsForNerdsEnabled: Boolean = false,
        override val runtimeStats: PlaybackRuntimeStats = PlaybackRuntimeStats(),
    ) : Active {
        override val sessionDeviceName: String = deviceName
    }

    data class CastConnecting(
        override val mediaId: String,
        val localDeviceName: String,
        val targetDeviceName: String?,
        override val stream: PlaybackStreamSelection,
        override val positionMs: Long,
        override val durationMs: Long?,
        override val audioTrack: AudioTrack?,
        override val subtitleTrack: SubtitleTrack?,
        override val isPaused: Boolean,
        override val source: ResolvedPlaybackSource,
        override val qualityOptions: List<PlaybackQualityOption>,
        override val selectedQualityId: String,
        override val metadata: PlaybackMetadata? = null,
        override val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
        override val phase: PlaybackPhase = PlaybackPhase.Ready,
        override val playbackSpeed: Float = 1f,
        override val statsForNerdsEnabled: Boolean = false,
        override val runtimeStats: PlaybackRuntimeStats = PlaybackRuntimeStats(),
    ) : Active {
        override val sessionDeviceName: String = targetDeviceName ?: localDeviceName
    }

    data class CastPlayback(
        override val mediaId: String,
        val localDeviceName: String,
        val castDeviceName: String,
        val castSnapshot: CastSessionSnapshot,
        override val stream: PlaybackStreamSelection,
        override val positionMs: Long,
        override val durationMs: Long?,
        override val audioTrack: AudioTrack?,
        override val subtitleTrack: SubtitleTrack?,
        override val isPaused: Boolean,
        override val source: ResolvedPlaybackSource,
        override val qualityOptions: List<PlaybackQualityOption>,
        override val selectedQualityId: String,
        override val metadata: PlaybackMetadata? = null,
        override val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
        override val phase: PlaybackPhase = PlaybackPhase.Ready,
        override val playbackSpeed: Float = 1f,
        override val statsForNerdsEnabled: Boolean = false,
        override val runtimeStats: PlaybackRuntimeStats = PlaybackRuntimeStats(),
    ) : Active {
        override val sessionDeviceName: String = castDeviceName
    }

    data class RecoveringPlayback(
        override val mediaId: String,
        val localDeviceName: String,
        val previousCastDeviceName: String?,
        val reason: String?,
        override val stream: PlaybackStreamSelection,
        override val positionMs: Long,
        override val durationMs: Long?,
        override val audioTrack: AudioTrack?,
        override val subtitleTrack: SubtitleTrack?,
        override val isPaused: Boolean,
        override val source: ResolvedPlaybackSource,
        override val qualityOptions: List<PlaybackQualityOption>,
        override val selectedQualityId: String,
        override val metadata: PlaybackMetadata? = null,
        override val mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
        override val phase: PlaybackPhase = PlaybackPhase.Ready,
        override val playbackSpeed: Float = 1f,
        override val statsForNerdsEnabled: Boolean = false,
        override val runtimeStats: PlaybackRuntimeStats = PlaybackRuntimeStats(),
    ) : Active {
        override val sessionDeviceName: String = localDeviceName
    }
}

sealed interface PlaybackNotice {
    data object AudioTrackSelectionFailed : PlaybackNotice

    data object SubtitleTrackSelectionFailed : PlaybackNotice
}

internal fun JellyfinMediaStream.toAudioTrack(audioIndex: Int? = null): AudioTrack? =
    if (type != JellyfinMediaStreamType.AUDIO) {
        null
    } else {
        val id = index?.toString() ?: displayTitle ?: language ?: "audio-${hashCode()}"
        AudioTrack(
            id = id,
            language = language,
            title = displayTitle,
            codec = codec,
            isDefault = isDefault,
            streamIndex = index,
            audioIndex = audioIndex,
        )
    }

internal fun JellyfinMediaStream.toSubtitleTrack(): SubtitleTrack? {
    if (type != JellyfinMediaStreamType.SUBTITLE) {
        return null
    }
    val format =
        when (codec?.lowercase()) {
            "srt", "subrip" -> SubtitleFormat.SRT
            "webvtt", "vtt" -> SubtitleFormat.VTT
            "pgs" -> SubtitleFormat.PGS
            "sup" -> SubtitleFormat.SUP
            "ass" -> SubtitleFormat.ASS
            "ssa" -> SubtitleFormat.SSA
            else -> SubtitleFormat.UNKNOWN
        }
    val id = index?.toString() ?: displayTitle ?: language ?: "subtitle-${hashCode()}"
    return SubtitleTrack(
        id = id,
        language = language,
        title = displayTitle ?: language ?: format.name,
        format = format,
        isDefault = isDefault,
        isForced = isForced,
        streamIndex = index,
    )
}

internal fun PlaybackStreamSelection.defaultAudioTrack(): AudioTrack? =
    audioTracks.firstOrNull { it.isDefault } ?: audioTracks.firstOrNull()

internal fun PlaybackStreamSelection.defaultSubtitleTrack(): SubtitleTrack? =
    subtitleTracks.firstOrNull { it.isDefault }
