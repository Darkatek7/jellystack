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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    private val viewsCallCount = MutableStateFlow(0)
    private val itemPageCallCount = MutableStateFlow(0)
    private val latestCallCount = MutableStateFlow(0)
    private val itemPageResponseWithTotal = MutableStateFlow<String?>(null)
    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val body =
                when {
                    path.endsWith("/Views") -> {
                        viewsCallCount.update { it + 1 }
                        LIBRARIES_JSON
                    }
                    path.endsWith("/Items/Resume") -> RESUME_JSON
                    path.endsWith("/Items/NextUp") -> NEXT_UP_JSON
                    path.endsWith("/Items/Latest") -> {
                        latestCallCount.update { count -> count + 1 }
                        when (request.url.parameters["includeItemTypes"]) {
                            "Series,Episode" -> LATEST_SHOWS_JSON
                            "Movie" -> LATEST_MOVIES_JSON
                            else -> error("Unexpected includeItemTypes: ${request.url.parameters}")
                        }
                    }
                    path.endsWith("/Items") -> {
                        itemPageResponseWithTotal.value
                            ?: when (itemPageCallCount.updateAndGet { it + 1 } - 1) {
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
    fun listLibrariesFailureClearsHomeLoadingAndPublishesOnlyHomeError() =
        runTest {
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = FakeBrowseRepository(loadLibraries = { error("library cache boom") }),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.bootstrap(forceRefresh = false)

            val failed = awaitState(coordinator) { it.homeErrorMessage == "library cache boom" }
            assertFalse(failed.isInitialLoading)
            assertFalse(failed.isHomeLoading)
            assertNull(failed.libraryErrorMessage)
        }

    @Test
    fun cachedStateFailureClearsHomeLoadingAndPublishesOnlyHomeError() =
        runTest {
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = FakeBrowseRepository(loadCachedContinueWatching = { error("home cache boom") }),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.bootstrap(forceRefresh = false)

            val failed = awaitState(coordinator) { it.homeErrorMessage == "home cache boom" }
            assertFalse(failed.isInitialLoading)
            assertFalse(failed.isHomeLoading)
            assertNull(failed.libraryErrorMessage)
        }

    @Test
    fun olderNonCooperativeBootstrapSuccessCannotOverwriteNewerBootstrap() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val firstReturned = CompletableDeferred<Unit>()
            val calls = MutableStateFlow(0)
            val oldLibraries = listOf(JellyfinLibrary("old", "Old", "movies", null, "old-image"))
            val newLibraries = listOf(JellyfinLibrary("new", "New", "movies", null, "new-image"))
            val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadLibraries = {
                                if (calls.updateAndGet { it + 1 } == 1) {
                                    firstStarted.complete(Unit)
                                    withContext(NonCancellable) {
                                        releaseFirst.await()
                                        firstReturned.complete(Unit)
                                        oldLibraries
                                    }
                                } else {
                                    newLibraries
                                }
                            },
                            loadPage = { libraryId, _, _, _, _ ->
                                LibraryPage(listOf(favoriteJellyfinItem("$libraryId-item")), 1)
                            },
                        ),
                    scope = coordinatorScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )
            try {
                coordinator.bootstrap(forceRefresh = false)
                firstStarted.await()
                coordinator.bootstrap(forceRefresh = false)
                awaitState(coordinator) { !it.isHomeLoading && it.libraries == newLibraries }

                releaseFirst.complete(Unit)
                firstReturned.await()
                delay(100)

                assertEquals(newLibraries, coordinator.state.value.libraries)
                assertEquals("new", coordinator.state.value.selectedLibraryId)
                assertEquals(
                    listOf("new-item"),
                    coordinator.state.value.libraryItems
                        .map { it.id },
                )
                assertNull(coordinator.state.value.homeErrorMessage)
            } finally {
                releaseFirst.complete(Unit)
                coordinatorScope.cancel()
            }
        }

    @Test
    fun olderNonCooperativeBootstrapFailureCannotOverwriteNewerBootstrap() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val firstReturned = CompletableDeferred<Unit>()
            val calls = MutableStateFlow(0)
            val newLibraries = listOf(JellyfinLibrary("new", "New", "movies", null, "new-image"))
            val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadLibraries = {
                                if (calls.updateAndGet { it + 1 } == 1) {
                                    firstStarted.complete(Unit)
                                    withContext(NonCancellable) {
                                        releaseFirst.await()
                                        firstReturned.complete(Unit)
                                        error("old bootstrap boom")
                                    }
                                } else {
                                    newLibraries
                                }
                            },
                            loadPage = { _, _, _, _, _ -> LibraryPage(emptyList(), 0) },
                        ),
                    scope = coordinatorScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )
            try {
                coordinator.bootstrap(forceRefresh = false)
                firstStarted.await()
                coordinator.bootstrap(forceRefresh = false)
                awaitState(coordinator) { !it.isHomeLoading && it.libraries == newLibraries }

                releaseFirst.complete(Unit)
                firstReturned.await()
                delay(100)

                assertEquals(newLibraries, coordinator.state.value.libraries)
                assertNull(coordinator.state.value.homeErrorMessage)
                assertFalse(coordinator.state.value.isInitialLoading)
                assertFalse(coordinator.state.value.isHomeLoading)
            } finally {
                releaseFirst.complete(Unit)
                coordinatorScope.cancel()
            }
        }

    @Test
    fun newerCatalogRefreshOwnsPublicationAgainstNonCooperativeOlderRefresh() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val firstReturned = CompletableDeferred<Unit>()
            val calls = MutableStateFlow(0)
            val oldLibraries = listOf(JellyfinLibrary("old", "Old", "movies", null, "old-image"))
            val newLibraries = listOf(JellyfinLibrary("new", "New", "movies", null, "new-image"))
            val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            refreshLibraryCatalog = {
                                if (calls.updateAndGet { it + 1 } == 1) {
                                    firstStarted.complete(Unit)
                                    withContext(NonCancellable) {
                                        releaseFirst.await()
                                        firstReturned.complete(Unit)
                                        oldLibraries
                                    }
                                } else {
                                    newLibraries
                                }
                            },
                        ),
                    scope = coordinatorScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )
            try {
                coordinator.refreshLibraries()
                firstStarted.await()
                coordinator.refreshLibraries()
                awaitState(coordinator) { it.libraries == newLibraries }

                releaseFirst.complete(Unit)
                firstReturned.await()
                delay(100)

                assertEquals(newLibraries, coordinator.state.value.libraries)
                assertNull(coordinator.state.value.homeErrorMessage)
            } finally {
                releaseFirst.complete(Unit)
                coordinatorScope.cancel()
            }
        }

    @Test
    fun homeFailureDoesNotBecomeLibraryErrorWhenSelectedLibrarySucceeds() =
        runTest {
            val selectedItem = favoriteJellyfinItem("selected-item")
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadLibraries = { error("home boom") },
                            loadPage = { _, _, _, _, _ -> LibraryPage(listOf(selectedItem), 1) },
                        ),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.bootstrap(forceRefresh = false)
            awaitState(coordinator) { it.homeErrorMessage == "home boom" }
            coordinator.selectLibrary("lib-1")

            val loaded = awaitState(coordinator) { !it.isLibraryLoading && it.libraryItems == listOf(selectedItem) }
            assertEquals("home boom", loaded.homeErrorMessage)
            assertNull(loaded.libraryErrorMessage)
        }

    @Test
    fun bootstrapLoadsLibrariesAndFirstPage() =
        runTest {
            itemPageCallCount.value = 0
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

            val state = awaitInitialLoad(coordinator)
            assertEquals(listOf("lib-1", "lib-2"), state.libraries.map { it.id }, "state=$state")
            assertEquals("lib-2", state.selectedLibraryId, "state=$state")
            assertEquals(2, state.libraryItems.size, "state=$state")
            assertFalse(state.endReached, "state=$state")
            assertEquals("resume-1", state.continueWatching.first().id, "state=$state")
            assertEquals("next-1", state.nextUp.first().id, "state=$state")
        }

    @Test
    fun bootstrapRefreshesLegacyCachedLibrariesWithoutImageTags() =
        runTest {
            viewsCallCount.value = 0
            itemPageCallCount.value = 0
            val libraryStore = InMemoryLibraryStore()
            val timestamp = Instant.parse("2026-01-01T00:00:00Z")
            libraryStore.replaceAll(
                environment.serverKey,
                listOf(
                    JellyfinLibraryRecord(
                        id = "legacy-lib",
                        serverId = environment.serverKey,
                        name = "Legacy",
                        collectionType = "movies",
                        primaryImageTag = null,
                        itemCount = null,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                ),
            )
            val repository =
                JellyfinBrowseRepository(
                    environmentProvider,
                    libraryStore,
                    InMemoryItemStore(),
                    InMemoryDetailStore(),
                    apiFactory,
                )
            val coordinator =
                JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

            val state = awaitInitialLoad(coordinator)

            assertEquals(1, viewsCallCount.value)
            assertEquals("movies-primary", state.libraries.first().primaryImageTag)
        }

    @Test
    fun bootstrapCapturesServerTotalOnFirstPage() =
        runTest {
            itemPageCallCount.value = 0
            itemPageResponseWithTotal.value = ITEMS_PAGE_JSON_WITH_TOTAL_215
            try {
                val itemStore = InMemoryItemStore()
                val repository =
                    JellyfinBrowseRepository(
                        environmentProvider,
                        InMemoryLibraryStore(),
                        itemStore,
                        InMemoryDetailStore(),
                        apiFactory,
                    )
                repository.refreshNextUp(limit = 20, libraryId = "lib-2")
                val coordinator =
                    JellyfinBrowseCoordinator(repository, backgroundScope, favoritesStore = FakeJellyfinFavoritesStore(), pageSize = 2)

                val state = awaitInitialLoad(coordinator)

                assertEquals(215L, state.totalLibraryItemCount, "state=$state")
            } finally {
                itemPageResponseWithTotal.value = null
            }
        }

    @Test
    fun loadNextPagePreservesServerTotalWhenSubsequentPageOmitsCount() =
        runTest {
            itemPageCallCount.value = 0
            itemPageResponseWithTotal.value = ITEMS_PAGE_JSON_WITH_TOTAL_215
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
            awaitInitialLoad(coordinator)
            itemPageResponseWithTotal.value = null

            coordinator.loadNextPage()

            val state = awaitState(coordinator) { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(215L, state.totalLibraryItemCount, "state=$state")
        }

    @Test
    fun loadNextPageAppendsItemsAndSetsEndReached() =
        runTest {
            itemPageCallCount.value = 0
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

            awaitInitialLoad(coordinator)

            coordinator.loadNextPage()

            val state = awaitState(coordinator) { it.currentPage == 1 && !it.isPageLoading }
            assertEquals(3, state.libraryItems.size, "state=$state")
            assertTrue(state.endReached, "state=$state")
            assertEquals("item-3", state.libraryItems.last().id, "state=$state")
        }

    @Test
    fun nonDefaultQueryUsesSessionPagesAndKeepsPagingBoundaries() =
        runTest {
            val queryCalls = mutableListOf<Pair<Int, LibraryBrowseQuery>>()
            val query =
                LibraryBrowseQuery(
                    sort = LibraryBrowseSort.DATE_ADDED,
                    direction = LibraryBrowseDirection.DESCENDING,
                    played = LibraryPlayedFilter.UNPLAYED,
                )
            val repository =
                FakeBrowseRepository(
                    loadPage = { _, _, _, _, _ -> LibraryPage(emptyList(), 0) },
                    loadQueryPage = { _, page, _, requested, policy ->
                        assertEquals(LibraryCachePolicy.SESSION_ONLY, policy)
                        queryCalls += page to requested
                        when (page) {
                            0 -> LibraryPage(listOf(favoriteJellyfinItem("query-1"), favoriteJellyfinItem("query-2")), 3)
                            else -> LibraryPage(listOf(favoriteJellyfinItem("query-3")), 3)
                        }
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                    autoBootstrap = false,
                )
            coordinator.selectLibrary("lib-1")
            coordinator.setLibraryBrowseQuery(query)
            val firstPage = awaitState(coordinator) { !it.isLibraryLoading && it.libraryItems.size == 2 }

            assertEquals(
                listOf("query-1", "query-2"),
                firstPage.libraryItems.map { it.id },
            )
            assertEquals(query, firstPage.libraryBrowseQuery)
            coordinator.loadNextPage()
            val secondPage = awaitState(coordinator) { !it.isPageLoading && it.currentPage == 1 }

            assertEquals(
                listOf("query-1", "query-2", "query-3"),
                secondPage.libraryItems.map { it.id },
            )
            assertTrue(secondPage.endReached)
            assertEquals(listOf(0 to query, 1 to query), queryCalls)
        }

    @Test
    fun replacingQueryCancelsPreviousGenerationBeforeItCanPublish() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val first = LibraryBrowseQuery(genres = setOf("Drama"))
            val second = LibraryBrowseQuery(genres = setOf("Comedy"))
            val repository =
                FakeBrowseRepository(
                    loadQueryPage = { _, _, _, query, _ ->
                        if (query == first) {
                            firstStarted.complete(Unit)
                            withContext(NonCancellable) { releaseFirst.await() }
                            LibraryPage(listOf(favoriteJellyfinItem("stale")), 1)
                        } else {
                            LibraryPage(listOf(favoriteJellyfinItem("current")), 1)
                        }
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository,
                    backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )
            coordinator.selectLibrary("lib-1")
            coordinator.setLibraryBrowseQuery(first)
            firstStarted.await()
            coordinator.setLibraryBrowseQuery(second)
            releaseFirst.complete(Unit)
            val state = awaitState(coordinator) { !it.isLibraryLoading && it.libraryItems.isNotEmpty() }

            assertEquals(second, state.libraryBrowseQuery)
            assertEquals(
                listOf("current"),
                state.libraryItems.map { it.id },
            )
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

            awaitInitialLoad(coordinator)
            coordinator.loadNextPage()
            advanceUntilIdle()
            val parent = awaitState(coordinator) { it.currentPage == 1 && !it.isPageLoading }
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

            val root = awaitInitialLoad(coordinator)
            coordinator.loadNextPage()
            pageRequestStarted.await()
            assertEquals(root.selectedLibraryId, coordinator.state.value.selectedLibraryId)
            assertEquals(emptyList(), coordinator.state.value.browsePath)
            assertTrue(coordinator.state.value.isPageLoading)
            coordinator.selectLibrary(root.selectedLibraryId!!)
            releasePageResponse.complete(Unit)

            val loaded = awaitState(coordinator) { it.currentPage == 1 }
            assertFalse(loaded.isPageLoading)
            assertEquals(listOf("item-1", "item-2", "item-3"), loaded.libraryItems.map { it.id })
        }

    @Test
    fun selectingAnotherLibraryDoesNotRestartOrClearHomeContent() =
        runTest {
            itemPageCallCount.value = 0
            latestCallCount.value = 0
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
            val home = awaitInitialLoad(coordinator)
            val latestCallsAfterBootstrap = latestCallCount.value

            coordinator.selectLibrary("lib-1")
            val library = awaitState(coordinator) { it.selectedLibraryId == "lib-1" && !it.isLibraryLoading }

            assertFalse(library.isHomeLoading)
            assertEquals(home.recentShows, library.recentShows)
            assertEquals(home.recentMovies, library.recentMovies)
            assertEquals(home.nextUp, library.nextUp)
            assertEquals(latestCallsAfterBootstrap, latestCallCount.value)
        }

    @Test
    fun blockedHomeFeedDoesNotDelayLibrarySelection() =
        runTest {
            val homeFeedStarted = CompletableDeferred<Unit>()
            val releaseHomeFeed = CompletableDeferred<Unit>()
            val selectedLibraryPageStarted = CompletableDeferred<Unit>()
            val selectedItem = favoriteJellyfinItem("selected-item")
            val repository =
                FakeBrowseRepository(
                    loadPage = { libraryId, _, _, _, _ ->
                        if (libraryId == "lib-1") {
                            selectedLibraryPageStarted.complete(Unit)
                            LibraryPage(listOf(selectedItem), 1)
                        } else {
                            LibraryPage(listOf(favoriteJellyfinItem("bootstrap-item")), 1)
                        }
                    },
                    loadRecentShows = { _, _ ->
                        homeFeedStarted.complete(Unit)
                        releaseHomeFeed.await()
                        emptyList()
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                )

            homeFeedStarted.await()
            coordinator.selectLibrary("lib-1")

            withTimeout(1_000) { selectedLibraryPageStarted.await() }
            releaseHomeFeed.complete(Unit)
            val selected = awaitState(coordinator) { it.selectedLibraryId == "lib-1" && !it.isLibraryLoading }
            assertEquals(listOf("selected-item"), selected.libraryItems.map { it.id })
        }

    @Test
    fun libraryMetadataResumePreservesCompletedHomeFeedState() =
        runTest {
            val pageMetadataStarted = CompletableDeferred<Unit>()
            val releasePageMetadata = CompletableDeferred<Unit>()
            val metadataLookups = MutableStateFlow(0)
            val homeItem = favoriteJellyfinItem("home-item")
            val libraryItem = favoriteJellyfinItem("library-item")
            val repository =
                FakeBrowseRepository(
                    loadPage = { _, _, _, _, _ -> LibraryPage(listOf(libraryItem), 1) },
                    loadRecentShows = { _, _ ->
                        pageMetadataStarted.await()
                        listOf(homeItem)
                    },
                    loadServerBaseUrl = {
                        if (metadataLookups.updateAndGet { it + 1 } == 3) {
                            pageMetadataStarted.complete(Unit)
                            releasePageMetadata.await()
                        }
                        "https://demo.jellyfin.org"
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            try {
                coordinator.bootstrap(forceRefresh = true)
                pageMetadataStarted.await()
                val homeComplete =
                    awaitState(coordinator) {
                        !it.isInitialLoading &&
                            !it.isHomeLoading &&
                            it.recentShows == listOf(homeItem)
                    }
                assertTrue(homeComplete.isLibraryLoading)

                releasePageMetadata.complete(Unit)
                val finalState =
                    awaitState(coordinator) {
                        !it.isLibraryLoading && it.libraryItems == listOf(libraryItem)
                    }

                assertFalse(finalState.isInitialLoading)
                assertFalse(finalState.isHomeLoading)
                assertEquals(listOf(homeItem), finalState.recentShows)
                assertEquals(listOf(libraryItem), finalState.libraryItems)
            } finally {
                releasePageMetadata.complete(Unit)
            }
        }

    @Test
    fun cachedItemsRemainVisibleDuringLibraryRefresh() =
        runTest {
            val cachedItem = favoriteJellyfinItem("cached-item")
            val refreshedItem = favoriteJellyfinItem("refreshed-item")
            val pageRequestStarted = CompletableDeferred<Unit>()
            val releasePage = CompletableDeferred<Unit>()
            val repository =
                FakeBrowseRepository(
                    cachedPages = mapOf("lib-2" to listOf(cachedItem)),
                    loadPage = { _, _, _, _, _ ->
                        pageRequestStarted.complete(Unit)
                        releasePage.await()
                        LibraryPage(listOf(refreshedItem), 1)
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                )

            pageRequestStarted.await()

            assertEquals(
                listOf("cached-item"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
            assertTrue(coordinator.state.value.isLibraryLoading)

            releasePage.complete(Unit)
            val refreshed = awaitState(coordinator) { it.libraryItems.map { item -> item.id } == listOf("refreshed-item") }
            assertFalse(refreshed.isLibraryLoading)
        }

    @Test
    fun nextPageCannotReplaceActiveFirstPageRefresh() =
        runTest {
            val refreshStarted = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()
            var pageRequests = 0
            val initialItems = listOf(favoriteJellyfinItem("initial-1"), favoriteJellyfinItem("initial-2"))
            val refreshedItems = listOf(favoriteJellyfinItem("refreshed-1"), favoriteJellyfinItem("refreshed-2"))
            val repository =
                FakeBrowseRepository(
                    loadPage = { _, page, _, _, _ ->
                        pageRequests += 1
                        when (pageRequests) {
                            1 -> LibraryPage(initialItems, 2)
                            2 -> {
                                refreshStarted.complete(Unit)
                                releaseRefresh.await()
                                LibraryPage(refreshedItems, 2)
                            }
                            else -> LibraryPage(listOf(favoriteJellyfinItem("unexpected-page-$page")), 3)
                        }
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                    autoBootstrap = false,
                )
            coordinator.selectLibrary("lib-1")
            awaitState(coordinator) { it.libraryItems == initialItems }

            coordinator.refreshSelectedLibrary()
            assertEquals(initialItems, coordinator.state.value.libraryItems)
            assertTrue(coordinator.state.value.isLibraryLoading)
            coordinator.loadNextPage()
            refreshStarted.await()
            advanceUntilIdle()
            assertEquals(2, pageRequests)

            releaseRefresh.complete(Unit)
            val refreshed = awaitState(coordinator) { it.libraryItems == refreshedItems }
            assertEquals(0, refreshed.currentPage)
            assertFalse(refreshed.isLibraryLoading)
        }

    @Test
    fun refreshWithoutSelectedLibraryDoesNotStartLibraryLoading() =
        runTest {
            var pageRequests = 0
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            libraries = emptyList(),
                            loadPage = { _, _, _, _, _ ->
                                pageRequests += 1
                                LibraryPage(emptyList(), 0)
                            },
                        ),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            assertNull(coordinator.state.value.selectedLibraryId)
            coordinator.refreshSelectedLibrary()
            assertFalse(coordinator.state.value.isLibraryLoading)

            advanceUntilIdle()

            assertFalse(coordinator.state.value.isLibraryLoading)
            assertEquals(0, pageRequests)
        }

    @Test
    fun rapidLibrarySelectionsPublishOnlyLatestPage() =
        runTest {
            val firstRequestStarted = CompletableDeferred<Unit>()
            val releaseFirstRequest = CompletableDeferred<Unit>()
            val repository =
                FakeBrowseRepository(
                    loadPage = { libraryId, _, _, _, _ ->
                        when (libraryId) {
                            "lib-1" -> {
                                firstRequestStarted.complete(Unit)
                                withContext(NonCancellable) { releaseFirstRequest.await() }
                                LibraryPage(listOf(favoriteJellyfinItem("stale-item")), 1)
                            }
                            else -> LibraryPage(listOf(favoriteJellyfinItem("latest-item")), 1)
                        }
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                    autoBootstrap = false,
                )

            coordinator.selectLibrary("lib-1")
            firstRequestStarted.await()
            coordinator.selectLibrary("lib-2")

            val latest = awaitState(coordinator) { it.libraryItems.map { item -> item.id } == listOf("latest-item") }
            assertEquals("lib-2", latest.selectedLibraryId)

            releaseFirstRequest.complete(Unit)
            advanceUntilIdle()
            assertEquals("lib-2", coordinator.state.value.selectedLibraryId)
            assertEquals(
                listOf("latest-item"),
                coordinator.state.value.libraryItems
                    .map { it.id },
            )
        }

    @Test
    fun staleMetadataLookupCannotRestoreOlderSelection() =
        runTest {
            val firstMetadataLookupStarted = CompletableDeferred<Unit>()
            val firstMetadataLookupReturned = CompletableDeferred<Unit>()
            val releaseFirstMetadataLookup = MutableStateFlow(false)
            val metadataLookups = MutableStateFlow(0)
            val repository =
                FakeBrowseRepository(
                    loadPage = { libraryId, _, _, _, _ ->
                        LibraryPage(listOf(favoriteJellyfinItem("$libraryId-item")), 1)
                    },
                    loadServerBaseUrl = {
                        if (metadataLookups.updateAndGet { it + 1 } == 1) {
                            firstMetadataLookupStarted.complete(Unit)
                            while (!releaseFirstMetadataLookup.value) {
                                // Model a synchronous metadata provider that cannot observe coroutine cancellation.
                            }
                            firstMetadataLookupReturned.complete(Unit)
                        }
                        "https://demo.jellyfin.org"
                    },
                )
            val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = coordinatorScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )
            try {
                coordinator.selectLibrary("lib-1")
                firstMetadataLookupStarted.await()
                coordinator.selectLibrary("lib-2")
                awaitState(coordinator) { it.libraryItems.map { item -> item.id } == listOf("lib-2-item") }
                val staleSelection =
                    async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(1_000) {
                            coordinator.state.first { it.selectedLibraryId == "lib-1" }
                        }
                    }

                releaseFirstMetadataLookup.value = true
                firstMetadataLookupReturned.await()

                assertNull(staleSelection.await())
                assertEquals("lib-2", coordinator.state.value.selectedLibraryId)
                assertEquals(
                    listOf("lib-2-item"),
                    coordinator.state.value.libraryItems
                        .map { it.id },
                )
                assertFalse(coordinator.state.value.isLibraryLoading)
            } finally {
                releaseFirstMetadataLookup.value = true
                coordinatorScope.cancel()
            }
        }

    @Test
    fun firstPageFailureClearsLibraryLoading() =
        runTest {
            val pageRequestStarted = CompletableDeferred<Unit>()
            val releaseFailure = CompletableDeferred<Unit>()
            val repository =
                FakeBrowseRepository(
                    loadPage = { _, _, _, _, _ ->
                        pageRequestStarted.complete(Unit)
                        releaseFailure.await()
                        error("boom")
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.selectLibrary("lib-1")
            pageRequestStarted.await()
            assertTrue(coordinator.state.value.isLibraryLoading)

            releaseFailure.complete(Unit)
            val failed = awaitState(coordinator) { it.errorMessage == "boom" }
            assertFalse(failed.isLibraryLoading)
            assertFalse(failed.isPageLoading)
        }

    @Test
    fun retainedItemsFirstPageFailureRecordsRefreshRetryKind() =
        runTest {
            val initialItems = listOf(favoriteJellyfinItem("one"), favoriteJellyfinItem("two"))
            var requests = 0
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadPage = { _, _, _, _, _ ->
                                if (++requests == 1) LibraryPage(initialItems, 2) else error("refresh boom")
                            },
                        ),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                    autoBootstrap = false,
                )
            coordinator.selectLibrary("lib-1")
            awaitState(coordinator) { it.libraryItems == initialItems }

            coordinator.refreshSelectedLibrary()

            val failed = awaitState(coordinator) { it.libraryErrorMessage == "refresh boom" }
            assertEquals(initialItems, failed.libraryItems)
            assertEquals(LibraryLoadErrorKind.FIRST_PAGE, failed.libraryErrorKind)
        }

    @Test
    fun laterPageFailureRecordsNextPageRetryKindAndRetainsItems() =
        runTest {
            val initialItems = listOf(favoriteJellyfinItem("one"), favoriteJellyfinItem("two"))
            var requests = 0
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadPage = { _, page, _, _, _ ->
                                if (++requests == 1) LibraryPage(initialItems, 2) else error("page $page boom")
                            },
                        ),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    pageSize = 2,
                    autoBootstrap = false,
                )
            coordinator.selectLibrary("lib-1")
            awaitState(coordinator) { it.libraryItems == initialItems }

            coordinator.loadNextPage()

            val failed = awaitState(coordinator) { it.libraryErrorMessage == "page 1 boom" }
            assertEquals(initialItems, failed.libraryItems)
            assertEquals(LibraryLoadErrorKind.NEXT_PAGE, failed.libraryErrorKind)
        }

    @Test
    fun metadataFailureClearsLibraryLoading() =
        runTest {
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository =
                        FakeBrowseRepository(
                            loadServerBaseUrl = { error("metadata boom") },
                        ),
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.selectLibrary("lib-1")

            val failed = awaitState(coordinator) { it.errorMessage == "metadata boom" }
            assertFalse(failed.isLibraryLoading)
            assertFalse(failed.isPageLoading)
        }

    @Test
    fun failedLibraryPageIsNotRetriedByCoordinator() =
        runTest {
            var pageRequests = 0
            val repository =
                FakeBrowseRepository(
                    loadPage = { _, _, _, _, _ ->
                        pageRequests += 1
                        error("single failure")
                    },
                )
            val coordinator =
                JellyfinBrowseCoordinator(
                    repository = repository,
                    scope = backgroundScope,
                    favoritesStore = FakeJellyfinFavoritesStore(),
                    autoBootstrap = false,
                )

            coordinator.selectLibrary("lib-1")

            awaitState(coordinator) { it.errorMessage == "single failure" }
            assertEquals(1, pageRequests)
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
            val parent = awaitInitialLoad(coordinator)

            coordinator.selectFavorites()
            val favoritesPage =
                awaitState(coordinator) { state ->
                    !state.isLibraryLoading && state.libraryItems.map { it.id } == listOf("favorite-1")
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
            val favoritePageRequests = MutableStateFlow(0)
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests.update { it + 1 } },
                    ),
                    backgroundScope,
                )
            val parent = awaitInitialLoad(coordinator)

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
            assertEquals(0, favoritePageRequests.value)
        }

    @Test
    fun shutdownWhileFavoriteIdsAreBlockedInvalidatesEntireFavoritesLoad() =
        runTest {
            val favoriteIdsStarted = CompletableDeferred<Unit>()
            val releaseFavoriteIds = CompletableDeferred<Unit>()
            val favoritePageRequests = MutableStateFlow(0)
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests.update { it + 1 } },
                    ),
                    backgroundScope,
                )
            val parent = awaitInitialLoad(coordinator)

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
            assertEquals(0, favoritePageRequests.value)
        }

    @Test
    fun serverBootstrapWhileFavoriteIdsAreBlockedPreventsOldFavoritesPublishing() =
        runTest {
            val favoriteIdsStarted = CompletableDeferred<Unit>()
            val releaseFavoriteIds = CompletableDeferred<Unit>()
            val favoritePageRequests = MutableStateFlow(0)
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        onFavoriteIds = {
                            favoriteIdsStarted.complete(Unit)
                            releaseFavoriteIds.await()
                        },
                        onFavoritePage = { favoritePageRequests.update { it + 1 } },
                    ),
                    backgroundScope,
                )
            awaitInitialLoad(coordinator)

            coordinator.selectFavorites()
            favoriteIdsStarted.await()
            coordinator.bootstrap(forceRefresh = true)
            releaseFavoriteIds.complete(Unit)

            val state =
                awaitState(coordinator) {
                    !it.isLibraryLoading &&
                        it.libraryItems.map { item -> item.id } == listOf("normal-1")
                }
            assertEquals(
                listOf("normal-1"),
                state.libraryItems
                    .map { it.id },
            )
            assertEquals(emptySet(), coordinator.favorites.value)
            assertEquals(0, favoritePageRequests.value)
        }

    @Test
    fun refreshingFavoritesReloadsIdsAndOnlyTheFavoriteFilteredPage() =
        runTest {
            val favoriteIdsRequests = MutableStateFlow(0)
            val favoritePageRequests = MutableStateFlow(0)
            val unfilteredPageRequests = MutableStateFlow(0)
            val coordinator =
                favoritesLifecycleCoordinator(
                    favoritesLifecycleEngine(
                        favoriteIdsJson = {
                            favoriteIdsRequests.update { it + 1 }
                            if (favoriteIdsRequests.value == 1) FAVORITE_ONE_IDS_JSON else FAVORITE_TWO_IDS_JSON
                        },
                        favoritePageJson = {
                            favoritePageRequests.update { it + 1 }
                            if (favoritePageRequests.value == 1) FAVORITE_ONE_PAGE_JSON else FAVORITE_TWO_PAGE_JSON
                        },
                        onUnfilteredPage = { unfilteredPageRequests.update { it + 1 } },
                    ),
                    backgroundScope,
                )
            awaitState(coordinator) {
                it.selectedLibraryId != null &&
                    !it.isLibraryLoading &&
                    unfilteredPageRequests.value == 1
            }
            assertEquals(1, unfilteredPageRequests.value)
            coordinator.selectFavorites()
            awaitState(coordinator) { it.libraryItems.map { item -> item.id } == listOf("favorite-1") }

            coordinator.refreshFavorites()
            val refreshed =
                awaitState(coordinator) { state ->
                    !state.isLibraryLoading && state.libraryItems.map { item -> item.id } == listOf("favorite-2")
                }

            assertEquals(setOf("favorite-2"), coordinator.favorites.value)
            assertEquals(listOf("favorite-2"), refreshed.libraryItems.map { it.id })
            assertEquals(2, favoriteIdsRequests.value)
            assertEquals(2, favoritePageRequests.value)
            assertEquals(1, unfilteredPageRequests.value)
        }

    @Test
    fun refreshingLibrariesUpdatesOnlyTheLibraryList() =
        runTest {
            val libraryRequests = MutableStateFlow(0)
            val itemRequests = MutableStateFlow(0)
            val refreshOnlyEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/Views") -> {
                            libraryRequests.update { it + 1 }
                            respond(
                                content = REFRESHED_LIBRARIES_JSON,
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders(),
                            )
                        }
                        path.endsWith("/Items") -> {
                            itemRequests.update { it + 1 }
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
            val state = awaitState(coordinator) { it.libraries.map { library -> library.id } == listOf("lib-refreshed") }

            assertEquals(listOf("Refreshed"), state.libraries.map { it.name })
            assertEquals(1, libraryRequests.value)
            assertEquals(0, itemRequests.value)
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
            val localPageCallCount = MutableStateFlow(0)
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
                                when (localPageCallCount.updateAndGet { it + 1 } - 1) {
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

            val state = awaitInitialLoad(coordinator)
            assertEquals("lib-shows", state.selectedLibraryId, "state=$state")
        }

    private suspend fun awaitInitialLoad(coordinator: JellyfinBrowseCoordinator): JellyfinHomeState =
        awaitState(coordinator) {
            it.selectedLibraryId != null && !it.isInitialLoading && !it.isLibraryLoading
        }

    private suspend fun awaitState(
        coordinator: JellyfinBrowseCoordinator,
        predicate: (JellyfinHomeState) -> Boolean,
    ): JellyfinHomeState =
        withContext(Dispatchers.Default) {
            withTimeout(15_000) {
                coordinator.state.first(predicate)
            }
        }

    private class FakeBrowseRepository(
        private val libraries: List<JellyfinLibrary> =
            listOf(
                JellyfinLibrary("lib-1", "Movies", "movies", null, "movies-image"),
                JellyfinLibrary("lib-2", "Shows", "tvshows", null, "shows-image"),
            ),
        private val cachedPages: Map<String, List<JellyfinItem>> = emptyMap(),
        private val loadPage: suspend (String, Int, Int, Boolean, String?) -> LibraryPage =
            { _, _, _, _, _ -> LibraryPage(emptyList(), 0) },
        private val loadQueryPage: suspend (String, Int, Int, LibraryBrowseQuery, LibraryCachePolicy) -> LibraryPage =
            { libraryId, page, pageSize, query, _ ->
                loadPage(libraryId, page, pageSize, page == 0, query.takeIf { it.favoritesOnly }?.let { "IsFavorite" })
            },
        private val loadRecentShows: suspend (String, Int) -> List<JellyfinItem> = { _, _ -> emptyList() },
        private val loadRecentMovies: suspend (String, Int) -> List<JellyfinItem> = { _, _ -> emptyList() },
        private val loadServerBaseUrl: suspend () -> String? = { "https://demo.jellyfin.org" },
        private val loadAccessToken: suspend () -> String? = { "dummy-token" },
        private val loadLibraries: suspend () -> List<JellyfinLibrary> = { libraries },
        private val refreshLibraryCatalog: suspend () -> List<JellyfinLibrary> = { libraries },
        private val loadCachedContinueWatching: suspend (Int) -> List<JellyfinItem> = { emptyList() },
    ) : JellyfinBrowseRepositoryApi {
        override suspend fun refreshLibraries(): List<JellyfinLibrary> = refreshLibraryCatalog()

        override suspend fun listLibraries(): List<JellyfinLibrary> = loadLibraries()

        override suspend fun cachedContinueWatching(limit: Int): List<JellyfinItem> = loadCachedContinueWatching(limit)

        override suspend fun cachedNextUp(limit: Int): List<JellyfinItem> = emptyList()

        override suspend fun cachedRecentShows(
            libraryId: String?,
            limit: Int,
        ): List<JellyfinItem> = emptyList()

        override suspend fun cachedRecentMovies(
            libraryId: String?,
            limit: Int,
        ): List<JellyfinItem> = emptyList()

        override suspend fun loadLibraryPage(
            libraryId: String,
            page: Int,
            pageSize: Int,
            refresh: Boolean,
            filters: String?,
        ): LibraryPage = loadPage(libraryId, page, pageSize, refresh, filters)

        override suspend fun loadLibraryPage(
            libraryId: String,
            page: Int,
            pageSize: Int,
            query: LibraryBrowseQuery,
            cachePolicy: LibraryCachePolicy,
        ): LibraryPage = loadQueryPage(libraryId, page, pageSize, query, cachePolicy)

        override suspend fun cachedLibraryPage(
            libraryId: String,
            page: Int,
            pageSize: Int,
        ): List<JellyfinItem> = cachedPages[libraryId].orEmpty()

        override suspend fun loadChildrenPage(
            libraryId: String,
            parentId: String,
            page: Int,
            pageSize: Int,
            refresh: Boolean,
        ): LibraryPage = loadPage(parentId, page, pageSize, refresh, null)

        override suspend fun refreshContinueWatching(limit: Int): List<JellyfinItem> = emptyList()

        override suspend fun refreshNextUp(
            limit: Int,
            libraryId: String?,
        ): List<JellyfinItem> = emptyList()

        override suspend fun refreshRecentlyAddedShows(
            libraryId: String,
            limit: Int,
        ): List<JellyfinItem> = loadRecentShows(libraryId, limit)

        override suspend fun refreshRecentlyAddedMovies(
            libraryId: String,
            limit: Int,
        ): List<JellyfinItem> = loadRecentMovies(libraryId, limit)

        override suspend fun currentServerBaseUrl(): String? = loadServerBaseUrl()

        override suspend fun currentAccessToken(): String? = loadAccessToken()

        override suspend fun currentApi(): JellyfinBrowseApi? = null

        override suspend fun currentUserId(): String? = "user-123"
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
                {"Id": "lib-1", "Name": "Movies", "CollectionType": "movies", "ImageTags": {"Primary": "movies-primary"}},
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
