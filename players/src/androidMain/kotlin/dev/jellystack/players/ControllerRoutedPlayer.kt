package dev.jellystack.players

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** The playback commands external MediaSession controllers (voice assistants, HDMI-CEC, remotes) may issue. */
internal interface PlaybackCommandTarget {
    fun resume()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    /** Seeks relative to the current position; ignored without an active item. */
    fun seekBy(deltaMs: Long)
}

/** Position a relative seek lands on: never before the start and never past a known duration. */
internal fun relativeSeekTarget(
    positionMs: Long,
    durationMs: Long?,
    deltaMs: Long,
): Long {
    val destination = (positionMs + deltaMs).coerceAtLeast(0L)
    return durationMs?.let(destination::coerceAtMost) ?: destination
}

/**
 * The player the MediaSession exposes. Transport commands from outside the app go through the
 * PlaybackController instead of straight to ExoPlayer, so the UI state, progress reporting and SyncPlay
 * see them. Commands that would swap or reshape the media behind the controller's back are not offered.
 */
@UnstableApi
internal class ControllerRoutedPlayer(
    player: Player,
    private val target: PlaybackCommandTarget,
    private val seekBackMs: () -> Long,
    private val seekForwardMs: () -> Long,
) : ForwardingPlayer(player) {
    override fun play() = target.resume()

    override fun pause() = target.pause()

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) target.resume() else target.pause()

    override fun stop() = target.stop()

    override fun seekTo(positionMs: Long) = target.seekTo(positionMs)

    // A single item is playing, so a media item index other than the current one has no meaning.
    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) = target.seekTo(positionMs)

    override fun seekToDefaultPosition() = target.seekTo(0L)

    override fun seekToDefaultPosition(mediaItemIndex: Int) = target.seekTo(0L)

    override fun seekBack() = target.seekBy(-seekBackMs())

    override fun seekForward() = target.seekBy(seekForwardMs())

    override fun getAvailableCommands(): Player.Commands =
        super
            .getAvailableCommands()
            .buildUpon()
            .removeAll(*UNSUPPORTED_COMMANDS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    private companion object {
        val UNSUPPORTED_COMMANDS =
            intArrayOf(
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_SPEED_AND_PITCH,
            )
    }
}
