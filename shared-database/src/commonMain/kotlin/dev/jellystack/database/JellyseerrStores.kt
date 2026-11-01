package dev.jellystack.database

import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRecord
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class SqlDelightJellyseerrRecommendationStore(
    private val queries: JellyseerrRecommendationsQueries,
    private val dispatcher: CoroutineDispatcher,
) : JellyseerrRecommendationStore {
    override suspend fun list(
        serverId: String,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationRecord> =
        withContext(dispatcher) {
            queries
                .selectByServerAndRail(serverId, rail.name)
                .executeAsList()
                .map { row ->
                    JellyseerrRecommendationRecord(
                        serverId = row.server_id,
                        rail = JellyseerrRecommendationRail.valueOf(row.rail),
                        page = row.page.toInt(),
                        json = row.items_json,
                        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
                    )
                }
        }

    override suspend fun upsert(record: JellyseerrRecommendationRecord) {
        withContext(dispatcher) {
            queries.insertOrReplace(
                server_id = record.serverId,
                rail = record.rail.name,
                page = record.page.toLong(),
                items_json = record.json,
                updated_at = record.updatedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun clear(
        serverId: String,
        rail: JellyseerrRecommendationRail,
    ) {
        withContext(dispatcher) {
            queries.deleteByServerAndRail(serverId, rail.name)
        }
    }

    override suspend fun clearAfter(
        serverId: String,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ) {
        withContext(dispatcher) {
            queries.deleteAfterPage(serverId, rail.name, page.toLong())
        }
    }

    override suspend fun clearServer(serverId: String) {
        withContext(dispatcher) {
            queries.deleteByServer(serverId)
        }
    }
}

fun JellystackDatabase.jellyseerrRecommendationStore(dispatcher: DatabaseDispatcher): JellyseerrRecommendationStore =
    SqlDelightJellyseerrRecommendationStore(jellyseerrRecommendationsQueries, dispatcher.value)
