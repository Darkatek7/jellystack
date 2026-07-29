package dev.jellystack.players

import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto
import dev.jellystack.network.jellyfin.JellyfinDirectPlayProfileDto
import dev.jellystack.network.jellyfin.JellyfinSubtitleProfileDto
import dev.jellystack.network.jellyfin.JellyfinTranscodingProfileDto

enum class PlaybackVideoCodec(
    val jellyfinName: String,
) {
    AV1("av1"),
    HEVC("hevc"),
    VP9("vp9"),
    H264("h264"),
}

data class PlaybackDecoderCapabilities(
    val videoCodecs: Set<PlaybackVideoCodec>,
)

fun interface PlaybackDeviceProfileProvider {
    fun deviceProfile(): JellyfinDeviceProfileDto
}

object PlaybackDeviceProfileFactory {
    fun create(
        name: String,
        capabilities: PlaybackDecoderCapabilities,
    ): JellyfinDeviceProfileDto {
        val directCodecs =
            listOf(
                PlaybackVideoCodec.AV1,
                PlaybackVideoCodec.HEVC,
                PlaybackVideoCodec.VP9,
                PlaybackVideoCodec.H264,
            ).filter(capabilities.videoCodecs::contains)
        val transcodeCodecs =
            listOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264)
                .filter(capabilities.videoCodecs::contains)

        fun directProfile(
            container: String,
            supportedCodecs: Set<PlaybackVideoCodec>,
            audioCodec: String,
        ): JellyfinDirectPlayProfileDto? {
            val codecs = directCodecs.filter(supportedCodecs::contains)
            return codecs
                .takeIf { it.isNotEmpty() }
                ?.let {
                    JellyfinDirectPlayProfileDto(
                        container = container,
                        videoCodec = it.joinToString(",") { codec -> codec.jellyfinName },
                        audioCodec = audioCodec,
                    )
                }
        }

        return JellyfinDeviceProfileDto(
            name = name,
            directPlayProfiles =
                listOfNotNull(
                    directProfile(
                        container = "mp4,m4v",
                        supportedCodecs =
                            setOf(
                                PlaybackVideoCodec.AV1,
                                PlaybackVideoCodec.HEVC,
                                PlaybackVideoCodec.H264,
                            ),
                        audioCodec = "aac,mp3,ac3,eac3,flac",
                    ),
                    directProfile(
                        container = "mkv",
                        supportedCodecs = PlaybackVideoCodec.entries.toSet(),
                        audioCodec = "aac,mp3,ac3,eac3,opus,vorbis,flac",
                    ),
                    directProfile(
                        container = "webm",
                        supportedCodecs = setOf(PlaybackVideoCodec.AV1, PlaybackVideoCodec.VP9),
                        audioCodec = "opus,vorbis",
                    ),
                    directProfile(
                        container = "ts,mpegts",
                        supportedCodecs = setOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264),
                        audioCodec = "aac,mp3,ac3,eac3",
                    ),
                ),
            transcodingProfiles =
                transcodeCodecs.map {
                    JellyfinTranscodingProfileDto(videoCodec = it.jellyfinName)
                },
            subtitleProfiles =
                listOf(
                    JellyfinSubtitleProfileDto("vtt"),
                    JellyfinSubtitleProfileDto("srt"),
                    JellyfinSubtitleProfileDto("ass"),
                    JellyfinSubtitleProfileDto("ssa"),
                ),
        )
    }
}

object ConservativePlaybackDeviceProfileProvider : PlaybackDeviceProfileProvider {
    override fun deviceProfile(): JellyfinDeviceProfileDto =
        PlaybackDeviceProfileFactory.create(
            "Jellystack",
            PlaybackDecoderCapabilities(setOf(PlaybackVideoCodec.H264)),
        )
}
