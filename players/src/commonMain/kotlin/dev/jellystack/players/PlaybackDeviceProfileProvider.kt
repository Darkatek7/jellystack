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

enum class PlaybackAudioCodec(
    val jellyfinName: String,
) {
    AAC("aac"),
    MP3("mp3"),
    AC3("ac3"),
    EAC3("eac3"),
    OPUS("opus"),
    VORBIS("vorbis"),
    FLAC("flac"),
}

data class PlaybackDecoderCapabilities(
    val videoCodecs: Set<PlaybackVideoCodec>,
    val audioCodecs: Set<PlaybackAudioCodec> =
        setOf(
            PlaybackAudioCodec.AAC,
            PlaybackAudioCodec.MP3,
        ),
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
            supportedAudioCodecs: Set<PlaybackAudioCodec>,
        ): JellyfinDirectPlayProfileDto? {
            val codecs = directCodecs.filter(supportedCodecs::contains)
            val audioCodecs =
                PlaybackAudioCodec.entries
                    .filter(capabilities.audioCodecs::contains)
                    .filter(supportedAudioCodecs::contains)
            return if (codecs.isNotEmpty() && audioCodecs.isNotEmpty()) {
                JellyfinDirectPlayProfileDto(
                    container = container,
                    videoCodec = codecs.joinToString(",") { codec -> codec.jellyfinName },
                    audioCodec = audioCodecs.joinToString(",") { codec -> codec.jellyfinName },
                )
            } else {
                null
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
                        supportedAudioCodecs =
                            setOf(
                                PlaybackAudioCodec.AAC,
                                PlaybackAudioCodec.MP3,
                                PlaybackAudioCodec.AC3,
                                PlaybackAudioCodec.EAC3,
                                PlaybackAudioCodec.FLAC,
                            ),
                    ),
                    directProfile(
                        container = "mkv",
                        supportedCodecs = PlaybackVideoCodec.entries.toSet(),
                        supportedAudioCodecs = PlaybackAudioCodec.entries.toSet(),
                    ),
                    directProfile(
                        container = "webm",
                        supportedCodecs = setOf(PlaybackVideoCodec.AV1, PlaybackVideoCodec.VP9),
                        supportedAudioCodecs =
                            setOf(
                                PlaybackAudioCodec.OPUS,
                                PlaybackAudioCodec.VORBIS,
                            ),
                    ),
                    directProfile(
                        container = "ts,mpegts",
                        supportedCodecs = setOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264),
                        supportedAudioCodecs =
                            setOf(
                                PlaybackAudioCodec.AAC,
                                PlaybackAudioCodec.MP3,
                                PlaybackAudioCodec.AC3,
                                PlaybackAudioCodec.EAC3,
                            ),
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
