package dev.jellystack.core.server

import dev.jellystack.core.jellyseerr.JellyseerrAuthRequest
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationException
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationResult
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.jellyseerr.JellyseerrQuickConnectAuthRequest
import kotlinx.coroutines.CancellationException

data class JellyfinConnectionInput(
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val serverId: String? = null,
)

data class SeerrServerInput(
    val name: String,
    val baseUrl: String,
    val serverId: String? = null,
    val appVersion: String = "unknown",
)

sealed interface SeerrLoginCredentials {
    data class Jellyfin(
        val username: String,
        val password: String,
    ) : SeerrLoginCredentials

    data class Local(
        val email: String,
        val password: String,
    ) : SeerrLoginCredentials
}

sealed interface SeerrConnectionResult {
    data class Connected(
        val server: ManagedServer,
    ) : SeerrConnectionResult

    data class CredentialsRequired(
        val reason: String,
        val suggestedUsername: String?,
    ) : SeerrConnectionResult

    data class ConnectionFailed(
        val reason: String,
    ) : SeerrConnectionResult
}

class ServerConnectionCoordinator(
    private val serverRepository: ServerRepository,
    private val jellyseerrAuthenticator: JellyseerrAuthenticator,
) {
    suspend fun connectJellyfin(input: JellyfinConnectionInput): ManagedServer =
        serverRepository.register(
            ServerRegistration(
                id = input.serverId,
                type = ServerType.JELLYFIN,
                name = input.name.trim().ifBlank { defaultName(input.baseUrl, "Jellyfin") },
                baseUrl = input.baseUrl,
                credentials =
                    CredentialInput.Jellyfin(
                        username = input.username.trim(),
                        password = input.password,
                    ),
            ),
        )

    suspend fun connectSeerrAutomatically(input: SeerrServerInput): SeerrConnectionResult {
        val jellyfin =
            serverRepository.activeServer(ServerType.JELLYFIN)
                ?: return SeerrConnectionResult.CredentialsRequired(
                    reason = "Connect Jellyfin first or enter Seerr credentials.",
                    suggestedUsername = null,
                )
        val credential = jellyfin.credentials as? StoredCredential.Jellyfin
        val password = serverRepository.jellyfinPassword(jellyfin.id)?.reveal()
        if (credential == null) {
            return SeerrConnectionResult.CredentialsRequired(
                reason = "Connect Jellyfin first or enter Seerr credentials.",
                suggestedUsername = null,
            )
        }
        return try {
            val auth =
                if (password != null) {
                    jellyseerrAuthenticator.authenticate(
                        JellyseerrAuthRequest(
                            baseUrl = normalizeBaseUrl(input.baseUrl),
                            method = JellyseerrAuthRequest.Method.JELLYFIN,
                            username = credential.username,
                            password = password,
                        ),
                    )
                } else {
                    jellyseerrAuthenticator.authenticateWithQuickConnect(
                        JellyseerrQuickConnectAuthRequest(
                            baseUrl = normalizeBaseUrl(input.baseUrl),
                            jellyfinBaseUrl = jellyfin.baseUrl,
                            jellyfinAccessToken = credential.accessToken,
                            jellyfinUserId = credential.userId,
                            jellyfinDeviceId = credential.deviceId ?: credential.userId,
                            appVersion = input.appVersion,
                        ),
                    )
                }
            SeerrConnectionResult.Connected(registerSeerr(input, auth))
        } catch (error: JellyseerrAuthenticationException) {
            SeerrConnectionResult.CredentialsRequired(
                reason = error.message ?: "Jellyfin-linked Seerr login failed.",
                suggestedUsername = credential.username,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            SeerrConnectionResult.ConnectionFailed(
                reason = "Could not connect to Seerr. Check the server URL and its HTTPS configuration.",
            )
        }
    }

    suspend fun connectSeerrManually(
        input: SeerrServerInput,
        credentials: SeerrLoginCredentials,
    ): ManagedServer {
        val normalizedUrl = normalizeBaseUrl(input.baseUrl)
        val request =
            when (credentials) {
                is SeerrLoginCredentials.Jellyfin ->
                    JellyseerrAuthRequest(
                        baseUrl = normalizedUrl,
                        method = JellyseerrAuthRequest.Method.JELLYFIN,
                        username = credentials.username.trim(),
                        password = credentials.password,
                    )
                is SeerrLoginCredentials.Local ->
                    JellyseerrAuthRequest(
                        baseUrl = normalizedUrl,
                        method = JellyseerrAuthRequest.Method.LOCAL,
                        email = credentials.email.trim(),
                        password = credentials.password,
                    )
            }
        return registerSeerr(input, jellyseerrAuthenticator.authenticate(request))
    }

    private suspend fun registerSeerr(
        input: SeerrServerInput,
        auth: JellyseerrAuthenticationResult,
    ): ManagedServer =
        serverRepository.register(
            ServerRegistration(
                id = input.serverId,
                type = ServerType.JELLYSEERR,
                name = input.name.trim().ifBlank { defaultName(input.baseUrl, "Seerr") },
                baseUrl = input.baseUrl,
                credentials =
                    CredentialInput.ApiKey(
                        apiKey = auth.apiKey,
                        userId = auth.userId?.toString(),
                        sessionCookie = auth.sessionCookie,
                    ),
            ),
        )

    private fun defaultName(
        baseUrl: String,
        fallback: String,
    ): String =
        runCatching { normalizeBaseUrl(baseUrl).substringAfter("://").substringBefore('/') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
}
