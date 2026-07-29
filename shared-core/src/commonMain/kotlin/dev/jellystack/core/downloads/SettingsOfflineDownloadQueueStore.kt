package dev.jellystack.core.downloads

import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsOfflineDownloadQueueStore(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OfflineDownloadQueueStore {
    override fun all(): List<DownloadRequest> =
        settings
            .getStringOrNull(KEY)
            ?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(DownloadRequest.serializer()), raw) }.getOrElse { emptyList() }
            }.orEmpty()

    override fun put(request: DownloadRequest) {
        val updated = all().filterNot { it.mediaId == request.mediaId } + request
        settings.putString(KEY, json.encodeToString(ListSerializer(DownloadRequest.serializer()), updated))
    }

    override fun remove(mediaId: String) {
        val updated = all().filterNot { it.mediaId == mediaId }
        if (updated.isEmpty()) {
            settings.remove(KEY)
        } else {
            settings.putString(KEY, json.encodeToString(ListSerializer(DownloadRequest.serializer()), updated))
        }
    }

    override fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        private const val KEY = "offline.download.queue"
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) {
        getString(key, "")
    } else {
        null
    }
