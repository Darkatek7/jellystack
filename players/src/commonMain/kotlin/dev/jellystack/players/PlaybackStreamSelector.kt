package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import kotlin.math.round

private val RESOLUTION_REGEX = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)

private val SUPPORTED_DIRECT_VIDEO_CODECS =
    setOf(
        "h264",
        "h265",
        "hevc",
        "av1",
    )

private data class VideoQualityPreset(
    val bitrate: Int,
    val maxHeight: Int,
)

private val VIDEO_QUALITY_PRESETS =
    listOf(
        VideoQualityPreset(120_000_000, 2160),
        VideoQualityPreset(80_000_000, 2160),
        VideoQualityPreset(60_000_000, 2160),
        VideoQualityPreset(40_000_000, 2160),
        VideoQualityPreset(20_000_000, 2160),
        VideoQualityPreset(15_000_000, 1440),
        VideoQualityPreset(10_000_000, 1440),
        VideoQualityPreset(8_000_000, 1080),
        VideoQualityPreset(6_000_000, 1080),
        VideoQualityPreset(4_000_000, 720),
        VideoQualityPreset(3_000_000, 720),
        VideoQualityPreset(1_500_000, 720),
        VideoQualityPreset(720_000, 480),
        VideoQualityPreset(420_000, 360),
    )

class PlaybackStreamSelector {
    fun select(
        mediaSources: List<JellyfinMediaSource>,
        preferred: PlaybackQualityOption? = null,
        mediaKind: PlaybackMediaKind = PlaybackMediaKind.VIDEO,
    ): PlaybackStreamSelection {
        require(mediaSources.isNotEmpty()) { "Playback requires at least one media source." }

        val directSources = mediaSources.filter { it.supportsDirectPlay || it.supportsDirectStream }
        val hlsSources = mediaSources.filter { it.supportsTranscoding }

        val directCandidate =
            directSources
                .asSequence()
                .mapNotNull { source ->
                    val primaryStream =
                        source.streams.firstOrNull {
                            it.type ==
                                if (mediaKind == PlaybackMediaKind.AUDIO) {
                                    JellyfinMediaStreamType.AUDIO
                                } else {
                                    JellyfinMediaStreamType.VIDEO
                                }
                        }
                    val supported =
                        if (mediaKind == PlaybackMediaKind.AUDIO) {
                            primaryStream != null
                        } else {
                            primaryStream?.codec?.let { it.lowercase() in SUPPORTED_DIRECT_VIDEO_CODECS } == true
                        }
                    if (primaryStream != null && supported) {
                        Triple(source, primaryStream, resolutionScore(primaryStream))
                    } else {
                        null
                    }
                }.maxWithOrNull(
                    compareBy<Triple<JellyfinMediaSource, JellyfinMediaStream, Int>> { it.third }
                        .thenBy { it.first.videoBitrate ?: 0 },
                )?.first

        val hlsCandidate = hlsSources.firstOrNull() ?: mediaSources.first()

        val defaultMode = if (directCandidate != null) PlaybackMode.DIRECT else PlaybackMode.HLS
        val defaultSource =
            when (defaultMode) {
                PlaybackMode.DIRECT -> directCandidate ?: hlsCandidate
                PlaybackMode.HLS -> hlsCandidate
                PlaybackMode.LOCAL -> mediaSources.first()
            }

        val qualityOptions =
            buildQualityOptions(
                hlsSources = hlsSources,
                defaultMode = defaultMode,
                defaultSource = defaultSource,
                mediaKind = mediaKind,
            )

        val preferredOption =
            preferred
                ?.takeIf { !it.isAuto }
                ?.let { option -> qualityOptions.firstOrNull { it.id == option.id } }

        val (selectedSource, selectedMode, selectedMaxBitrate, selectedQualityId) =
            if (preferredOption != null) {
                val targetMode = preferredOption.mode
                val resolvedSource =
                    when (targetMode) {
                        PlaybackMode.DIRECT ->
                            directSources.firstOrNull { it.id == preferredOption.sourceId }
                                ?: directCandidate
                                ?: defaultSource
                        PlaybackMode.HLS ->
                            hlsSources.firstOrNull { it.id == preferredOption.sourceId }
                                ?: hlsCandidate
                        PlaybackMode.LOCAL -> defaultSource
                    }
                QuadrupleData(
                    source = resolvedSource ?: defaultSource,
                    mode = targetMode,
                    maxBitrate = preferredOption.maxBitrate,
                    qualityId = preferredOption.id,
                )
            } else {
                QuadrupleData(
                    source = defaultSource,
                    mode = defaultMode,
                    maxBitrate = null,
                    qualityId = PlaybackQualityOption.AUTO_ID,
                )
            }

        return buildSelection(
            source = selectedSource,
            mode = selectedMode,
            maxBitrate = selectedMaxBitrate,
            qualityOptions = qualityOptions,
            selectedQualityId = selectedQualityId,
        )
    }

    private data class QuadrupleData(
        val source: JellyfinMediaSource,
        val mode: PlaybackMode,
        val maxBitrate: Int?,
        val qualityId: String,
    )

    private fun buildSelection(
        source: JellyfinMediaSource,
        mode: PlaybackMode,
        maxBitrate: Int?,
        qualityOptions: List<PlaybackQualityOption>,
        selectedQualityId: String,
    ): PlaybackStreamSelection {
        val audioTracks =
            source.streams
                .filter { it.type == JellyfinMediaStreamType.AUDIO }
                .mapIndexedNotNull { audioIndex, stream -> stream.toAudioTrack(audioIndex) }
        val subtitleTracks = source.streams.mapNotNull { it.toSubtitleTrack() }
        val defaultAudioCodec =
            audioTracks.firstOrNull { it.isDefault }?.codec
                ?: audioTracks.firstOrNull()?.codec
        val videoCodec =
            source.streams
                .firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
                ?.codec
        val videoStream = source.streams.firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
        return PlaybackStreamSelection(
            sourceId = source.id,
            mode = mode,
            container = source.container,
            videoCodec = videoCodec,
            audioCodec = defaultAudioCodec,
            videoBitrate = source.videoBitrate?.takeIf { it > 0 } ?: videoStream?.bitrate?.takeIf { it > 0 },
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            maxBitrate = maxBitrate,
            qualityOptions = qualityOptions,
            selectedQualityId = selectedQualityId,
            videoWidth = videoStream?.width,
            videoHeight = videoStream?.height,
        )
    }

    private fun buildQualityOptions(
        hlsSources: List<JellyfinMediaSource>,
        defaultMode: PlaybackMode,
        defaultSource: JellyfinMediaSource,
        mediaKind: PlaybackMediaKind,
    ): List<PlaybackQualityOption> {
        val hlsOptions = if (mediaKind == PlaybackMediaKind.VIDEO) buildHlsQualityOptions(hlsSources) else emptyList()
        val autoOption =
            PlaybackQualityOption(
                id = PlaybackQualityOption.AUTO_ID,
                label = "",
                mode = defaultMode,
                sourceId = defaultSource.id,
                maxBitrate = null,
                maxHeight = null,
                isAuto = true,
            )
        return listOf(autoOption) + hlsOptions
    }

    private fun buildHlsQualityOptions(hlsSources: List<JellyfinMediaSource>): List<PlaybackQualityOption> {
        if (hlsSources.isEmpty()) return emptyList()
        val primary = hlsSources.first()
        val referenceBitrate = hlsSources.mapNotNull { it.effectiveVideoBitrate() }.maxOrNull()
        val presets = qualityPresets(referenceBitrate)

        return presets.map { preset ->
            PlaybackQualityOption(
                id = qualityOptionId(PlaybackMode.HLS, primary.id, preset.bitrate),
                label = formatQuality(preset),
                mode = PlaybackMode.HLS,
                sourceId = primary.id,
                maxBitrate = preset.bitrate,
                maxHeight = preset.maxHeight,
                isAuto = false,
            )
        }
    }

    private fun qualityPresets(referenceBitrate: Int?): List<VideoQualityPreset> {
        if (referenceBitrate == null || referenceBitrate <= 0) return VIDEO_QUALITY_PRESETS
        val nearestHigher = VIDEO_QUALITY_PRESETS.lastOrNull { it.bitrate > referenceBitrate }
        return (listOfNotNull(nearestHigher) + VIDEO_QUALITY_PRESETS.filter { it.bitrate <= referenceBitrate }).distinct()
    }

    private fun JellyfinMediaSource.effectiveVideoBitrate(): Int? =
        videoBitrate?.takeIf { it > 0 }
            ?: streams
                .firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
                ?.bitrate
                ?.takeIf { it > 0 }

    private fun qualityOptionId(
        mode: PlaybackMode,
        sourceId: String?,
        bitrate: Int?,
    ): String =
        buildString {
            append("quality-")
            append(mode.name.lowercase())
            append('-')
            append(sourceId ?: "source")
            append('-')
            append(bitrate ?: 0)
        }

    private fun formatBitrate(bitrate: Int): String {
        if (bitrate <= 0) return ""
        val megabits = bitrate / 1_000_000.0
        return if (megabits >= 1.0) {
            "${round(megabits * 10.0) / 10.0} Mbps"
        } else {
            "${round(bitrate / 1_000.0).toLong()} Kbps"
        }
    }

    private fun formatQuality(preset: VideoQualityPreset): String = "${formatBitrate(preset.bitrate)} · ${preset.maxHeight}p"

    private fun resolutionScore(stream: JellyfinMediaStream): Int {
        val resolution =
            stream.displayTitle?.let { title ->
                RESOLUTION_REGEX
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
        return resolution ?: 0
    }
}
