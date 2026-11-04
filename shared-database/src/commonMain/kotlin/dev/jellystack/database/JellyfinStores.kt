package dev.jellystack.database

import dev.jellystack.core.jellyfin.JellyfinItemDetailRecord
import dev.jellystack.core.jellyfin.JellyfinItemDetailStore
import dev.jellystack.core.jellyfin.JellyfinItemRecord
import dev.jellystack.core.jellyfin.JellyfinItemStore
import dev.jellystack.core.jellyfin.JellyfinLibraryRecord
import dev.jellystack.core.jellyfin.JellyfinLibraryStore
import dev.jellystack.core.profile.MediaIdentity
import dev.jellystack.core.profile.MediaIdentityProvider
import dev.jellystack.core.profile.MediaProviderIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class SqlDelightJellyfinLibraryStore(
    private val queries: JellyfinLibrariesQueries,
    private val dispatcher: CoroutineDispatcher,
) : JellyfinLibraryStore {
    override suspend fun replaceAll(
        serverId: String,
        libraries: List<JellyfinLibraryRecord>,
    ) {
        withContext(dispatcher) {
            queries.transaction {
                queries.deleteByServer(serverId)
                libraries.forEach { record ->
                    queries.insertOrReplace(
                        id = record.id,
                        server_id = record.serverId,
                        name = record.name,
                        collection_type = record.collectionType,
                        primary_image_tag = record.primaryImageTag,
                        item_count = record.itemCount,
                        created_at = record.createdAt.toEpochMilliseconds(),
                        updated_at = record.updatedAt.toEpochMilliseconds(),
                    )
                }
            }
        }
    }

    override suspend fun list(serverId: String): List<JellyfinLibraryRecord> =
        withContext(dispatcher) {
            queries.selectAllByServer(serverId).executeAsList().map { it.toRecord() }
        }
}

class SqlDelightJellyfinItemStore(
    private val queries: JellyfinItemsQueries,
    private val dispatcher: CoroutineDispatcher,
) : JellyfinItemStore {
    override suspend fun replaceForLibrary(
        serverId: String,
        libraryId: String,
        items: List<JellyfinItemRecord>,
    ) {
        withContext(dispatcher) {
            queries.transaction {
                queries.deleteByServerAndLibrary(serverId, libraryId)
                items.forEach(::insert)
            }
        }
    }

    override suspend fun upsert(items: List<JellyfinItemRecord>) {
        withContext(dispatcher) {
            queries.transaction { items.forEach(::insert) }
        }
    }

    override suspend fun listByLibrary(
        serverId: String,
        libraryId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries.selectByLibrary(serverId, libraryId, limit, offset).executeAsList().map { it.toRecord() }
        }

    override suspend fun replaceForParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        items: List<JellyfinItemRecord>,
    ) {
        withContext(dispatcher) {
            queries.transaction {
                queries.deleteByServerLibraryAndParent(serverId, libraryId, parentId)
                items.forEach(::insert)
            }
        }
    }

    override suspend fun listByParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries.selectByParent(serverId, libraryId, parentId, limit, offset).executeAsList().map { it.toRecord() }
        }

    override suspend fun listRecentShows(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectRecentShows(serverId, libraryId, limit) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun listRecentMovies(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectRecentMovies(serverId, libraryId, limit) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun listContinueWatching(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectContinueWatching(serverId, limit) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun replaceNextUp(
        serverId: String,
        itemIds: List<String>,
        updatedAt: Instant,
    ) {
        withContext(dispatcher) {
            queries.transaction {
                queries.deleteNextUpByServer(serverId)
                itemIds.forEachIndexed { index, itemId ->
                    queries.insertNextUp(
                        server_id = serverId,
                        item_id = itemId,
                        sort_order = index.toLong(),
                        updated_at = updatedAt.toEpochMilliseconds(),
                    )
                }
            }
        }
    }

    override suspend fun listNextUp(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectNextUp(serverId, limit) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun clearContinueWatching(
        serverId: String,
        keepIds: Set<String>,
    ) {
        withContext(dispatcher) {
            if (keepIds.isEmpty()) {
                queries.clearAllContinueWatching(serverId)
            } else {
                queries.clearContinueWatching(serverId, keepIds)
            }
        }
    }

    override suspend fun listEpisodesForSeries(
        serverId: String,
        seriesId: String,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectEpisodesForSeries(serverId, seriesId) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun listEpisodesForSeason(
        serverId: String,
        seasonId: String,
    ): List<JellyfinItemRecord> =
        withContext(dispatcher) {
            queries
                .selectEpisodesForSeason(serverId, seasonId) {
                        id,
                        server_id,
                        library_id,
                        name,
                        sort_name,
                        overview,
                        type,
                        media_type,
                        location_type,
                        tmdb_id,
                        tvdb_id,
                        taglines,
                        parent_id,
                        primary_image_tag,
                        thumb_image_tag,
                        backdrop_image_tag,
                        series_id,
                        series_primary_image_tag,
                        series_thumb_image_tag,
                        series_backdrop_image_tag,
                        parent_logo_image_tag,
                        run_time_ticks,
                        position_ticks,
                        played_percentage,
                        production_year,
                        premiere_date,
                        community_rating,
                        official_rating,
                        index_number,
                        parent_index_number,
                        series_name,
                        season_id,
                        episode_title,
                        last_played,
                        date_created,
                        updated_at,
                        played,
                        unplayed_count,
                    ->
                    mapItemRecord(
                        id = id,
                        serverId = server_id,
                        libraryId = library_id,
                        name = name,
                        sortName = sort_name,
                        overview = overview,
                        type = type,
                        mediaType = media_type,
                        locationType = location_type,
                        tmdbId = tmdb_id,
                        tvdbId = tvdb_id,
                        taglines = taglines,
                        parentId = parent_id,
                        primaryImageTag = primary_image_tag,
                        thumbImageTag = thumb_image_tag,
                        backdropImageTag = backdrop_image_tag,
                        seriesId = series_id,
                        seriesPrimaryImageTag = series_primary_image_tag,
                        seriesThumbImageTag = series_thumb_image_tag,
                        seriesBackdropImageTag = series_backdrop_image_tag,
                        parentLogoImageTag = parent_logo_image_tag,
                        runTimeTicks = run_time_ticks,
                        positionTicks = position_ticks,
                        playedPercentage = played_percentage,
                        productionYear = production_year,
                        premiereDate = premiere_date,
                        communityRating = community_rating,
                        officialRating = official_rating,
                        indexNumber = index_number,
                        parentIndexNumber = parent_index_number,
                        seriesName = series_name,
                        seasonId = season_id,
                        episodeTitle = episode_title,
                        lastPlayed = last_played,
                        dateCreated = date_created,
                        updatedAt = updated_at,
                        played = played,
                        unplayedCount = unplayed_count,
                    )
                }.executeAsList()
        }

    override suspend fun get(itemId: String): JellyfinItemRecord? =
        withContext(dispatcher) {
            queries.selectById(itemId).executeAsOneOrNull()?.toRecord()
        }

    override suspend fun findByProviderIdentity(
        serverId: String,
        identity: MediaIdentity,
    ): JellyfinItemRecord? =
        withContext(dispatcher) {
            val jellyfinType = if (identity.mediaType == "tv") "series" else identity.mediaType
            val row =
                when (identity.provider) {
                    MediaIdentityProvider.TMDB ->
                        queries.selectByTmdbId(serverId, jellyfinType, identity.providerId).executeAsOneOrNull()
                    MediaIdentityProvider.TVDB ->
                        queries.selectByTvdbId(serverId, jellyfinType, identity.providerId).executeAsOneOrNull()
                    MediaIdentityProvider.SOURCE_LOCAL -> queries.selectById(identity.providerId).executeAsOneOrNull()
                }
            row
                ?.takeIf { it.server_id == serverId && it.type.lowercase() == jellyfinType }
                ?.toRecord()
        }

    private fun insert(record: JellyfinItemRecord) {
        queries.insertOrReplace(
            id = record.id,
            server_id = record.serverId,
            library_id = record.libraryId,
            name = record.name,
            sort_name = record.sortName,
            overview = record.overview,
            type = record.type,
            media_type = record.mediaType,
            location_type = record.locationType,
            tmdb_id = record.providerIds.tmdbId,
            tvdb_id = record.providerIds.tvdbId,
            taglines = record.taglines.takeIf(List<String>::isNotEmpty)?.joinToString("\n"),
            parent_id = record.parentId,
            primary_image_tag = record.primaryImageTag,
            thumb_image_tag = record.thumbImageTag,
            backdrop_image_tag = record.backdropImageTag,
            series_id = record.seriesId,
            series_primary_image_tag = record.seriesPrimaryImageTag,
            series_thumb_image_tag = record.seriesThumbImageTag,
            series_backdrop_image_tag = record.seriesBackdropImageTag,
            parent_logo_image_tag = record.parentLogoImageTag,
            run_time_ticks = record.runTimeTicks,
            position_ticks = record.positionTicks,
            played_percentage = record.playedPercentage,
            production_year = record.productionYear,
            premiere_date = record.premiereDate,
            community_rating = record.communityRating,
            official_rating = record.officialRating,
            index_number = record.indexNumber,
            parent_index_number = record.parentIndexNumber,
            series_name = record.seriesName,
            season_id = record.seasonId,
            episode_title = record.episodeTitle,
            last_played = record.lastPlayed,
            date_created = record.dateCreated,
            updated_at = record.updatedAt.toEpochMilliseconds(),
            played = if (record.played) 1L else 0L,
            unplayed_count = record.unplayedCount?.toLong(),
        )
    }
}

class SqlDelightJellyfinItemDetailStore(
    private val queries: JellyfinItemDetailsQueries,
    private val dispatcher: CoroutineDispatcher,
) : JellyfinItemDetailStore {
    override suspend fun get(itemId: String): JellyfinItemDetailRecord? =
        withContext(dispatcher) {
            queries.selectByItem(itemId).executeAsOneOrNull()?.toRecord()
        }

    override suspend fun exists(itemId: String): Boolean =
        withContext(dispatcher) {
            queries.existsByItem(itemId).executeAsOne()
        }

    override suspend fun upsert(record: JellyfinItemDetailRecord) {
        withContext(dispatcher) {
            queries.insertOrReplace(
                item_id = record.itemId,
                json = record.json,
                updated_at = record.updatedAt.toEpochMilliseconds(),
            )
        }
    }
}

private fun Jellyfin_libraries.toRecord(): JellyfinLibraryRecord =
    JellyfinLibraryRecord(
        id = id,
        serverId = server_id,
        name = name,
        collectionType = collection_type,
        primaryImageTag = primary_image_tag,
        itemCount = item_count,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )

private fun mapItemRecord(
    id: String,
    serverId: String,
    libraryId: String?,
    name: String,
    sortName: String?,
    overview: String?,
    type: String,
    mediaType: String?,
    locationType: String?,
    tmdbId: String?,
    tvdbId: String?,
    taglines: String?,
    parentId: String?,
    primaryImageTag: String?,
    thumbImageTag: String?,
    backdropImageTag: String?,
    seriesId: String?,
    seriesPrimaryImageTag: String?,
    seriesThumbImageTag: String?,
    seriesBackdropImageTag: String?,
    parentLogoImageTag: String?,
    runTimeTicks: Long?,
    positionTicks: Long?,
    playedPercentage: Double?,
    productionYear: Long?,
    premiereDate: String?,
    communityRating: Double?,
    officialRating: String?,
    indexNumber: Long?,
    parentIndexNumber: Long?,
    seriesName: String?,
    seasonId: String?,
    episodeTitle: String?,
    lastPlayed: String?,
    dateCreated: String?,
    updatedAt: Long,
    played: Long,
    unplayedCount: Long?,
): JellyfinItemRecord =
    JellyfinItemRecord(
        id = id,
        serverId = serverId,
        libraryId = libraryId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        locationType = locationType,
        providerIds =
            MediaProviderIds(
                tmdbId = tmdbId,
                tvdbId = tvdbId,
                sourceLocalId = id,
            ).normalized(),
        taglines = taglines?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        parentId = parentId,
        primaryImageTag = primaryImageTag,
        thumbImageTag = thumbImageTag,
        backdropImageTag = backdropImageTag,
        seriesId = seriesId,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag,
        parentLogoImageTag = parentLogoImageTag,
        runTimeTicks = runTimeTicks,
        positionTicks = positionTicks,
        playedPercentage = playedPercentage,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber,
        parentIndexNumber = parentIndexNumber,
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = lastPlayed,
        dateCreated = dateCreated,
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        played = played != 0L,
        unplayedCount = unplayedCount?.toInt(),
    )

private fun Jellyfin_items.toRecord(): JellyfinItemRecord =
    mapItemRecord(
        id = id,
        serverId = server_id,
        libraryId = library_id,
        name = name,
        sortName = sort_name,
        overview = overview,
        type = type,
        mediaType = media_type,
        locationType = location_type,
        tmdbId = tmdb_id,
        tvdbId = tvdb_id,
        taglines = taglines,
        parentId = parent_id,
        primaryImageTag = primary_image_tag,
        thumbImageTag = thumb_image_tag,
        backdropImageTag = backdrop_image_tag,
        seriesId = series_id,
        seriesPrimaryImageTag = series_primary_image_tag,
        seriesThumbImageTag = series_thumb_image_tag,
        seriesBackdropImageTag = series_backdrop_image_tag,
        parentLogoImageTag = parent_logo_image_tag,
        runTimeTicks = run_time_ticks,
        positionTicks = position_ticks,
        playedPercentage = played_percentage,
        productionYear = production_year,
        premiereDate = premiere_date,
        communityRating = community_rating,
        officialRating = official_rating,
        indexNumber = index_number,
        parentIndexNumber = parent_index_number,
        seriesName = series_name,
        seasonId = season_id,
        episodeTitle = episode_title,
        lastPlayed = last_played,
        dateCreated = date_created,
        updatedAt = updated_at,
        played = played,
        unplayedCount = unplayed_count,
    )

private fun SelectContinueWatching.toRecord(): JellyfinItemRecord =
    mapItemRecord(
        id = id,
        serverId = server_id,
        libraryId = library_id,
        name = name,
        sortName = sort_name,
        overview = overview,
        type = type,
        mediaType = media_type,
        locationType = location_type,
        tmdbId = tmdb_id,
        tvdbId = tvdb_id,
        taglines = taglines,
        parentId = parent_id,
        primaryImageTag = primary_image_tag,
        thumbImageTag = thumb_image_tag,
        backdropImageTag = backdrop_image_tag,
        seriesId = series_id,
        seriesPrimaryImageTag = series_primary_image_tag,
        seriesThumbImageTag = series_thumb_image_tag,
        seriesBackdropImageTag = series_backdrop_image_tag,
        parentLogoImageTag = parent_logo_image_tag,
        runTimeTicks = run_time_ticks,
        positionTicks = position_ticks,
        playedPercentage = played_percentage,
        productionYear = production_year,
        premiereDate = premiere_date,
        communityRating = community_rating,
        officialRating = official_rating,
        indexNumber = index_number,
        parentIndexNumber = parent_index_number,
        seriesName = series_name,
        seasonId = season_id,
        episodeTitle = episode_title,
        lastPlayed = last_played,
        dateCreated = date_created,
        updatedAt = updated_at,
        played = played,
        unplayedCount = unplayed_count,
    )

private fun Jellyfin_item_details.toRecord(): JellyfinItemDetailRecord =
    JellyfinItemDetailRecord(
        itemId = item_id,
        json = json,
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )

fun JellystackDatabase.jellyfinLibraryStore(dispatcher: DatabaseDispatcher): JellyfinLibraryStore =
    SqlDelightJellyfinLibraryStore(jellyfinLibrariesQueries, dispatcher.value)

fun JellystackDatabase.jellyfinItemStore(dispatcher: DatabaseDispatcher): JellyfinItemStore =
    SqlDelightJellyfinItemStore(jellyfinItemsQueries, dispatcher.value)

fun JellystackDatabase.jellyfinItemDetailStore(dispatcher: DatabaseDispatcher): JellyfinItemDetailStore =
    SqlDelightJellyfinItemDetailStore(jellyfinItemDetailsQueries, dispatcher.value)
