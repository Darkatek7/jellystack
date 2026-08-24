package dev.jellystack.core.jellyfin

import dev.jellystack.core.playback.StreamingPlayStrategy
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinBrowseRepositoryTest {
    private enum class NextUpResponseMode {
        DEFAULT,
        FORCE_SHOWS_ENDPOINT,
    }

    private enum class LibraryItemsResponseMode {
        DEFAULT,
        REJECT_NON_DEFAULT_INCLUDE_TYPES,
    }

    private var nextUpMode: NextUpResponseMode = NextUpResponseMode.DEFAULT
    private var libraryItemsMode: LibraryItemsResponseMode = LibraryItemsResponseMode.DEFAULT
    private var libraryItemsRequestCount: Int = 0

    private val environment =
        JellyfinEnvironment(
            serverKey = "srv-1",
            baseUrl = "https://demo.jellyfin.org",
            accessToken = "dummy-token",
            userId = "user-123",
            deviceId = "device-1",
            deviceName = "Test Device",
        )

    private val environmentProvider = JellyfinEnvironmentProvider { environment }
    private val libraryStore = InMemoryLibraryStore()
    private val itemStore = InMemoryItemStore()
    private val detailStore = InMemoryDetailStore()
    private val engine =
        MockEngine { request ->
            var status = HttpStatusCode.OK
            val body =
                when (val path = request.url.encodedPath) {
                    "/Users/user-123/Views" -> LIBRARIES_JSON
                    "/Users/user-123/Items/Resume" -> RESUME_JSON
                    "/Users/user-123/Items/NextUp" ->
                        when (nextUpMode) {
                            NextUpResponseMode.DEFAULT ->
                                if (request.url.parameters["ParentId"].isNullOrBlank()) {
                                    NEXT_UP_JSON
                                } else {
                                    EMPTY_NEXT_UP_JSON
                                }
                            NextUpResponseMode.FORCE_SHOWS_ENDPOINT -> EMPTY_NEXT_UP_JSON
                        }
                    "/Shows/NextUp" ->
                        when (nextUpMode) {
                            NextUpResponseMode.DEFAULT -> EMPTY_NEXT_UP_JSON
                            NextUpResponseMode.FORCE_SHOWS_ENDPOINT -> NEXT_UP_JSON
                        }
                    "/Users/user-123/Items/item-1" -> DETAIL_JSON
                    "/Users/user-123/Items/episode-artwork" -> EPISODE_ARTWORK_DETAIL_JSON
                    "/Items/item-1/Similar" -> SIMILAR_JSON
                    "/Users/user-123/PlayedItems/item-1" -> PLAYED_USER_DATA_JSON
                    "/Items/item-1/LocalTrailers" -> LOCAL_TRAILERS_JSON
                    "/Users/user-123/Items/Latest" ->
                        when (request.url.parameters["IncludeItemTypes"]) {
                            "Movie" -> LATEST_MOVIES_JSON
                            "Series,Episode" -> LATEST_SHOWS_JSON
                            else -> error("Unexpected includeItemTypes: ${request.url.parameters}")
                        }
                    "/Users/user-123/Items" ->
                        when (libraryItemsMode) {
                            LibraryItemsResponseMode.DEFAULT -> ITEMS_JSON
                            LibraryItemsResponseMode.REJECT_NON_DEFAULT_INCLUDE_TYPES -> {
                                val includeParam = request.url.parameters["IncludeItemTypes"].orEmpty()
                                libraryItemsRequestCount++
                                if (includeParam == JellyfinBrowseApi.DEFAULT_INCLUDE_ITEM_TYPES) {
                                    ITEMS_JSON
                                } else {
                                    status = HttpStatusCode.BadRequest
                                    FILTER_ERROR_JSON
                                }
                            }
                        }
                    else -> error("Unexpected request path: $path")
                }
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    private val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
    private val apiFactory: JellyfinBrowseApiFactory = { env ->
        JellyfinBrowseApi(
            client,
            env.baseUrl,
            env.accessToken,
            env.deviceId,
            clientName = "Test",
            deviceName = env.deviceName,
            clientVersion = "1.0",
        )
    }
    private val repository =
        JellyfinBrowseRepository(environmentProvider, libraryStore, itemStore, detailStore, apiFactory, clock = FixedClock)

    @Test
    fun refreshLibrariesStoresRecords() =
        runTest {
            val libraries = repository.refreshLibraries()

            assertEquals(2, libraries.size)
            assertEquals("Movies", libraries.first().name)
            assertEquals("movies-primary", libraries.first().primaryImageTag)
            val stored = libraryStore.list(environment.serverKey)
            assertEquals(libraries.size, stored.size)
            assertEquals("movies-primary", stored.first().primaryImageTag)
        }

    @Test
    fun stopStreamingPlaybackReportsExactPositionWithoutForcingCompletion() =
        runTest {
            var stopPayload: String? = null
            var playedRequestCount = 0
            val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val trackingEngine =
                MockEngine { request ->
                    when (val path = request.url.encodedPath) {
                        "/Sessions/Playing/Stopped" -> {
                            stopPayload = (request.body as TextContent).text
                            respond("", HttpStatusCode.NoContent, headers)
                        }
                        "/Users/user-123/PlayedItems/item-1" -> {
                            playedRequestCount++
                            respond(PLAYED_USER_DATA_JSON, HttpStatusCode.OK, headers)
                        }
                        else -> error("Unexpected request path: $path")
                    }
                }
            val trackingClient = NetworkClientFactory.create(ClientConfig(engine = trackingEngine, installLogging = false))
            val trackingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    trackingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val trackingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    trackingApiFactory,
                    clock = FixedClock,
                )
            val context =
                StreamingProgressContext(
                    mediaId = "item-1",
                    mediaSourceId = "source-1",
                    playSessionId = "play-session-1",
                    audioStreamIndex = 2,
                    subtitleStreamIndex = 3,
                    strategy = StreamingPlayStrategy.DIRECT,
                )

            trackingRepository.stopStreamingPlayback(
                context = context,
                positionMs = 37_500L,
                completed = false,
            )

            val payload = Json.parseToJsonElement(requireNotNull(stopPayload)).jsonObject
            assertEquals("item-1", payload.getValue("ItemId").jsonPrimitive.content)
            assertEquals("play-session-1", payload.getValue("PlaySessionId").jsonPrimitive.content)
            assertEquals(375_000_000L, payload.getValue("PositionTicks").jsonPrimitive.long)
            assertEquals(0, playedRequestCount)
        }

    @Test
    fun loadLibraryPageDoesNotRequestAdditionalLibraries() =
        runTest {
            val recordedParentIds = mutableListOf<String?>()
            val trackingEngine =
                MockEngine { request ->
                    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    when (val path = request.url.encodedPath) {
                        "/Users/user-123/Views" -> respond(THREE_LIBRARIES_JSON, HttpStatusCode.OK, headers)
                        "/Users/user-123/Items" -> {
                            recordedParentIds += request.url.parameters["ParentId"]
                            respond(ITEMS_JSON, HttpStatusCode.OK, headers)
                        }
                        else -> error("Unexpected request path: $path")
                    }
                }
            val trackingClient = NetworkClientFactory.create(ClientConfig(engine = trackingEngine, installLogging = false))
            val trackingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    trackingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val trackingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    trackingApiFactory,
                    clock = FixedClock,
                )

            trackingRepository.refreshLibraries()
            trackingRepository.loadLibraryPage(libraryId = "lib-primary", page = 0, pageSize = 2, refresh = true)

            assertTrue(
                recordedParentIds.isNotEmpty() && recordedParentIds.all { it == "lib-primary" },
                "Unexpected additional library requests: $recordedParentIds",
            )
        }

    @Test
    fun loadLibraryPageChoosesIncludeItemTypesFromCollectionType() =
        runTest {
            val recordedParameters = mutableListOf<Triple<String?, String?, String?>>()
            val trackingEngine =
                MockEngine { request ->
                    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    when (val path = request.url.encodedPath) {
                        "/Users/user-123/Views" -> respond(THREE_LIBRARIES_JSON, HttpStatusCode.OK, headers)
                        "/Users/user-123/Items" -> {
                            recordedParameters +=
                                Triple(
                                    request.url.parameters["ParentId"],
                                    request.url.parameters["IncludeItemTypes"],
                                    request.url.parameters["Recursive"],
                                )
                            respond(ITEMS_JSON, HttpStatusCode.OK, headers)
                        }
                        else -> error("Unexpected request path: $path")
                    }
                }
            val trackingClient = NetworkClientFactory.create(ClientConfig(engine = trackingEngine, installLogging = false))
            val trackingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    trackingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val trackingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    trackingApiFactory,
                    clock = FixedClock,
                )

            trackingRepository.refreshLibraries()
            trackingRepository.loadLibraryPage(libraryId = "lib-secondary", page = 0, pageSize = 2, refresh = true)
            trackingRepository.loadLibraryPage(libraryId = "lib-primary", page = 0, pageSize = 2, refresh = true)
            trackingRepository.loadLibraryPage(libraryId = "lib-extra", page = 0, pageSize = 2, refresh = true)

            val paramsByLibrary = recordedParameters.associate { it.first to (it.second to it.third) }
            assertEquals("Series" to "true", paramsByLibrary["lib-secondary"])
            assertEquals("Movie" to "true", paramsByLibrary["lib-primary"])
            assertEquals("MusicArtist,MusicAlbum,Audio" to "false", paramsByLibrary["lib-extra"])
        }

    @Test
    fun filteredLibraryPageQueriesRecursivelyAcrossAllLibraries() =
        runTest {
            var recordedParentId: String? = "not-recorded"
            var recordedRecursive: String? = null
            var recordedFilters: String? = null
            val trackingEngine =
                MockEngine { request ->
                    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    when (val path = request.url.encodedPath) {
                        "/Users/user-123/Items" -> {
                            recordedParentId = request.url.parameters["ParentId"]
                            recordedRecursive = request.url.parameters["Recursive"]
                            recordedFilters = request.url.parameters["Filters"]
                            respond(ITEMS_JSON, HttpStatusCode.OK, headers)
                        }
                        else -> error("Unexpected request path: $path")
                    }
                }
            val trackingClient = NetworkClientFactory.create(ClientConfig(engine = trackingEngine, installLogging = false))
            val trackingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    trackingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val trackingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    trackingApiFactory,
                    clock = FixedClock,
                )

            val page =
                trackingRepository.loadLibraryPage(
                    libraryId = "__favorites__",
                    page = 0,
                    pageSize = 30,
                    refresh = true,
                    filters = "IsFavorite",
                )

            assertEquals(null, recordedParentId)
            assertEquals("true", recordedRecursive)
            assertEquals("IsFavorite", recordedFilters)
            assertEquals(2, page.items.size)
        }

    @Test
    fun loadLibraryPageRetriesWithDefaultIncludeTypesWhenServerRejectsFilter() =
        runTest {
            libraryItemsMode = LibraryItemsResponseMode.REJECT_NON_DEFAULT_INCLUDE_TYPES
            libraryItemsRequestCount = 0
            try {
                repository.refreshLibraries()

                val items = repository.loadLibraryPage(libraryId = "lib-2", page = 0, pageSize = 2, refresh = true)

                assertEquals(2, items.items.size)
                assertTrue(libraryItemsRequestCount >= 2, "Expected a fallback request with default include types")
            } finally {
                libraryItemsMode = LibraryItemsResponseMode.DEFAULT
            }
        }

    @Test
    fun loadLibraryPageCachesAndReturnsItems() =
        runTest {
            repository.refreshLibraries()

            val items = repository.loadLibraryPage(libraryId = "lib-1", page = 0, pageSize = 2, refresh = true)

            assertEquals(2, items.items.size)
            assertEquals(
                "603",
                items.items
                    .first()
                    .providerIds.tmdbId,
            )
            assertEquals(
                "item-1",
                items.items
                    .first()
                    .providerIds.sourceLocalId,
            )
            val stored = itemStore.listByLibrary(environment.serverKey, "lib-1", limit = 10, offset = 0)
            assertEquals(2, stored.size)
            assertEquals("603", stored.single { it.id == "item-1" }.providerIds.tmdbId)
        }

    @Test
    fun loadLibraryPageExposesTotalRecordCount() =
        runTest {
            val recordingEngine =
                MockEngine { request ->
                    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    when (val path = request.url.encodedPath) {
                        "/Users/user-123/Views" -> respond(LIBRARIES_JSON, HttpStatusCode.OK, headers)
                        "/Users/user-123/Items" -> respond(ITEMS_JSON_WITH_TOTAL, HttpStatusCode.OK, headers)
                        else -> error("Unexpected request path: $path")
                    }
                }
            val recordingClient = NetworkClientFactory.create(ClientConfig(engine = recordingEngine, installLogging = false))
            val recordingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    recordingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val recordingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    recordingApiFactory,
                    clock = FixedClock,
                )
            recordingRepository.refreshLibraries()

            val page =
                recordingRepository.loadLibraryPage(
                    libraryId = "lib-1",
                    page = 0,
                    pageSize = 30,
                    refresh = true,
                )

            assertEquals(142L, page.totalRecordCount)
            assertEquals(2, page.items.size)
        }

    @Test
    fun refreshRecentlyAddedParsesArrayResponse() =
        runTest {
            repository.refreshLibraries()

            val movies = repository.refreshRecentlyAddedMovies(libraryId = "lib-1", limit = 5)
            val shows = repository.refreshRecentlyAddedShows(libraryId = "lib-1", limit = 5)

            assertEquals(listOf("movie-latest"), movies.map { it.id })
            assertEquals(listOf("lib-1"), movies.mapNotNull { it.libraryId })
            assertEquals(listOf("2026-06-10T12:30:00.0000000Z"), movies.map { it.dateCreated })
            assertEquals(listOf("show-latest"), shows.map { it.id })
            assertEquals(listOf("2026-06-11T12:30:00.0000000Z"), shows.map { it.dateCreated })
        }

    @Test
    fun getItemDetailCachesMediaSources() =
        runTest {
            repository.refreshLibraries()
            repository.loadLibraryPage(libraryId = "lib-1", page = 0, pageSize = 2, refresh = true)

            val detail = repository.getItemDetail("item-1")

            assertNotNull(detail)
            assertEquals("Sample Movie", detail.name)
            assertEquals(1, detail.mediaSources.size)
            val videoStream =
                detail.mediaSources
                    .single()
                    .streams
                    .first { it.type == JellyfinMediaStreamType.VIDEO }
            assertEquals(8_000_000, videoStream.bitrate)
            assertEquals(1920, videoStream.width)
            assertEquals(1080, videoStream.height)
            assertEquals("High", videoStream.profile)
            assertEquals("HDR10", videoStream.videoRangeType)
            assertEquals(24.0, videoStream.averageFrameRate)
            assertEquals(10, videoStream.bitDepth)
            assertEquals("Enola Holmes 3", detail.originalTitle)
            assertEquals("en", detail.originalLanguage)
            assertEquals(78.0, detail.criticRating)
            assertEquals(listOf("United Kingdom"), detail.productionLocations)
            assertEquals(listOf("detective", "victorian"), detail.tags)
            assertEquals("logo-tag", detail.logoImageTag)
            assertEquals(emptyList(), detail.parentBackdropImageTags)
            assertEquals(
                listOf(JellyfinPerson("person-1", "Millie Brown", "Enola Holmes", "Actor", "person-tag")),
                detail.people,
            )
            assertEquals("603", detail.providerIds["Tmdb"])
            assertNotNull(detailStore.get("srv-1:item-1"))
        }

    @Test
    fun favoriteItemsAreFetchedWithMetadataAndCached() =
        runTest {
            val favorites = repository.refreshFavoriteItems()

            assertEquals(listOf("item-1", "item-2"), favorites.map { it.id })
            assertEquals("603", favorites.first().providerIds.tmdbId)
            assertEquals("srv-1", itemStore.get("item-1")?.serverId)
        }

    @Test
    fun detailCacheIsIsolatedByManagedConnection() =
        runTest {
            var activeEnvironment = environment.copy(serverKey = "connection-a", userId = "user-a")
            var networkRequests = 0
            val isolatedEngine =
                MockEngine { request ->
                    networkRequests++
                    val user =
                        request.url.encodedPath
                            .substringAfter("/Users/")
                            .substringBefore('/')
                    respond(
                        content = detailJson(user),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val isolatedClient = NetworkClientFactory.create(ClientConfig(engine = isolatedEngine, installLogging = false))
            val isolatedRepository =
                JellyfinBrowseRepository(
                    environmentProvider = JellyfinEnvironmentProvider { activeEnvironment },
                    libraryStore = InMemoryLibraryStore(),
                    itemStore = InMemoryItemStore(),
                    detailStore = InMemoryDetailStore(),
                    apiFactory = { env ->
                        JellyfinBrowseApi(
                            isolatedClient,
                            env.baseUrl,
                            env.accessToken,
                            env.deviceId,
                            clientName = "Test",
                            deviceName = env.deviceName,
                            clientVersion = "1.0",
                        )
                    },
                    clock = FixedClock,
                )

            assertEquals("user-a", isolatedRepository.getItemDetail("shared")?.name)
            activeEnvironment = environment.copy(serverKey = "connection-b", userId = "user-b")
            assertEquals("user-b", isolatedRepository.getItemDetail("shared")?.name)
            activeEnvironment = environment.copy(serverKey = "connection-a", userId = "user-a")
            assertEquals("user-a", isolatedRepository.getItemDetail("shared")?.name)
            assertEquals(2, networkRequests)
        }

    @Test
    fun episodeDetailKeepsOwnedAndParentBackdropsSeparateThroughCache() =
        runTest {
            val detail = repository.getItemDetail("episode-artwork")

            assertNotNull(detail)
            assertEquals(listOf("episode-backdrop"), detail.backdropImageTags)
            assertEquals(listOf("series-backdrop"), detail.parentBackdropImageTags)

            val cached = repository.cachedItemDetail("episode-artwork")
            assertNotNull(cached)
            assertEquals(listOf("episode-backdrop"), cached.backdropImageTags)
            assertEquals(listOf("series-backdrop"), cached.parentBackdropImageTags)
        }

    @Test
    fun fetchSimilarItemsStoresAndReturnsRelatedMedia() =
        runTest {
            val similar = repository.fetchSimilarItems("item-1", limit = 12)

            assertEquals(listOf("related-1", "related-2"), similar.map { it.id })
            assertEquals("Related One", itemStore.get("related-1")?.name)
        }

    @Test
    fun fetchLocalTrailersParsesPlayableItems() =
        runTest {
            val trailers = repository.fetchLocalTrailers("item-1")

            assertEquals(listOf("trailer-1"), trailers.map { it.id })
            assertEquals("Video", trailers.single().mediaType)
        }

    @Test
    fun itemDetailPreservesIsFavorite() =
        runTest {
            val recordingEngine =
                MockEngine { request ->
                    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    when (val path = request.url.encodedPath) {
                        "/Users/user-123/Items/movie-1" -> respond(DETAIL_WITH_FAVORITE_JSON, HttpStatusCode.OK, headers)
                        else -> error("Unexpected request path: $path")
                    }
                }
            val recordingClient = NetworkClientFactory.create(ClientConfig(engine = recordingEngine, installLogging = false))
            val recordingApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    recordingClient,
                    env.baseUrl,
                    env.accessToken,
                    env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val recordingRepository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    recordingApiFactory,
                    clock = FixedClock,
                )

            val detail = recordingRepository.getItemDetail("movie-1")

            assertNotNull(detail)
            assertTrue(detail.isFavorite)
        }

    @Test
    fun setPlayedStatusUpdatesReturnedAndCachedDetail() =
        runTest {
            val initial = repository.getItemDetail("item-1")
            assertNotNull(initial)
            assertFalse(initial.isPlayed)

            val updated = repository.setPlayedStatus("item-1", played = true)

            assertNotNull(updated)
            assertTrue(updated.isPlayed)
            assertTrue(requireNotNull(repository.cachedItemDetail("item-1")).isPlayed)
        }

    @Test
    fun refreshContinueWatchingClearsMissingItems() =
        runTest {
            val staleRecord =
                JellyfinItemRecord(
                    id = "stale-episode",
                    serverId = environment.serverKey,
                    libraryId = "lib-2",
                    name = "Stale Episode",
                    sortName = null,
                    overview = null,
                    type = "Episode",
                    mediaType = "Video",
                    locationType = null,
                    taglines = emptyList(),
                    parentId = "series-1",
                    primaryImageTag = null,
                    thumbImageTag = null,
                    backdropImageTag = null,
                    seriesId = "series-1",
                    seriesPrimaryImageTag = null,
                    seriesThumbImageTag = null,
                    seriesBackdropImageTag = null,
                    parentLogoImageTag = null,
                    runTimeTicks = 1L,
                    positionTicks = 1L,
                    playedPercentage = 5.0,
                    productionYear = null,
                    premiereDate = null,
                    communityRating = null,
                    officialRating = null,
                    indexNumber = 1L,
                    parentIndexNumber = 1L,
                    seriesName = "Sample Series",
                    seasonId = "season-1",
                    episodeTitle = "Episode 1",
                    lastPlayed = "2024-01-01T00:00:00Z",
                    updatedAt = FixedClock.now(),
                )
            itemStore.upsert(listOf(staleRecord))

            val refreshed = repository.refreshContinueWatching(limit = 12)

            assertEquals(listOf("item-1"), refreshed.map { it.id })
            val stored = itemStore.listContinueWatching(environment.serverKey, limit = 10)
            assertEquals(listOf("item-1"), stored.map { it.id })
            assertNull(itemStore.get("stale-episode")?.positionTicks)
        }

    @Test
    fun refreshNextUpCachesAndReturnsItems() =
        runTest {
            nextUpMode = NextUpResponseMode.DEFAULT
            repository.refreshLibraries()

            val nextUp = repository.refreshNextUp(limit = 12, libraryId = "lib-2")

            assertEquals(listOf("next-episode"), nextUp.map { it.id })
            val stored = itemStore.listNextUp(environment.serverKey, limit = 10)
            assertEquals(listOf("next-episode"), stored.map { it.id })
            val cached = repository.cachedNextUp(limit = 12)
            assertEquals(listOf("next-episode"), cached.map { it.id })
        }

    @Test
    fun refreshNextUpFallsBackToShowsEndpoint() =
        runTest {
            nextUpMode = NextUpResponseMode.FORCE_SHOWS_ENDPOINT
            repository.refreshLibraries()

            val nextUp = repository.refreshNextUp(limit = 12, libraryId = "lib-2")

            assertEquals(listOf("next-episode"), nextUp.map { it.id })
            val stored = itemStore.listNextUp(environment.serverKey, limit = 10)
            assertEquals(listOf("next-episode"), stored.map { it.id })
        }

    private object FixedClock : Clock {
        private val instant = Instant.parse("2024-01-01T00:00:00Z")

        override fun now(): Instant = instant
    }

    private class InMemoryLibraryStore : JellyfinLibraryStore {
        private val records = mutableListOf<JellyfinLibraryRecord>()

        override suspend fun replaceAll(
            serverId: String,
            libraries: List<JellyfinLibraryRecord>,
        ) {
            records.removeAll { it.serverId == serverId }
            records.addAll(libraries)
        }

        override suspend fun list(serverId: String): List<JellyfinLibraryRecord> = records.filter { it.serverId == serverId }
    }

    private class InMemoryItemStore : JellyfinItemStore {
        private val records = mutableMapOf<String, MutableMap<String, JellyfinItemRecord>>()
        private val nextUp = mutableMapOf<String, List<String>>()

        override suspend fun replaceForLibrary(
            serverId: String,
            libraryId: String,
            items: List<JellyfinItemRecord>,
        ) {
            val serverRecords = records.getOrPut(serverId) { mutableMapOf() }
            serverRecords.entries.removeAll { it.value.libraryId == libraryId }
            items.forEach { serverRecords[it.id] = it }
        }

        override suspend fun upsert(items: List<JellyfinItemRecord>) {
            items.forEach { item ->
                val serverRecords = records.getOrPut(item.serverId) { mutableMapOf() }
                serverRecords[item.id] = item
            }
        }

        override suspend fun listByLibrary(
            serverId: String,
            libraryId: String,
            limit: Long,
            offset: Long,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.libraryId == libraryId }
                ?.sortedBy { it.sortName ?: it.name }
                ?.drop(offset.toInt())
                ?.take(limit.toInt())
                ?: emptyList()

        override suspend fun listRecentShows(
            serverId: String,
            libraryId: String?,
            limit: Long,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun listRecentMovies(
            serverId: String,
            libraryId: String?,
            limit: Long,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun listContinueWatching(
            serverId: String,
            limit: Long,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { (it.positionTicks ?: 0L) > 0L }
                ?.sortedByDescending { it.updatedAt }
                ?.take(limit.toInt())
                ?: emptyList()

        override suspend fun clearContinueWatching(
            serverId: String,
            keepIds: Set<String>,
        ) {
            val serverRecords = records[serverId] ?: return
            val preserved = keepIds.ifEmpty { emptySet() }
            val updated = mutableMapOf<String, JellyfinItemRecord>()
            serverRecords.forEach { (id, record) ->
                val adjusted =
                    if (preserved.isNotEmpty() && id in preserved) {
                        record
                    } else if ((record.positionTicks ?: 0L) > 0L && (preserved.isEmpty() || id !in preserved)) {
                        record.copy(positionTicks = null, playedPercentage = null, lastPlayed = null)
                    } else {
                        record
                    }
                updated[id] = adjusted
            }
            records[serverId] = updated
        }

        override suspend fun replaceNextUp(
            serverId: String,
            itemIds: List<String>,
            updatedAt: Instant,
        ) {
            nextUp[serverId] = itemIds.toList()
        }

        override suspend fun listNextUp(
            serverId: String,
            limit: Long,
        ): List<JellyfinItemRecord> =
            nextUp[serverId]
                ?.take(limit.toInt())
                ?.mapNotNull { id -> records[serverId]?.get(id) }
                ?: emptyList()

        override suspend fun listEpisodesForSeries(
            serverId: String,
            seriesId: String,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun listEpisodesForSeason(
            serverId: String,
            seasonId: String,
        ): List<JellyfinItemRecord> = emptyList()

        override suspend fun get(itemId: String): JellyfinItemRecord? = records.values.firstNotNullOfOrNull { it[itemId] }
    }

    private class InMemoryDetailStore : JellyfinItemDetailStore {
        private val records = mutableMapOf<String, JellyfinItemDetailRecord>()

        override suspend fun get(itemId: String): JellyfinItemDetailRecord? = records[itemId]

        override suspend fun upsert(record: JellyfinItemDetailRecord) {
            records[record.itemId] = record
        }
    }

    companion object {
        private fun detailJson(name: String): String = """{"Id":"shared","Name":"$name","Type":"Movie","MediaSources":[]}"""

        private const val LIBRARIES_JSON = """
            {
              "Items": [
                {"Id": "lib-1", "Name": "Movies", "CollectionType": "movies", "ImageTags": {"Primary": "movies-primary"}},
                {"Id": "lib-2", "Name": "Shows", "CollectionType": "tvshows"}
              ]
            }
        """
        private const val THREE_LIBRARIES_JSON = """
            {
              "Items": [
                {"Id": "lib-primary", "Name": "Primary", "CollectionType": "movies"},
                {"Id": "lib-secondary", "Name": "Secondary", "CollectionType": "tvshows"},
                {"Id": "lib-extra", "Name": "Extra Library", "CollectionType": "music"}
              ]
            }
        """

        private const val ITEMS_JSON = """
            {
              "Items": [
                {
                  "Id": "item-1",
                  "Name": "Sample Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "ProviderIds": {"Tmdb": "603", "Tvdb": ""},
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-primary"},
                  "UserData": {
                    "PlaybackPositionTicks": 12000000000,
                    "PlayedPercentage": 33.3
                  }
                },
                {
                  "Id": "item-2",
                  "Name": "Sample Episode",
                  "Type": "Episode",
                  "MediaType": "Video",
                  "Overview": "Episode overview",
                  "RunTimeTicks": 18000000000,
                  "SeriesName": "Sample Series",
                  "EpisodeTitle": "Pilot",
                  "ImageTags": {"Primary": "tag-episode"}
                }
              ],
              "TotalRecordCount": 2
            }
        """

        private const val ITEMS_JSON_WITH_TOTAL = """
            {
              "Items": [
                {
                  "Id": "movie-1",
                  "Name": "Alpha",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-movie-1"}
                },
                {
                  "Id": "movie-2",
                  "Name": "Bravo",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Another sample",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-movie-2"}
                }
              ],
              "TotalRecordCount": 142
            }
        """

        private const val FILTER_ERROR_JSON = """
            {
              "Message": "No media found with the specified filter"
            }
        """

        private const val LATEST_MOVIES_JSON = """
            [
              {
                "Id": "movie-latest",
                "Name": "Latest Movie",
                "Type": "Movie",
                "MediaType": "Video",
                "ParentId": "lib-1",
                "DateCreated": "2026-06-10T12:30:00.0000000Z",
                "ImageTags": {"Primary": "latest-movie-tag"}
              }
            ]
        """

        private const val LATEST_SHOWS_JSON = """
            [
              {
                "Id": "show-latest",
                "Name": "Latest Show",
                "Type": "Series",
                "MediaType": "Video",
                "ParentId": "lib-2",
                "DateCreated": "2026-06-11T12:30:00.0000000Z",
                "ImageTags": {"Primary": "latest-show-tag"}
              }
            ]
        """

        private const val EMPTY_NEXT_UP_JSON = """
            {
              "Items": []
            }
        """

        private const val NEXT_UP_JSON = """
            {
              "Items": [
                {
                  "Id": "next-episode",
                  "Name": "Next Up Episode",
                  "Type": "Episode",
                  "SeriesId": "series-1",
                  "ParentId": "series-1",
                  "IndexNumber": 2,
                  "ParentIndexNumber": 1,
                  "RunTimeTicks": 24000000000,
                  "ImageTags": {"Primary": "next-up-tag"}
                }
              ]
            }
        """

        private const val RESUME_JSON = ITEMS_JSON

        private const val DETAIL_JSON = """
            {
              "Id": "item-1",
              "Name": "Sample Movie",
              "Overview": "Detailed overview",
              "Taglines": ["An epic journey"],
              "RunTimeTicks": 36000000000,
              "OriginalTitle": "Enola Holmes 3",
              "OriginalLanguage": "en",
              "CriticRating": 78,
              "ProductionLocations": ["United Kingdom"],
              "Tags": ["detective", "victorian"],
              "Genres": ["Adventure"],
              "Studios": [{"Name": "Sample Studio"}],
              "People": [
                {
                  "Id": "person-1",
                  "Name": "Millie Brown",
                  "Role": "Enola Holmes",
                  "Type": "Actor",
                  "PrimaryImageTag": "person-tag"
                },
                {
                  "Name": "Person without an id",
                  "Role": "Unknown"
                }
              ],
              "MediaSources": [
                {
                  "Id": "source-1",
                  "Name": "Main",
                  "RunTimeTicks": 36000000000,
                  "Container": "mp4",
                  "SupportsDirectPlay": true,
                  "SupportsTranscoding": true,
                  "MediaStreams": [
                    {
                      "Type": "Video",
                      "Index": 0,
                      "DisplayTitle": "1080p",
                      "Codec": "h264",
                      "Profile": "High",
                      "VideoRangeType": "HDR10",
                      "AverageFrameRate": 24.0,
                      "BitDepth": 10,
                      "BitRate": 8000000,
                      "Width": 1920,
                      "Height": 1080
                    },
                    {"Type": "Audio", "Index": 1, "DisplayTitle": "English", "Codec": "aac"}
                  ]
                }
              ],
              "ImageTags": {"Primary": "tag-primary", "Logo": "logo-tag"}
              ,"ProviderIds": {"Tmdb": "603", "Imdb": "tt0133093"}
            }
        """

        private const val EPISODE_ARTWORK_DETAIL_JSON = """
            {
              "Id": "episode-artwork",
              "Name": "Artwork Episode",
              "BackdropImageTags": ["episode-backdrop"],
              "ParentBackdropImageTags": ["series-backdrop"]
            }
        """

        private const val SIMILAR_JSON = """
            {
              "Items": [
                {
                  "Id": "related-1",
                  "Name": "Related One",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "ImageTags": {"Primary": "related-primary-1"}
                },
                {
                  "Id": "related-2",
                  "Name": "Related Two",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "ImageTags": {"Primary": "related-primary-2"}
                }
              ],
              "TotalRecordCount": 2
            }
        """

        private const val PLAYED_USER_DATA_JSON = """
            {
              "PlaybackPositionTicks": 0,
              "PlayCount": 1,
              "Played": true,
              "PlayedPercentage": 100.0,
              "LastPlayedDate": "2026-07-18T12:00:00Z"
            }
        """

        private const val LOCAL_TRAILERS_JSON = """
            [
              {
                "Id": "trailer-1",
                "Name": "Official Trailer",
                "Type": "Trailer",
                "MediaType": "Video",
                "ParentId": "item-1",
                "RunTimeTicks": 1200000000
              }
            ]
        """

        private const val DETAIL_WITH_FAVORITE_JSON = """
            {
              "Id": "movie-1",
              "Name": "Favorite Movie",
              "Overview": "Detailed overview",
              "RunTimeTicks": 36000000000,
              "Genres": ["Adventure"],
              "Studios": [{"Name": "Sample Studio"}],
              "MediaSources": [
                {
                  "Id": "source-1",
                  "Name": "Main",
                  "RunTimeTicks": 36000000000,
                  "Container": "mp4",
                  "SupportsDirectPlay": true,
                  "SupportsTranscoding": true,
                  "MediaStreams": [
                    {"Type": "Video", "Index": 0, "DisplayTitle": "1080p", "Codec": "h264"}
                  ]
                }
              ],
              "ImageTags": {"Primary": "tag-primary"},
              "UserData": {
                "IsFavorite": true,
                "PlaybackPositionTicks": 0,
                "PlayCount": 0,
                "Played": false
              }
            }
        """
    }
}
