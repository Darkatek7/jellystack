package dev.jellystack.core.profile

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyseerr.JellyseerrEnvironment
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

class ProfileEnvironmentProvider(
    private val activeState: StateFlow<ActiveProfileState>,
    private val bindingResolver: suspend (profileId: String) -> ProfileConnectionBinding?,
    private val serverResolver: suspend (connectionId: String) -> ManagedServer?,
    private val deviceNameProvider: () -> String = { "Jellystack" },
    private val clientVersionProvider: () -> String = { "unknown" },
) {
    suspend fun jellyfin(): JellyfinEnvironment? {
        val profileId = activeProfileId() ?: return null
        val connectionId = bindingResolver(profileId)?.jellyfinConnectionId ?: return null
        val server = serverResolver(connectionId)?.takeIf { it.id == connectionId && it.type == ServerType.JELLYFIN } ?: return null
        val credential = server.credentials as? StoredCredential.Jellyfin ?: return null
        if (credential.accessToken.isBlank() || credential.userId.isBlank()) return null
        return JellyfinEnvironment(
            serverKey = server.id,
            baseUrl = server.baseUrl,
            accessToken = credential.accessToken,
            userId = credential.userId,
            deviceId = credential.deviceId ?: credential.username,
            deviceName = deviceNameProvider(),
            clientVersion = clientVersionProvider(),
        )
    }

    suspend fun seerr(): JellyseerrEnvironment? {
        val profileId = activeProfileId() ?: return null
        val connectionId = bindingResolver(profileId)?.seerrConnectionId ?: return null
        val server = serverResolver(connectionId)?.takeIf { it.id == connectionId && it.type == ServerType.JELLYSEERR } ?: return null
        val credential = server.credentials as? StoredCredential.ApiKey ?: return null
        if (credential.apiKey.isNullOrBlank() && credential.sessionCookie.isNullOrBlank()) return null
        return JellyseerrEnvironment(
            serverId = server.id,
            serverName = server.name,
            baseUrl = server.baseUrl,
            apiKey = credential.apiKey,
            sessionCookie = credential.sessionCookie,
            apiUserId = credential.userId?.toIntOrNull(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSeerr(): Flow<JellyseerrEnvironment?> = activeState.mapLatest { seerr() }.distinctUntilChanged()

    private fun activeProfileId(): String? = (activeState.value as? ActiveProfileState.Active)?.profileId
}
