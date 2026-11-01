package dev.jellystack.players

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Audio and subtitle delay of the current title. Written from the main thread, read on the playback thread by
 * [DelayedAudioSink] and [DelayedTextRenderer], so a change needs no player rebuild.
 */
internal class PlaybackDelays {
    @Volatile
    var audioUs: Long = 0L
        private set

    @Volatile
    var subtitleUs: Long = 0L
        private set

    fun setAudioMs(delayMs: Int) {
        audioUs = PlaybackDelay.clamp(delayMs) * MICROS_PER_MILLI
    }

    fun setSubtitleMs(delayMs: Int) {
        subtitleUs = PlaybackDelay.clamp(delayMs) * MICROS_PER_MILLI
    }

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
    }
}

/**
 * The audio position is Media3's media clock: video frames are released when the clock reaches their timestamp.
 * Reporting the position [PlaybackDelays.audioUs] later makes the picture run that much earlier relative to the
 * sound, so a positive delay means the audio is heard later than the picture. The offset only touches the
 * reported position, so PCM, passthrough and offload output are all affected the same way.
 */
@UnstableApi
internal class DelayedAudioSink(
    sink: AudioSink,
    private val delays: PlaybackDelays,
) : ForwardingAudioSink(sink) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else position + delays.audioUs
    }
}

/**
 * Renders the wrapped text renderer at the playback position minus [PlaybackDelays.subtitleUs], so a positive
 * delay shows each cue later. The shifted position never moves backwards between two seeks: text renderers
 * only walk forward through their cues, so a larger delay holds the subtitles on screen until the shifted
 * clock has caught up instead of rewinding into cues they have already dropped.
 */
@UnstableApi
internal class DelayedTextRenderer(
    renderer: Renderer,
    private val delays: PlaybackDelays,
) : ForwardingRenderer(renderer) {
    private var hasRendered = false
    private var lastPositionUs = 0L

    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        val shifted = positionUs - delays.subtitleUs
        val effective = if (hasRendered) maxOf(shifted, lastPositionUs) else shifted
        hasRendered = true
        lastPositionUs = effective
        super.render(effective, elapsedRealtimeUs)
    }

    override fun resetPosition(
        positionUs: Long,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) {
        hasRendered = false
        super.resetPosition(positionUs, sampleStreamIsResetToKeyFrame)
    }

    override fun disable() {
        hasRendered = false
        super.disable()
    }
}
