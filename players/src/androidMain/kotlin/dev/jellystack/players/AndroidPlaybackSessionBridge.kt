package dev.jellystack.players

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TV-facing MediaSession and hardware-key bridge.
 *
 * The PlaybackController remains the single source of truth. Hardware events are therefore
 * translated to controller commands instead of mutating ExoPlayer behind its back.
 */
@UnstableApi
class AndroidPlaybackSessionBridge(
    context: Context,
    private val engine: AndroidPlayerEngine,
    private val controller: PlaybackController,
    private val seekBackMs: () -> Long = { 10_000L },
    private val seekForwardMs: () -> Long = { 30_000L },
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stopped = AtomicBoolean(false)
    private val mediaSession = MediaSession.Builder(appContext, engine.media3Player()).build()
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any(AudioDeviceInfo::isPlaybackOutput)) {
                    controller.pause()
                }
            }
        }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    /**
     * Handles transport keys delivered by Android TV and Fire TV hardware.
     *
     * D-pad events deliberately stay in Compose so focused controls and dialogs can consume
     * them instead of unexpectedly seeking or toggling playback.
     */
    fun handleMediaKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val active = controller.state.value as? PlaybackState.Active
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> {
                if (active?.isPaused == false) controller.pause() else controller.resume()
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                active?.let { controller.seekTo((it.positionMs - seekBackMs()).coerceAtLeast(0L)) }
                active != null
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            -> {
                active?.let { current ->
                    val destination = current.positionMs + seekForwardMs()
                    controller.seekTo(current.durationMs?.let(destination::coerceAtMost) ?: destination)
                }
                active != null
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                stopPlayback()
                true
            }
            else -> false
        }
    }

    fun stopPlayback() {
        if (stopped.compareAndSet(false, true)) {
            controller.stop(saveProgress = true)
        }
    }

    fun markPlaybackStarted() {
        stopped.set(false)
    }

    fun release() {
        stopPlayback()
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        mediaSession.release()
    }
}

private fun AudioDeviceInfo.isPlaybackOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_HDMI ||
        type == AudioDeviceInfo.TYPE_HDMI_ARC ||
        type == AudioDeviceInfo.TYPE_HDMI_EARC ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET
