package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class JellyfinPlaybackApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
) {
    suspend fun stopEncodingProcess(
        deviceId: String,
        playSessionId: String,
    ) {
        client.delete {
            url {
                takeFrom(baseUrl)
                path("Videos/ActiveEncodings")
            }
            header("X-Emby-Token", accessToken)
            parameter("DeviceId", deviceId)
            parameter("PlaySessionId", playSessionId)
        }
    }

    suspend fun fetchPlaybackInfo(
        itemId: String,
        userId: String,
        request: JellyfinPlaybackInfoRequestDto,
    ): JellyfinPlaybackInfoResponseDto =
        client
            .post {
                url {
                    takeFrom(baseUrl)
                    path("Items/$itemId/PlaybackInfo")
                }
                header("X-Emby-Token", accessToken)
                parameter("UserId", userId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
}

@Serializable
data class JellyfinPlaybackInfoRequestDto(
    @SerialName("UserId") val userId: String,
    @SerialName("DeviceProfile") val deviceProfile: JellyfinDeviceProfileDto,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long = 0,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean,
)

@Serializable
data class JellyfinDeviceProfileDto(
    @SerialName("Name") val name: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<JellyfinDirectPlayProfileDto>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<JellyfinTranscodingProfileDto>,
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<JellyfinSubtitleProfileDto> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<JellyfinCodecProfileDto> = emptyList(),
)

@Serializable
data class JellyfinDirectPlayProfileDto(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
)

@Serializable
data class JellyfinTranscodingProfileDto(
    @SerialName("Container") val container: String = "ts",
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String = "aac",
    @SerialName("Protocol") val protocol: String = "hls",
    @SerialName("Context") val context: String = "Streaming",
    @SerialName("MinSegments") val minSegments: Int = 1,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean = true,
    @SerialName("MaxAudioChannels") val maxAudioChannels: String? = null,
)

@Serializable
data class JellyfinCodecProfileDto(
    @SerialName("Type") val type: String = "VideoAudio",
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Conditions") val conditions: List<JellyfinProfileConditionDto> = emptyList(),
    @SerialName("ApplyConditions") val applyConditions: List<JellyfinProfileConditionDto> = emptyList(),
)

@Serializable
data class JellyfinProfileConditionDto(
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean = true,
)

@Serializable
data class JellyfinSubtitleProfileDto(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String = "External",
)

@Serializable
data class JellyfinPlaybackInfoResponseDto(
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinPlaybackMediaSourceDto> = emptyList(),
)

@Serializable
data class JellyfinPlaybackMediaSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerialName("TranscodingReasons") val transcodingReasons: List<String> = emptyList(),
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingContainer") val transcodingContainer: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
)
