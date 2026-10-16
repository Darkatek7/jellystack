package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.network.jellyfin.JellyfinCodecProfileDto
import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinProfileConditionDto
import dev.jellystack.network.jellyfin.JellyfinTranscodingProfileDto
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

    override fun canPlayAudioCodecLocally(codec: String?): Boolean {
        if (!deviceProfileProvider.usesBestAvailableAudioTranscodeTarget()) return true
        val normalized = codec?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return true
        return deviceProfileProvider.deviceProfile().directPlayProfiles.any { profile ->
            profile.audioCodec.split(',').any { it.trim().equals(normalized, ignoreCase = true) }
        }
    }

    private suspend fun resolveVideo(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        options.stopEncodingPlaySessionId?.let { playSessionId ->
            runCatching { playbackInfoService.stopEncoding(environment, playSessionId) }
        }
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
        val baseDeviceProfile = deviceProfileProvider.deviceProfile()
        val selectedAudioTrack =
            selection.audioTracks.firstOrNull { it.streamIndex == audioStreamIndex }
                ?: selection.defaultAudioTrack()
        val selectedAudioMaxChannels = baseDeviceProfile.maxAudioChannelsFor(selectedAudioTrack?.codec)
        val selectedAudioIsAac = selectedAudioTrack?.codec.equals("aac", ignoreCase = true)
        val requiresSafeAudioTranscode =
            selectedAudioMaxChannels != null &&
                (
                    selectedAudioTrack?.channels?.let { it > selectedAudioMaxChannels }
                        ?: selectedAudioIsAac
                )
        val usesBestAudioTarget =
            request.mediaKind == PlaybackMediaKind.VIDEO &&
                deviceProfileProvider.usesBestAvailableAudioTranscodeTarget()
        // Stereo AAC is only the explicit compatibility fallback. Devices that negotiate for the best
        // audio target let Jellyfin judge channel limits itself, so the client-side safety check is
        // skipped for them.
        val forceStereoAac =
            !options.forceTranscoding &&
                (
                    options.forceAudioTranscoding ||
                        (requiresSafeAudioTranscode && !usesBestAudioTarget) ||
                        (options.selectAudioOnServer && !usesBestAudioTarget)
                )
        // The server must pick one audio rendition (HLS, no direct play) for the compatibility
        // fallback and for an explicit track switch that the device cannot do locally.
        val serverSelectsAudio = !options.forceTranscoding && (forceStereoAac || options.selectAudioOnServer)
        val forceAnyTranscoding = options.forceTranscoding || serverSelectsAudio
        val canRemuxVideoOnServer = !options.forceTranscoding && (serverSelectsAudio || usesBestAudioTarget)
        val deviceProfile =
            baseDeviceProfile
                .let { profile ->
                    if (forceStereoAac) profile.withCompatibilityStereoAudio() else profile
                }.let { profile ->
                    if (options.preferFmp4Hls) {
                        profile.copy(
                            transcodingProfiles =
                                profile.transcodingProfiles.filter { transcodingProfile ->
                                    transcodingProfile.container.equals("mp4", ignoreCase = true)
                                },
                        )
                    } else {
                        profile
                    }
                }
        val preferredTranscodeProfile = deviceProfile.transcodingProfiles.firstOrNull()
        val preferredTranscodeCodec =
            preferredTranscodeProfile
                ?.videoCodec
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: PlaybackVideoCodec.H264.jellyfinName
        val selectedVideoCodec = selection.videoCodec?.lowercase()
        val canCopyVideoIntoPreferredHls =
            canRemuxVideoOnServer &&
                (
                    selectedVideoCodec == null ||
                        deviceProfile.transcodingProfiles.any { profile ->
                            selectedVideoCodec in profile.videoCodec.split(',').map { it.trim().lowercase() }
                        }
                )
        val allowDirectPlayback = isAuto && !forceAnyTranscoding
        val audioTarget =
            resolveAudioTranscodeTarget(
                deviceProfile = deviceProfile,
                preferredTranscodeProfile = preferredTranscodeProfile,
                selectedAudioTrack = selectedAudioTrack,
                forceStereoAac = forceStereoAac,
                preferSourceCodec = usesBestAudioTarget && !forceStereoAac && !options.forceTranscoding,
            )
        val allowAudioStreamCopy = !options.forceTranscoding && !forceStereoAac
        // Subtitles the player can render itself are sideloaded; a missing method would make the server burn them
        // in. Bitmap (PGS) subtitles only qualify for devices whose profile accepts them as external streams.
        val externalPgsSupported = deviceProfile.acceptsExternalPgsSubtitles
        val deliverSubtitleExternally =
            selection.subtitleTracks
                .firstOrNull { it.streamIndex == subtitleStreamIndex }
                ?.format
                ?.let { format -> !format.isImageBased || externalPgsSupported } == true
        val fallbackVideoBitrate =
            autoTranscodeVideoBitrate(
                sourceVideoBitrate = selection.videoBitrate,
                sourceVideoHeight = selection.videoHeight,
                maxStreamingBitrate = manualOption?.maxBitrate ?: deviceProfile.maxStreamingBitrate,
            )
        val playbackInfoRequest =
            JellyfinPlaybackInfoRequestDto(
                userId = environment.userId,
                deviceProfile = deviceProfile,
                mediaSourceId = selection.sourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startTimeTicks = max(0, startPositionMs).toTicks(),
                maxStreamingBitrate = manualOption?.maxBitrate ?: deviceProfile.maxStreamingBitrate,
                enableDirectPlay = allowDirectPlayback,
                enableDirectStream = allowDirectPlayback || serverSelectsAudio,
                enableTranscoding = true,
                allowVideoStreamCopy = allowDirectPlayback || canCopyVideoIntoPreferredHls,
                allowAudioStreamCopy = allowAudioStreamCopy,
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

        // Jellyfin only reports SupportsDirectPlay when container, video and the selected audio stream all
        // match the profile. A bare direct-stream answer next to a usable transcode URL is not trusted on
        // devices that negotiate for the best audio target: that URL carries the audio transcode.
        val canUseDirectSource =
            allowDirectPlayback &&
                (
                    negotiatedSource.supportsDirectPlay ||
                        (
                            negotiatedSource.supportsDirectStream &&
                                (!usesBestAudioTarget || usableTranscodingUrl == null)
                        )
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
                        deliverSubtitleExternally = deliverSubtitleExternally,
                        maxAudioChannels = audioTarget.maxChannels,
                        maxStreamingBitrate = manualOption?.maxBitrate ?: deviceProfile.maxStreamingBitrate,
                        copyVideo = canCopyVideoIntoPreferredHls,
                        videoCodec = preferredTranscodeCodec,
                        audioCodec = audioTarget.codec,
                        allowAudioCopy = allowAudioStreamCopy && usesBestAudioTarget,
                        videoBitrate = fallbackVideoBitrate,
                        maxWidth = if (manualOption == null) selection.videoWidth else null,
                        maxHeight = manualOption?.maxHeight ?: selection.videoHeight,
                        segmentContainer = preferredTranscodeProfile?.container ?: "ts",
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
                    serverTranscodeUrl,
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

        logPlaybackDecision(
            mode = resolvedMode,
            url = resolvedUrl,
            isFallbackHls = isFallbackHls,
            forceStereoAac = forceStereoAac,
            allowAudioStreamCopy = allowAudioStreamCopy,
        )

        return ResolvedPlaybackSource(
            url = resolvedUrl,
            headers = playbackHeaders(),
            mode = resolvedMode,
            mimeType = resolvedMimeType,
            subtitles =
                buildSubtitleStreams(
                    baseUrl = environment.baseUrl.trimEnd('/'),
                    request = request,
                    selection = selection,
                    mediaSourceId = mediaSourceId,
                    environment = environment,
                    // On Direct Play the player reads PGS from the container; only a transcode needs the .sup.
                    sideloadImageBased = externalPgsSupported && resolvedMode == PlaybackMode.HLS,
                ),
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId,
            supportsTranscoding = supportsTranscoding,
            isFallbackHls = isFallbackHls,
            segmentContainer =
                if (resolvedMode == PlaybackMode.HLS) {
                    resolvedUrl.queryParameter("SegmentContainer")
                        ?: negotiatedSource.transcodingContainer
                } else {
                    null
                },
        )
    }

    /**
     * One debug line per negotiation. Only codec names, channel counts, the stream index and the
     * server's transcode reasons are read from the URL; the URL itself, its token and its ids never
     * reach the log.
     */
    private fun logPlaybackDecision(
        mode: PlaybackMode,
        url: String,
        isFallbackHls: Boolean,
        forceStereoAac: Boolean,
        allowAudioStreamCopy: Boolean,
    ) {
        fun loggable(name: String): String =
            url
                .queryParameter(name)
                ?.takeIf { LOGGABLE_QUERY_VALUE.matches(it) }
                ?: "-"
        val channels =
            listOf("TranscodingMaxAudioChannels", "MaxAudioChannels", "AudioChannels")
                .firstNotNullOfOrNull { name -> url.queryParameter(name)?.takeIf { LOGGABLE_QUERY_VALUE.matches(it) } }
                ?: "-"
        JellystackLog.d(
            "Playback decision mode=$mode fallbackUrl=$isFallbackHls stereoAacFallback=$forceStereoAac " +
                "audioCopyAllowed=$allowAudioStreamCopy transcodeReasons=${loggable("TranscodeReasons")} " +
                "audioCodec=${loggable("AudioCodec")} audioChannels=$channels " +
                "audioStreamIndex=${loggable("AudioStreamIndex")}",
        )
    }

    private fun buildFallbackHlsUrl(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
        mediaSourceId: String,
        playSessionId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        deliverSubtitleExternally: Boolean,
        maxAudioChannels: Int?,
        maxStreamingBitrate: Int?,
        copyVideo: Boolean,
        videoCodec: String,
        audioCodec: String,
        allowAudioCopy: Boolean,
        videoBitrate: Int?,
        maxWidth: Int?,
        maxHeight: Int?,
        segmentContainer: String,
    ): String =
        buildString {
            append(environment.baseUrl.trimEnd('/'))
            append("/Videos/")
            append(request.mediaId)
            append("/master.m3u8")
            append("?ApiKey=")
            append(environment.accessToken)
            append("&MediaSourceId=")
            append(mediaSourceId)
            append("&DeviceId=")
            append(environment.deviceId)
            append("&UserId=")
            append(environment.userId)
            append("&PlaySessionId=")
            append(playSessionId)
            append("&VideoCodec=")
            append(if (copyVideo) "copy" else videoCodec)
            append("&AudioCodec=")
            append(audioCodec)
            append("&SegmentContainer=")
            append(segmentContainer)
            append("&BreakOnNonKeyFrames=")
            append(segmentContainer == "ts")
            append("&MinSegments=1")
            append("&StartTimeTicks=0")
            append("&EnableAutoStreamCopy=")
            append(copyVideo)
            append("&AllowVideoStreamCopy=")
            append(copyVideo)
            append("&AllowAudioStreamCopy=")
            append(allowAudioCopy)
            videoBitrate?.let { bitrate ->
                append("&VideoBitRate=")
                append(bitrate)
            }
            maxWidth?.let { width ->
                append("&MaxWidth=")
                append(width)
            }
            maxHeight?.let { height ->
                append("&MaxHeight=")
                append(height)
            }
            maxStreamingBitrate?.let { bitrate ->
                append("&MaxStreamingBitrate=")
                append(bitrate)
            }
            maxAudioChannels?.let { maxChannels ->
                append("&TranscodingMaxAudioChannels=")
                append(maxChannels)
                append("&MaxAudioChannels=")
                append(maxChannels)
                append("&")
                append(audioCodec)
                append("-audiochannels=")
                append(maxChannels)
                append("&AudioBitrate=")
                append(TV_FALLBACK_AUDIO_BITRATE)
            }
            audioStreamIndex?.let { index ->
                append("&AudioStreamIndex=")
                append(index)
            }
            subtitleStreamIndex?.let { index ->
                append("&SubtitleStreamIndex=")
                append(index)
                if (deliverSubtitleExternally) append("&SubtitleMethod=External")
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
                        append("?ApiKey=")
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
            headers = playbackHeaders(),
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
            append("&ApiKey=")
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
        sideloadImageBased: Boolean,
    ): List<ResolvedSubtitle> =
        selection.subtitleTracks.mapNotNull { track ->
            // Jellyfin cannot convert bitmap subtitles to text. They are rendered from the container on direct
            // play and handed over as the raw .sup next to a transcode when the device accepts that; otherwise
            // the server burns them in.
            if (track.format.isImageBased && !sideloadImageBased) return@mapNotNull null
            val streamIndex = track.streamIndex ?: return@mapNotNull null
            val format = subtitleFormat(track.format)
            val path = "$baseUrl/Videos/${request.mediaId}/$mediaSourceId/Subtitles/$streamIndex/stream.${format.extension}"
            val url =
                "$path?ApiKey=${environment.accessToken}" +
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
            SubtitleFormat.PGS, SubtitleFormat.SUP ->
                SubtitleFormatDescriptor("sup", "sup", PGS_MIME_TYPE)
            SubtitleFormat.ASS, SubtitleFormat.SSA ->
                SubtitleFormatDescriptor("vtt", "vtt", "text/vtt")
            SubtitleFormat.UNKNOWN -> SubtitleFormatDescriptor("vtt", "vtt", "text/vtt")
        }

    private fun playbackHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to "$clientName/$clientVersion",
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

    private data class SubtitleFormatDescriptor(
        val extension: String,
        val format: String,
        val mimeType: String,
    )

    private companion object {
        private const val TV_FALLBACK_AUDIO_BITRATE = 256_000
        private const val SAFE_AAC_FALLBACK_CHANNELS = 2
        private const val UNKNOWN_SOURCE_VIDEO_BITRATE = 40_000_000
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        private const val PGS_MIME_TYPE = "application/pgs"
        private val LOGGABLE_QUERY_VALUE = Regex("[A-Za-z0-9,_-]{1,96}")
    }

    private fun autoTranscodeVideoBitrate(
        sourceVideoBitrate: Int?,
        sourceVideoHeight: Int?,
        maxStreamingBitrate: Int?,
    ): Int? {
        val ceiling = maxStreamingBitrate?.minus(TV_FALLBACK_AUDIO_BITRATE)?.coerceAtLeast(1)
        val resolutionFloor =
            when {
                sourceVideoHeight == null -> UNKNOWN_SOURCE_VIDEO_BITRATE
                sourceVideoHeight >= 2_160 -> 60_000_000
                sourceVideoHeight >= 1_440 -> 30_000_000
                sourceVideoHeight >= 1_080 -> 16_000_000
                sourceVideoHeight >= 720 -> 8_000_000
                else -> 4_000_000
            }
        val qualityTarget =
            sourceVideoBitrate
                ?.takeIf { it > 0 }
                ?.toLong()
                ?.times(2L)
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
                ?.coerceAtLeast(resolutionFloor)
                ?: resolutionFloor
        return ceiling?.let { qualityTarget.coerceAtMost(it) } ?: qualityTarget
    }
}

private data class AudioTranscodeTarget(
    val codec: String,
    val maxChannels: Int?,
)

/**
 * Picks the audio codec and channel ceiling for the fallback HLS URL and mirrors what Jellyfin
 * derives from the same profile: the first codec of the transcoding profile is the encoder target.
 * Only the explicit compatibility fallback is pinned to stereo AAC. When the device can play the
 * selected source codec within its channel limit, that codec is requested so the server copies it.
 */
private fun resolveAudioTranscodeTarget(
    deviceProfile: JellyfinDeviceProfileDto,
    preferredTranscodeProfile: JellyfinTranscodingProfileDto?,
    selectedAudioTrack: AudioTrack?,
    forceStereoAac: Boolean,
    preferSourceCodec: Boolean,
): AudioTranscodeTarget {
    val aac = PlaybackAudioCodec.AAC.jellyfinName
    val offeredCodecs =
        preferredTranscodeProfile
            ?.audioCodec
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter(String::isNotEmpty)
            .orEmpty()
    val sourceCodec = selectedAudioTrack?.codec?.trim()?.lowercase()
    val sourceFitsDevice =
        preferSourceCodec &&
            sourceCodec != null &&
            sourceCodec in offeredCodecs &&
            selectedAudioTrack.channels?.let { channels ->
                channels <= (deviceProfile.maxAudioChannelsFor(sourceCodec) ?: Int.MAX_VALUE)
            } == true
    val codec =
        when {
            forceStereoAac -> aac
            sourceFitsDevice -> checkNotNull(sourceCodec)
            else -> offeredCodecs.firstOrNull() ?: aac
        }
    val detectedMax = deviceProfile.maxAudioChannelsFor(codec)
    val maxChannels =
        detectedMax?.let { max -> if (forceStereoAac) minOf(max, SAFE_COMPATIBILITY_AUDIO_CHANNELS) else max }
            ?: preferredTranscodeProfile?.maxAudioChannels?.toIntOrNull()
    return AudioTranscodeTarget(codec, maxChannels)
}

private fun JellyfinDeviceProfileDto.maxAudioChannelsFor(codec: String?): Int? {
    val normalizedCodec = codec?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    return codecProfiles
        .asSequence()
        .filter { profile ->
            profile.codec
                ?.split(',')
                ?.any { it.trim().equals(normalizedCodec, ignoreCase = true) } == true
        }.flatMap { profile -> profile.conditions.asSequence() }
        .firstOrNull { condition -> condition.property.equals("AudioChannels", ignoreCase = true) }
        ?.value
        ?.toIntOrNull()
}

/**
 * Mirrors Jellyfin Android TV's compatibility/downmix profile. The server receives the
 * restriction during PlaybackInfo negotiation, so the returned URL, container, codecs and
 * timestamps remain one consistent server-generated decision.
 */
private fun JellyfinDeviceProfileDto.withCompatibilityStereoAudio(): JellyfinDeviceProfileDto {
    val safeCodecs = setOf(PlaybackAudioCodec.AAC.jellyfinName, PlaybackAudioCodec.MP3.jellyfinName)

    fun filtered(codecs: String): String =
        codecs
            .split(',')
            .map(String::trim)
            .filter { codec -> codec.lowercase() in safeCodecs }
            .distinct()
            .joinToString(",")

    val compatibilityConditions =
        safeCodecs.map { codec ->
            JellyfinCodecProfileDto(
                codec = codec,
                conditions =
                    listOf(
                        JellyfinProfileConditionDto(
                            condition = "LessThanEqual",
                            property = "AudioChannels",
                            value = SAFE_COMPATIBILITY_AUDIO_CHANNELS.toString(),
                        ),
                    ),
            )
        }
    return copy(
        directPlayProfiles =
            directPlayProfiles.mapNotNull { profile ->
                filtered(profile.audioCodec)
                    .takeIf(String::isNotEmpty)
                    ?.let { audioCodecs -> profile.copy(audioCodec = audioCodecs) }
            },
        transcodingProfiles =
            transcodingProfiles.mapNotNull { profile ->
                filtered(profile.audioCodec)
                    .takeIf(String::isNotEmpty)
                    ?.let { audioCodecs ->
                        profile.copy(
                            audioCodec = audioCodecs,
                            maxAudioChannels = SAFE_COMPATIBILITY_AUDIO_CHANNELS.toString(),
                        )
                    }
            },
        codecProfiles =
            codecProfiles.filterNot { profile ->
                profile.codec
                    ?.split(',')
                    ?.any { codec -> codec.trim().lowercase() in safeCodecs } == true
            } + compatibilityConditions,
    )
}

private const val SAFE_COMPATIBILITY_AUDIO_CHANNELS = 2
