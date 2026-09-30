package dev.jellystack.core.downloads

import dev.jellystack.core.testing.InMemorySettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SettingsOfflineDownloadQueueStoreTest {
    @Test
    fun persistedDownloadsResumeWithQueryAuthenticationAndKeepMetadata() {
        val request =
            request(
                url = "https://media.example/Videos/item/stream.mkv?Static=true&api_key=dummy-token#api_key=fragment",
                headers =
                    mapOf(
                        "x-emby-authorization" to "MediaBrowser Token=\"stale-token\"",
                        "X-Emby-Token" to "stale-token",
                        "User-Agent" to "Jellystack",
                    ),
            ).copy(metadata = OfflineMediaMetadata(itemId = "item", name = "Movie", type = "Movie"))
        val settings = InMemorySettings()
        settings.putString("offline.download.queue", Json.encodeToString(listOf(request)))
        val store = SettingsOfflineDownloadQueueStore(settings)

        val migrated = store.all().single()

        assertEquals(
            "https://media.example/Videos/item/stream.mkv?Static=true&ApiKey=dummy-token#api_key=fragment",
            migrated.downloadUrl,
        )
        assertEquals(mapOf("User-Agent" to "Jellystack"), migrated.headers)
        assertEquals(request.metadata, migrated.metadata)
        store.put(migrated)
        assertEquals(migrated, SettingsOfflineDownloadQueueStore(settings).all().single())
    }

    @Test
    fun headerOnlyDownloadsKeepTheirTokenWhenMigrated() {
        for (header in listOf("x-emby-token", "X-MediaBrowser-Token")) {
            val migrated = request(headers = mapOf(header to "dummy-token")).withModernJellyfinAuth()
            assertEquals(mapOf("Authorization" to "MediaBrowser Token=\"dummy-token\""), migrated.headers)
        }
        val schema = "MediaBrowser Client=\"Jellystack\", Token=\"dummy-token\""
        assertEquals(
            mapOf("Authorization" to schema),
            request(headers = mapOf("X-Emby-Authorization" to schema)).withModernJellyfinAuth().headers,
        )
    }

    @Test
    fun currentHeaderAuthorizationTakesPrecedenceOverLegacyHeaders() {
        val migrated =
            request(
                headers =
                    mapOf(
                        "authorization" to "MediaBrowser Token=\"current-token\"",
                        "X-Emby-Authorization" to "MediaBrowser Token=\"stale-token\"",
                        "X-Emby-Token" to "stale-token",
                    ),
            ).withModernJellyfinAuth()
        assertEquals(mapOf("authorization" to "MediaBrowser Token=\"current-token\""), migrated.headers)
    }

    @Test
    fun queryAuthenticatedDownloadsRemoveDuplicateHeaderAuthentication() {
        val migrated =
            request(
                url = "https://media.example/video?api_key=dummy-token&Static=true",
                headers = mapOf("Authorization" to "MediaBrowser Token=\"stale-token\""),
            ).withModernJellyfinAuth()
        assertEquals("https://media.example/video?ApiKey=dummy-token&Static=true", migrated.downloadUrl)
        assertEquals(emptyMap(), migrated.headers)
    }

    @Test
    fun modernAndUnauthenticatedRequestsAreUnchanged() {
        for (request in listOf(
            request(),
            request(url = "https://media.example/video?ApiKey=dummy-token"),
            request(headers = mapOf("Authorization" to "MediaBrowser Token=\"dummy-token\"")),
            request(url = "https://media.example/video#?api_key=fragment"),
        )) {
            assertSame(request, request.withModernJellyfinAuth())
        }
    }

    private fun request(
        url: String = "https://media.example/video",
        headers: Map<String, String> = emptyMap(),
    ) = DownloadRequest(
        mediaId = "item",
        downloadUrl = url,
        headers = headers,
        mimeType = "video/mp4",
        expectedSizeBytes = 100L,
        checksumSha256 = null,
    )
}
