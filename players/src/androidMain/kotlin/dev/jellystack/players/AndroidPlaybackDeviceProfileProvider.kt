package dev.jellystack.players

import android.media.MediaCodecList
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
