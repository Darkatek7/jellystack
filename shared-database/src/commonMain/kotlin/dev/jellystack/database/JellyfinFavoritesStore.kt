package dev.jellystack.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Favourites cache. The suspending writes and [observe] run on [dispatcher]; [scoped] and [snapshot] are
 * synchronous by contract (the browse coordinator seeds its initial state with [snapshot] in its constructor) and
 * therefore read on the calling thread. Both are single small indexed reads.
 */
class JellyfinFavoritesStore private constructor(
    private val queries: JellyfinFavoritesQueries,
    private val dispatcher: CoroutineDispatcher,
    private val scopeId: String = LEGACY_SCOPE,
) : JellyfinFavoritesStoreApi {
    constructor(database: JellystackDatabase, dispatcher: CoroutineDispatcher) : this(database.jellyfinFavoritesQueries, dispatcher)

    override fun scoped(scopeId: String): JellyfinFavoritesStoreApi {
        require(scopeId.isNotBlank())
        promoteLegacyFavorites(scopeId)
        return JellyfinFavoritesStore(queries, dispatcher, scopeId)
    }

    private fun promoteLegacyFavorites(targetScopeId: String) {
        if (scopeId != LEGACY_SCOPE || targetScopeId == LEGACY_SCOPE) return
        val legacyIds = queries.selectAll(LEGACY_SCOPE).executeAsList()
        if (legacyIds.isEmpty()) return
        queries.transaction {
            val now = Clock.System.now().toEpochMilliseconds()
            legacyIds.forEach { id -> queries.upsert(targetScopeId, id, now) }
            queries.clear(LEGACY_SCOPE)
        }
    }

    override fun snapshot(): Set<String> = queries.selectAll(scopeId).executeAsList().toSet()

    override fun observe(): Flow<Set<String>> =
        queries
            .selectAll(scopeId)
            .asFlow()
            .mapToList(dispatcher)
            .map { it.toSet() }

    override suspend fun replaceAll(ids: Set<String>) =
        withContext(dispatcher) {
            val now = Clock.System.now().toEpochMilliseconds()
            queries.transaction {
                queries.clear(scopeId)
                ids.forEach { id -> queries.upsert(scopeId, id, now) }
            }
        }

    override suspend fun upsert(id: String) =
        withContext(dispatcher) {
            queries.upsert(scopeId, id, Clock.System.now().toEpochMilliseconds())
        }

    override suspend fun delete(id: String) =
        withContext(dispatcher) {
            queries.delete(scopeId, id)
        }

    private companion object {
        const val LEGACY_SCOPE = "legacy"
    }
}
