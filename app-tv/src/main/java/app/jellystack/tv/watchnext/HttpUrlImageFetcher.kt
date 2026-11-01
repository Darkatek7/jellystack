package app.jellystack.tv.watchnext

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plain `HttpURLConnection` download for artwork. Redirects are not followed, so the authorization header
 * can never be sent to a host other than the configured server; size and time are capped.
 */
internal class HttpUrlImageFetcher : WatchNextImageFetcher {
    override suspend fun fetch(
        url: String,
        authorization: String,
    ): ByteArray? =
        try {
            download(url, authorization)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: IOException) {
            JellystackLog.w("Watch Next art download failed: ${failure::class.java.simpleName}")
            null
        } catch (failure: SecurityException) {
            JellystackLog.w("Watch Next art download failed: ${failure::class.java.simpleName}")
            null
        }

    private fun download(
        url: String,
        authorization: String,
    ): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("Accept", "image/*")
            val ok = connection.responseCode == HttpURLConnection.HTTP_OK
            val isImage = ok && connection.contentType?.startsWith("image/") == true
            return if (isImage) connection.inputStream.use(::readCapped) else null
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
        const val BUFFER_BYTES = 16 * 1024
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
