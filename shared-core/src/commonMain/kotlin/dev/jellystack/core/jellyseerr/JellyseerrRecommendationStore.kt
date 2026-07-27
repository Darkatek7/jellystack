package dev.jellystack.core.jellyseerr

import kotlinx.datetime.Instant

data class JellyseerrRecommendationRecord(
    val serverId: String,
    val rail: JellyseerrRecommendationRail,
    val page: Int,
    val json: String,
    val updatedAt: Instant,
)

interface JellyseerrRecommendationStore {
    suspend fun list(
        serverId: String,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationRecord>

    suspend fun upsert(record: JellyseerrRecommendationRecord)

    suspend fun clear(
        serverId: String,
        rail: JellyseerrRecommendationRail,
    )

    suspend fun clearAfter(
        serverId: String,
        rail: JellyseerrRecommendationRail,
        page: Int,
    )

    suspend fun clearServer(serverId: String)
}
