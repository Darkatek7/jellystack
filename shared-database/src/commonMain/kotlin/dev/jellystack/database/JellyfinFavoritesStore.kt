package dev.jellystack.database

import app.cash.sqldelight.coroutines.asFlow
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class JellyfinFavoritesStore(
    database: JellystackDatabase,
) : JellyfinFavoritesStoreApi {
    private val queries = database.jellyfinFavoritesQueries

    override fun snapshot(): Set<String> = queries.selectAll().executeAsList().toSet()

    override fun observe(): Flow<Set<String>> = queries.selectAll().asFlow().map { it.executeAsList().toSet() }

    override suspend fun replaceAll(ids: Set<String>) =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.clear()
                ids.forEach { id -> queries.upsert(id, Clock.System.now().toEpochMilliseconds()) }
            }
        }

    override suspend fun upsert(id: String) =
        withContext(Dispatchers.IO) {
            queries.upsert(id, Clock.System.now().toEpochMilliseconds())
        }

    override suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            queries.delete(id)
        }
}
