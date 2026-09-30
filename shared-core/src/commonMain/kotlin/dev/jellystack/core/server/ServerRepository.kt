package dev.jellystack.core.server

import dev.jellystack.core.security.SecretValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ServerRepository(
    private val store: ServerStore,
    private val connectivity: ServerConnectivity,
    private val credentialVault: ServerCredentialVault,
    private val activeServerPreferences: ActiveServerPreferenceRepository,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { randomId(16) },
) {
    private val mutex = Mutex()
    private val servers = MutableStateFlow<List<ManagedServer>>(emptyList())

    init {
        servers.value = runBlocking { loadServers() }
        ensureActiveSelections()
    }

    fun observeServers(): StateFlow<List<ManagedServer>> = servers.asStateFlow()

    fun observeActiveServer(type: ServerType): Flow<ManagedServer?> =
        combine(servers, activeServerPreferences.observeActiveServerId(type)) { currentServers, activeId ->
            currentServers.firstOrNull { it.type == type && it.id == activeId }
                ?: currentServers.firstOrNull { it.type == type }
        }

    fun activeServer(type: ServerType): ManagedServer? {
        val candidates = servers.value.filter { it.type == type }
        val activeId = activeServerPreferences.activeServerId(type)
        return candidates.firstOrNull { it.id == activeId }
            ?: candidates.firstOrNull()?.also { fallback ->
                activeServerPreferences.setActiveServerId(type, fallback.id)
            }
    }

    suspend fun setActiveServer(
        type: ServerType,
        serverId: String,
    ) {
        val server =
            findServer(serverId)
                ?: throw InvalidServerConfiguration("Server $serverId does not exist")
        if (server.type != type) {
            throw InvalidServerConfiguration("Server $serverId is ${server.type.name}, not ${type.name}")
        }
        activeServerPreferences.setActiveServerId(type, serverId)
    }

    suspend fun findServer(serverId: String): ManagedServer? =
        store.get(serverId)?.let { record ->
            runCatching { record.toManagedServer() }
                .onFailure { error ->
                    Napier.e(
                        message = "Failed to load server $serverId",
                        throwable = error,
                    )
                }.getOrNull()
        }

    suspend fun jellyfinPassword(serverId: String): SecretValue? =
        findServer(serverId)?.takeIf { it.type == ServerType.JELLYFIN }?.let {
            credentialVault.readJellyfinPassword(serverId)
        }

    suspend fun register(request: ServerRegistration): ManagedServer =
        mutex.withLock {
            validate(request)
            val normalizedUrl = normalizeBaseUrl(request.baseUrl)
            val normalizedRequest = request.copy(baseUrl = normalizedUrl)
            when (val result = connectivity.test(normalizedRequest)) {
                is ConnectivityResult.Failure ->
                    throw ConnectivityException(result.message, result.cause)
                is ConnectivityResult.Success -> {
                    val existing =
                        store.findByIdentity(
                            request.type,
                            normalizedUrl,
                            result.credentials.authenticatedPrincipal(),
                        )
                    if (existing != null && existing.id != request.id) {
                        throw DuplicateServerException(existing.id, request.type, normalizedUrl)
                    }
                    val record =
                        persist(
                            id = request.id,
                            type = request.type,
                            name = normalizedRequest.name,
                            baseUrl = normalizedUrl,
                            credential = result.credentials,
                            existingAtUrl = existing,
                        )
                    when (val input = request.credentials) {
                        is CredentialInput.Jellyfin -> credentialVault.saveJellyfinPassword(record.id, input.password)
                        is CredentialInput.ApiKey -> {
                            // No-op
                        }
                    }
                    refreshServers()
                    ensureActiveSelection(request.type)
                    record.toManagedServer()
                }
            }
        }

    suspend fun registerAuthenticatedJellyfin(registration: AuthenticatedJellyfinRegistration): ManagedServer =
        mutex.withLock {
            validate(registration)
            val normalizedUrl = normalizeBaseUrl(registration.baseUrl)
            val existing =
                store.findByIdentity(
                    ServerType.JELLYFIN,
                    normalizedUrl,
                    registration.credentials.authenticatedPrincipal(),
                )
            if (existing != null && existing.id != registration.id) {
                throw DuplicateServerException(existing.id, ServerType.JELLYFIN, normalizedUrl)
            }
            val record =
                persist(
                    id = registration.id,
                    type = ServerType.JELLYFIN,
                    name = registration.name,
                    baseUrl = normalizedUrl,
                    credential = registration.credentials,
                    existingAtUrl = existing,
                )
            credentialVault.removeJellyfinPassword(record.id)
            refreshServers()
            ensureActiveSelection(ServerType.JELLYFIN)
            record.toManagedServer()
        }

    suspend fun remove(id: String) {
        mutex.withLock {
            val removedType = store.get(id)?.type
            store.delete(id)
            credentialVault.removeJellyfinPassword(id)
            refreshServers()
            if (removedType != null && activeServerPreferences.activeServerId(removedType) == id) {
                activeServerPreferences.setActiveServerId(removedType, null)
                ensureActiveSelection(removedType)
            }
        }
    }

    fun currentServers(): List<ManagedServer> = servers.value

    private suspend fun refreshServers() {
        servers.value = loadServers()
    }

    private fun ensureActiveSelections() {
        ServerType.entries.forEach(::ensureActiveSelection)
    }

    private fun ensureActiveSelection(type: ServerType) {
        val candidates = servers.value.filter { it.type == type }
        val selected = activeServerPreferences.activeServerId(type)
        if (candidates.none { it.id == selected }) {
            activeServerPreferences.setActiveServerId(type, candidates.firstOrNull()?.id)
        }
    }

    private suspend fun loadServers(): List<ManagedServer> {
        val records = store.list()
        if (records.isEmpty()) {
            return emptyList()
        }

        val validServers = mutableListOf<ManagedServer>()
        records.forEach { record ->
            val managed =
                runCatching { record.toManagedServer() }
                    .onFailure { error ->
                        Napier.e(
                            message = "Dropping corrupted server entry ${record.id}",
                            throwable = error,
                        )
                        runCatching { store.delete(record.id) }
                            .onFailure { cleanupError ->
                                Napier.e(
                                    message = "Failed to remove corrupted server entry ${record.id}",
                                    throwable = cleanupError,
                                )
                            }
                    }.getOrNull()
            if (managed != null) {
                validServers += managed
            }
        }
        return validServers
    }

    private fun validate(request: ServerRegistration) {
        if (request.name.isBlank()) {
            throw InvalidServerConfiguration("Server name cannot be blank")
        }
        when (val creds = request.credentials) {
            is CredentialInput.Jellyfin -> {
                if (creds.username.isBlank()) {
                    throw InvalidServerConfiguration("Jellyfin username cannot be blank")
                }
            }
            is CredentialInput.ApiKey -> {
                val hasApiKey = !creds.apiKey.isNullOrBlank()
                val hasSession = !creds.sessionCookie.isNullOrBlank()
                if (!hasApiKey && !hasSession) {
                    throw InvalidServerConfiguration("API key or session cookie is required")
                }
            }
        }
    }

    private fun validate(registration: AuthenticatedJellyfinRegistration) {
        if (registration.name.isBlank()) {
            throw InvalidServerConfiguration("Server name cannot be blank")
        }
        with(registration.credentials) {
            if (username.isBlank()) {
                throw InvalidServerConfiguration("Jellyfin username cannot be blank")
            }
            if (deviceId.isNullOrBlank()) {
                throw InvalidServerConfiguration("Jellyfin device id cannot be blank")
            }
            if (accessToken.isBlank()) {
                throw InvalidServerConfiguration("Jellyfin access token cannot be blank")
            }
            if (userId.isBlank()) {
                throw InvalidServerConfiguration("Jellyfin user id cannot be blank")
            }
        }
    }

    private suspend fun persist(
        id: String?,
        type: ServerType,
        name: String,
        baseUrl: String,
        credential: StoredCredential,
        existingAtUrl: ServerRecord?,
    ): ServerRecord {
        val existingById = id?.let { store.get(it) }
        if (existingById != null && existingById.type != type) {
            throw InvalidServerConfiguration("Server ${existingById.id} is ${existingById.type.name}, not ${type.name}")
        }
        val now = clock.now()
        val record =
            toRecord(
                id = id ?: existingAtUrl?.id ?: idGenerator(),
                type = type,
                name = name,
                baseUrl = baseUrl,
                credential = credential,
                createdAt = existingById?.createdAt ?: existingAtUrl?.createdAt ?: now,
                updatedAt = now,
            )
        store.upsert(record)
        return record
    }

    private fun toRecord(
        id: String,
        type: ServerType,
        name: String,
        baseUrl: String,
        credential: StoredCredential,
        createdAt: Instant,
        updatedAt: Instant,
    ): ServerRecord =
        when (credential) {
            is StoredCredential.Jellyfin ->
                ServerRecord(
                    id = id,
                    type = type,
                    name = name.trim(),
                    baseUrl = baseUrl,
                    username = credential.username,
                    deviceId = credential.deviceId,
                    apiKey = null,
                    accessToken = credential.accessToken,
                    sessionCookie = null,
                    userId = credential.userId,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
            is StoredCredential.ApiKey ->
                ServerRecord(
                    id = id,
                    type = type,
                    name = name.trim(),
                    baseUrl = baseUrl,
                    username = null,
                    deviceId = null,
                    apiKey = credential.apiKey,
                    accessToken = null,
                    sessionCookie = credential.sessionCookie,
                    userId = credential.userId,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                )
        }

    private fun ServerRecord.toManagedServer(): ManagedServer {
        val storedCredential =
            when (type) {
                ServerType.JELLYFIN ->
                    StoredCredential.Jellyfin(
                        username = username ?: throw IllegalStateException("Missing username"),
                        deviceId = deviceId,
                        accessToken = accessToken ?: throw IllegalStateException("Missing access token"),
                        userId = userId ?: throw IllegalStateException("Missing user id"),
                    )
                ServerType.SONARR,
                ServerType.RADARR,
                ServerType.JELLYSEERR,
                -> {
                    val key = apiKey
                    val cookie = sessionCookie
                    if (key.isNullOrBlank() && cookie.isNullOrBlank()) {
                        throw IllegalStateException("Missing Seerr credentials")
                    }
                    StoredCredential.ApiKey(apiKey = key, userId = userId, sessionCookie = cookie)
                }
            }

        return ManagedServer(
            id = id,
            type = type,
            name = name,
            baseUrl = baseUrl,
            credentials = storedCredential,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

private fun StoredCredential.authenticatedPrincipal(): String? =
    when (this) {
        is StoredCredential.Jellyfin -> userId.trim().takeIf(String::isNotEmpty)
        is StoredCredential.ApiKey -> userId?.trim()?.takeIf(String::isNotEmpty)
    }
