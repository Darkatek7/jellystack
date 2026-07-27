package dev.jellystack.players

import com.russhwolf.settings.Settings

interface PlaybackProgressStore {
    fun read(mediaId: String): PlaybackProgress?

    fun write(progress: PlaybackProgress)

    fun clear(mediaId: String)
}

class InMemoryPlaybackProgressStore : PlaybackProgressStore {
    private val progress = mutableMapOf<String, PlaybackProgress>()

    override fun read(mediaId: String): PlaybackProgress? = progress[mediaId]

    override fun write(progress: PlaybackProgress) {
        this.progress[progress.mediaId] = progress
    }

    override fun clear(mediaId: String) {
        progress.remove(mediaId)
    }
}

class SettingsPlaybackProgressStore(
    private val settings: Settings,
) : PlaybackProgressStore {
    override fun read(mediaId: String): PlaybackProgress? {
        val value = settings.getLongOrNull(key(mediaId)) ?: return null
        return PlaybackProgress(mediaId = mediaId, positionMs = value)
    }

    override fun write(progress: PlaybackProgress) {
        settings.putLong(key(progress.mediaId), progress.positionMs)
    }

    override fun clear(mediaId: String) {
        settings.remove(key(mediaId))
    }

    private fun key(mediaId: String): String = "playback.progress.$mediaId"
}

private fun Settings.getLongOrNull(key: String): Long? =
    if (hasKey(key)) {
        getLong(key, 0L)
    } else {
        null
    }
