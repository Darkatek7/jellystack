package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinBrowseCoordinatorTest {
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
    private var itemPageCallCount = 0
    private var itemPageResponseWithTotal: String? = null
    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val body =
                when {
                    path.endsWith("/Views") -> LIBRARIES_JSON
                    path.endsWith("/Items/Resume") -> RESUME_JSON
                    path.endsWith("/Items/NextUp") -> NEXT_UP_JSON
                    path.endsWith("/Items/Latest") ->
                        when (request.url.parameters["includeItemTypes"]) {
                            "Series,Episode" -> LATEST_SHOWS_JSON
                            "Movie" -> LATEST_MOVIES_JSON
                            else -> error("Unexpected includeItemTypes: ${request.url.parameters}")
                        }
                    path.endsWith("/Items") -> {
                        itemPageResponseWithTotal
                            ?: when (itemPageCallCount++) {
                                0 -> ITEMS_PAGE_1_JSON
                                else -> ITEMS_PAGE_2_JSON
                            }
                    }
                    else -> error("Unexpected request path: $path")
                }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    private val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
    private val apiFactory: JellyfinBrowseApiFactory = { env ->
        JellyfinBrowseApi(
            client = client,
            baseUrl = env.baseUrl,
            accessToken = env.accessToken,
            deviceId = env.deviceId,
            clientName = "Test",
            deviceName = env.deviceName,
            clientVersion = "1.0",
        )
    }

    @Test
    fun bootstrapLoadsLibrariesAndFirstPage() =
        runTest {
            itemPageCallCount = 0
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator =
                JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

            val state = coordinator.state.first { !it.isInitialLoading }
            assertEquals(listOf("lib-1", "lib-2"), state.libraries.map { it.id }, "state=$state")
            assertEquals("lib-2", state.selectedLibraryId, "state=$state")
            assertEquals(2, state.libraryItems.size, "state=$state")
            assertFalse(state.endReached, "state=$state")
            assertEquals("resume-1", state.continueWatching.first().id, "state=$state")
            assertEquals("next-1", state.nextUp.first().id, "state=$state")
        }

    @Test
    fun bootstrapCapturesServerTotalOnFirstPage() =
        runTest {
            itemPageCallCount = 0
            itemPageResponseWithTotal = ITEMS_PAGE_JSON_WITH_TOTAL_215
            try {
                val repository =
                    JellyfinBrowseRepository(
                        environmentProvider,
                        InMemoryLibraryStore(),
                        InMemoryItemStore(),
                        InMemoryDetailStore(),
                        apiFactory,
                    )
                val coordinator =
                    JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

                val state = coordinator.state.first { !it.isInitialLoading }

                assertEquals(215L, state.totalLibraryItemCount, "state=$state")
            } finally {
                itemPageResponseWithTotal = null
            }
        }

    @Test
    fun loadNextPagePreservesServerTotalWhenSubsequentPageOmitsCount() =
        runTest {
            itemPageCallCount = 0
            itemPageResponseWithTotal = ITEMS_PAGE_JSON_WITH_TOTAL_215
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator =
                JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)
            coordinator.state.first { !it.isInitialLoading }
            itemPageResponseWithTotal = null

            coordinator.loadNextPage()

            val state = coordinator.state.first { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(215L, state.totalLibraryItemCount, "state=$state")
        }

    @Test
    fun loadNextPageAppendsItemsAndSetsEndReached() =
        runTest {
            itemPageCallCount = 0
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator =
                JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

            coordinator.state.first { !it.isInitialLoading }

            coordinator.loadNextPage()

            val state = coordinator.state.first { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(3, state.libraryItems.size, "state=$state")
            assertTrue(state.endReached, "state=$state")
            assertEquals("item-3", state.libraryItems.last().id, "state=$state")
        }

    @Test
    fun navigateUpRestoresParentItemsAndPageImmediately() =
        runTest {
            val childRequestStarted = CompletableDeferred<Unit>()
            val releaseChildResponse = CompletableDeferred<Unit>()
            val nestedEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    val body =
                        when {
                            path.endsWith("/Views") -> LIBRARIES_JSON
                            path.endsWith("/Items/Resume") -> RESUME_JSON
                            path.endsWith("/Items/NextUp") -> NEXT_UP_JSON
                            path.endsWith("/Items/Latest") -> "[]"
                            path.endsWith("/Items") && request.url.parameters["ParentId"] == "folder-1" -> {
                                childRequestStarted.complete(Unit)
                                releaseChildResponse.await()
                                CHILD_ITEMS_JSON
                            }
                            path.endsWith("/Items") && request.url.parameters["StartIndex"] == "0" ->
                                PARENT_ITEMS_PAGE_1_JSON
                            path.endsWith("/Items") -> PARENT_ITEMS_PAGE_2_JSON
                            else -> error("Unexpected request path: $path")
                        }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(nestedEngine),
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                )

            coordinator.state.first { !it.isInitialLoading }
            coordinator.loadNextPage()
            val parent = coordinator.state.first { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(
                listOf("folder-1", "parent-2", "parent-3"),
                parent.libraryItems.map { it.id },
            )
            assertEquals(parent, coordinator.state.value)
            coordinator.openContainer(parent.libraryItems.first { it.id == "folder-1" })
            childRequestStarted.await()

            assertTrue(coordinator.navigateUp())

            assertEquals(
                listOf("folder-1", "parent-2", "parent-3"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(1, coordinator.state.value.currentPage)
            assertEquals(emptyList(), coordinator.state.value.browsePath)

            releaseChildResponse.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                listOf("folder-1", "parent-2", "parent-3"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(emptyList(), coordinator.state.value.browsePath)
        }

    @Test
    fun reselectingActiveRootLibraryDoesNotCancelPageLoad() =
        runTest {
            val pageRequestStarted = CompletableDeferred<Unit>()
            val releasePageResponse = CompletableDeferred<Unit>()
            val pagingEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    val body =
                        when {
                            path.endsWith("/Views") -> LIBRARIES_JSON
                            path.endsWith("/Items/Resume") -> RESUME_JSON
                            path.endsWith("/Items/NextUp") -> NEXT_UP_JSON
                            path.endsWith("/Items/Latest") -> "[]"
                            path.endsWith("/Items") && request.url.parameters["StartIndex"] == "0" ->
                                ITEMS_PAGE_1_JSON
                            path.endsWith("/Items") -> {
                                pageRequestStarted.complete(Unit)
                                releasePageResponse.await()
                                ITEMS_PAGE_2_JSON
                            }
                            else -> error("Unexpected request path: $path")
                        }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(pagingEngine),
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                )

            val root = coordinator.state.first { !it.isInitialLoading }
            coordinator.loadNextPage()
            pageRequestStarted.await()
            assertEquals(root.selectedLibraryId, coordinator.state.value.selectedLibraryId)
            assertEquals(emptyList(), coordinator.state.value.browsePath)
            assertTrue(coordinator.state.value.isPageLoading)
            coordinator.selectLibrary(root.selectedLibraryId!!)
            releasePageResponse.complete(Unit)
            runCurrent()

            val loaded = withTimeout(5_000) { coordinator.state.first { it.currentPage == 1 } }
            assertFalse(loaded.isPageLoading)
            assertEquals(listOf("item-1", "item-2", "item-3"), loaded.libraryItems.map { it.id })
        }

    @Test
    fun toggleFavoriteAddsOptimisticallyAndPersists() =
        runTest {
            val apiEngine = favoriteApiEngine(addFavoriteBehavior = FavoriteApiBehavior.Succeed)
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(apiEngine),
                )
            val favoritesStore = FakeJellyfinFavoritesStore()
            val coordinator = JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = favoritesStore, pageSize = 2)

            coordinator.toggleFavorite(favoriteJellyfinItem(id = "movie-1"))
            advanceUntilIdle()

            assertTrue("movie-1" in coordinator.favorites.value, "favorites=${coordinator.favorites.value}")
            assertTrue(favoritesStore.contains("movie-1"), "snapshot=${favoritesStore.snapshot()}")
            assertNull(coordinator.favoriteError.value, "error=${coordinator.favoriteError.value}")
        }

    @Test
    fun toggleFavoriteRevertsOnApiFailure() =
        runTest {
            val apiEngine = favoriteApiEngine(addFavoriteBehavior = FavoriteApiBehavior.FailWithBoom)
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(apiEngine),
                )
            val favoritesStore = FakeJellyfinFavoritesStore()
            val coordinator = JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = favoritesStore, pageSize = 2)

            coordinator.toggleFavorite(favoriteJellyfinItem(id = "movie-1"))
            advanceUntilIdle()

            assertEquals(emptySet(), coordinator.favorites.value, "favorites=${coordinator.favorites.value}")
            assertEquals(emptySet(), favoritesStore.snapshot(), "snapshot=${favoritesStore.snapshot()}")
            assertTrue(coordinator.favoriteError.value != null, "error=${coordinator.favoriteError.value}")
        }

    @Test
    fun toggleFavoritePropagatesCancellation() =
        runTest {
            val cancellingEngine = MockEngine { throw CancellationException("cancel favorite") }
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(cancellingEngine),
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            assertFailsWith<CancellationException> {
                coordinator.toggleFavorite(favoriteJellyfinItem(id = "movie-1"))
            }
        }

    @Test
    fun loadFavoritesReplacesCachedSetFromApi() =
        runTest {
            val apiEngine = favoriteApiEngine(fetchFavoriteIdsJson = FAVORITE_IDS_JSON)
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(apiEngine),
                )
            val favoritesStore = FakeJellyfinFavoritesStore()
            // Seed the cache to make sure loadFavorites replaces (not appends) the contents.
            favoritesStore.seed(setOf("stale-id"))
            val coordinator = JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = favoritesStore, pageSize = 2)

            coordinator.loadFavorites()
            advanceUntilIdle()

            assertEquals(setOf("a", "b", "c"), coordinator.favorites.value, "favorites=${coordinator.favorites.value}")
            assertEquals(setOf("a", "b", "c"), favoritesStore.snapshot(), "snapshot=${favoritesStore.snapshot()}")
        }

    @Test
    fun leavingCompletedFavoritesRestoresPreviousLibraryPageImmediately() =
        runTest {
            val coordinator = favoritesLifecycleCoordinator(favoritesLifecycleEngine(), backgroundScope)
            val parent = coordinator.state.first { !it.isInitialLoading }

            coordinator.selectFavorites()
            val favoritesPage =
                coordinator.state.first { state ->
                    !state.isInitialLoading && state.libraryItems.map { it.id } == listOf("favorite-1")
                }

            assertEquals(listOf("favorite-1"), favoritesPage.libraryItems.map { it.id })
            assertTrue(coordinator.leaveFavorites())
            assertEquals(
                parent.libraryItems.map { it.id },
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(parent.selectedLibraryId, coordinator.state.value.selectedLibraryId)
            assertEquals(parent.currentPage, coordinator.state.value.currentPage)
        }

    @Test
    fun leavingFavoritesWhileIdsAreBlockedPreventsLateIdsAndItems() =
        runTest {
            val favoriteIdsStarted = CompletableDeferred<Unit>()
            val releaseFavoriteIds = CompletableDeferred<Unit>()
            var favoritePageRequests = 0
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests += 1 },
                    ),
                    backgroundScope,
                )
            val parent = coordinator.state.first { !it.isInitialLoading }

            coordinator.selectFavorites()
            favoriteIdsStarted.await()
            assertTrue(coordinator.leaveFavorites())
            releaseFavoriteIds.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                parent.libraryItems.map { it.id },
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(emptySet(), coordinator.favorites.value)
            assertEquals(0, favoritePageRequests)
        }

    @Test
    fun shutdownWhileFavoriteIdsAreBlockedInvalidatesEntireFavoritesLoad() =
        runTest {
            val favoriteIdsStarted = CompletableDeferred<Unit>()
            val releaseFavoriteIds = CompletableDeferred<Unit>()
            var favoritePageRequests = 0
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests += 1 },
                    ),
                    backgroundScope,
                )
            val parent = coordinator.state.first { !it.isInitialLoading }

            coordinator.selectFavorites()
            favoriteIdsStarted.await()
            coordinator.shutdown()
            releaseFavoriteIds.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                parent.libraryItems.map { it.id },
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(emptySet(), coordinator.favorites.value)
            assertEquals(0, favoritePageRequests)
        }

    @Test
    fun serverBootstrapWhileFavoriteIdsAreBlockedPreventsOldFavoritesPublishing() =
        runTest {
            val favoriteIdsStarted = CompletableDeferred<Unit>()
            val releaseFavoriteIds = CompletableDeferred<Unit>()
            var favoritePageRequests = 0
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests += 1 },
                    ),
                    backgroundScope,
                )
            coordinator.state.first { !it.isInitialLoading }

            coordinator.selectFavorites()
            favoriteIdsStarted.await()
            coordinator.bootstrap(forceRefresh = true)
            releaseFavoriteIds.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("normal-1"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(emptySet(), coordinator.favorites.value)
            assertEquals(0, favoritePageRequests)
        }

    @Test
    fun refreshingFavoritesReloadsIdsAndOnlyTheFavoriteFilteredPage() =
        runTest {
            var favoriteIdsRequests = 0
            var favoritePageRequests = 0
            var unfilteredPageRequests = 0
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        favoriteIdsJson = {
                            favoriteIdsRequests += 1
                            if (favoriteIdsRequests == 1) FAVORITE_ONE_IDS_JSON else FAVORITE_TWO_IDS_JSON
                        },
                        favoritePageJson = {
                            favoritePageRequests += 1
                            if (favoritePageRequests == 1) FAVORITE_ONE_PAGE_JSON else FAVORITE_TWO_PAGE_JSON
                        },
                        onUnfilteredPage = { unfilteredPageRequests += 1 },
                    ),
                    backgroundScope,
                )
            coordinator.state.first { !it.isInitialLoading }
            assertEquals(1, unfilteredPageRequests)
            coordinator.selectFavorites()
            coordinator.state.first { it.libraryItems.map { item -> item.id } == listOf("favorite-1") }

            coordinator.refreshFavorites()
            val refreshed =
                coordinator.state.first { state ->
                    !state.isInitialLoading && state.libraryItems.map { item -> item.id } == listOf("favorite-2")
                }

            assertEquals(setOf("favorite-2"), coordinator.favorites.value)
            assertEquals(listOf("favorite-2"), refreshed.libraryItems.map { it.id })
            assertEquals(2, favoriteIdsRequests)
            assertEquals(2, favoritePageRequests)
            assertEquals(1, unfilteredPageRequests)
        }

    @Test
    fun refreshingLibrariesUpdatesOnlyTheLibraryList() =
        runTest {
            var libraryRequests = 0
            var itemRequests = 0
            val refreshOnlyEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/Views") -> {
                            libraryRequests += 1
                            respond(
                                content = REFRESHED_LIBRARIES_JSON,
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders(),
                            )
                        }
                        path.endsWith("/Items") -> {
                            itemRequests += 1
                            error("Library-list refresh must not load a media page")
                        }
                        else -> error("Unexpected request path: $path")
                    }
                }
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = favoritesLifecycleRepository(refreshOnlyEngine),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.refreshLibraries()
            val state = coordinator.state.first { it.libraries.map { library -> library.id } == listOf("lib-refreshed") }

            assertEquals(listOf("Refreshed"), state.libraries.map { it.name })
            assertEquals(1, libraryRequests)
            assertEquals(0, itemRequests)
        }

    @Test
    fun toggleFavoriteUpdatesFavoritesGridLibraryItems() =
        runTest {
            val apiEngine = favoriteApiEngine(addFavoriteBehavior = FavoriteApiBehavior.Succeed)
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactoryFrom(apiEngine),
                )
            val favoritesStore = FakeJellyfinFavoritesStore()
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = favoritesStore,
                    pageSize = 2,
                    autoBootstrap = false,
                )

            // Simulate the Favorites subview being rendered with two items and no favourites yet.
            val itemA = favoriteJellyfinItem(id = "movie-1")
            val itemB = favoriteJellyfinItem(id = "movie-2")
            coordinator.setLibraryItemsForTest(listOf(itemA, itemB))

            assertEquals(
                listOf("movie-1", "movie-2"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )

            // Toggling itemA on adds it to favourites; it stays in the rendered grid (which shows
            // favourited items).
            coordinator.toggleFavorite(itemA)
            advanceUntilIdle()

            assertEquals(
                listOf("movie-1", "movie-2"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(setOf("movie-1"), coordinator.favorites.value)

            // Toggling itemA off removes it from favourites; the grid should drop it.
            coordinator.toggleFavorite(itemA)
            advanceUntilIdle()

            assertEquals(
                listOf("movie-2"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertEquals(emptySet(), coordinator.favorites.value)
        }

    @Test
    fun bootstrapPrefersShowsLibraryOverRepositoryOrder() =
        runTest {
            var localPageCallCount = 0
            val orderedLibrariesJson =
                """
                {
                  "Items": [
                    {"Id":"lib-movies","Name":"Movies","CollectionType":"movies","ImageTags":{"Primary":"movies-tag"}},
                    {"Id":"lib-bonus","Name":"Bonus","CollectionType":"music","ImageTags":{"Primary":"bonus-tag"}},
                    {"Id":"lib-shows","Name":"Shows","CollectionType":"tvshows","ImageTags":{"Primary":"shows-tag"}}
                  ]
                }
                """.trimIndent()
            val orderedEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    val body =
                        when {
                            path.endsWith("/Views") -> orderedLibrariesJson
                            path.endsWith("/Items/Resume") -> RESUME_JSON
                            path.endsWith("/Items/NextUp") -> NEXT_UP_JSON
                            path.endsWith("/Items/Latest") ->
                                when (request.url.parameters["includeItemTypes"]) {
                                    "Series,Episode" -> LATEST_SHOWS_JSON
                                    "Movie" -> LATEST_MOVIES_JSON
                                    else -> error("Unexpected includeItemTypes: ${request.url.parameters}")
                                }
                            path.endsWith("/Items") ->
                                when (localPageCallCount++) {
                                    0 -> ITEMS_PAGE_1_JSON
                                    else -> ITEMS_PAGE_2_JSON
                                }
                            else -> error("Unexpected request path: $path")
                        }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val orderedClient = NetworkClientFactory.create(ClientConfig(engine = orderedEngine, installLogging = false))
            val orderedApiFactory: JellyfinBrowseApiFactory = { env ->
                JellyfinBrowseApi(
                    client = orderedClient,
                    baseUrl = env.baseUrl,
                    accessToken = env.accessToken,
                    deviceId = env.deviceId,
                    clientName = "Test",
                    deviceName = env.deviceName,
                    clientVersion = "1.0",
                )
            }
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    InMemoryLibraryStore(),
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    orderedApiFactory,
                )
            val coordinator =
                JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

            val state = coordinator.state.first { !it.isInitialLoading }
            assertEquals("lib-shows", state.selectedLibraryId, "state=$state")
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
            serverRecords.entries.removeIf { it.value.libraryId == libraryId }
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
            if (keepIds.isEmpty()) {
                serverRecords.keys.forEach { id ->
                    val record = serverRecords[id] ?: return@forEach
                    if ((record.positionTicks ?: 0L) > 0L) {
                        serverRecords[id] =
                            record.copy(positionTicks = null, playedPercentage = null, lastPlayed = null)
                    }
                }
            } else {
                serverRecords.keys.forEach { id ->
                    if (id in keepIds) return@forEach
                    val record = serverRecords[id] ?: return@forEach
                    if ((record.positionTicks ?: 0L) > 0L) {
                        serverRecords[id] =
                            record.copy(positionTicks = null, playedPercentage = null, lastPlayed = null)
                    }
                }
            }
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
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.seriesId == seriesId }
                ?.sortedBy { it.indexNumber ?: Long.MAX_VALUE }
                ?: emptyList()

        override suspend fun listEpisodesForSeason(
            serverId: String,
            seasonId: String,
        ): List<JellyfinItemRecord> =
            records[serverId]
                ?.values
                ?.filter { it.seasonId == seasonId }
                ?.sortedBy { it.indexNumber ?: Long.MAX_VALUE }
                ?: emptyList()

        override suspend fun get(itemId: String): JellyfinItemRecord? = records.values.firstNotNullOfOrNull { it[itemId] }
    }

    private class InMemoryDetailStore : JellyfinItemDetailStore {
        private val records = mutableMapOf<String, JellyfinItemDetailRecord>()

        override suspend fun get(itemId: String): JellyfinItemDetailRecord? = records[itemId]

        override suspend fun upsert(record: JellyfinItemDetailRecord) {
            records[record.itemId] = record
        }
    }

    private enum class FavoriteApiBehavior {
        Succeed,
        FailWithBoom,
    }

    private fun favoriteApiEngine(
        addFavoriteBehavior: FavoriteApiBehavior = FavoriteApiBehavior.Succeed,
        fetchFavoriteIdsJson: String = "{\"Items\":[]}",
    ): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val headers =
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                addFavoriteBehavior == FavoriteApiBehavior.FailWithBoom &&
                    request.method == HttpMethod.Post &&
                    path.endsWith("/Users/user-123/FavoriteItems/movie-1") -> {
                    respond("boom", HttpStatusCode.InternalServerError, headers)
                }
                request.method == HttpMethod.Post &&
                    path.endsWith("/Users/user-123/FavoriteItems/movie-1") -> {
                    respond("", HttpStatusCode.NoContent, headers)
                }
                request.method == HttpMethod.Delete &&
                    path.endsWith("/Users/user-123/FavoriteItems/movie-1") -> {
                    respond("", HttpStatusCode.NoContent, headers)
                }
                request.method == HttpMethod.Get &&
                    path.endsWith("/Users/user-123/Items") &&
                    request.url.parameters["Filters"] == "IsFavorite" -> {
                    respond(fetchFavoriteIdsJson, HttpStatusCode.OK, headers)
                }
                path.endsWith("/Views") -> respond(LIBRARIES_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/Resume") -> respond(RESUME_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/NextUp") -> respond(NEXT_UP_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/Latest") ->
                    when (request.url.parameters["includeItemTypes"]) {
                        "Series,Episode" -> respond(LATEST_SHOWS_JSON, HttpStatusCode.OK, headers)
                        "Movie" -> respond(LATEST_MOVIES_JSON, HttpStatusCode.OK, headers)
                        else -> respond("[]", HttpStatusCode.OK, headers)
                    }
                path.endsWith("/Items") ->
                    respond(
                        "{\"Items\":[]}",
                        HttpStatusCode.OK,
                        headers,
                    )
                else -> respond("not found", HttpStatusCode.NotFound, headers)
            }
        }

    private fun favoritesLifecycleCoordinator(
        engine: MockEngine,
        scope: CoroutineScope,
    ): JellyfinBrowseCoordinator =
        JellyfinBrowseCoordinator(
            repository = favoritesLifecycleRepository(engine),
            scope = scope,
            favoritesStore = FakeJellyfinFavoritesStore(),
            pageSize = 2,
        )

    private fun favoritesLifecycleRepository(engine: MockEngine): JellyfinBrowseRepository =
        JellyfinBrowseRepository(
            environmentProvider,
            InMemoryLibraryStore(),
            InMemoryItemStore(),
            InMemoryDetailStore(),
            apiFactoryFrom(engine),
        )

    private fun favoritesLifecycleEngine(
        favoriteIdsJson: () -> String = { FAVORITE_ONE_IDS_JSON },
        favoritePageJson: () -> String = { FAVORITE_ONE_PAGE_JSON },
        onFavoriteIds: suspend () -> Unit = {},
        onFavoritePage: () -> Unit = {},
        onUnfilteredPage: () -> Unit = {},
    ): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val headers = jsonHeaders()
            when {
                path.endsWith("/Views") -> respond(LIBRARIES_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/Resume") -> respond(RESUME_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/NextUp") -> respond(NEXT_UP_JSON, HttpStatusCode.OK, headers)
                path.endsWith("/Items/Latest") -> respond("[]", HttpStatusCode.OK, headers)
                path.endsWith("/Items") &&
                    request.url.parameters["Filters"] == "IsFavorite" &&
                    request.url.parameters["Limit"] == "10000" -> {
                    onFavoriteIds()
                    respond(favoriteIdsJson(), HttpStatusCode.OK, headers)
                }
                path.endsWith("/Items") && request.url.parameters["Filters"] == "IsFavorite" -> {
                    onFavoritePage()
                    respond(favoritePageJson(), HttpStatusCode.OK, headers)
                }
                path.endsWith("/Items") -> {
                    onUnfilteredPage()
                    respond(NORMAL_LIBRARY_PAGE_JSON, HttpStatusCode.OK, headers)
                }
                else -> error("Unexpected request path: $path")
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    companion object {
        private const val LIBRARIES_JSON = """
            {
              "Items": [
                {"Id": "lib-1", "Name": "Movies", "CollectionType": "movies"},
                {"Id": "lib-2", "Name": "Shows", "CollectionType": "tvshows"}
              ]
            }
        """

        private const val RESUME_JSON = """
            {
              "Items": [
                {
                  "Id": "resume-1",
                  "Name": "Resume Item",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "RunTimeTicks": 36000000000,
                  "UserData": {"PlaybackPositionTicks": 18000000000},
                  "ImageTags": {"Primary": "resume-tag"}
                }
              ]
            }
        """

        private const val NEXT_UP_JSON = """
            {
              "Items": [
                {
                  "Id": "next-1",
                  "Name": "Next Episode",
                  "Type": "Episode",
                  "SeriesId": "series-1",
                  "ParentId": "series-1",
                  "IndexNumber": 2,
                  "ParentIndexNumber": 1,
                  "RunTimeTicks": 30000000000,
                  "ImageTags": {"Primary": "next-tag"},
                  "UserData": {"PlaybackPositionTicks": 0}
                }
              ]
            }
        """

        private const val ITEMS_PAGE_1_JSON = """
            {
              "Items": [
                {
                  "Id": "item-1",
                  "Name": "Alpha Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-primary"}
                },
                {
                  "Id": "item-2",
                  "Name": "Beta Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Another overview",
                  "RunTimeTicks": 30000000000,
                  "ImageTags": {"Primary": "tag-secondary"}
                }
              ]
            }
        """

        private const val ITEMS_PAGE_2_JSON = """
            {
              "Items": [
                {
                  "Id": "item-3",
                  "Name": "Gamma Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Final overview",
                  "RunTimeTicks": 42000000000,
                  "ImageTags": {"Primary": "tag-third"}
                }
              ]
            }
        """

        private const val PARENT_ITEMS_PAGE_1_JSON = """
            {
              "Items": [
                {"Id":"folder-1","Name":"Albums","Type":"Folder"},
                {"Id":"parent-2","Name":"Parent two","Type":"Movie","MediaType":"Video"}
              ],
              "TotalRecordCount": 3
            }
        """

        private const val PARENT_ITEMS_PAGE_2_JSON = """
            {
              "Items": [
                {"Id":"parent-3","Name":"Parent three","SortName":"Parent zthree","Type":"Movie","MediaType":"Video"}
              ],
              "TotalRecordCount": 3
            }
        """

        private const val CHILD_ITEMS_JSON = """
            {
              "Items": [
                {"Id":"child-1","Name":"Child album","Type":"MusicAlbum"}
              ],
              "TotalRecordCount": 1
            }
        """

        private const val ITEMS_PAGE_JSON_WITH_TOTAL_215 = """
            {
              "Items": [
                {
                  "Id": "item-1",
                  "Name": "Alpha Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "A sample overview",
                  "RunTimeTicks": 36000000000,
                  "ImageTags": {"Primary": "tag-primary"}
                },
                {
                  "Id": "item-2",
                  "Name": "Beta Movie",
                  "Type": "Movie",
                  "MediaType": "Video",
                  "Overview": "Another overview",
                  "RunTimeTicks": 30000000000,
                  "ImageTags": {"Primary": "tag-secondary"}
                }
              ],
              "TotalRecordCount": 215
            }
        """

        private const val LATEST_SHOWS_JSON = "[]"

        private const val LATEST_MOVIES_JSON = "[]"

        private const val FAVORITE_IDS_JSON = """
            {
              "Items": [
                {"Id": "a", "Name": "A", "Type": "Movie"},
                {"Id": "b", "Name": "B", "Type": "Movie"},
                {"Id": "c", "Name": "C", "Type": "Movie"}
              ],
              "TotalRecordCount": 3
            }
        """

        private const val NORMAL_LIBRARY_PAGE_JSON = """
            {
              "Items": [
                {"Id":"normal-1","Name":"Normal one","Type":"Movie","MediaType":"Video"}
              ],
              "TotalRecordCount": 1
            }
        """

        private const val FAVORITE_ONE_IDS_JSON = """
            {"Items":[{"Id":"favorite-1","Name":"Favorite one","Type":"Movie"}]}
        """

        private const val FAVORITE_TWO_IDS_JSON = """
            {"Items":[{"Id":"favorite-2","Name":"Favorite two","Type":"Movie"}]}
        """

        private const val FAVORITE_ONE_PAGE_JSON = """
            {"Items":[{"Id":"favorite-1","Name":"Favorite one","Type":"Movie","MediaType":"Video"}]}
        """

        private const val FAVORITE_TWO_PAGE_JSON = """
            {"Items":[{"Id":"favorite-2","Name":"Favorite two","Type":"Movie","MediaType":"Video"}]}
        """

        private const val REFRESHED_LIBRARIES_JSON = """
            {"Items":[{"Id":"lib-refreshed","Name":"Refreshed","CollectionType":"movies"}]}
        """
    }

    private fun apiFactoryFrom(engine: MockEngine): JellyfinBrowseApiFactory =
        { env ->
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            JellyfinBrowseApi(
                client = client,
                baseUrl = env.baseUrl,
                accessToken = env.accessToken,
                deviceId = env.deviceId,
                clientName = "Test",
                deviceName = env.deviceName,
                clientVersion = "1.0",
            )
        }

    private fun favoriteJellyfinItem(id: String): JellyfinItem =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = "Favorite $id",
            sortName = null,
            overview = null,
            type = "Movie",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = null,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = null,
            seriesName = null,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
        )
}

/**
 * In-memory implementation of [JellyfinFavoritesStoreApi] for tests. Doesn't depend on SQLDelight — suitable
 * for coordinator-level tests where we exercise the flow/observe/replace lifecycle.
 */
class FakeJellyfinFavoritesStore(
    private val clock: Clock = Clock.System,
) : JellyfinFavoritesStoreApi {
    private val mutableSnapshot = MutableStateFlow<Set<String>>(emptySet())

    fun seed(ids: Set<String>) {
        mutableSnapshot.value = ids.toSet()
    }

    fun contains(id: String): Boolean = id in mutableSnapshot.value

    override fun snapshot(): Set<String> = mutableSnapshot.value

    override fun observe(): Flow<Set<String>> = mutableSnapshot.asStateFlow()

    override suspend fun replaceAll(ids: Set<String>) {
        mutableSnapshot.value = ids.toSet()
        // Touch clock to keep test parity with the real store's behavior (which records now()).
        clock.now()
    }

    override suspend fun upsert(id: String) {
        mutableSnapshot.value = mutableSnapshot.value + id
    }

    override suspend fun delete(id: String) {
        mutableSnapshot.value = mutableSnapshot.value - id
    }
}
