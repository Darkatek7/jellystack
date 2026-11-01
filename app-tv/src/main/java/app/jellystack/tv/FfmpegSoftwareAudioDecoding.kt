package app.jellystack.tv

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import dev.jellystack.players.SoftwareAudioDecoding

/**
 * FFmpeg software audio decoding from the Jellyfin build of Media3's FFmpeg extension (GPL-3.0). It plays what
 * neither a platform decoder nor a passthrough route can take, typically DTS or TrueHD on a stereo TV.
 * Only this app links the extension, so the phone APK carries neither the classes nor the native libraries.
 */
@OptIn(UnstableApi::class)
internal object FfmpegSoftwareAudioDecoding : SoftwareAudioDecoding {
    // Also loads the native library on first use; false when this device's ABI has none.
    override fun canDecode(mimeType: String): Boolean {
        if (!FfmpegLibrary.isAvailable()) return false
        return FfmpegLibrary.supportsFormat(mimeType)
    }

    override fun createAudioRenderer(
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        audioSink: AudioSink,
    ): Renderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
}
