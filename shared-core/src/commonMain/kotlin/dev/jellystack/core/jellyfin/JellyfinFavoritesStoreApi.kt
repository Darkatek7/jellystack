package dev.jellystack.core.jellyfin

import kotlinx.coroutines.flow.Flow

/**
 * Minimal contract for a favourites cache that the coordinator depends on. Lives in [shared-core] so the
 * production store (in `shared-database`) can implement it without dragging a SQLDelight dependency into
 * the core module — and unit tests can substitute an in-memory fake without a real database.
 */
interface JellyfinFavoritesStoreApi {
    fun snapshot(): Set<String>

    fun observe(): Flow<Set<String>>

    suspend fun replaceAll(ids: Set<String>)

    suspend fun upsert(id: String)

    suspend fun delete(id: String)
}
