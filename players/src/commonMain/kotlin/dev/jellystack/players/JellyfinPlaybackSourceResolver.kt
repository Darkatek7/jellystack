package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import kotlin.math.max
import kotlin.random.Random

class JellyfinPlaybackSourceResolver(
    private val playbackInfoService: JellyfinPlaybackInfoService = NetworkJellyfinPlaybackInfoService(),
    private val deviceProfileProvider: PlaybackDeviceProfileProvider = ConservativePlaybackDeviceProfileProvider,
    private val clientName: String = "Jellystack",
    private val clientVersion: String = "0.1.0",
) : PlaybackSourceResolver {
    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource =
        if (request.mediaKind == PlaybackMediaKind.AUDIO) {
            resolveAudio(request, selection, environment, startPositionMs, options)
        } else {
            resolveVideo(request, selection, environment, startPositionMs, options)
        }

    private suspend fun resolveVideo(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        val isAuto = selection.selectedQualityId == PlaybackQualityOption.AUTO_ID
        val manualOption =
            if (isAuto) {
                null
            } else {
                checkNotNull(
                    selection.qualityOptions.firstOrNull { it.id == selection.selectedQualityId },
                ) { "Selected manual quality is unavailable: ${selection.selectedQualityId}" }
            }
        val audioStreamIndex = options.audioStreamIndex ?: selection.defaultAudioTrack()?.streamIndex
        val subtitleStreamIndex = options.subtitleStreamIndex ?: selection.defaultSubtitleTrack()?.streamIndex
        val playbackInfoRequest =
            JellyfinPlaybackInfoRequestDto(
                userId = environment.userId,
                deviceProfile = deviceProfileProvider.deviceProfile(),
                mediaSourceId = selection.sourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startTimeTicks = max(0, startPositionMs).toTicks(),
                maxStreamingBitrate = manualOption?.maxBitrate,
                enableDirectPlay = isAuto,
                enableDirectStream = isAuto,
                enableTranscoding = true,
                allowVideoStreamCopy = isAuto,
                allowAudioStreamCopy = true,
            )
        val response =
            playbackInfoService.fetch(
                environment = environment,
                itemId = request.mediaId,
                userId = environment.userId,
                request = playbackInfoRequest,
            )
        val negotiatedSource =
            response.mediaSources.firstOrNull { it.id == selection.sourceId }
                ?: response.mediaSources.firstOrNull()
                ?: error("Jellyfin PlaybackInfo returned no media sources for ${request.mediaId}")
        val mediaSourceId = negotiatedSource.id ?: selection.sourceId
        val isRequestedSource = negotiatedSource.id == selection.sourceId
        val usableTranscodingUrl = negotiatedSource.transcodingUrl?.takeIf { it.isNotBlank() }
        val supportsTranscoding =
            when {
                negotiatedSource.supportsTranscoding == true || usableTranscodingUrl != null -> true
                isRequestedSource -> true
                negotiatedSource.supportsTranscoding == false -> false
                else -> null
            }
        val playSessionId =
            response.playSessionId
                ?: usableTranscodingUrl?.queryParameter("PlaySessionId")
                ?: options.playSessionId
                ?: generatePlaySessionId()
        val resolvedMode: PlaybackMode
        val resolvedUrl: String
        val resolvedMimeType: String?
        val isFallbackHls: Boolean

        val canUseDirectSource =
            isAuto &&
                (
                    negotiatedSource.supportsDirectPlay ||
                        negotiatedSource.supportsDirectStream ||
                        (selection.mode == PlaybackMode.DIRECT && isRequestedSource)
                )
        if (canUseDirectSource) {
            resolvedMode = PlaybackMode.DIRECT
            resolvedUrl =
                buildDirectUrl(
                    request = request,
                    environment = environment,
                    mediaSourceId = mediaSourceId,
                    container = negotiatedSource.container ?: selection.container,
                    startPositionMs = startPositionMs,
                )
            resolvedMimeType = mediaMimeType(PlaybackMediaKind.VIDEO, negotiatedSource.container ?: selection.container)
            isFallbackHls = false
        } else {
            val fallbackHlsUrl =
                if (usableTranscodingUrl == null && isRequestedSource) {
                    buildFallbackHlsUrl(
                        request = request,
                        environment = environment,
                        mediaSourceId = mediaSourceId,
                        playSessionId = playSessionId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                    )
                } else {
                    null
                }
            val serverTranscodeUrl =
                usableTranscodingUrl
                    ?: fallbackHlsUrl
                    ?: error("Jellyfin PlaybackInfo returned no usable playback URL for ${request.mediaId}")
            resolvedMode = PlaybackMode.HLS
            resolvedUrl =
                absoluteServerUrl(
                    environment.baseUrl,
                    if (manualOption == null) {
                        serverTranscodeUrl
                    } else {
                        constrainManualTranscodeUrl(serverTranscodeUrl, manualOption)
                    },
                )
            resolvedMimeType =
                if (
                    negotiatedSource.transcodingSubProtocol.equals("hls", ignoreCase = true) ||
                    serverTranscodeUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                ) {
                    HLS_MIME_TYPE
                } else {
                    mediaMimeType(
                        PlaybackMediaKind.VIDEO,
                        negotiatedSource.transcodingContainer ?: negotiatedSource.container,
                    )
                }
            isFallbackHls = fallbackHlsUrl != null
        }

        return ResolvedPlaybackSource(
            url = resolvedUrl,
            headers = playbackHeaders(environment),
            mode = resolvedMode,
            mimeType = resolvedMimeType,
            subtitles =
                buildSubtitleStreams(
                    baseUrl = environment.baseUrl.trimEnd('/'),
                    request = request,
                    selection = selection,
                    mediaSourceId = mediaSourceId,
                    environment = environment,
                ),
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId,
            supportsTranscoding = supportsTranscoding,
            isFallbackHls = isFallbackHls,
        )
    }

    private fun buildFallbackHlsUrl(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
        mediaSourceId: String,
        playSessionId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ): String =
        buildString {
            append(environment.baseUrl.trimEnd('/'))
            append("/Videos/")
            append(request.mediaId)
            append("/master.m3u8")
            append("?api_key=")
            append(environment.accessToken)
            append("&MediaSourceId=")
            append(mediaSourceId)
            append("&DeviceId=")
            append(environment.deviceId)
            append("&UserId=")
            append(environment.userId)
            append("&PlaySessionId=")
            append(playSessionId)
            append("&VideoCodec=h264")
            append("&AudioCodec=aac")
            append("&SegmentContainer=ts")
            append("&MinSegments=1")
            append("&StartTimeTicks=0")
            audioStreamIndex?.let { index ->
                append("&AudioStreamIndex=")
                append(index)
            }
            subtitleStreamIndex?.let { index ->
                append("&SubtitleStreamIndex=")
                append(index)
            }
        }

    private fun resolveAudio(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        val baseUrl = environment.baseUrl.trimEnd('/')
        val playSessionId = options.playSessionId ?: generatePlaySessionId()
        val audioStreamIndex = options.audioStreamIndex ?: selection.defaultAudioTrack()?.streamIndex
        val (url, mimeType) =
            when (selection.mode) {
                PlaybackMode.DIRECT ->
                    buildDirectUrl(
                        request = request,
                        environment = environment,
                        mediaSourceId = selection.sourceId,
                        container = selection.container,
                        startPositionMs = startPositionMs,
                    ) to mediaMimeType(PlaybackMediaKind.AUDIO, selection.container)

                PlaybackMode.HLS ->
                    buildString {
                        append(baseUrl)
                        append("/Audio/")
                        append(request.mediaId)
                        append("/universal")
                        append("?api_key=")
                        append(environment.accessToken)
                        append("&MediaSourceId=")
                        append(selection.sourceId)
                        append("&DeviceId=")
                        append(environment.deviceId)
                        append("&UserId=")
                        append(environment.userId)
                        append("&PlaySessionId=")
                        append(playSessionId)
                        selection.maxBitrate?.let { bitrate ->
                            append("&MaxStreamingBitrate=")
                            append(bitrate)
                        }
                    } to "audio/aac"

                PlaybackMode.LOCAL ->
                    throw IllegalArgumentException("Local playback must not be resolved via network resolver")
            }

        return ResolvedPlaybackSource(
            url = url,
            headers = playbackHeaders(environment),
            mode = selection.mode,
            mimeType = mimeType,
            subtitles = emptyList(),
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = null,
            mediaSourceId = selection.sourceId,
        )
    }

    private fun buildDirectUrl(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
        mediaSourceId: String,
        container: String?,
        startPositionMs: Long,
    ): String =
        buildString {
            append(environment.baseUrl.trimEnd('/'))
            append(if (request.mediaKind == PlaybackMediaKind.AUDIO) "/Audio/" else "/Videos/")
            append(request.mediaId)
            append("/stream.")
            append(container ?: "mp4")
            append("?Static=true")
            append("&api_key=")
            append(environment.accessToken)
            append("&MediaSourceId=")
            append(mediaSourceId)
            append("&DeviceId=")
            append(environment.deviceId)
            append("&UserId=")
            append(environment.userId)
            val startTicks = max(0, startPositionMs).toTicks()
            if (startTicks > 0) {
                append("&StartTimeTicks=")
                append(startTicks)
            }
        }

    private fun buildSubtitleStreams(
        baseUrl: String,
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        mediaSourceId: String,
        environment: JellyfinEnvironment,
    ): List<ResolvedSubtitle> =
        selection.subtitleTracks.mapNotNull { track ->
            val streamIndex = track.streamIndex ?: return@mapNotNull null
            val format = subtitleFormat(track.format)
            val path = "$baseUrl/Videos/${request.mediaId}/$mediaSourceId/Subtitles/$streamIndex/stream.${format.extension}"
            val url =
                "$path?api_key=${environment.accessToken}" +
                    "&MediaSourceId=$mediaSourceId" +
                    "&SubtitleStreamIndex=$streamIndex" +
                    "&format=${format.format}"
            ResolvedSubtitle(
                trackId = track.id,
                url = url,
                mimeType = format.mimeType,
                isForced = track.isForced,
                language = track.language,
                label = track.title,
            )
        }

    private fun constrainManualTranscodeUrl(
        url: String,
        option: PlaybackQualityOption,
    ): String =
        replaceQueryParameters(
            url,
            mapOf(
                "VideoBitRate" to requireNotNull(option.maxBitrate).toString(),
                "MaxHeight" to requireNotNull(option.maxHeight).toString(),
                "EnableAutoStreamCopy" to "false",
                "AllowVideoStreamCopy" to "false",
                "AllowAudioStreamCopy" to "true",
            ),
        )

    private fun replaceQueryParameters(
        url: String,
        replacements: Map<String, String>,
    ): String {
        val fragment = url.substringAfter('#', missingDelimiterValue = "").takeIf { '#' in url }
        val withoutFragment = url.substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        val replacementByKey = replacements.entries.associateBy { it.key.lowercase() }
        val applied = mutableSetOf<String>()
        val parameters =
            query
                .split('&')
                .filter { it.isNotBlank() }
                .mapNotNull { parameter ->
                    val key = parameter.substringBefore('=')
                    val normalizedKey = key.lowercase()
                    val replacement = replacementByKey[normalizedKey]
                    when {
                        replacement == null -> parameter
                        applied.add(normalizedKey) -> "${replacement.key}=${replacement.value}"
                        else -> null
                    }
                }.toMutableList()
        replacements.forEach { (key, value) ->
            if (applied.add(key.lowercase())) {
                parameters += "$key=$value"
            }
        }
        return buildString {
            append(path)
            if (parameters.isNotEmpty()) {
                append('?')
                append(parameters.joinToString("&"))
            }
            fragment?.let {
                append('#')
                append(it)
            }
        }
    }

    private fun String.queryParameter(name: String): String? =
        substringBefore('#')
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstNotNullOfOrNull { parameter ->
                val key = parameter.substringBefore('=', missingDelimiterValue = parameter)
                parameter
                    .substringAfter('=', missingDelimiterValue = "")
                    .takeIf { key.equals(name, ignoreCase = true) && it.isNotBlank() }
            }

    private fun absoluteServerUrl(
        baseUrl: String,
        value: String,
    ): String =
        if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "${baseUrl.trimEnd('/')}/${value.trimStart('/')}"
        }

    private fun subtitleFormat(format: SubtitleFormat): SubtitleFormatDescriptor =
        when (format) {
            SubtitleFormat.SRT -> SubtitleFormatDescriptor("srt", "srt", "application/x-subrip")
            SubtitleFormat.VTT -> SubtitleFormatDescriptor("vtt", "vtt", "text/vtt")
            SubtitleFormat.PGS, SubtitleFormat.SUP, SubtitleFormat.ASS, SubtitleFormat.SSA ->
                SubtitleFormatDescriptor("vtt", "vtt", "text/vtt")
            SubtitleFormat.UNKNOWN -> SubtitleFormatDescriptor("vtt", "vtt", "text/vtt")
        }

    private fun playbackHeaders(environment: JellyfinEnvironment): Map<String, String> =
        mapOf(
            "X-Emby-Authorization" to authorizationHeader(environment),
            "User-Agent" to "$clientName/${environment.deviceName}",
        )

    private fun generatePlaySessionId(): String {
        val bytes = Random.Default.nextBytes(12)
        return bytes.joinToString(separator = "") { byte ->
            ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
    }

    private fun mediaMimeType(
        kind: PlaybackMediaKind,
        container: String?,
    ): String =
        if (kind == PlaybackMediaKind.VIDEO) {
            when (container?.lowercase()) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "ts", "mpegts", "m2ts" -> "video/mp2t"
                "avi" -> "video/x-msvideo"
                else -> "video/mp4"
            }
        } else {
            when (container?.lowercase()) {
                "flac" -> "audio/flac"
                "mp3" -> "audio/mpeg"
                "m4a", "mp4", "aac" -> "audio/mp4"
                "ogg", "oga", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "audio/*"
            }
        }

    private fun authorizationHeader(environment: JellyfinEnvironment): String {
        val builder = StringBuilder()
        builder.append("MediaBrowser ")
        builder.append("""Client="$clientName"""")
        builder.append(""", Device="${environment.deviceName}"""")
        builder.append(""", DeviceId="${environment.deviceId}"""")
        builder.append(""", Version="$clientVersion"""")
        if (environment.accessToken.isNotEmpty()) {
            builder.append(""", Token="${environment.accessToken}"""")
        }
        return builder.toString()
    }

    private data class SubtitleFormatDescriptor(
        val extension: String,
        val format: String,
        val mimeType: String,
    )

    private companion object {
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
    }
}
