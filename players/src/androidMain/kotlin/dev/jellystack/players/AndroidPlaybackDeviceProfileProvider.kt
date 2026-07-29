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
        val codecs =
            buildSet {
                if (MIME_TYPE_AV1 in types) add(PlaybackVideoCodec.AV1)
                if (MIME_TYPE_HEVC in types) add(PlaybackVideoCodec.HEVC)
                if (MIME_TYPE_VP9 in types) add(PlaybackVideoCodec.VP9)
                if (MIME_TYPE_H264 in types) add(PlaybackVideoCodec.H264)
            }

        return PlaybackDeviceProfileFactory.create(
            "Jellystack Android",
            PlaybackDecoderCapabilities(codecs),
        )
    }

    private companion object {
        private const val MIME_TYPE_AV1 = "video/av01"
        private const val MIME_TYPE_HEVC = "video/hevc"
        private const val MIME_TYPE_VP9 = "video/x-vnd.on2.vp9"
        private const val MIME_TYPE_H264 = "video/avc"
    }
}
