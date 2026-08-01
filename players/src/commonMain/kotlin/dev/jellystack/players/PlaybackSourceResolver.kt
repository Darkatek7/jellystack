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
)

data class PlaybackSourceOptions(
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val playSessionId: String? = null,
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
}
