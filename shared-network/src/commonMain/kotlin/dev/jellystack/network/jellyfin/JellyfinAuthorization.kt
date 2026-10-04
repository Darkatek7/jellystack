package dev.jellystack.network.jellyfin

import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter

/** Default client name reported to Jellyfin servers. */
const val JELLYSTACK_CLIENT_NAME = "Jellystack"

/**
 * URL query parameter used to carry the access token on URLs that cannot use headers
 * (media streams, subtitle files, images, WebSockets, and Cast receiver URLs).
 *
 * Jellyfin 12.0 removed the legacy lowercase `api_key` parameter together with the
 * `X-Emby-Token`, `X-MediaBrowser-Token`, and `X-Emby-Authorization` headers. The
 * supported mechanisms are the [Authorization] header with the `MediaBrowser` scheme
 * and this `ApiKey` query parameter; both are also accepted by Jellyfin 10.x servers.
 */
const val JELLYFIN_API_KEY_QUERY_PARAMETER = "ApiKey"

/** Client identity reported to Jellyfin through the `Authorization: MediaBrowser` scheme. */
data class JellyfinClientIdentity(
    val appName: String = JELLYSTACK_CLIENT_NAME,
    val appVersion: String,
    val deviceName: String,
    val deviceId: String,
)

/**
 * Builds the value for the standard `Authorization` header using Jellyfin's `MediaBrowser` scheme.
 *
 * The [token] is appended when present; omit it for unauthenticated calls such as Quick Connect
 * initiation or username/password login, where the server derives the device record from the
 * identity fields alone.
 */
fun jellyfinAuthorizationValue(
    identity: JellyfinClientIdentity,
    token: String? = null,
): String =
    buildString {
        append("MediaBrowser Client=\"")
        append(identity.appName.encodeJellyfinAuthValue())
        append("\", Device=\"")
        append(identity.deviceName.encodeJellyfinAuthValue())
        append("\", DeviceId=\"")
        append(identity.deviceId.encodeJellyfinAuthValue())
        append("\", Version=\"")
        append(identity.appVersion.encodeJellyfinAuthValue())
        append('"')
        if (!token.isNullOrBlank()) {
            append(", Token=\"")
            append(token.encodeJellyfinAuthValue())
            append('"')
        }
    }

/** Token-only `Authorization` value for call sites that do not carry full client identity. */
fun jellyfinTokenAuthorizationValue(token: String): String = "MediaBrowser Token=\"${token.encodeJellyfinAuthValue()}\""

/**
 * Appends the `Authorization` header unless one is already present.
 *
 * Uses the full identity scheme when [identity] is available and falls back to the token-only
 * form so every request carries exactly one authorization value.
 */
fun HeadersBuilder.appendJellyfinAuthorization(
    identity: JellyfinClientIdentity?,
    token: String?,
) {
    if (contains(HttpHeaders.Authorization)) return
    val value =
        when {
            identity != null -> jellyfinAuthorizationValue(identity, token)
            !token.isNullOrBlank() -> jellyfinTokenAuthorizationValue(token)
            else -> return
        }
    append(HttpHeaders.Authorization, value)
}

/** Jellyfin decodes percent-encoded values after splitting the scheme's comma-separated keys. */
private fun String.encodeJellyfinAuthValue(): String = encodeURLParameter(spaceToPlus = false)
