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
        return repository
            .activeServer(ServerType.JELLYSEERR)
            ?.toEnvironment()
    }

    override fun observe(): Flow<JellyseerrEnvironment?> =
        profileEnvironmentProvider?.observeSeerr()
            ?: repository.observeActiveServer(ServerType.JELLYSEERR).map { server -> server?.toEnvironment() }
}

private fun ManagedServer.toEnvironment(): JellyseerrEnvironment? {
    val credential = credentials as? StoredCredential.ApiKey ?: return null
    return JellyseerrEnvironment(
        serverId = id,
        serverName = name,
        baseUrl = baseUrl,
        apiKey = credential.apiKey,
        sessionCookie = credential.sessionCookie,
        apiUserId = credential.userId?.toIntOrNull(),
    )
}
