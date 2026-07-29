package dev.jellystack.core.jellyseerr

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinClientIdentity
import dev.jellystack.network.jellyfin.JellyfinQuickConnectAuthorizationApi
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrJellyfinLoginPayload
import dev.jellystack.network.jellyseerr.JellyseerrLocalLoginPayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

open class JellyseerrAuthenticator {
    open suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult {
        val client =
            NetworkClientFactory.create(
                ClientConfig(
                    installLogging = false,
                    configure = {
                        install(HttpCookies)
                    },
                ),
            )
        return client.use { httpClient ->
            performAuthentication(httpClient, request)
        }
    }

    open suspend fun authenticateWithQuickConnect(request: JellyseerrQuickConnectAuthRequest): JellyseerrAuthenticationResult {
        val seerrClient = sessionClient()
        val jellyfinClient =
            NetworkClientFactory.create(
                ClientConfig(installLogging = false),
            )
        return try {
            val api =
                JellyseerrApi.create(
                    baseUrl = request.baseUrl,
                    apiKey = null,
                    client = seerrClient,
                )
            val session = api.initiateJellyfinQuickConnect()
            if (!session.code.matches(Regex("""\d{6}""")) || session.secret.isBlank()) {
                throw JellyseerrAuthenticationException(
                    message = QUICK_CONNECT_FALLBACK_MESSAGE,
                    reason = JellyseerrAuthenticationException.Reason.QUICK_CONNECT_UNAVAILABLE,
                )
            }
            val authorizer =
                JellyfinQuickConnectAuthorizationApi(
                    client = jellyfinClient,
                    baseUrl = request.jellyfinBaseUrl,
                    identity =
                        JellyfinClientIdentity(
                            appName = "Jellystack",
                            appVersion = request.appVersion,
                            deviceName = "Jellystack",
                            deviceId = request.jellyfinDeviceId,
                        ),
                    accessToken = request.jellyfinAccessToken,
                )
            if (!authorizer.authorize(code = session.code, userId = request.jellyfinUserId)) {
                throw JellyseerrAuthenticationException(
                    message = QUICK_CONNECT_FALLBACK_MESSAGE,
                    reason = JellyseerrAuthenticationException.Reason.QUICK_CONNECT_UNAVAILABLE,
                )
            }

            var authorized = false
            for (attempt in 0 until QUICK_CONNECT_CHECK_ATTEMPTS) {
                if (api.checkJellyfinQuickConnect(session.secret).authenticated) {
                    authorized = true
                    break
                }
                if (attempt < QUICK_CONNECT_CHECK_ATTEMPTS - 1) {
                    delay(QUICK_CONNECT_CHECK_DELAY_MS)
                }
            }
            if (!authorized) {
                throw JellyseerrAuthenticationException(
                    message = QUICK_CONNECT_FALLBACK_MESSAGE,
                    reason = JellyseerrAuthenticationException.Reason.QUICK_CONNECT_UNAVAILABLE,
                )
            }
            completeAuthentication(
                client = seerrClient,
                api = api,
                authResponse = api.loginWithJellyfinQuickConnect(session.secret),
                baseUrl = request.baseUrl,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: JellyseerrAuthenticationException) {
            throw error
        } catch (_: Throwable) {
            throw JellyseerrAuthenticationException(
                message = QUICK_CONNECT_FALLBACK_MESSAGE,
                reason = JellyseerrAuthenticationException.Reason.QUICK_CONNECT_UNAVAILABLE,
            )
        } finally {
            jellyfinClient.close()
            seerrClient.close()
        }
    }

    private suspend fun performAuthentication(
        client: HttpClient,
        request: JellyseerrAuthRequest,
    ): JellyseerrAuthenticationResult {
        val api =
            JellyseerrApi.create(
                baseUrl = request.baseUrl,
                apiKey = null,
                client = client,
            )
        val authResponse =
            when (request.method) {
                JellyseerrAuthRequest.Method.LOCAL -> {
                    val email =
                        request.email?.takeIf { it.isNotBlank() }
                            ?: throw JellyseerrAuthenticationException(
                                message = "Email is required for Seerr account login.",
                                reason = JellyseerrAuthenticationException.Reason.MISSING_EMAIL,
                            )
                    api.loginWithCredentials(
                        JellyseerrLocalLoginPayload(
                            email = email,
                            password = request.password,
                        ),
                    )
                }
                JellyseerrAuthRequest.Method.JELLYFIN -> {
                    val username =
                        request.username?.takeIf { it.isNotBlank() }
                            ?: throw JellyseerrAuthenticationException(
                                message = "Username is required for Jellyfin login.",
                                reason = JellyseerrAuthenticationException.Reason.MISSING_JELLYFIN_USERNAME,
                            )
                    api.loginWithJellyfin(
                        JellyseerrJellyfinLoginPayload(
                            username = username,
                            password = request.password,
                        ),
                    )
                }
            }
        return completeAuthentication(
            client = client,
            api = api,
            authResponse = authResponse,
            baseUrl = request.baseUrl,
        )
    }

    private suspend fun completeAuthentication(
        client: HttpClient,
        api: JellyseerrApi,
        authResponse: dev.jellystack.network.jellyseerr.JellyseerrAuthResponse,
        baseUrl: String,
    ): JellyseerrAuthenticationResult {
        val user = authResponse.user
        val cookies = client.cookies(baseUrl)
        val cookieHeader =
            authResponse.sessionCookie
                ?: cookies
                    .filter { it.name.isNotBlank() && it.value.isNotBlank() }
                    .joinToString(separator = "; ") { cookie -> "${cookie.name}=${cookie.value}" }
                    .takeIf { it.isNotBlank() }
        val userApiKey = user.apiKey?.takeIf { it.isNotBlank() }
        val hasValidatedSession = runCatching { api.getProfile() }.isSuccess
        if (userApiKey.isNullOrBlank() && cookieHeader == null && !hasValidatedSession) {
            throw JellyseerrAuthenticationException("Seerr server did not return reusable authentication details.")
        }
        return JellyseerrAuthenticationResult(
            apiKey = userApiKey,
            userId = user.id,
            sessionCookie = cookieHeader,
            hasValidatedSession = hasValidatedSession,
        )
    }

    private fun sessionClient(): HttpClient =
        NetworkClientFactory.create(
            ClientConfig(
                installLogging = false,
                configure = {
                    install(HttpCookies)
                },
            ),
        )

    private companion object {
        const val QUICK_CONNECT_CHECK_ATTEMPTS = 6
        const val QUICK_CONNECT_CHECK_DELAY_MS = 500L
        const val QUICK_CONNECT_FALLBACK_MESSAGE =
            "Automatic Seerr sign-in is unavailable. Use your Jellyfin or Seerr password."
    }
}

data class JellyseerrAuthRequest(
    val baseUrl: String,
    val method: Method,
    val email: String? = null,
    val username: String? = null,
    val password: String,
) {
    enum class Method {
        LOCAL,
        JELLYFIN,
    }
}

data class JellyseerrAuthenticationResult(
    val apiKey: String?,
    val userId: Int?,
    val sessionCookie: String?,
    val hasValidatedSession: Boolean = false,
)

data class JellyseerrQuickConnectAuthRequest(
    val baseUrl: String,
    val jellyfinBaseUrl: String,
    val jellyfinAccessToken: String,
    val jellyfinUserId: String,
    val jellyfinDeviceId: String,
    val appVersion: String,
)

class JellyseerrAuthenticationException(
    message: String,
    cause: Throwable? = null,
    val reason: Reason = Reason.UNKNOWN,
) : IllegalStateException(message, cause) {
    enum class Reason {
        UNKNOWN,
        SERVER_NOT_FOUND,
        INVALID_LINKED_SERVER,
        MISSING_JELLYFIN_PASSWORD,
        MISSING_EMAIL,
        MISSING_JELLYFIN_USERNAME,
        QUICK_CONNECT_UNAVAILABLE,
    }
}

private inline fun <T> HttpClient.use(block: (HttpClient) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
