package dev.jellystack.core.server

import kotlinx.datetime.Instant

data class ServerRecord(
    val id: String,
    val type: ServerType,
    val name: String,
    val baseUrl: String,
    val username: String?,
    val deviceId: String?,
    val apiKey: String?,
    val accessToken: String?,
    val sessionCookie: String?,
    val userId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

interface ServerStore {
    suspend fun list(): List<ServerRecord>

    suspend fun findByTypeAndUrl(
        type: ServerType,
        baseUrl: String,
    ): ServerRecord?

    suspend fun get(id: String): ServerRecord?

    suspend fun upsert(record: ServerRecord)

    suspend fun delete(id: String)
}
