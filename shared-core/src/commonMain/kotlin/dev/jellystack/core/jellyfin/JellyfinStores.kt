package dev.jellystack.core.jellyfin

import dev.jellystack.core.profile.MediaProviderIds
import kotlinx.datetime.Instant

data class JellyfinLibraryRecord(
    val id: String,
    val serverId: String,
    val name: String,
    val collectionType: String?,
    val primaryImageTag: String?,
    val itemCount: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class JellyfinItemRecord(
    val id: String,
    val serverId: String,
    val libraryId: String?,
    val name: String,
    val sortName: String?,
    val overview: String?,
    val type: String,
    val mediaType: String?,
    val locationType: String?,
    val taglines: List<String>,
    val parentId: String?,
    val primaryImageTag: String?,
    val thumbImageTag: String?,
    val backdropImageTag: String?,
    val seriesId: String?,
    val seriesPrimaryImageTag: String?,
    val seriesThumbImageTag: String?,
    val seriesBackdropImageTag: String?,
    val parentLogoImageTag: String?,
    val runTimeTicks: Long?,
    val positionTicks: Long?,
    val playedPercentage: Double?,
    val productionYear: Long?,
    val premiereDate: String?,
    val communityRating: Double?,
    val officialRating: String?,
    val indexNumber: Long?,
    val parentIndexNumber: Long?,
    val seriesName: String?,
    val seasonId: String?,
    val episodeTitle: String?,
    val lastPlayed: String?,
    val updatedAt: Instant,
    val dateCreated: String? = null,
    val logoImageTag: String? = null,
    val artImageTag: String? = null,
    val bannerImageTag: String? = null,
    val seriesLogoImageTag: String? = null,
    val seriesArtImageTag: String? = null,
    val seriesBannerImageTag: String? = null,
    val providerIds: MediaProviderIds = MediaProviderIds(sourceLocalId = id),
)

data class JellyfinItemDetailRecord(
    val itemId: String,
    val json: String,
    val updatedAt: Instant,
)

interface JellyfinLibraryStore {
    suspend fun replaceAll(
        serverId: String,
        libraries: List<JellyfinLibraryRecord>,
    )

    suspend fun list(serverId: String): List<JellyfinLibraryRecord>
}

interface JellyfinItemStore {
    suspend fun replaceForLibrary(
        serverId: String,
        libraryId: String,
        items: List<JellyfinItemRecord>,
    )

    suspend fun upsert(items: List<JellyfinItemRecord>)

    suspend fun listByLibrary(
        serverId: String,
        libraryId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord>

    suspend fun replaceForParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        items: List<JellyfinItemRecord>,
    ) {
        upsert(items)
    }

    suspend fun listByParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord> =
        listByLibrary(serverId, libraryId, Int.MAX_VALUE.toLong(), 0)
            .asSequence()
            .filter { it.parentId == parentId }
            .drop(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .take(limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .toList()

    suspend fun listRecentShows(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord>

    suspend fun listRecentMovies(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord>

    suspend fun listContinueWatching(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord>

    suspend fun replaceNextUp(
        serverId: String,
        itemIds: List<String>,
        updatedAt: Instant,
    )

    suspend fun listNextUp(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord>

    suspend fun clearContinueWatching(
        serverId: String,
        keepIds: Set<String>,
    )

    suspend fun listEpisodesForSeries(
        serverId: String,
        seriesId: String,
    ): List<JellyfinItemRecord>

    suspend fun listEpisodesForSeason(
        serverId: String,
        seasonId: String,
    ): List<JellyfinItemRecord>

    suspend fun get(itemId: String): JellyfinItemRecord?
}

interface JellyfinItemDetailStore {
    suspend fun get(itemId: String): JellyfinItemDetailRecord?

    suspend fun upsert(record: JellyfinItemDetailRecord)
}
