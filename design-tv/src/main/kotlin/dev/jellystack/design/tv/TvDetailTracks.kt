@file:Suppress("MaxLineLength")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleFormat
import dev.jellystack.players.SubtitleTrack

/** One audio track or subtitle the picker offers; [streamIndex] is the server's index of the stream. */
internal data class TvDetailTrackOption(
    val streamIndex: Int,
    val title: String,
    val detail: String?,
)

/** One media source (version) of the item, such as the 4K and the 1080p file. */
internal data class TvDetailVersionOption(
    val sourceId: String,
    val title: String,
    val detail: String?,
)

/**
 * What the user picked for this detail screen; a null part follows the file and the language preferences. It lives
 * only as long as the screen does.
 */
internal data class TvDetailSelection(
    val sourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val subtitlesOff: Boolean = false,
) {
    /** Picking another version resets the tracks, whose indexes belong to the previous file. */
    fun withVersion(id: String?): TvDetailSelection = TvDetailSelection(sourceId = id)

    fun withAudio(index: Int?): TvDetailSelection = copy(audioStreamIndex = index)

    /** A null [index] switches the subtitles off. */
    fun withSubtitle(index: Int?): TvDetailSelection = copy(subtitleStreamIndex = index, subtitlesOff = index == null)

    /** The override to start playback with, or null when nothing was picked. */
    fun toPlayChoice(): TvPlayChoice? =
        if (this == TvDetailSelection()) null else TvPlayChoice(sourceId, audioStreamIndex, subtitleStreamIndex, subtitlesOff)
}

/** The picker's choices for one version of an item, with the current picks. */
internal data class TvDetailPlaybackOptions(
    val versions: List<TvDetailVersionOption>,
    val selectedSourceId: String?,
    val audio: List<TvDetailTrackOption>,
    val subtitles: List<TvDetailTrackOption>,
    val audioStreamIndex: Int?,
    /** Null means off. */
    val subtitleStreamIndex: Int?,
)

internal fun JellyfinItemDetail.tvSelectedSource(selection: TvDetailSelection): JellyfinMediaSource? =
    selection.sourceId?.let { id -> mediaSources.firstOrNull { it.id == id } } ?: mediaSources.firstOrNull()

private fun JellyfinMediaStream.toAudioTrack(): AudioTrack =
    AudioTrack(
        id = index.toString(),
        language = language,
        title = displayTitle,
        codec = codec,
        isDefault = isDefault,
        streamIndex = index,
        channels = channels,
    )

private fun JellyfinMediaStream.toSubtitleTrack(): SubtitleTrack =
    SubtitleTrack(
        id = index.toString(),
        language = language,
        title = displayTitle,
        format = SubtitleFormat.UNKNOWN,
        isDefault = isDefault,
        isForced = isForced,
        streamIndex = index,
    )

private fun JellyfinMediaSource.streamsOf(type: JellyfinMediaStreamType) = streams.filter { it.type == type && it.index != null }

/** The audio tracks of [this] version, titled like the player's track list ("German 5.1"). */
internal fun JellyfinMediaSource.tvAudioOptions(strings: TvStrings): List<TvDetailTrackOption> {
    val tracks = streamsOf(JellyfinMediaStreamType.AUDIO).map(JellyfinMediaStream::toAudioTrack)
    return tvAudioTrackRows(tracks, strings).mapIndexed { position, row ->
        TvDetailTrackOption(tracks[position].streamIndex ?: position, row.title, row.detail)
    }
}

/** The subtitles of [this] version, titled like the player's track list; the detail names the flags and the codec. */
internal fun JellyfinMediaSource.tvSubtitleOptions(strings: TvStrings): List<TvDetailTrackOption> {
    val streams = streamsOf(JellyfinMediaStreamType.SUBTITLE)
    return streams.mapIndexed { position, stream ->
        val track = stream.toSubtitleTrack()
        val flags =
            listOfNotNull(
                strings.player.forcedTrack.takeIf { stream.isForced },
                strings.player.defaultTrack.takeIf { stream.isDefault },
                stream.codec?.uppercase()?.takeIf(String::isNotBlank),
            )
        TvDetailTrackOption(
            streamIndex = track.streamIndex ?: position,
            title = tvSubtitleButtonLabel(track, strings.locale),
            detail = flags.joinToString(" · ").ifBlank { null },
        )
    }
}

/** The versions of an item when it has more than one; a single file needs no choice. */
internal fun JellyfinItemDetail.tvVersionOptions(strings: TvStrings): List<TvDetailVersionOption> {
    if (mediaSources.size < 2) return emptyList()
    return mediaSources.mapIndexed { position, source ->
        val video = source.streams.firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
        val resolution = video?.height?.let { "${it}p" }
        TvDetailVersionOption(
            sourceId = source.id,
            title = source.name?.takeIf(String::isNotBlank) ?: "${strings.detailScreen.versionLabel} ${position + 1}",
            detail =
                listOfNotNull(resolution, video?.codec?.uppercase(), source.container?.uppercase())
                    .joinToString(" · ")
                    .ifBlank { null },
        )
    }
}

/** The stream the file plays by default: the one flagged default, else the first. */
internal fun List<JellyfinMediaStream>.defaultStreamIndex(type: JellyfinMediaStreamType): Int? {
    val candidates = filter { it.type == type && it.index != null }
    return (candidates.firstOrNull { it.isDefault } ?: candidates.firstOrNull())?.index
}

/**
 * The choices and current picks for [detail]: what the user chose, else the file's default audio and a subtitle only
 * if the file flags a (non-forced) one as default. Null for an item without media sources.
 */
internal fun tvPlaybackOptions(
    detail: JellyfinItemDetail,
    selection: TvDetailSelection,
    strings: TvStrings,
): TvDetailPlaybackOptions? {
    val source = detail.tvSelectedSource(selection) ?: return null
    val audio = source.tvAudioOptions(strings)
    val subtitles = source.tvSubtitleOptions(strings)
    val defaultSubtitle =
        source.streamsOf(JellyfinMediaStreamType.SUBTITLE).firstOrNull { it.isDefault && !it.isForced }?.index
    return TvDetailPlaybackOptions(
        versions = detail.tvVersionOptions(strings),
        selectedSourceId = source.id,
        audio = audio,
        subtitles = subtitles,
        audioStreamIndex =
            selection.audioStreamIndex?.takeIf { index -> audio.any { it.streamIndex == index } }
                ?: source.streams.defaultStreamIndex(JellyfinMediaStreamType.AUDIO),
        subtitleStreamIndex =
            if (selection.subtitlesOff) {
                null
            } else {
                selection.subtitleStreamIndex?.takeIf { index -> subtitles.any { it.streamIndex == index } } ?: defaultSubtitle
            },
    )
}

/**
 * "Audio: German 5.1  ·  Subtitles: Off  ·  Version: 4K"; null when there is nothing to pick, so the line stays
 * hidden. The version part only appears when the item has several files.
 */
internal fun tvTrackLineSummary(
    options: TvDetailPlaybackOptions?,
    strings: TvStrings,
): String? {
    options ?: return null
    val copy = strings.detailScreen
    val parts =
        buildList {
            options.audio.firstOrNull { it.streamIndex == options.audioStreamIndex }?.let {
                add(copy.trackLineFormat.format(copy.audioLabel, it.title))
            }
            if (options.subtitles.isNotEmpty()) {
                val subtitle = options.subtitles.firstOrNull { it.streamIndex == options.subtitleStreamIndex }
                add(copy.trackLineFormat.format(copy.subtitlesLabel, subtitle?.title ?: strings.off))
            }
            options.versions.firstOrNull { it.sourceId == options.selectedSourceId }?.let {
                add(copy.trackLineFormat.format(copy.versionLabel, it.title))
            }
        }
    return parts.joinToString(TV_TRACK_LINE_SEPARATOR).ifBlank { null }
}

private const val TV_TRACK_LINE_SEPARATOR = "  ·  "
