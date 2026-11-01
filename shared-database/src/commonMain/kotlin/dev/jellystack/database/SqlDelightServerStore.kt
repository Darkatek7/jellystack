package dev.jellystack.database

import dev.jellystack.core.server.ServerRecord
import dev.jellystack.core.server.ServerStore
import dev.jellystack.core.server.ServerType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class SqlDelightServerStore(
    private val queries: ServersQueries,
    private val dispatcher: CoroutineDispatcher,
) : ServerStore {
    override suspend fun list(): List<ServerRecord> =
        withContext(dispatcher) {
            queries.selectAll().executeAsList().map { it.toRecord() }
        }

    override suspend fun findByTypeAndUrl(
        type: ServerType,
        baseUrl: String,
    ): ServerRecord? =
        withContext(dispatcher) {
            queries.selectByTypeAndUrl(type.name, baseUrl).executeAsOneOrNull()?.toRecord()
        }

    override suspend fun findByIdentity(
        type: ServerType,
        baseUrl: String,
        authenticatedPrincipal: String?,
    ): ServerRecord? =
        withContext(dispatcher) {
            queries
                .selectByIdentity(type.name, baseUrl, authenticatedPrincipal?.trim()?.takeIf(String::isNotEmpty))
                .executeAsOneOrNull()
                ?.toRecord()
        }

    override suspend fun get(id: String): ServerRecord? =
        withContext(dispatcher) {
            queries.selectById(id).executeAsOneOrNull()?.toRecord()
        }

    override suspend fun upsert(record: ServerRecord) {
        withContext(dispatcher) {
            queries.insertOrReplace(
                id = record.id,
                type = record.type.name,
                name = record.name,
                base_url = record.baseUrl,
                username = record.username,
                device_id = record.deviceId,
                api_key = record.apiKey,
                access_token = record.accessToken,
                session_cookie = record.sessionCookie,
                user_id = record.userId,
                created_at = record.createdAt.toEpochMilliseconds(),
                updated_at = record.updatedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) {
            queries.deleteById(id)
        }
    }
}

private fun Servers.toRecord(): ServerRecord =
    ServerRecord(
        id = id,
        type = ServerType.fromStored(type),
        name = name,
        baseUrl = base_url,
        username = username,
        deviceId = device_id,
        apiKey = api_key,
        accessToken = access_token,
        sessionCookie = session_cookie,
        userId = user_id,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )

fun JellystackDatabase.serverStore(dispatcher: DatabaseDispatcher): ServerStore = SqlDelightServerStore(serversQueries, dispatcher.value)
