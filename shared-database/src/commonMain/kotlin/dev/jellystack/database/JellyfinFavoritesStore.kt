package dev.jellystack.database

import app.cash.sqldelight.coroutines.asFlow
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class JellyfinFavoritesStore private constructor(
    private val queries: JellyfinFavoritesQueries,
    private val scopeId: String = LEGACY_SCOPE,
) : JellyfinFavoritesStoreApi {
    constructor(database: JellystackDatabase) : this(database.jellyfinFavoritesQueries)

    override fun scoped(scopeId: String): JellyfinFavoritesStoreApi {
        require(scopeId.isNotBlank())
        promoteLegacyFavorites(scopeId)
        return JellyfinFavoritesStore(queries, scopeId)
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

    override fun observe(): Flow<Set<String>> = queries.selectAll(scopeId).asFlow().map { it.executeAsList().toSet() }

    override suspend fun replaceAll(ids: Set<String>) =
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.clear(scopeId)
                ids.forEach { id -> queries.upsert(scopeId, id, Clock.System.now().toEpochMilliseconds()) }
            }
        }

    override suspend fun upsert(id: String) =
        withContext(Dispatchers.Default) {
            queries.upsert(scopeId, id, Clock.System.now().toEpochMilliseconds())
        }

    override suspend fun delete(id: String) =
        withContext(Dispatchers.Default) {
            queries.delete(scopeId, id)
        }

    private companion object {
        const val LEGACY_SCOPE = "legacy"
    }
}
