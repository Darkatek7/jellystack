package app.jellystack.tv.watchnext

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.design.tv.TvDeepLink
import dev.jellystack.design.tv.TvWatchNextArtAspect
import dev.jellystack.design.tv.TvWatchNextArtwork
import dev.jellystack.network.jellyfin.jellyfinTokenAuthorizationValue
import java.io.File
import java.security.MessageDigest

/** Names and URIs of the cached artwork. The same rules serve the downloader and [WatchNextArtProvider]. */
internal object WatchNextArtFiles {
    private val hex = Regex("^[0-9a-f]{64}$")
    private val tag = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val imageTypes = setOf("Primary", "Thumb", "Backdrop")
    private const val LANDSCAPE_WIDTH = 640
    private const val PORTRAIT_WIDTH = 400

    const val PATH_PREFIX = "art"
    const val CACHE_DIRECTORY = "watchnext-art"

    /**
     * The file name hashes a per-install salt with the image identity, so another app cannot guess the URI of
     * an image; it can only read the ones the launcher was given.
     */
    fun name(
        salt: String,
        serverKey: String,
        artwork: TvWatchNextArtwork,
    ): String =
        with("$salt|$serverKey|${artwork.itemId}|${artwork.imageType}|${artwork.imageTag}") {
            MessageDigest
                .getInstance("SHA-256")
                .digest(toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

    fun uri(
        authority: String,
        name: String,
    ): String = "content://$authority/$PATH_PREFIX/$name"

    /** The file name behind a `/art/{name}` path, or null for anything else (including traversal attempts). */
    fun nameFromPath(segments: List<String>): String? =
        segments
            .takeIf { it.size == 2 && it[0] == PATH_PREFIX }
            ?.get(1)
            ?.takeIf(hex::matches)

    private fun maxWidth(aspect: TvWatchNextArtAspect): Int =
        when (aspect) {
            TvWatchNextArtAspect.LANDSCAPE_16_9 -> LANDSCAPE_WIDTH
            TvWatchNextArtAspect.PORTRAIT_2_3 -> PORTRAIT_WIDTH
        }

    /**
     * The download URL. The token is not part of it (it travels in a header), and every piece that comes from
     * the server or the item is checked, so it cannot change the path or add parameters.
     */
    fun imageUrl(
        baseUrl: String,
        artwork: TvWatchNextArtwork,
    ): String? {
        val base = baseUrl.trimEnd('/')
        val validBase = base.startsWith("http://") || base.startsWith("https://")
        val valid =
            validBase &&
                TvDeepLink.isValidItemId(artwork.itemId) &&
                artwork.imageType in imageTypes &&
                tag.matches(artwork.imageTag)
        return if (valid) {
            "$base/Items/${artwork.itemId}/Images/${artwork.imageType}" +
                "?tag=${artwork.imageTag}&maxWidth=${maxWidth(artwork.aspect)}&quality=85&format=Jpg"
        } else {
            null
        }
    }
}

/** Downloads one image; null on any failure. Implementations must not follow redirects with credentials. */
internal fun interface WatchNextImageFetcher {
    suspend fun fetch(
        url: String,
        authorization: String,
    ): ByteArray?
}

/**
 * Keeps poster art in the app cache and hands the launcher `content://` URIs served by [WatchNextArtProvider].
 *
 * Why not a server URL: the Watch Next table is readable by other apps (the launcher at least), and a Jellyfin
 * image URL needs the access token. Downloading in-process keeps the token in the request header of our own
 * connection; the table only ever holds an opaque local URI, and the files hold nothing but the image.
 */
internal class CachedWatchNextArt(
    private val directory: File,
    private val authority: String,
    private val salt: String,
    private val environments: JellyfinEnvironmentProvider,
    private val fetcher: WatchNextImageFetcher,
) : WatchNextArt {
    override suspend fun uriFor(
        serverKey: String,
        artwork: TvWatchNextArtwork,
    ): String? {
        val name = WatchNextArtFiles.name(salt, serverKey, artwork)
        val uri = WatchNextArtFiles.uri(authority, name)
        val file = File(directory, name)
        return if (file.isFile && file.length() > 0L) uri else uri.takeIf { download(serverKey, artwork, file) }
    }

    private suspend fun download(
        serverKey: String,
        artwork: TvWatchNextArtwork,
        target: File,
    ): Boolean {
        // Art of an account that is no longer the active one is never fetched with the active account token.
        val environment = environments.current()?.takeIf { it.serverKey == serverKey }
        val bytes =
            environment?.let { active ->
                WatchNextArtFiles.imageUrl(active.baseUrl, artwork)?.let { url ->
                    fetcher.fetch(url, jellyfinTokenAuthorizationValue(active.accessToken))
                }
            }
        return bytes != null && store(target, bytes)
    }

    override fun retain(keepUris: Set<String>) {
        val keep = keepUris.mapNotNullTo(HashSet()) { it.substringAfterLast('/', "").takeIf(String::isNotEmpty) }
        directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    override fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun store(
        file: File,
        bytes: ByteArray,
    ): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        return temporary.renameTo(file).also { if (!it) temporary.delete() }
    }
}
