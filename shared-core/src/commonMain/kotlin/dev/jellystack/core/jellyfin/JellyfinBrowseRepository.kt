package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.AUDIO
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.OTHER
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.SUBTITLE
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType.VIDEO
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.playback.OfflinePlaybackProgressReporter
import dev.jellystack.core.playback.StreamingPlayStrategy
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.network.NetworkJson
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import dev.jellystack.network.jellyfin.JellyfinItemDetailDto
import dev.jellystack.network.jellyfin.JellyfinItemDto
import dev.jellystack.network.jellyfin.JellyfinItemsResponse
import dev.jellystack.network.jellyfin.JellyfinLibraryDto
import dev.jellystack.network.jellyfin.JellyfinMediaSourceDto
import dev.jellystack.network.jellyfin.JellyfinMediaStreamDto
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

typealias JellyfinBrowseApiFactory = (JellyfinEnvironment) -> JellyfinBrowseApi

private const val FILTER_ERROR_PHRASE = "No media found with the specified filter"

data class LibraryPage(
    val items: List<JellyfinItem>,
    val totalRecordCount: Long?,
)

internal data class LibraryQuery(
    val includeItemTypes: String?,
    val recursive: Boolean,
)

internal fun libraryQueryForCollectionType(collectionType: String?): LibraryQuery =
    when (collectionType?.lowercase()) {
        "tvshows", "series" -> LibraryQuery("Series", recursive = true)
        "movies" -> LibraryQuery("Movie", recursive = true)
        "music" -> LibraryQuery("MusicArtist,MusicAlbum,Audio", recursive = false)
        "boxsets", "collections" -> LibraryQuery("BoxSet", recursive = false)
        "musicvideos" -> LibraryQuery("MusicVideo", recursive = false)
        "photos", "homevideos", "photosandhomevideos" ->
            LibraryQuery("Folder,PhotoAlbum,Photo,Video", recursive = false)
        "books" -> LibraryQuery("Folder,Book,AudioBook,Audio", recursive = false)
        "playlists" -> LibraryQuery("Playlist", recursive = false)
        else -> LibraryQuery(includeItemTypes = null, recursive = false)
    }

class JellyfinBrowseRepository(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val libraryStore: JellyfinLibraryStore,
    private val itemStore: JellyfinItemStore,
    private val detailStore: JellyfinItemDetailStore,
    private val apiFactory: JellyfinBrowseApiFactory,
    private val clock: Clock = Clock.System,
) : OfflinePlaybackProgressReporter {
    private val cachedApis = mutableMapOf<String, JellyfinBrowseApi>()

    suspend fun refreshLibraries(): List<JellyfinLibrary> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val response = api.fetchLibraries(environment.userId)
        val now = clock.now()
        val records = response.items.map { it.toRecord(environment, now) }
        libraryStore.replaceAll(environment.serverKey, records)
        return records.map { it.toDomain() }
    }

    suspend fun listLibraries(): List<JellyfinLibrary> {
        val environment = environmentProvider.current() ?: return emptyList()
        return libraryStore.list(environment.serverKey).map { it.toDomain() }
    }

    suspend fun cachedContinueWatching(limit: Int): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listContinueWatching(environment.serverKey, limit.toLong()).map { it.toDomain() }
    }

    suspend fun cachedNextUp(limit: Int): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listNextUp(environment.serverKey, limit.toLong()).map { it.toDomain() }
    }

    suspend fun cachedRecentShows(
        libraryId: String?,
        limit: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listRecentShows(environment.serverKey, libraryId, limit.toLong()).map { it.toDomain() }
    }

    suspend fun cachedRecentMovies(
        libraryId: String?,
        limit: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listRecentMovies(environment.serverKey, libraryId, limit.toLong()).map { it.toDomain() }
    }

    suspend fun loadLibraryPage(
        libraryId: String,
        page: Int,
        pageSize: Int,
        refresh: Boolean = page == 0,
        filters: String? = null,
    ): LibraryPage {
        val environment = environmentProvider.current() ?: return LibraryPage(emptyList(), null)
        val api = apiFor(environment)
        val startIndex = page * pageSize
        val now = clock.now()
        // When a filters value (e.g. IsFavorite) is supplied, scope the API query to no ParentId so
        // the server returns results across all libraries. The local storage key still uses the
        // caller's libraryId so existing replace/list paths keep working.
        val apiLibraryId = if (filters != null) "" else libraryId
        val response = fetchLibraryItemsWithFallback(api, environment, apiLibraryId, startIndex, pageSize, filters)
        val records = response.items.map { it.toRecord(environment, libraryId, now) }
        val query = queryForLibrary(environment.serverKey, libraryId)
        if (refresh) {
            if (!query.recursive && filters == null) {
                itemStore.replaceForParent(environment.serverKey, libraryId, libraryId, records)
            } else {
                itemStore.replaceForLibrary(environment.serverKey, libraryId, records)
            }
        } else {
            itemStore.upsert(records)
        }
        val items =
            if (!query.recursive && filters == null) {
                itemStore.listByParent(
                    environment.serverKey,
                    libraryId,
                    libraryId,
                    pageSize.toLong(),
                    startIndex.toLong(),
                )
            } else {
                itemStore.listByLibrary(
                    environment.serverKey,
                    libraryId,
                    limit = pageSize.toLong(),
                    offset = startIndex.toLong(),
                )
            }.map { it.toDomain() }
        return LibraryPage(items = items, totalRecordCount = response.totalRecordCount)
    }

    private suspend fun fetchLibraryItemsWithFallback(
        api: JellyfinBrowseApi,
        environment: JellyfinEnvironment,
        libraryId: String,
        startIndex: Int,
        pageSize: Int,
        filters: String? = null,
    ): JellyfinItemsResponse {
        val query =
            if (filters != null) {
                LibraryQuery(includeItemTypes = null, recursive = true)
            } else {
                queryForLibrary(environment.serverKey, libraryId)
            }
        val includeItemTypes = query.includeItemTypes
        val primaryResponse =
            runCatching {
                api.fetchLibraryItems(
                    userId = environment.userId,
                    libraryId = libraryId,
                    startIndex = startIndex,
                    limit = pageSize,
                    includeItemTypes = includeItemTypes,
                    recursive = query.recursive,
                    filters = filters,
                )
            }.getOrElse { primary ->
                if (shouldRetryWithoutLibraryFilter(primary)) {
                    JellystackLog.w(
                        "Library fetch failed with includeItemTypes=$includeItemTypes on server=${environment.serverKey}; retrying without type filter",
                        primary,
                    )
                    return api.fetchLibraryItems(
                        environment.userId,
                        libraryId,
                        startIndex,
                        pageSize,
                        includeItemTypes = JellyfinBrowseApi.DEFAULT_INCLUDE_ITEM_TYPES,
                        recursive = query.recursive,
                        filters = filters,
                    )
                }
                throw primary
            }
        if (
            includeItemTypes == null ||
            includeItemTypes == JellyfinBrowseApi.DEFAULT_INCLUDE_ITEM_TYPES ||
            primaryResponse.items.isNotEmpty()
        ) {
            return primaryResponse
        }
        // Some servers return a non-item error payload without throwing, which decodes as an empty response.
        if (primaryResponse.totalRecordCount == null) {
            JellystackLog.w(
                "Library fetch returned empty payload with includeItemTypes=$includeItemTypes on server=${environment.serverKey}; retrying without type filter",
            )
            return api.fetchLibraryItems(
                environment.userId,
                libraryId,
                startIndex,
                pageSize,
                includeItemTypes = JellyfinBrowseApi.DEFAULT_INCLUDE_ITEM_TYPES,
                recursive = query.recursive,
                filters = filters,
            )
        }
        return primaryResponse
    }

    private suspend fun shouldRetryWithoutLibraryFilter(error: Throwable): Boolean {
        if (error !is ResponseException) return false
        if (error.message?.contains(FILTER_ERROR_PHRASE, ignoreCase = true) == true) {
            return true
        }
        val responseBody = runCatching { error.response.bodyAsText() }.getOrNull()
        return responseBody?.contains(FILTER_ERROR_PHRASE, ignoreCase = true) == true
    }

    private suspend fun queryForLibrary(
        serverId: String,
        libraryId: String,
    ): LibraryQuery {
        val collectionType =
            libraryStore
                .list(serverId)
                .firstOrNull { it.id == libraryId }
                ?.collectionType
                ?.lowercase()
        return libraryQueryForCollectionType(collectionType)
    }

    suspend fun cachedLibraryPage(
        libraryId: String,
        page: Int,
        pageSize: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val startIndex = page * pageSize
        val query = queryForLibrary(environment.serverKey, libraryId)
        return if (!query.recursive) {
            itemStore.listByParent(
                environment.serverKey,
                libraryId,
                libraryId,
                pageSize.toLong(),
                startIndex.toLong(),
            )
        } else {
            itemStore.listByLibrary(environment.serverKey, libraryId, pageSize.toLong(), startIndex.toLong())
        }.map { it.toDomain() }
    }

    suspend fun loadChildrenPage(
        libraryId: String,
        parentId: String,
        page: Int,
        pageSize: Int,
        refresh: Boolean = page == 0,
    ): LibraryPage {
        val environment = environmentProvider.current() ?: return LibraryPage(emptyList(), null)
        val startIndex = page * pageSize
        val response =
            apiFor(environment).fetchLibraryItems(
                userId = environment.userId,
                libraryId = parentId,
                startIndex = startIndex,
                limit = pageSize,
                includeItemTypes = null,
                recursive = false,
            )
        val records = response.items.map { it.toRecord(environment, libraryId, clock.now()) }
        if (refresh) {
            itemStore.replaceForParent(environment.serverKey, libraryId, parentId, records)
        } else {
            itemStore.upsert(records)
        }
        val items =
            itemStore
                .listByParent(
                    environment.serverKey,
                    libraryId,
                    parentId,
                    pageSize.toLong(),
                    startIndex.toLong(),
                ).map { it.toDomain() }
        return LibraryPage(items, response.totalRecordCount)
    }

    suspend fun cachedChildrenPage(
        libraryId: String,
        parentId: String,
        page: Int,
        pageSize: Int,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore
            .listByParent(
                environment.serverKey,
                libraryId,
                parentId,
                pageSize.toLong(),
                (page * pageSize).toLong(),
            ).map { it.toDomain() }
    }

    suspend fun refreshContinueWatching(limit: Int): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val response = api.fetchContinueWatching(environment.userId, limit)
        val records = response.items.map { it.toRecord(environment, fallbackLibraryId = it.parentId, updatedAt = now) }
        itemStore.upsert(records)
        itemStore.clearContinueWatching(
            serverId = environment.serverKey,
            keepIds = records.map { it.id }.toSet(),
        )
        return itemStore.listContinueWatching(environment.serverKey, limit.toLong()).map { it.toDomain() }
    }

    suspend fun refreshNextUp(
        limit: Int,
        libraryId: String?,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val fallback =
            itemStore
                .listNextUp(environment.serverKey, limit.toLong())
                .map { it.toDomain() }
        JellystackLog.d("Fetching Jellyfin Next Up (server=${environment.serverKey}, library=$libraryId, limit=$limit)")
        var usedLibraryFilter = libraryId != null
        var response =
            runCatching {
                api.fetchNextUp(environment.userId, limit, parentId = libraryId)
            }.getOrElse { primary ->
                JellystackLog.e(
                    "Next Up fetch failed for library=$libraryId on server=${environment.serverKey}: ${primary.message}",
                    primary,
                )
                runCatching {
                    JellystackLog.d("Retrying Jellyfin Next Up without library filter (server=${environment.serverKey})")
                    api.fetchNextUp(environment.userId, limit, parentId = null)
                }.getOrElse { fallbackError ->
                    JellystackLog.e(
                        "Fallback Next Up fetch without library filter also failed on server=${environment.serverKey}: ${fallbackError.message}",
                        fallbackError,
                    )
                    return fallback
                }.also {
                    usedLibraryFilter = false
                }
            }
        if (response.items.isEmpty() && usedLibraryFilter) {
            JellystackLog.d(
                "Next Up response empty for library=$libraryId on server=${environment.serverKey}; retrying without library filter",
            )
            response =
                runCatching {
                    api.fetchNextUp(environment.userId, limit, parentId = null)
                }.getOrElse { fallbackError ->
                    JellystackLog.e(
                        "Fallback Next Up fetch without library filter also failed on server=${environment.serverKey}: ${fallbackError.message}",
                        fallbackError,
                    )
                    return fallback
                }
            usedLibraryFilter = false
        }
        if (response.items.isEmpty()) {
            JellystackLog.d(
                "Next Up Items endpoint returned no results on server=${environment.serverKey}; trying Shows endpoint",
            )
            response =
                runCatching {
                    api.fetchShowsNextUp(environment.userId, limit, parentId = libraryId)
                }.getOrElse { showsError ->
                    JellystackLog.e(
                        "Shows Next Up fetch failed on server=${environment.serverKey}: ${showsError.message}",
                        showsError,
                    )
                    return fallback
                }
        }
        if (response.items.isEmpty()) {
            JellystackLog.d(
                "Next Up still empty on server=${environment.serverKey}; keeping ${fallback.size} cached items",
            )
            return fallback
        }
        val records =
            response.items.map { item ->
                item.toRecord(
                    environment,
                    fallbackLibraryId = libraryId ?: item.parentId,
                    updatedAt = now,
                )
            }
        itemStore.upsert(records)
        itemStore.replaceNextUp(environment.serverKey, records.map { it.id }, now)
        val updated =
            itemStore
                .listNextUp(environment.serverKey, limit.toLong())
                .map { it.toDomain() }
        JellystackLog.d(
            "Updated Jellyfin Next Up with ${updated.size} items on server=${environment.serverKey}: ${
                updated.joinToString(
                    limit = 3,
                    truncated = "…",
                    transform = JellyfinItem::id,
                )
            }",
        )
        return updated
    }

    suspend fun refreshRecentlyAddedShows(
        libraryId: String,
        limit: Int,
    ): List<JellyfinItem> = refreshRecentlyAdded(libraryId = libraryId, limit = limit, includeItemTypes = "Series,Episode")

    suspend fun refreshRecentlyAddedMovies(
        libraryId: String,
        limit: Int,
    ): List<JellyfinItem> = refreshRecentlyAdded(libraryId = libraryId, limit = limit, includeItemTypes = "Movie")

    suspend fun episodesForSeries(seriesId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listEpisodesForSeries(environment.serverKey, seriesId).map { it.toDomain() }
    }

    suspend fun refreshEpisodesForSeries(seriesId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val response = api.fetchEpisodesForSeries(environment.userId, seriesId)
        val records = response.items.map { it.toRecord(environment, fallbackLibraryId = seriesId, updatedAt = now) }
        itemStore.upsert(records)
        return itemStore.listEpisodesForSeries(environment.serverKey, seriesId).map { it.toDomain() }
    }

    suspend fun episodesForSeason(seasonId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        return itemStore.listEpisodesForSeason(environment.serverKey, seasonId).map { it.toDomain() }
    }

    override suspend fun reportOfflineProgress(
        mediaId: String,
        positionMs: Long,
    ) {
        val environment = environmentProvider.current() ?: error("No Jellyfin environment available for offline progress sync")
        val api = apiFor(environment)
        val ticks = if (positionMs <= 0) 0L else positionMs * 10_000
        api.reportPlaybackProgress(environment.userId, mediaId, ticks)
    }

    override suspend fun markOfflinePlaybackCompleted(mediaId: String) {
        val environment = environmentProvider.current() ?: error("No Jellyfin environment available for offline completion sync")
        val api = apiFor(environment)
        api.markPlaybackCompleted(environment.userId, mediaId)
    }

    suspend fun startStreamingPlayback(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        val environment = environmentProvider.current() ?: return
        val api = apiFor(environment)
        val playSessionId = context.playSessionId ?: return
        val positionTicks = if (positionMs <= 0) 0L else positionMs * 10_000
        val playMethod = context.strategy.toApiValue()
        val body =
            buildJsonObject {
                put("ItemId", context.mediaId)
                put("PlaySessionId", playSessionId)
                context.mediaSourceId?.let { put("MediaSourceId", it) }
                context.audioStreamIndex?.let { put("AudioStreamIndex", it) }
                context.subtitleStreamIndex?.let { put("SubtitleStreamIndex", it) }
                put("CanSeek", true)
                put("IsMuted", false)
                put("IsPaused", false)
                put("PlaybackRate", 1.0)
                put("RepeatMode", "RepeatNone")
                put("PositionTicks", positionTicks)
                put("PlayMethod", playMethod)
                environment.deviceId?.let { put("DeviceId", it) }
            }
        runCatching { api.startStreamingPlayback(environment.userId, body) }
        runCatching { reportOfflineProgress(context.mediaId, positionMs) }
    }

    suspend fun reportStreamingProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        val environment = environmentProvider.current() ?: return
        val api = apiFor(environment)
        val playSessionId = context.playSessionId ?: return
        val positionTicks = if (positionMs <= 0) 0L else positionMs * 10_000
        val playMethod = context.strategy.toApiValue()
        val body =
            buildJsonObject {
                put("ItemId", context.mediaId)
                put("PlaySessionId", playSessionId)
                context.mediaSourceId?.let { put("MediaSourceId", it) }
                put("PositionTicks", positionTicks)
                put("IsPaused", false)
                put("PlayMethod", playMethod)
            }
        runCatching { api.reportStreamingProgress(environment.userId, body) }
        runCatching { reportOfflineProgress(context.mediaId, positionMs) }
    }

    suspend fun completeStreamingPlayback(context: StreamingProgressContext) {
        val environment = environmentProvider.current() ?: return
        val api = apiFor(environment)
        val playSessionId = context.playSessionId ?: return
        val playMethod = context.strategy.toApiValue()
        val body =
            buildJsonObject {
                put("ItemId", context.mediaId)
                put("PlaySessionId", playSessionId)
                context.mediaSourceId?.let { put("MediaSourceId", it) }
                put("PlayMethod", playMethod)
                environment.deviceId?.let { put("DeviceId", it) }
            }
        runCatching { api.stopStreamingPlayback(environment.userId, body) }
        runCatching { markOfflinePlaybackCompleted(context.mediaId) }
    }

    private suspend fun refreshRecentlyAdded(
        libraryId: String,
        limit: Int,
        includeItemTypes: String,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val api = apiFor(environment)
        val now = clock.now()
        val items = api.fetchLatestItems(environment.userId, libraryId, limit, includeItemTypes)
        val records = items.map { it.toRecord(environment, fallbackLibraryId = libraryId, updatedAt = now) }
        itemStore.upsert(records)
        return records.map { it.toDomain() }
    }

    suspend fun getItemDetail(
        itemId: String,
        forceRefresh: Boolean = false,
    ): JellyfinItemDetail? {
        val environment = environmentProvider.current() ?: return null
        val now = clock.now()
        val cached = detailStore.get(itemId)
        if (!forceRefresh && cached != null) {
            return cached.toDomain()
        }
        val api = apiFor(environment)
        val dto = api.fetchItemDetail(environment.userId, itemId)
        detailStore.upsert(
            JellyfinItemDetailRecord(
                itemId = itemId,
                json = NetworkJson.default.encodeToString(dto),
                updatedAt = now,
            ),
        )
        // Sync base item metadata with latest detail overview.
        itemStore.get(itemId)?.let { existing ->
            itemStore.upsert(
                listOf(
                    existing.copy(
                        overview = dto.overview ?: existing.overview,
                        taglines = dto.taglines ?: existing.taglines,
                        runTimeTicks = dto.runTimeTicks ?: existing.runTimeTicks,
                        communityRating = dto.communityRating ?: existing.communityRating,
                        officialRating = dto.officialRating ?: existing.officialRating,
                        updatedAt = now,
                    ),
                ),
            )
        }
        return dto.toDomain()
    }

    suspend fun cachedItemDetail(itemId: String): JellyfinItemDetail? {
        val record = detailStore.get(itemId) ?: return null
        return record.toDomain()
    }

    suspend fun setPlayedStatus(
        itemId: String,
        played: Boolean,
    ): JellyfinItemDetail? {
        val environment = environmentProvider.current() ?: return null
        val returnedUserData = apiFor(environment).setPlayedStatus(environment.userId, itemId, played)
        val now = clock.now()
        val cachedRecord = detailStore.get(itemId)
        if (cachedRecord == null) {
            return getItemDetail(itemId, forceRefresh = true)
        }

        val cachedDto = NetworkJson.default.decodeFromString<JellyfinItemDetailDto>(cachedRecord.json)
        val mergedUserData =
            returnedUserData.copy(
                isFavorite = cachedDto.userData?.isFavorite ?: returnedUserData.isFavorite,
                played = returnedUserData.played ?: played,
            )
        val updatedDto = cachedDto.copy(userData = mergedUserData)
        detailStore.upsert(
            cachedRecord.copy(
                json = NetworkJson.default.encodeToString(updatedDto),
                updatedAt = now,
            ),
        )
        itemStore.get(itemId)?.let { item ->
            itemStore.upsert(
                listOf(
                    item.copy(
                        positionTicks = mergedUserData.playbackPositionTicks ?: item.positionTicks,
                        playedPercentage =
                            mergedUserData.playedPercentage
                                ?: if (mergedUserData.played == true) 100.0 else 0.0,
                        lastPlayed = mergedUserData.lastPlayedDate ?: item.lastPlayed,
                        updatedAt = now,
                    ),
                ),
            )
        }
        return updatedDto.toDomain()
    }

    suspend fun fetchLocalTrailers(itemId: String): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val records =
            apiFor(environment)
                .fetchLocalTrailers(environment.userId, itemId)
                .map { it.toRecord(environment, fallbackLibraryId = it.parentId, updatedAt = clock.now()) }
        itemStore.upsert(records)
        return records.map { it.toDomain() }
    }

    suspend fun fetchSimilarItems(
        itemId: String,
        limit: Int = 12,
    ): List<JellyfinItem> {
        val environment = environmentProvider.current() ?: return emptyList()
        val now = clock.now()
        val records =
            apiFor(environment)
                .fetchSimilarItems(environment.userId, itemId, limit)
                .items
                .map { item -> item.toRecord(environment, fallbackLibraryId = item.parentId, updatedAt = now) }
        itemStore.upsert(records)
        return records.map { it.toDomain() }
    }

    private fun apiFor(environment: JellyfinEnvironment): JellyfinBrowseApi =
        cachedApis.getOrPut(environment.serverKey) { apiFactory(environment) }

    suspend fun currentServerBaseUrl(): String? = environmentProvider.current()?.baseUrl

    suspend fun currentAccessToken(): String? = environmentProvider.current()?.accessToken

    /**
     * Returns the currently active Jellyfin Browse API instance for the active environment, or null if no
     * environment is available. Intended for coordinator-side callers (e.g. favorites) that need direct API
     * access without re-implementing the cached factory logic.
     */
    suspend fun currentApi(): JellyfinBrowseApi? {
        val environment = environmentProvider.current() ?: return null
        return apiFor(environment)
    }

    /**
     * Returns the Jellyfin user id for the active environment, or null if no environment is available.
     */
    suspend fun currentUserId(): String? = environmentProvider.current()?.userId
}

private fun JellyfinLibraryDto.toRecord(
    environment: JellyfinEnvironment,
    now: Instant,
): JellyfinLibraryRecord =
    JellyfinLibraryRecord(
        id = id,
        serverId = environment.serverKey,
        name = name,
        collectionType = collectionType,
        primaryImageTag = primaryImageTag,
        itemCount = itemCount,
        createdAt = now,
        updatedAt = now,
    )

private fun JellyfinLibraryRecord.toDomain(): JellyfinLibrary =
    JellyfinLibrary(
        id = id,
        name = name,
        collectionType = collectionType,
        itemCount = itemCount,
        primaryImageTag = primaryImageTag,
    )

private fun JellyfinItemDto.toRecord(
    environment: JellyfinEnvironment,
    fallbackLibraryId: String?,
    updatedAt: Instant,
): JellyfinItemRecord =
    JellyfinItemRecord(
        id = id,
        serverId = environment.serverKey,
        libraryId = fallbackLibraryId ?: parentId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        locationType = locationType,
        taglines = taglines ?: emptyList(),
        parentId = parentId,
        primaryImageTag = imageTags?.get("Primary"),
        thumbImageTag = imageTags?.get("Thumb"),
        backdropImageTag = backdropImageTags?.firstOrNull() ?: parentBackdropImageTags?.firstOrNull(),
        seriesId = seriesId ?: parentId,
        seriesPrimaryImageTag =
            seriesPrimaryImageTag
                ?: imageTags?.get("Primary")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesThumbImageTag =
            seriesThumbImageTag
                ?: parentThumbImageTag
                ?: imageTags?.get("Thumb")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesBackdropImageTag =
            seriesBackdropImageTag
                ?: parentBackdropImageTags?.firstOrNull(),
        parentLogoImageTag = parentLogoImageTag ?: imageTags?.get("Logo"),
        runTimeTicks = runTimeTicks,
        positionTicks = userData?.playbackPositionTicks,
        playedPercentage = userData?.playedPercentage,
        productionYear = productionYear?.toLong(),
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber?.toLong(),
        parentIndexNumber = parentIndexNumber?.toLong(),
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = userData?.lastPlayedDate,
        updatedAt = updatedAt,
        dateCreated = dateCreated,
        logoImageTag = imageTags?.get("Logo"),
        artImageTag = imageTags?.get("Art"),
        bannerImageTag = imageTags?.get("Banner"),
        seriesLogoImageTag =
            parentLogoImageTag
                ?: imageTags?.get("Logo")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesArtImageTag =
            parentArtImageTag
                ?: imageTags?.get("Art")?.takeIf { type.equals("Series", ignoreCase = true) },
        seriesBannerImageTag =
            parentBannerImageTag
                ?: imageTags?.get("Banner")?.takeIf { type.equals("Series", ignoreCase = true) },
    )

private fun JellyfinItemRecord.toDomain(): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = libraryId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        locationType = locationType,
        taglines = taglines,
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
        productionYear = productionYear?.toInt(),
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber?.toInt(),
        parentIndexNumber = parentIndexNumber?.toInt(),
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = lastPlayed,
        dateCreated = dateCreated,
        logoImageTag = logoImageTag,
        artImageTag = artImageTag,
        bannerImageTag = bannerImageTag,
        seriesLogoImageTag = seriesLogoImageTag,
        seriesArtImageTag = seriesArtImageTag,
        seriesBannerImageTag = seriesBannerImageTag,
    )

private fun JellyfinItemDetailRecord.toDomain(): JellyfinItemDetail =
    NetworkJson.default.decodeFromString<JellyfinItemDetailDto>(json).toDomain()

private fun JellyfinItemDetailDto.toDomain(): JellyfinItemDetail =
    JellyfinItemDetail(
        id = id,
        name = name,
        overview = overview,
        taglines = taglines ?: emptyList(),
        runTimeTicks = runTimeTicks,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres ?: emptyList(),
        studios = studios?.map { it.name } ?: emptyList(),
        primaryImageTag = imageTags?.get("Primary"),
        backdropImageTags = backdropImageTags.orEmpty(),
        mediaSources = mediaSources.map { it.toDomain() },
        isFavorite = userData?.isFavorite ?: false,
        isPlayed = userData?.played ?: false,
        providerIds = providerIds.orEmpty(),
        originalTitle = originalTitle,
        originalLanguage = originalLanguage,
        criticRating = criticRating,
        productionLocations = productionLocations.orEmpty(),
        tags = tags.orEmpty(),
        logoImageTag = imageTags?.get("Logo"),
        people =
            people
                .orEmpty()
                .mapNotNull { person ->
                    val resolvedId = person.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val resolvedName = person.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    JellyfinPerson(
                        id = resolvedId,
                        name = resolvedName,
                        role = person.role,
                        type = person.type,
                        primaryImageTag = person.primaryImageTag,
                    )
                },
        parentBackdropImageTags = parentBackdropImageTags.orEmpty(),
    )

private fun JellyfinMediaSourceDto.toDomain(): JellyfinMediaSource =
    JellyfinMediaSource(
        id = id,
        name = name,
        runTimeTicks = runTimeTicks,
        container = container,
        videoBitrate = videoBitrate,
        supportsDirectPlay = supportsDirectPlay ?: false,
        supportsDirectStream = supportsDirectStream ?: false,
        supportsTranscoding = supportsTranscoding ?: false,
        streams = mediaStreams.map { it.toDomain() },
    )

private fun JellyfinMediaStreamDto.toDomain(): JellyfinMediaStream =
    JellyfinMediaStream(
        type =
            when (type.lowercase()) {
                "video" -> VIDEO
                "audio" -> AUDIO
                "subtitle" -> SUBTITLE
                else -> OTHER
            },
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = language,
        isDefault = isDefault ?: false,
        isForced = isForced ?: false,
        bitrate = bitrate,
        width = width,
        height = height,
        profile = profile,
        videoRange = videoRange,
        videoRangeType = videoRangeType,
        averageFrameRate = averageFrameRate,
        bitDepth = bitDepth,
        channels = channels,
        channelLayout = channelLayout,
        isExternal = isExternal ?: false,
        isHearingImpaired = isHearingImpaired ?: false,
    )

private fun StreamingPlayStrategy.toApiValue(): String =
    when (this) {
        StreamingPlayStrategy.DIRECT -> "DirectPlay"
        StreamingPlayStrategy.TRANSCODED -> "Transcode"
    }
