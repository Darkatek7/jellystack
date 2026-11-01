package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.isMusicContainer
import dev.jellystack.core.jellyfin.isPlayableAudio
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackStartDecision
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.decidePlaybackStart
import dev.jellystack.players.formatPlaybackTime

/**
 * How playback of [this] starts. Music never asks whether to resume and always starts at the beginning, whatever the
 * resume setting is; anything else follows [decidePlaybackStart].
 */
internal fun JellyfinItem.startDecision(resumeMode: ResumeMode): PlaybackStartDecision =
    if (isPlayableAudio()) {
        PlaybackStartDecision.Start(PlaybackStartPolicy.RESTART)
    } else {
        decidePlaybackStart(positionTicks, resumeMode)
    }

/** What the player route draws: the video player, or the now-playing view for music. */
internal enum class TvPlayerSurface { VIDEO, AUDIO }

private fun surfaceOf(audio: Boolean) = if (audio) TvPlayerSurface.AUDIO else TvPlayerSurface.VIDEO

private fun PlaybackMediaKind.surface() = surfaceOf(this == PlaybackMediaKind.AUDIO)

private fun JellyfinItem.surface() = surfaceOf(isPlayableAudio() || isMusicContainer())

/**
 * The surface the current playback or start needs, or null while nothing says (stopped and idle), when the screen
 * keeps what it shows. The controller's kind wins; before it has one, the item the user started decides.
 */
internal fun tvPlayerSurface(
    playback: PlaybackState,
    start: TvPlaybackStartState,
): TvPlayerSurface? =
    when {
        playback is PlaybackState.Active -> playback.mediaKind.surface()
        playback is PlaybackState.Preparing -> playback.mediaKind.surface()
        playback is PlaybackState.PlaybackError -> playback.mediaKind.surface()
        start is TvPlaybackStartState.Starting -> start.item.surface()
        start is TvPlaybackStartState.Failed -> start.item.surface()
        else -> null
    }

/** What Previous does: a track that has played a while starts over, an early press goes back to the track before. */
internal enum class TvAudioPreviousAction { RESTART_TRACK, PREVIOUS_TRACK }

/** Within this long of the start of a track, Previous goes to the track before it. */
internal const val TV_AUDIO_RESTART_THRESHOLD_MS = 3_000L

internal fun tvAudioPreviousAction(
    positionMs: Long,
    hasPrevious: Boolean,
): TvAudioPreviousAction =
    if (hasPrevious && positionMs <= TV_AUDIO_RESTART_THRESHOLD_MS) {
        TvAudioPreviousAction.PREVIOUS_TRACK
    } else {
        TvAudioPreviousAction.RESTART_TRACK
    }

/** The playing track as the now-playing view shows it. */
internal data class TvAudioNowPlaying(
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val isPaused: Boolean,
) {
    /** How far into the track, 0 to 1; 0 while the length is unknown. */
    val progress: Float =
        durationMs
            ?.takeIf { it > 0L }
            ?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
            ?: 0f

    val positionLabel: String = formatPlaybackTime(positionMs)
    val durationLabel: String? = durationMs?.takeIf { it > 0L }?.let(::formatPlaybackTime)
}

internal fun PlaybackState.Active.toAudioNowPlaying(
    images: TvPlayerImageSource,
    untitled: String,
): TvAudioNowPlaying {
    val track = metadata
    val albumId = track?.albumId
    val albumTag = track?.albumPrimaryImageTag
    // The cover of the album, which every track of it shares, before a picture the track might carry on its own.
    val artworkUrl =
        if (albumId != null && albumTag != null) {
            jellyfinImageUrl(images.baseUrl, images.token, albumId, albumTag, maxWidth = ARTWORK_WIDTH)
        } else {
            jellyfinImageUrl(images.baseUrl, images.token, mediaId, track?.primaryImageTag, maxWidth = ARTWORK_WIDTH)
        }
    return TvAudioNowPlaying(
        title = track?.title?.takeIf(String::isNotBlank) ?: untitled,
        artist = track?.artist?.takeIf(String::isNotBlank),
        album = track?.albumName?.takeIf(String::isNotBlank),
        artworkUrl = artworkUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        isPaused = isPaused,
    )
}

private const val ARTWORK_WIDTH = 900
