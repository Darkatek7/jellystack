package dev.jellystack.players.cast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val CAST_NOTIFICATION_ID = 92
private const val CAST_CHANNEL_ID = "cast_playback"
private const val CAST_CHANNEL_NAME = "Cast playback"

internal class CastMediaNotificationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controls: Controls,
) {
    interface Controls {
        suspend fun onPlay()

        suspend fun onPause()

        suspend fun onStop()

        suspend fun onSeek(positionMs: Long)
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val mediaSession =
        MediaSessionCompat(context, "JellystackCastSession").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        scope.launch { controls.onPlay() }
                    }

                    override fun onPause() {
                        scope.launch { controls.onPause() }
                    }

                    override fun onStop() {
                        scope.launch { controls.onStop() }
                    }

                    override fun onSeekTo(pos: Long) {
                        scope.launch { controls.onSeek(pos) }
                    }
                },
            )
        }

    private var lastSnapshot: CastSessionSnapshot? = null
    private var lastDeviceName: String? = null

    init {
        createChannel()
    }

    fun updateState(state: CastConnectionState) {
        when (state) {
            CastConnectionState.Idle -> clear()
            is CastConnectionState.Connecting -> {
                lastDeviceName = state.deviceName
                mediaSession.isActive = true
                mediaSession.setPlaybackState(
                    PlaybackStateCompat
                        .Builder()
                        .setState(PlaybackStateCompat.STATE_CONNECTING, 0L, 0f)
                        .build(),
                )
                postNotification(
                    title = "Connecting",
                    text = state.deviceName ?: "Chromecast",
                    isPaused = true,
                    showActions = false,
                )
            }
            is CastConnectionState.Connected -> {
                lastDeviceName = state.deviceName
                onSnapshotUpdated(state.snapshot, state.deviceName)
            }
            is CastConnectionState.Error -> {
                clear()
            }
        }
    }

    fun onSnapshotUpdated(
        snapshot: CastSessionSnapshot,
        deviceName: String,
    ) {
        lastSnapshot = snapshot
        lastDeviceName = deviceName
        mediaSession.isActive = true
        mediaSession.setMetadata(buildMetadata(snapshot))
        mediaSession.setPlaybackState(buildPlaybackState(snapshot))
        postNotification(
            title = snapshot.title ?: snapshot.episodeName ?: snapshot.seriesName ?: "Casting",
            text = snapshot.seriesName ?: deviceName,
            isPaused = snapshot.isPaused,
            showActions = true,
        )
    }

    fun onProgress(positionMs: Long) {
        val current = lastSnapshot ?: return
        val updated = current.copy(positionMs = positionMs, isPaused = false)
        lastSnapshot = updated
        mediaSession.setPlaybackState(buildPlaybackState(updated))
        postNotification(
            title = updated.title ?: updated.episodeName ?: updated.seriesName ?: "Casting",
            text = updated.seriesName ?: lastDeviceName ?: "Casting",
            isPaused = false,
            showActions = true,
        )
    }

    fun onPaused(positionMs: Long?) {
        val current = lastSnapshot ?: return
        val updated =
            current.copy(
                positionMs = positionMs ?: current.positionMs,
                isPaused = true,
            )
        lastSnapshot = updated
        mediaSession.setPlaybackState(buildPlaybackState(updated))
        postNotification(
            title = updated.title ?: updated.episodeName ?: updated.seriesName ?: "Casting",
            text = updated.seriesName ?: lastDeviceName ?: "Casting",
            isPaused = true,
            showActions = true,
        )
    }

    fun clear() {
        lastSnapshot = null
        mediaSession.isActive = false
        mediaSession.setMetadata(MediaMetadataCompat.Builder().build())
        mediaSession.setPlaybackState(
            PlaybackStateCompat
                .Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                .build(),
        )
        notificationManager.cancel(CAST_NOTIFICATION_ID)
    }

    fun release() {
        clear()
        mediaSession.release()
    }

    private fun buildMetadata(snapshot: CastSessionSnapshot): MediaMetadataCompat =
        MediaMetadataCompat
            .Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, snapshot.mediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.title ?: snapshot.episodeName ?: snapshot.mediaId)
            .apply {
                snapshot.seriesName?.let { putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, it) }
                snapshot.episodeName?.let { putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it) }
                snapshot.artworkUrl?.let { putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it) }
                snapshot.durationMs?.let { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it) }
            }.build()

    private fun buildPlaybackState(snapshot: CastSessionSnapshot): PlaybackStateCompat {
        val state =
            if (snapshot.isPaused) {
                PlaybackStateCompat.STATE_PAUSED
            } else {
                PlaybackStateCompat.STATE_PLAYING
            }
        val actions =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
        val speed = if (snapshot.isPaused) 0f else 1f
        return PlaybackStateCompat
            .Builder()
            .setActions(actions)
            .setState(state, snapshot.positionMs, speed)
            .build()
    }

    private fun postNotification(
        title: String,
        text: String,
        isPaused: Boolean,
        showActions: Boolean,
    ) {
        val builder =
            NotificationCompat
                .Builder(context, CAST_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(
                    if (isPaused) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                ).setOnlyAlertOnce(true)
                .setOngoing(!isPaused)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)

        val actions = mutableListOf<NotificationCompat.Action>()
        if (showActions) {
            if (isPaused) {
                actions +=
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_play,
                        "Play",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY),
                    )
            } else {
                actions +=
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PAUSE),
                    )
            }
            actions +=
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Stop",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP),
                )
        }
        actions.forEach(builder::addAction)

        val style =
            androidx.media.app.NotificationCompat
                .MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
        if (actions.isNotEmpty()) {
            style.setShowActionsInCompactView(*actions.indices.toList().toIntArray())
        }
        builder.setStyle(style)

        notificationManager.notify(CAST_NOTIFICATION_ID, builder.build())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel =
                NotificationChannel(
                    CAST_CHANNEL_ID,
                    CAST_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notifications for Google Cast playback"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
            manager.createNotificationChannel(channel)
        }
    }
}
