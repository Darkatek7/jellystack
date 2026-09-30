package dev.jellystack.core.jellyfin

data class JellyfinEnvironment(
    val serverKey: String,
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val deviceId: String?,
    val deviceName: String,
    val clientVersion: String = "unknown",
)

fun interface JellyfinEnvironmentProvider {
    suspend fun current(): JellyfinEnvironment?
}

fun interface JellystackClientVersionProvider {
    fun versionName(): String
}

/**
 * Supplies the active app language as a BCP 47 tag (for example `de` or `en-US`).
 *
 * Jellyfin 12.0 servers can localize their responses per client language; the value is sent
 * as `Accept-Language` on Jellyfin requests and re-evaluated per request.
 */
fun interface JellystackLocaleProvider {
    fun languageTag(): String?
}
