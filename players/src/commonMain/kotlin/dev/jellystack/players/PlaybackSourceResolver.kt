package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment

data class ResolvedPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val mode: PlaybackMode,
    val mimeType: String?,
    val subtitles: List<ResolvedSubtitle>,
    val playSessionId: String?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val mediaSourceId: String? = null,
    val supportsTranscoding: Boolean? = null,
    val isFallbackHls: Boolean = false,
    val segmentContainer: String? = null,
)

/** `SubtitleStreamIndex` value that asks the server for no subtitles at all. */
internal const val SUBTITLES_DISABLED_INDEX = -1

data class PlaybackSourceOptions(
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val playSessionId: String? = null,
    val forceTranscoding: Boolean = false,
    val forceAudioTranscoding: Boolean = false,
    /**
     * Ask the server for exactly one audio rendition of the chosen track (used by an explicit
     * track switch). Devices that already negotiate this way get their best audio target; all
     * others get the stereo AAC compatibility rendition, as with [forceAudioTranscoding].
     */
    val selectAudioOnServer: Boolean = false,
    val preferFmp4Hls: Boolean = false,
    val stopEncodingPlaySessionId: String? = null,
)

data class ResolvedSubtitle(
    val trackId: String,
    val url: String,
    val mimeType: String,
    val isForced: Boolean,
    val language: String?,
    val label: String?,
)

fun interface PlaybackSourceResolver {
    suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource

    /**
     * Whether a directly played file can switch to an audio track with this codec inside the player.
     * Resolvers that cannot tell keep the local switch.
     */
    fun canPlayAudioCodecLocally(codec: String?): Boolean = true
}
