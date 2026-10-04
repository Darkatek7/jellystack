package dev.jellystack.core.downloads

import com.russhwolf.settings.Settings
import dev.jellystack.network.jellyfin.jellyfinTokenAuthorizationValue
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
            .map { it.withModernJellyfinAuth() }

    override fun put(request: DownloadRequest) {
        val updated = all().filterNot { it.mediaId == request.mediaId } + request.withModernJellyfinAuth()
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

/**
 * Rewrites download requests persisted before the Jellyfin 12.0 authorization migration.
 *
 * Jellyfin 12.0 removed the legacy `api_key` query parameter and `X-Emby-*` headers, so queued
 * downloads from older app versions are normalized before they are retried. Query-authenticated
 * media URLs use `ApiKey` alone; header-only requests use the standard `Authorization` header.
 */
internal fun DownloadRequest.withModernJellyfinAuth(): DownloadRequest {
    val fragmentIndex = downloadUrl.indexOf('#').takeIf { it >= 0 } ?: downloadUrl.length
    val requestUrl = downloadUrl.substring(0, fragmentIndex)
    val modernUrl =
        requestUrl.replace("?api_key=", "?ApiKey=").replace("&api_key=", "&ApiKey=") +
            downloadUrl.substring(fragmentIndex)
    val hasQueryToken = Regex("[?&]ApiKey=[^&#]+").containsMatchIn(modernUrl.substringBefore('#'))
    val legacyAuthorization =
        headers.entries.firstOrNull { (name, _) -> name.equals("X-Emby-Authorization", ignoreCase = true) }
    val tokenHeader =
        headers.entries.firstOrNull { (name, _) ->
            name.equals("X-Emby-Token", ignoreCase = true) || name.equals("X-MediaBrowser-Token", ignoreCase = true)
        }
    var modernHeaders =
        headers.filterKeys { name ->
            !name.equals("X-Emby-Authorization", ignoreCase = true) &&
                !name.equals("X-Emby-Token", ignoreCase = true) &&
                !name.equals("X-MediaBrowser-Token", ignoreCase = true) &&
                !(hasQueryToken && name.equals("Authorization", ignoreCase = true))
        }
    if (!hasQueryToken && modernHeaders.keys.none { it.equals("Authorization", ignoreCase = true) }) {
        val authorization = legacyAuthorization?.value ?: tokenHeader?.value?.let(::jellyfinTokenAuthorizationValue)
        if (authorization != null) modernHeaders = modernHeaders + ("Authorization" to authorization)
    }
    if (modernUrl == downloadUrl && modernHeaders == headers) return this
    return copy(downloadUrl = modernUrl, headers = modernHeaders)
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) {
        getString(key, "")
    } else {
        null
    }
