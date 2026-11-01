package dev.jellystack.core.jellyseerr

import dev.jellystack.core.profile.ProfileEnvironmentProvider
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface JellyseerrEnvironmentProvider {
    suspend fun current(): JellyseerrEnvironment?

    fun observe(): Flow<JellyseerrEnvironment?>
}

class ServerRepositoryJellyseerrEnvironmentProvider(
    private val repository: ServerRepository,
    private val profileEnvironmentProvider: ProfileEnvironmentProvider? = null,
) : JellyseerrEnvironmentProvider {
    override suspend fun current(): JellyseerrEnvironment? {
        profileEnvironmentProvider?.let { return it.seerr() }
        return repository.activeRequestServer()?.toEnvironment()
    }

    override fun observe(): Flow<JellyseerrEnvironment?> =
        profileEnvironmentProvider?.observeSeerr()
            ?: repository.observeActiveRequestServer().map { it?.toEnvironment() }
}

private fun ManagedServer.toEnvironment(): JellyseerrEnvironment? {
    val credential = credentials as? StoredCredential.ApiKey ?: return null
    if (type == ServerType.MEDIARIUM && credential.apiKey.isNullOrBlank()) return null
    return JellyseerrEnvironment(
        serverId = id,
        serverName = name,
        baseUrl = baseUrl,
        apiKey = credential.apiKey,
        sessionCookie = credential.sessionCookie,
        apiUserId = credential.userId?.toIntOrNull(),
        provider = if (type == ServerType.MEDIARIUM) RequestProviderType.MEDIARIUM else RequestProviderType.SEERR,
    )
}
