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
        audioLabel = tvAudioButtonLabel(audioTrack) ?: strings.automatic,
        subtitleLabel = subtitleTrack?.let(::tvSubtitleButtonLabel) ?: strings.off,
        promptActions = promptActions,
    )
}

/** "English 5.1"-style label: the language name and the channel layout when known. */
internal fun tvAudioButtonLabel(track: AudioTrack?): String? {
    track ?: return null
    val language = tvLanguageName(track.language) ?: track.title?.takeIf(String::isNotBlank)
    return listOfNotNull(language, jellyfinChannelLayout(track.channels)?.label()).joinToString(" ").ifBlank { null }
}

internal fun tvSubtitleButtonLabel(track: SubtitleTrack): String =
    tvLanguageName(track.language) ?: track.title?.takeIf(String::isNotBlank) ?: track.format.name

private fun tvLanguageName(code: String?): String? {
    val tag = code?.trim()?.takeIf(String::isNotBlank) ?: return null
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
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
