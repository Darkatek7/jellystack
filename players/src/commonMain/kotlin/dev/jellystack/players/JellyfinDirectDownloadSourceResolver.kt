package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment

/** Resolves the original Jellyfin file for offline storage without playback negotiation. */
class JellyfinDirectDownloadSourceResolver(
    private val clientName: String = "Jellystack",
    private val clientVersion: String = "0.1.0",
) : PlaybackSourceResolver {
    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        require(selection.mode == PlaybackMode.DIRECT) {
            "Offline downloads require a direct-play media source"
        }
        val selectedSource =
            request.mediaSources.firstOrNull { it.id == selection.sourceId }
                ?: error("Selected download source ${selection.sourceId} is unavailable")
        require(selectedSource.supportsDirectPlay) {
            "Offline downloads require a direct-play media source"
        }
        val container = selectedSource.container ?: selection.container ?: "mp4"
        val baseUrl = environment.baseUrl.trimEnd('/')
        val mediaRoute = if (request.mediaKind == PlaybackMediaKind.AUDIO) "Audio" else "Videos"
        val url =
            "$baseUrl/$mediaRoute/${request.mediaId}/stream.$container" +
                "?Static=true" +
                "&api_key=${environment.accessToken}" +
                "&MediaSourceId=${selection.sourceId}" +
                "&DeviceId=${environment.deviceId}" +
                "&UserId=${environment.userId}"

        return ResolvedPlaybackSource(
            url = url,
            headers =
                mapOf(
                    "X-Emby-Authorization" to authorizationHeader(environment),
                    "User-Agent" to "$clientName/${environment.deviceName}",
                ),
            mode = PlaybackMode.DIRECT,
            mimeType = directMimeType(request.mediaKind, container),
            subtitles =
                if (request.mediaKind == PlaybackMediaKind.VIDEO) {
                    selection.subtitleTracks.mapNotNull { track ->
                        val streamIndex = track.streamIndex ?: return@mapNotNull null
                        val descriptor = subtitleDescriptor(track.format)
                        ResolvedSubtitle(
                            trackId = track.id,
                            url =
                                "$baseUrl/Videos/${request.mediaId}/${selection.sourceId}" +
                                    "/Subtitles/$streamIndex/stream.${descriptor.extension}" +
                                    "?api_key=${environment.accessToken}" +
                                    "&MediaSourceId=${selection.sourceId}" +
                                    "&SubtitleStreamIndex=$streamIndex" +
                                    "&format=${descriptor.format}",
                            mimeType = descriptor.mimeType,
                            isForced = track.isForced,
                            language = track.language,
                            label = track.title,
                        )
                    }
                } else {
                    emptyList()
                },
            playSessionId = null,
            audioStreamIndex = options.audioStreamIndex ?: selection.defaultAudioTrack()?.streamIndex,
            subtitleStreamIndex = selection.defaultSubtitleTrack()?.streamIndex,
            mediaSourceId = selection.sourceId,
            supportsTranscoding = null,
        )
    }

    private fun authorizationHeader(environment: JellyfinEnvironment): String =
        buildString {
            append("MediaBrowser ")
            append("""Client="$clientName""")
            append(""", Device="${environment.deviceName}""")
            append(""", DeviceId="${environment.deviceId}""")
            append(""", Version="$clientVersion""")
            if (environment.accessToken.isNotEmpty()) {
                append(""", Token="${environment.accessToken}""")
            }
        }

    private fun directMimeType(
        kind: PlaybackMediaKind,
        container: String,
    ): String =
        if (kind == PlaybackMediaKind.VIDEO) {
            when (container.lowercase()) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "ts", "mpegts", "m2ts" -> "video/mp2t"
                "avi" -> "video/x-msvideo"
                else -> "video/*"
            }
        } else {
            when (container.lowercase()) {
                "flac" -> "audio/flac"
                "mp3" -> "audio/mpeg"
                "m4a", "mp4", "aac" -> "audio/mp4"
                "ogg", "oga", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "audio/*"
            }
        }

    private fun subtitleDescriptor(format: SubtitleFormat): SubtitleDescriptor =
        when (format) {
            SubtitleFormat.SRT -> SubtitleDescriptor("srt", "srt", "application/x-subrip")
            SubtitleFormat.VTT -> SubtitleDescriptor("vtt", "vtt", "text/vtt")
            SubtitleFormat.PGS,
            SubtitleFormat.SUP,
            SubtitleFormat.ASS,
            SubtitleFormat.SSA,
            SubtitleFormat.UNKNOWN,
            -> SubtitleDescriptor("vtt", "vtt", "text/vtt")
        }

    private data class SubtitleDescriptor(
        val extension: String,
        val format: String,
        val mimeType: String,
    )
}
