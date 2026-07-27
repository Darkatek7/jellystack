package dev.jellystack.core.playback

import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsOfflinePlaybackEventStore(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OfflinePlaybackEventStore {
    override fun read(): List<OfflinePlaybackEvent> =
        settings
            .getStringOrNull(KEY)
            ?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(OfflinePlaybackEvent.serializer()), raw) }.getOrElse { emptyList() }
            }.orEmpty()

    override fun write(events: List<OfflinePlaybackEvent>) {
        if (events.isEmpty()) {
            settings.remove(KEY)
        } else {
            settings.putString(KEY, json.encodeToString(ListSerializer(OfflinePlaybackEvent.serializer()), events))
        }
    }

    private companion object {
        private const val KEY = "offline.playback.events"
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) {
        getString(key, "")
    } else {
        null
    }
