package dev.jellystack.players

import dev.jellystack.network.jellyfin.JellyfinCodecProfileDto
import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto
import dev.jellystack.network.jellyfin.JellyfinDirectPlayProfileDto
import dev.jellystack.network.jellyfin.JellyfinProfileConditionDto
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
    val maxAacChannelCount: Int? = null,
    val maxAudioChannelCounts: Map<PlaybackAudioCodec, Int> = emptyMap(),
    val maxStreamingBitrate: Int? = null,
    val transcodingVideoCodecs: Set<PlaybackVideoCodec> = videoCodecs,
    val transcodingAudioCodecs: Set<PlaybackAudioCodec> = audioCodecs,
    val maxTranscodingAudioChannelCount: Int? = null,
    /**
     * Preferred HLS audio encoder targets, best first. Jellyfin offers the profile's codec list
     * in order and encodes to the first one it can, so the head of this list is the target for
     * every source the device cannot play itself. Codecs outside [transcodingAudioCodecs] are
     * ignored; the remaining codecs keep the factory's default order. Empty keeps that default.
     */
    val transcodingAudioCodecPreference: List<PlaybackAudioCodec> = emptyList(),
    /**
     * The player can render PGS (bitmap) subtitles itself, both from the container on Direct Play
     * and from a sideloaded `.sup` file next to a transcoded stream. Jellyfin then never has to
     * burn them into the video.
     */
    val supportsPgsSubtitles: Boolean = false,
    /**
     * Decodable video codecs the server may copy into fMP4 HLS segments but never encodes to. They are
     * appended after the encoder targets of the fMP4 transcoding profile only (MPEG-TS cannot carry
     * AV1). Jellyfin 10.10+ prefers the transcoding profile that can copy the source video, so a source
     * in one of these codecs keeps its video and only the audio is transcoded. Codecs outside
     * [videoCodecs] are ignored.
     */
    val hlsFmp4VideoCopyCodecs: Set<PlaybackVideoCodec> = emptySet(),
)

fun interface PlaybackDeviceProfileProvider {
    fun deviceProfile(): JellyfinDeviceProfileDto

    /**
     * True for devices that negotiate Direct Play first and let the server transcode anything they
     * cannot decode to the best audio codec of their profile (instead of the stereo AAC
     * compatibility rendition phones fall back to). Stereo AAC HLS remains the explicit fallback
     * after audio output failed, which is requested through [PlaybackSourceOptions].
     */
    fun usesBestAvailableAudioTranscodeTarget(): Boolean = false
}

internal const val PGS_SUBTITLE_FORMAT = "pgssub"
internal const val SUBTITLE_METHOD_EMBED = "Embed"
internal const val SUBTITLE_METHOD_EXTERNAL = "External"

/** True when the profile tells Jellyfin it may deliver PGS as a separate `.sup` stream next to a transcode. */
internal val JellyfinDeviceProfileDto.acceptsExternalPgsSubtitles: Boolean
    get() =
        subtitleProfiles.any { profile ->
            profile.format.equals(PGS_SUBTITLE_FORMAT, ignoreCase = true) &&
                profile.method.equals(SUBTITLE_METHOD_EXTERNAL, ignoreCase = true)
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
        // Match Jellyfin Android TV: modern codecs are valid Direct Play inputs, but HLS
        // encoder targets are limited to codecs the server can package reliably.
        val hlsVideoCodecs =
            listOf(
                PlaybackVideoCodec.HEVC,
                PlaybackVideoCodec.H264,
            ).filter(capabilities.transcodingVideoCodecs::contains)
        // The encoder target stays the head of the list; copy-only codecs only ever match a source.
        val hlsFmp4VideoCodecs =
            hlsVideoCodecs +
                directCodecs.filter { codec ->
                    codec in capabilities.hlsFmp4VideoCopyCodecs && codec !in hlsVideoCodecs
                }

        fun orderedTranscodeAudioCodecs(defaultOrder: List<PlaybackAudioCodec>): List<PlaybackAudioCodec> =
            (capabilities.transcodingAudioCodecPreference + defaultOrder)
                .distinct()
                .filter { codec -> codec in defaultOrder && codec in capabilities.transcodingAudioCodecs }
        val hlsMpegTsAudioCodecs =
            orderedTranscodeAudioCodecs(
                listOf(
                    PlaybackAudioCodec.AAC,
                    PlaybackAudioCodec.AC3,
                    PlaybackAudioCodec.EAC3,
                    PlaybackAudioCodec.MP3,
                ),
            )
        val hlsFmp4AudioCodecs =
            orderedTranscodeAudioCodecs(
                listOf(
                    PlaybackAudioCodec.AAC,
                    PlaybackAudioCodec.AC3,
                    PlaybackAudioCodec.EAC3,
                    PlaybackAudioCodec.MP3,
                    PlaybackAudioCodec.FLAC,
                    PlaybackAudioCodec.OPUS,
                ),
            )
        val maxTranscodeAudioChannels =
            capabilities.maxTranscodingAudioChannelCount
                ?: hlsFmp4AudioCodecs.mapNotNull(capabilities.maxAudioChannelCounts::get).maxOrNull()
                ?: capabilities.maxAacChannelCount

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
            maxStreamingBitrate = capabilities.maxStreamingBitrate,
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
                if (hlsVideoCodecs.isEmpty() || hlsMpegTsAudioCodecs.isEmpty()) {
                    emptyList()
                } else {
                    buildList {
                        add(
                            JellyfinTranscodingProfileDto(
                                container = "ts",
                                videoCodec = hlsVideoCodecs.joinToString(",") { codec -> codec.jellyfinName },
                                audioCodec = hlsMpegTsAudioCodecs.joinToString(",") { codec -> codec.jellyfinName },
                                maxAudioChannels = maxTranscodeAudioChannels?.toString(),
                                breakOnNonKeyFrames = true,
                            ),
                        )
                        if (hlsFmp4AudioCodecs.isNotEmpty()) {
                            add(
                                JellyfinTranscodingProfileDto(
                                    container = "mp4",
                                    videoCodec = hlsFmp4VideoCodecs.joinToString(",") { codec -> codec.jellyfinName },
                                    audioCodec = hlsFmp4AudioCodecs.joinToString(",") { codec -> codec.jellyfinName },
                                    maxAudioChannels = maxTranscodeAudioChannels?.toString(),
                                    breakOnNonKeyFrames = false,
                                ),
                            )
                        }
                    }
                },
            subtitleProfiles =
                buildList {
                    add(JellyfinSubtitleProfileDto("vtt"))
                    add(JellyfinSubtitleProfileDto("srt"))
                    add(JellyfinSubtitleProfileDto("ass"))
                    add(JellyfinSubtitleProfileDto("ssa"))
                    if (capabilities.supportsPgsSubtitles) {
                        // Embed keeps Direct Play alive for a selected PGS stream; External lets the server hand the
                        // raw .sup to the player next to a transcode instead of burning the bitmaps into the video.
                        add(JellyfinSubtitleProfileDto(PGS_SUBTITLE_FORMAT, method = SUBTITLE_METHOD_EMBED))
                        add(JellyfinSubtitleProfileDto(PGS_SUBTITLE_FORMAT, method = SUBTITLE_METHOD_EXTERNAL))
                    }
                },
            codecProfiles =
                buildList {
                    capabilities.maxAudioChannelCounts.forEach { (codec, maxChannels) ->
                        if (codec in capabilities.audioCodecs) {
                            add(
                                JellyfinCodecProfileDto(
                                    codec = codec.jellyfinName,
                                    conditions =
                                        listOf(
                                            JellyfinProfileConditionDto(
                                                condition = "LessThanEqual",
                                                property = "AudioChannels",
                                                value = maxChannels.toString(),
                                            ),
                                        ),
                                ),
                            )
                        }
                    }
                    val aacIsOffered =
                        PlaybackAudioCodec.AAC in capabilities.audioCodecs ||
                            PlaybackAudioCodec.AAC in capabilities.transcodingAudioCodecs
                    if (aacIsOffered && PlaybackAudioCodec.AAC !in capabilities.maxAudioChannelCounts) {
                        capabilities.maxAacChannelCount?.let { maxChannels ->
                            add(
                                JellyfinCodecProfileDto(
                                    codec = PlaybackAudioCodec.AAC.jellyfinName,
                                    conditions =
                                        listOf(
                                            JellyfinProfileConditionDto(
                                                condition = "LessThanEqual",
                                                property = "AudioChannels",
                                                value = maxChannels.toString(),
                                            ),
                                        ),
                                ),
                            )
                        }
                    }
                },
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
