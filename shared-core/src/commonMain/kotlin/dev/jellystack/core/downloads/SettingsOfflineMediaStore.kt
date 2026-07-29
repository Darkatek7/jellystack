package dev.jellystack.core.downloads

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsOfflineMediaStore(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OfflineMediaStore {
    override fun read(mediaId: String): OfflineMedia? {
        val raw = settings.getStringOrNull(key(mediaId)) ?: return null
        return runCatching { json.decodeFromString<OfflineMedia>(raw) }.getOrNull()
    }

    override fun write(media: OfflineMedia) {
        settings.putString(key(media.mediaId), json.encodeToString(media))
    }

    override fun writeAll(media: List<OfflineMedia>) {
        media.forEach { write(it) }
    }

    override fun remove(mediaId: String) {
        settings.remove(key(mediaId))
    }

    override fun list(): List<OfflineMedia> =
        settings.keys
            .asSequence()
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { key ->
                val raw = settings.getStringOrNull(key) ?: return@mapNotNull null
                runCatching { json.decodeFromString<OfflineMedia>(raw) }.getOrNull()
            }.toList()

    private fun key(mediaId: String): String = "$PREFIX$mediaId"

    private companion object {
        private const val PREFIX = "offline.media."
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) {
        getString(key, "")
    } else {
        null
    }
