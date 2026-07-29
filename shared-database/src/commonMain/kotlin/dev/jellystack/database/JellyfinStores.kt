package dev.jellystack.database

import dev.jellystack.core.jellyfin.JellyfinItemDetailRecord
import dev.jellystack.core.jellyfin.JellyfinItemDetailStore
import dev.jellystack.core.jellyfin.JellyfinItemRecord
import dev.jellystack.core.jellyfin.JellyfinItemStore
import dev.jellystack.core.jellyfin.JellyfinLibraryRecord
import dev.jellystack.core.jellyfin.JellyfinLibraryStore
import kotlinx.datetime.Instant

class SqlDelightJellyfinLibraryStore(
    private val queries: JellyfinLibrariesQueries,
) : JellyfinLibraryStore {
    override suspend fun replaceAll(
        serverId: String,
        libraries: List<JellyfinLibraryRecord>,
    ) {
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

    override suspend fun list(serverId: String): List<JellyfinLibraryRecord> =
        queries.selectAllByServer(serverId).executeAsList().map { it.toRecord() }
}

class SqlDelightJellyfinItemStore(
    private val queries: JellyfinItemsQueries,
) : JellyfinItemStore {
    override suspend fun replaceForLibrary(
        serverId: String,
        libraryId: String,
        items: List<JellyfinItemRecord>,
    ) {
        queries.deleteByServerAndLibrary(serverId, libraryId)
        upsert(items)
    }

    override suspend fun upsert(items: List<JellyfinItemRecord>) {
        items.forEach { record -> insert(record) }
    }

    override suspend fun listByLibrary(
        serverId: String,
        libraryId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord> = queries.selectByLibrary(serverId, libraryId, limit, offset).executeAsList().map { it.toRecord() }

    override suspend fun replaceForParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        items: List<JellyfinItemRecord>,
    ) {
        queries.deleteByServerLibraryAndParent(serverId, libraryId, parentId)
        upsert(items)
    }

    override suspend fun listByParent(
        serverId: String,
        libraryId: String,
        parentId: String,
        limit: Long,
        offset: Long,
    ): List<JellyfinItemRecord> = queries.selectByParent(serverId, libraryId, parentId, limit, offset).executeAsList().map { it.toRecord() }

    override suspend fun listRecentShows(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun listRecentMovies(
        serverId: String,
        libraryId: String?,
        limit: Long,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun listContinueWatching(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun replaceNextUp(
        serverId: String,
        itemIds: List<String>,
        updatedAt: Instant,
    ) {
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

    override suspend fun listNextUp(
        serverId: String,
        limit: Long,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun clearContinueWatching(
        serverId: String,
        keepIds: Set<String>,
    ) {
        if (keepIds.isEmpty()) {
            queries.clearAllContinueWatching(serverId)
        } else {
            queries.clearContinueWatching(serverId, keepIds)
        }
    }

    override suspend fun listEpisodesForSeries(
        serverId: String,
        seriesId: String,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun listEpisodesForSeason(
        serverId: String,
        seasonId: String,
    ): List<JellyfinItemRecord> =
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
                )
            }.executeAsList()

    override suspend fun get(itemId: String): JellyfinItemRecord? = queries.selectById(itemId).executeAsOneOrNull()?.toRecord()

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
        )
    }
}

class SqlDelightJellyfinItemDetailStore(
    private val queries: JellyfinItemDetailsQueries,
) : JellyfinItemDetailStore {
    override suspend fun get(itemId: String): JellyfinItemDetailRecord? = queries.selectByItem(itemId).executeAsOneOrNull()?.toRecord()

    override suspend fun upsert(record: JellyfinItemDetailRecord) {
        queries.insertOrReplace(
            item_id = record.itemId,
            json = record.json,
            updated_at = record.updatedAt.toEpochMilliseconds(),
        )
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
    )

private fun Jellyfin_item_details.toRecord(): JellyfinItemDetailRecord =
    JellyfinItemDetailRecord(
        itemId = item_id,
        json = json,
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )

fun JellystackDatabase.jellyfinLibraryStore(): JellyfinLibraryStore = SqlDelightJellyfinLibraryStore(jellyfinLibrariesQueries)

fun JellystackDatabase.jellyfinItemStore(): JellyfinItemStore = SqlDelightJellyfinItemStore(jellyfinItemsQueries)

fun JellystackDatabase.jellyfinItemDetailStore(): JellyfinItemDetailStore = SqlDelightJellyfinItemDetailStore(jellyfinItemDetailsQueries)
