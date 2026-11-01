package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import kotlin.random.Random

/**
 * The request that plays a Live TV channel. A channel has no media source to pick from before the server opens its
 * stream, so the request carries one stand-in that only names the channel; [JellyfinPlaybackSourceResolver] asks the
 * server for the real stream.
 */
fun PlaybackRequest.Companion.forLiveChannel(
    channelId: String,
    name: String,
    primaryImageTag: String? = null,
): PlaybackRequest =
    PlaybackRequest(
        mediaId = channelId,
        mediaSources =
            listOf(
                JellyfinMediaSource(
                    id = channelId,
                    name = name,
                    runTimeTicks = null,
                    container = null,
                    videoBitrate = null,
                    supportsDirectPlay = false,
                    supportsDirectStream = false,
                    // False keeps the quality list to Auto and keeps recovery from opening a second stream.
                    supportsTranscoding = false,
                    streams = emptyList(),
                ),
            ),
        startPolicy = PlaybackStartPolicy.RESTART,
        metadata =
            PlaybackMetadata(
                title = name,
                seriesId = null,
                seriesName = null,
                episodeName = null,
                artworkUrl = null,
                primaryImageTag = primaryImageTag,
            ),
        isLive = true,
    )

/**
 * Resolves a Live TV channel: PlaybackInfo with `AutoOpenLiveStream` makes the server open the stream, and the HLS
 * transcoding URL it returns is what plays. Direct play is off because a tuner stream is infinite and has no file to
 * range-request; video and audio are still copied when the device can decode them.
 */
internal class LiveChannelSourceResolver(
    private val playbackInfoService: JellyfinPlaybackInfoService,
    private val deviceProfileProvider: PlaybackDeviceProfileProvider,
    private val headers: Map<String, String>,
) {
    suspend fun resolve(
        request: PlaybackRequest,
        environment: JellyfinEnvironment,
    ): ResolvedPlaybackSource {
        val profile = deviceProfileProvider.deviceProfile()
        val response =
            playbackInfoService.fetch(
                environment = environment,
                itemId = request.mediaId,
                userId = environment.userId,
                request =
                    JellyfinPlaybackInfoRequestDto(
                        userId = environment.userId,
                        deviceProfile = profile,
                        maxStreamingBitrate = profile.maxStreamingBitrate,
                        enableDirectPlay = false,
                        enableDirectStream = false,
                        enableTranscoding = true,
                        allowVideoStreamCopy = true,
                        allowAudioStreamCopy = true,
                        autoOpenLiveStream = true,
                    ),
            )
        val source =
            response.mediaSources.firstOrNull()
                ?: error("Jellyfin PlaybackInfo returned no media sources for the channel")
        val transcodingUrl =
            source.transcodingUrl?.takeIf(String::isNotBlank)
                ?: error("Jellyfin PlaybackInfo returned no usable playback URL for the channel")
        return ResolvedPlaybackSource(
            url = absoluteUrl(environment.baseUrl, transcodingUrl),
            headers = headers,
            mode = PlaybackMode.HLS,
            mimeType = HLS_MIME_TYPE,
            subtitles = emptyList(),
            playSessionId = response.playSessionId ?: queryValue(transcodingUrl, "PlaySessionId") ?: randomPlaySessionId(),
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            mediaSourceId = source.id ?: request.mediaId,
            // Recovery would resolve again, which opens a second stream next to the first.
            supportsTranscoding = false,
            segmentContainer = source.transcodingContainer,
            isLive = true,
            liveStreamId = source.liveStreamId,
        )
    }

    private fun absoluteUrl(
        baseUrl: String,
        value: String,
    ): String =
        if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "${baseUrl.trimEnd('/')}/${value.trimStart('/')}"
        }

    private fun queryValue(
        url: String,
        name: String,
    ): String? =
        url
            .substringBefore('#')
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstNotNullOfOrNull { parameter ->
                parameter
                    .substringAfter('=', missingDelimiterValue = "")
                    .takeIf { parameter.substringBefore('=').equals(name, ignoreCase = true) && it.isNotBlank() }
            }

    private fun randomPlaySessionId(): String =
        Random.Default.nextBytes(PLAY_SESSION_ID_BYTES).joinToString(separator = "") { byte ->
            ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }

    private companion object {
        const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        const val PLAY_SESSION_ID_BYTES = 12
    }
}
