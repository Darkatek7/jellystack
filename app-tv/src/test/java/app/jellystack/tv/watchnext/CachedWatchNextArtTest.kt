package app.jellystack.tv.watchnext

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedWatchNextArtTest {
    private val token = "super-secret-token"
    private val directory: File = Files.createTempDirectory("watchnext-art").toFile().resolve("art")
    private val requests = mutableListOf<Pair<String, String>>()
    private var imageBytes: ByteArray? = byteArrayOf(1, 2, 3)

    private fun environment(serverKey: String = "server-a") =
        JellyfinEnvironment(
            serverKey = serverKey,
            baseUrl = "https://media.example:8920/",
            accessToken = token,
            userId = "user-1",
            deviceId = null,
            deviceName = "TV",
        )

    private fun art(environments: JellyfinEnvironmentProvider = JellyfinEnvironmentProvider { environment() }) =
        CachedWatchNextArt(
            directory = directory,
            authority = "app.example.watchnext.art",
            salt = "salt",
            environments = environments,
            fetcher =
                WatchNextImageFetcher { url, authorization ->
                    requests += url to authorization
                    imageBytes
                },
        )

    @AfterTest
    fun cleanUp() {
        directory.parentFile.deleteRecursively()
    }

    @Test
    fun thePublishedUriAndTheDownloadUrlNeverContainTheToken() =
        runTest {
            val uri = assertNotNull(art().uriFor("server-a", artwork()))

            assertFalse(token in uri)
            assertTrue(uri.startsWith("content://app.example.watchnext.art/art/"))
            val (url, authorization) = requests.single()
            assertFalse(token in url)
            assertFalse("ApiKey" in url || "api_key" in url)
            assertTrue(token in authorization)
            assertEquals(
                "https://media.example:8920/Items/0123456789abcdef0123456789abcdef/Images/Thumb" +
                    "?tag=tag1&maxWidth=640&quality=85&format=Jpg",
                url,
            )
        }

    @Test
    fun theUriDoesNotRevealItemOrServerAndFollowsTheServedPattern() =
        runTest {
            val uri = assertNotNull(art().uriFor("server-a", artwork()))

            val path = uri.removePrefix("content://app.example.watchnext.art").trim('/').split('/')
            assertEquals(path[1], WatchNextArtFiles.nameFromPath(path))
            assertFalse("0123456789abcdef0123456789abcdef" in uri)
            assertFalse("server-a" in uri)
        }

    @Test
    fun aCachedImageIsServedWithoutAnotherDownload() =
        runTest {
            val first = art().uriFor("server-a", artwork())
            val second = art().uriFor("server-a", artwork())

            assertEquals(first, second)
            assertEquals(1, requests.size)
            assertEquals(
                listOf<Byte>(1, 2, 3),
                directory
                    .listFiles()
                    .single()
                    .readBytes()
                    .toList(),
            )
        }

    @Test
    fun nothingIsDownloadedForAnotherServerThanTheActiveOne() =
        runTest {
            assertNull(art().uriFor("server-b", artwork()))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun noEnvironmentMeansNoArt() =
        runTest {
            assertNull(art(environments = { null }).uriFor("server-a", artwork()))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun aFailedDownloadLeavesNoFileAndNoUri() =
        runTest {
            imageBytes = null

            assertNull(art().uriFor("server-a", artwork()))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun serverControlledPartsCannotChangeTheRequestedPath() =
        runTest {
            val hostile =
                listOf(
                    artwork(itemId = "../../Users"),
                    artwork(tag = "x&ApiKey=1"),
                    artwork(tag = "a/b"),
                    artwork(type = "Primary/../x"),
                )

            hostile.forEach { assertNull(art().uriFor("server-a", it)) }
            assertTrue(requests.isEmpty())
        }

    @Test
    fun retainKeepsOnlyTheImagesStillInUse() =
        runTest {
            val kept = assertNotNull(art().uriFor("server-a", artwork(tag = "keep")))
            art().uriFor("server-a", artwork(tag = "drop"))

            art().retain(setOf(kept))

            assertEquals(1, directory.listFiles().orEmpty().size)
            assertTrue(kept.endsWith(directory.listFiles().single().name))
        }

    @Test
    fun clearRemovesEveryImage() =
        runTest {
            art().uriFor("server-a", artwork())

            art().clear()

            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun theProviderPathRejectsTraversalAndForeignNames() {
        val name = "a".repeat(64)

        assertEquals(name, WatchNextArtFiles.nameFromPath(listOf("art", name)))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("art", "..")))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("art", "../$name")))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("other", name)))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("art", name, "x")))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("art", "A".repeat(64))))
        assertNull(WatchNextArtFiles.nameFromPath(listOf("art", "$name.tmp")))
    }
}
