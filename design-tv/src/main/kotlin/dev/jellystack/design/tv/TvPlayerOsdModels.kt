package dev.jellystack.design.tv

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.jellystack.core.jellyfin.jellyfinChannelLayout
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.PlaybackExtrasState
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SubtitleTrack
import dev.jellystack.players.playbackEndsAtMs
import dev.jellystack.players.playbackTimelineMarkers
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/** Builds the controls model; chapters and markers only apply when the extras belong to this item. */
internal fun PlaybackState.Active.toTvPlayerOsdModel(
    strings: TvStrings,
    extras: PlaybackExtrasState,
    timelineSegments: List<PlaybackSegment>,
    promptActions: List<TvPlaybackActionModel>,
): TvPlayerOsdModel {
    val chapters = extras.chapters.takeIf { extras.mediaId == mediaId }.orEmpty()
    return TvPlayerOsdModel(
        positionMs = positionMs,
        durationMs = durationMs,
        isPaused = isPaused,
        markers = playbackTimelineMarkers(durationMs, chapters, timelineSegments),
        chapters = chapters,
        audioLabel = tvAudioButtonLabel(audioTrack, strings.locale) ?: strings.automatic,
        audioCompactLabel = tvAudioButtonLabel(audioTrack, strings.locale, withChannels = false),
        subtitleLabel = subtitleTrack?.let { tvSubtitleButtonLabel(it, strings.locale) } ?: strings.off,
        promptActions = promptActions,
    )
}

/**
 * "English 5.1"-style label: the language name in the app's [locale] and, [withChannels], the channel layout when
 * known.
 */
internal fun tvAudioButtonLabel(
    track: AudioTrack?,
    locale: Locale,
    withChannels: Boolean = true,
): String? {
    track ?: return null
    val language = tvLanguageName(track.language, locale) ?: track.title?.takeIf(String::isNotBlank)
    val channels = jellyfinChannelLayout(track.channels)?.label()?.takeIf { withChannels }
    return listOfNotNull(language, channels).joinToString(" ").ifBlank { null }
}

internal fun tvSubtitleButtonLabel(
    track: SubtitleTrack,
    locale: Locale,
): String = tvLanguageName(track.language, locale) ?: track.title?.takeIf(String::isNotBlank) ?: track.format.name

/** Title and detail line of a track in the audio or subtitle list. */
internal data class TvTrackRowText(
    val title: String,
    val detail: String?,
)

/**
 * Audio list rows titled like the player chip ("German 5.1"), with codec and default flag as detail. When a title
 * repeats, the server's track title joins the detail, so commentary and main audio stay apart.
 */
internal fun tvAudioTrackRows(
    tracks: List<AudioTrack>,
    strings: TvStrings,
): List<TvTrackRowText> {
    val titles =
        tracks.mapIndexed { index, track ->
            tvAudioButtonLabel(track, strings.locale) ?: strings.audioTrack.format(track.streamIndex ?: (index + 1))
        }
    return tracks.mapIndexed { index, track ->
        val flags = listOfNotNull(track.codec?.uppercase(), strings.player.defaultTrack.takeIf { track.isDefault })
        val title = titles[index]
        TvTrackRowText(title, tvTrackDetail(flags, track.title, repeated = titles.count { it == title } > 1))
    }
}

/** Subtitle list rows titled like the player chip, with forced and default flags and the format as detail. */
internal fun tvSubtitleTrackRows(
    tracks: List<SubtitleTrack>,
    strings: TvStrings,
): List<TvTrackRowText> {
    val titles = tracks.map { tvSubtitleButtonLabel(it, strings.locale) }
    return tracks.mapIndexed { index, track ->
        val flags =
            listOfNotNull(
                strings.player.forcedTrack.takeIf { track.isForced },
                strings.player.defaultTrack.takeIf { track.isDefault },
                track.format.name,
            )
        val title = titles[index]
        TvTrackRowText(title, tvTrackDetail(flags, track.title, repeated = titles.count { it == title } > 1))
    }
}

private fun tvTrackDetail(
    flags: List<String>,
    serverTitle: String?,
    repeated: Boolean,
): String? {
    val title = serverTitle?.takeIf { repeated && it.isNotBlank() }
    return (flags + listOfNotNull(title)).joinToString(" · ").ifBlank { null }
}

private fun tvLanguageName(
    code: String?,
    locale: Locale,
): String? {
    val tag = code?.trim()?.takeIf(String::isNotBlank) ?: return null
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale)
    return name.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
}

/** Wall clock and "Ends at" for the player header. */
internal data class TvPlayerClock(
    val time: String,
    val endsAt: String?,
)

/** Current time in milliseconds, updated at the start of every minute. */
@Composable
internal fun rememberTvClockMs(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MS - value % MINUTE_MS)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** Formats the clock in the device's 12/24-hour setting. */
@Composable
internal fun tvPlayerClockLabels(
    nowMs: Long,
    active: PlaybackState.Active,
    strings: TvStrings,
): TvPlayerClock {
    val format = DateFormat.getTimeFormat(LocalContext.current)
    val endsAt = playbackEndsAtMs(nowMs, active.positionMs, active.durationMs, active.playbackSpeed)
    return TvPlayerClock(
        time = format.format(Date(nowMs)),
        endsAt = endsAt?.let { strings.player.endsAt.format(format.format(Date(it))) },
    )
}

private const val MINUTE_MS = 60_000L
