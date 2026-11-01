package dev.jellystack.players

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities

/**
 * Audio codecs the current output route takes as an undecoded bitstream, each with the most channels the route
 * accepts. A codec that is missing cannot be passed through.
 */
internal class AudioPassthroughSupport(
    maxChannelsByCodec: Map<PlaybackAudioCodec, Int>,
) {
    val maxChannelsByCodec: Map<PlaybackAudioCodec, Int> = maxChannelsByCodec.toMap()

    companion object {
        val NONE = AudioPassthroughSupport(emptyMap())
    }
}

/** Probes the audio output route right now. Throws when the platform cannot be asked. */
internal fun interface AudioPassthroughProbe {
    fun probe(): AudioPassthroughSupport
}

/** Whether the connected display lists Dolby Vision. */
internal fun interface DolbyVisionDisplayProbe {
    fun displayListsDolbyVision(): Boolean
}

/**
 * Asks Media3's [AudioCapabilities], the logic ExoPlayer's `DefaultAudioSink` itself uses to decide on passthrough
 * (HDMI/ARC encodings, `AudioManager` direct profiles on API 29+, the user's surround-sound setting, Bluetooth
 * falling back to PCM). What it reports is therefore what the sink will actually do for the same format. DTS-HD
 * falls back to DTS core inside Media3 when the route only knows DTS, which the channel ceiling reflects.
 */
@UnstableApi
internal class Media3AudioPassthroughProbe(
    private val context: Context,
) : AudioPassthroughProbe {
    override fun probe(): AudioPassthroughSupport {
        val capabilities = AudioCapabilities.getCapabilities(context, PASSTHROUGH_AUDIO_ATTRIBUTES, null)
        return AudioPassthroughSupport(
            PASSTHROUGH_CANDIDATES
                .mapNotNull { (codec, attempts) ->
                    attempts
                        .firstOrNull { (mimeType, channels) ->
                            capabilities.isPassthroughPlaybackSupported(passthroughFormat(mimeType, channels), PASSTHROUGH_AUDIO_ATTRIBUTES)
                        }?.let { (_, channels) -> codec to channels }
                }.toMap(),
        )
    }

    private fun passthroughFormat(
        mimeType: String,
        channels: Int,
    ): Format =
        Format
            .Builder()
            .setSampleMimeType(mimeType)
            .setChannelCount(channels)
            .setSampleRate(PASSTHROUGH_SAMPLE_RATE)
            .build()

    private companion object {
        const val PASSTHROUGH_SAMPLE_RATE = 48_000

        // Same attributes as the player, so the route is judged the way playback will use it.
        val PASSTHROUGH_AUDIO_ATTRIBUTES: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

        // Best first: the first format the route accepts decides the channel ceiling. DTS-HD is asked for with
        // eight channels before the core DTS stream with six.
        val PASSTHROUGH_CANDIDATES: List<Pair<PlaybackAudioCodec, List<Pair<String, Int>>>> =
            listOf(
                PlaybackAudioCodec.AC3 to listOf(MimeTypes.AUDIO_AC3 to 6, MimeTypes.AUDIO_AC3 to 2),
                PlaybackAudioCodec.EAC3 to
                    listOf(MimeTypes.AUDIO_E_AC3 to 8, MimeTypes.AUDIO_E_AC3 to 6, MimeTypes.AUDIO_E_AC3 to 2),
                PlaybackAudioCodec.DTS to
                    listOf(
                        MimeTypes.AUDIO_DTS_HD to 8,
                        MimeTypes.AUDIO_DTS_HD to 6,
                        MimeTypes.AUDIO_DTS to 6,
                        MimeTypes.AUDIO_DTS to 2,
                    ),
                PlaybackAudioCodec.TRUEHD to
                    listOf(MimeTypes.AUDIO_TRUEHD to 8, MimeTypes.AUDIO_TRUEHD to 6, MimeTypes.AUDIO_TRUEHD to 2),
            )
    }
}

internal class AndroidDolbyVisionDisplayProbe(
    private val context: Context,
) : DolbyVisionDisplayProbe {
    override fun displayListsDolbyVision(): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val hdrTypes =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                display.mode.supportedHdrTypes
            } else {
                legacyHdrTypes(display)
            }
        return Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
    }

    // Deprecated from API 34 in favour of Display.Mode.getSupportedHdrTypes, which does not exist before it.
    @Suppress("DEPRECATION")
    private fun legacyHdrTypes(display: Display): IntArray = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
}
