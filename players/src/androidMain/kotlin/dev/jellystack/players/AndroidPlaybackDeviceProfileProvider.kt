package dev.jellystack.players

import android.media.MediaCodecList
import android.os.Build
import dev.jellystack.network.jellyfin.JellyfinDeviceProfileDto

class AndroidPlaybackDeviceProfileProvider(
    private val decoderTypes: () -> Set<String> = {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .map(String::lowercase)
            .toSet()
    },
) : PlaybackDeviceProfileProvider {
    override fun deviceProfile(): JellyfinDeviceProfileDto {
        val types = decoderTypes()
        val videoCodecs =
            buildSet {
                if (MIME_TYPE_AV1 in types) add(PlaybackVideoCodec.AV1)
                if (MIME_TYPE_HEVC in types) add(PlaybackVideoCodec.HEVC)
                if (MIME_TYPE_VP9 in types) add(PlaybackVideoCodec.VP9)
                if (MIME_TYPE_H264 in types) add(PlaybackVideoCodec.H264)
            }
        val audioCodecs =
            buildSet {
                if (MIME_TYPE_AAC in types || MIME_TYPE_AAC_ADTS in types) add(PlaybackAudioCodec.AAC)
                if (MIME_TYPE_MP3 in types) add(PlaybackAudioCodec.MP3)
                if (MIME_TYPE_AC3 in types) add(PlaybackAudioCodec.AC3)
                if (MIME_TYPE_EAC3 in types || MIME_TYPE_EAC3_JOC in types) add(PlaybackAudioCodec.EAC3)
                if (MIME_TYPE_OPUS in types) add(PlaybackAudioCodec.OPUS)
                if (MIME_TYPE_VORBIS in types) add(PlaybackAudioCodec.VORBIS)
                if (MIME_TYPE_FLAC in types) add(PlaybackAudioCodec.FLAC)
            }

        return PlaybackDeviceProfileFactory.create(
            "Jellystack Android",
            PlaybackDecoderCapabilities(
                videoCodecs = videoCodecs,
                audioCodecs = audioCodecs,
            ),
        )
    }

    private companion object {
        private const val MIME_TYPE_AV1 = "video/av01"
        private const val MIME_TYPE_HEVC = "video/hevc"
        private const val MIME_TYPE_VP9 = "video/x-vnd.on2.vp9"
        private const val MIME_TYPE_H264 = "video/avc"
        private const val MIME_TYPE_AAC = "audio/mp4a-latm"
        private const val MIME_TYPE_AAC_ADTS = "audio/aac"
        private const val MIME_TYPE_MP3 = "audio/mpeg"
        private const val MIME_TYPE_AC3 = "audio/ac3"
        private const val MIME_TYPE_EAC3 = "audio/eac3"
        private const val MIME_TYPE_EAC3_JOC = "audio/eac3-joc"
        private const val MIME_TYPE_OPUS = "audio/opus"
        private const val MIME_TYPE_VORBIS = "audio/vorbis"
        private const val MIME_TYPE_FLAC = "audio/flac"
    }
}

/** Device profile tuned for TV playback where software-only modern video codecs are unsafe. */
class AndroidTvPlaybackDeviceProfileProvider(
    private val decoderDescriptors: () -> List<PlaybackDecoderDescriptor> = {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .flatMap { codecInfo ->
                codecInfo.supportedTypes.asSequence().map { mimeType ->
                    PlaybackDecoderDescriptor(
                        mimeType = mimeType.lowercase(),
                        isHardwareAccelerated =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                codecInfo.isHardwareAccelerated
                            } else {
                                !codecInfo.name.contains("google", ignoreCase = true) &&
                                    !codecInfo.name.contains("software", ignoreCase = true)
                            },
                        isAlias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias,
                        isEncoder = codecInfo.isEncoder,
                    )
                }
            }.toList()
    },
    private val allowSoftwareAdvancedVideo: () -> Boolean = {
        Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    },
) : PlaybackDeviceProfileProvider {
    override fun deviceProfile(): JellyfinDeviceProfileDto =
        PlaybackDeviceProfileFactory.create(
            name = "Jellystack TV",
            capabilities =
                selectTvDecoderCapabilities(
                    decoders = decoderDescriptors(),
                    allowSoftwareAdvancedVideo = allowSoftwareAdvancedVideo(),
                ),
        )
}
