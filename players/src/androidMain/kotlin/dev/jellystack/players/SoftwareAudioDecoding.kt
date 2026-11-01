package dev.jellystack.players

import android.os.Handler
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink

/**
 * Asks whether the app ships a software decoder for a Media3 audio MIME type. Plain values in, plain values out,
 * so the device profile can be tested without the native library.
 */
fun interface SoftwareAudioDecoderProbe {
    fun canDecode(mimeType: String): Boolean

    companion object {
        val NONE = SoftwareAudioDecoderProbe { false }
    }
}

/**
 * A software audio decoder (FFmpeg on TV) the host app brings along. It lives behind this interface so the shared
 * `players` module, and with it the phone APK, never links against the decoder or its native libraries: only the
 * app that declares the dependency hands an implementation to [AndroidPlayerEngine].
 */
@UnstableApi
interface SoftwareAudioDecoding : SoftwareAudioDecoderProbe {
    /**
     * Creates the renderer. The player appends it after the platform audio renderers, so it only ever receives
     * tracks that neither a platform decoder nor a passthrough route can take.
     */
    fun createAudioRenderer(
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        audioSink: AudioSink,
    ): Renderer
}

private class SoftwareAudioCandidate(
    val codec: PlaybackAudioCodec,
    val mimeTypes: List<String>,
    val maxChannels: Int,
    /** True when the codec is only worth declaring on devices whose platform has no way to play it. */
    val onlyWithoutPlatformSupport: Boolean,
)

// DTS and TrueHD have no platform decoder on TVs, so the software decoder (up to 7.1) is what makes them playable
// on a stereo sink. Dolby Digital normally has a platform decoder or passthrough, which stays the better route,
// so it is only declared where neither exists. Every MIME type of a codec must be decodable: Jellyfin reports all
// DTS flavours as `dts`, and Media3 tells them apart.
private val SOFTWARE_AUDIO_CANDIDATES =
    listOf(
        SoftwareAudioCandidate(
            PlaybackAudioCodec.DTS,
            listOf(MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD),
            maxChannels = 8,
            onlyWithoutPlatformSupport = false,
        ),
        SoftwareAudioCandidate(
            PlaybackAudioCodec.TRUEHD,
            listOf(MimeTypes.AUDIO_TRUEHD),
            maxChannels = 8,
            onlyWithoutPlatformSupport = false,
        ),
        SoftwareAudioCandidate(
            PlaybackAudioCodec.AC3,
            listOf(MimeTypes.AUDIO_AC3),
            maxChannels = 6,
            onlyWithoutPlatformSupport = true,
        ),
        SoftwareAudioCandidate(
            PlaybackAudioCodec.EAC3,
            listOf(MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC),
            maxChannels = 6,
            onlyWithoutPlatformSupport = true,
        ),
    )

/**
 * Codecs the software decoder adds to the platform evidence in [platformAudioSupport], each with its channel
 * ceiling. A probe that fails counts as "cannot decode": the software decoder is a bonus and never makes the
 * inspection incomplete.
 */
internal fun softwareAudioChannelCounts(
    probe: SoftwareAudioDecoderProbe,
    platformAudioSupport: Set<PlaybackAudioCodec>,
): Map<PlaybackAudioCodec, Int> =
    SOFTWARE_AUDIO_CANDIDATES
        .filterNot { it.onlyWithoutPlatformSupport && it.codec in platformAudioSupport }
        .filter { candidate ->
            runCatching { candidate.mimeTypes.all(probe::canDecode) }.getOrDefault(false)
        }.associate { it.codec to it.maxChannels }
