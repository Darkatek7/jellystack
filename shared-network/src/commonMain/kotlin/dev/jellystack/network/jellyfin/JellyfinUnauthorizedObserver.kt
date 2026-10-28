package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart

/**
 * Reports every `401 Unauthorized` answer of a Jellyfin client, however the calling API maps the response, so one
 * place notices a rejected session instead of every call site. Install it only on clients that talk to Jellyfin with
 * the user's access token: the session probe itself (it would report its own failure) and login or Seerr clients
 * (a wrong password is not an expired session) must not carry it.
 *
 * [onUnauthorized] receives the access token the rejected request carried, or `null` when none was found. It runs
 * on the response pipeline and must not block.
 */
fun HttpClientConfig<*>.observeJellyfinUnauthorized(onUnauthorized: (accessToken: String?) -> Unit) {
    install(
        createClientPlugin("JellystackJellyfinUnauthorized") {
            onResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    val request = response.call.request
                    onUnauthorized(
                        jellyfinAccessTokenOf(
                            authorization = request.headers[HttpHeaders.Authorization],
                            apiKey = request.url.parameters[JELLYFIN_API_KEY_QUERY_PARAMETER],
                        ),
                    )
                }
            }
        },
    )
}

private val authorizationTokenPattern = Regex("""Token="([^"]*)"""")

/** The access token of a request, from the `MediaBrowser` authorization header or the `ApiKey` query parameter. */
internal fun jellyfinAccessTokenOf(
    authorization: String?,
    apiKey: String?,
): String? =
    authorization
        ?.let { authorizationTokenPattern.find(it)?.groupValues?.get(1) }
        ?.decodeURLPart()
        ?.takeIf(String::isNotBlank)
        ?: apiKey?.takeIf(String::isNotBlank)
