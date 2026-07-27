package dev.jellystack.core.jellyfin

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class JellyfinHomeState(
    val isInitialLoading: Boolean = false,
    val isPageLoading: Boolean = false,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val continueWatching: List<JellyfinItem> = emptyList(),
    val nextUp: List<JellyfinItem> = emptyList(),
    val recentShows: List<JellyfinItem> = emptyList(),
    val recentMovies: List<JellyfinItem> = emptyList(),
    val selectedLibraryId: String? = null,
    val libraryItems: List<JellyfinItem> = emptyList(),
    val currentPage: Int = 0,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val imageBaseUrl: String? = null,
    val imageAccessToken: String? = null,
    val totalLibraryItemCount: Long? = null,
    val favorites: Set<String> = emptySet(),
    val browsePath: List<LibraryBrowseEntry> = emptyList(),
)

data class LibraryBrowseEntry(
    val id: String,
    val name: String,
)

private data class LibraryPageSnapshot(
    val selectedLibraryId: String?,
    val browsePath: List<LibraryBrowseEntry>,
    val libraryItems: List<JellyfinItem>,
    val currentPage: Int,
    val endReached: Boolean,
    val totalLibraryItemCount: Long?,
)

private fun JellyfinHomeState.snapshot(): LibraryPageSnapshot =
    LibraryPageSnapshot(
        selectedLibraryId = selectedLibraryId,
        browsePath = browsePath,
        libraryItems = libraryItems,
        currentPage = currentPage,
        endReached = endReached,
        totalLibraryItemCount = totalLibraryItemCount,
    )

fun JellyfinItem.isBrowseContainer(): Boolean =
    type.lowercase() in
        setOf(
            "folder",
            "boxset",
            "musicartist",
            "musicalbum",
            "playlist",
            "photoalbum",
            "collectionfolder",
        )

class JellyfinBrowseCoordinator(
    private val repository: JellyfinBrowseRepository,
    private val scope: CoroutineScope,
    private val favoritesStore: JellyfinFavoritesStoreApi,
    private val pageSize: Int = 30,
    private val autoBootstrap: Boolean = true,
) {
    private val mutableFavorites = MutableStateFlow<Set<String>>(favoritesStore.snapshot())
    val favorites: StateFlow<Set<String>> = mutableFavorites.asStateFlow()

    private val mutableFavoriteError = MutableStateFlow<String?>(null)
    val favoriteError: StateFlow<String?> = mutableFavoriteError.asStateFlow()

    private fun updateFavorites(next: Set<String>) {
        mutableFavorites.value = next
        mutableState.value = mutableState.value.copy(favorites = next)
    }

    fun clearFavoriteError() {
        mutableFavoriteError.value = null
    }

    private val mutableState = MutableStateFlow(JellyfinHomeState(isInitialLoading = true))
    private val loadMutex = Mutex()
    private var refreshJob: Job? = null
    private val browseHistory = ArrayDeque<LibraryPageSnapshot>()
    private var browseLoadGeneration = 0L
    private var browseLoadJob: Job? = null
    private var favoritesParentSnapshot: LibraryPageSnapshot? = null
    private var libraryListRefreshJob: Job? = null

    val state: StateFlow<JellyfinHomeState> = mutableState.asStateFlow()

    init {
        // Mirror the initial favorites snapshot into the public state so `state.favorites` is
        // populated immediately rather than waiting for the first toggle / load.
        mutableState.value = mutableState.value.copy(favorites = mutableFavorites.value)
        if (autoBootstrap) {
            bootstrap(forceRefresh = false)
        }
    }

    fun bootstrap(forceRefresh: Boolean) {
        invalidateBrowseLoad()
        browseHistory.clear()
        favoritesParentSnapshot = null
        libraryListRefreshJob?.cancel()
        libraryListRefreshJob = null
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                loadMutex.withLock {
                    val cachedState = loadCachedState()
                    val shouldRefresh = forceRefresh || cachedState == null
                    if (cachedState != null) {
                        mutableState.value =
                            cachedState.copy(
                                isInitialLoading = shouldRefresh,
                                errorMessage = null,
                            )
                    } else {
                        mutableState.value = mutableState.value.copy(isInitialLoading = true, errorMessage = null)
                    }
                    try {
                        val cachedLibraries = repository.listLibraries()
                        val libraries =
                            if (forceRefresh || cachedLibraries.isEmpty()) {
                                repository.refreshLibraries()
                            } else {
                                cachedLibraries
                            }
                        val selectedId = selectDefaultLibrary(libraries)
                        val imageBaseUrl = repository.currentServerBaseUrl()
                        val imageAccessToken = repository.currentAccessToken()
                        val showsLibraryId = preferredLibraryId(libraries, "tvshows", "series") ?: selectedId
                        val moviesLibraryId = preferredLibraryId(libraries, "movies") ?: selectedId

                        val (continueWatching, nextUp, firstPage) =
                            coroutineScope {
                                val continueWatchingDeferred =
                                    async {
                                        if (forceRefresh || cachedState?.continueWatching.isNullOrEmpty()) {
                                            repository.refreshContinueWatching(limit = HOME_SECTION_ITEM_LIMIT)
                                        } else {
                                            cachedState!!.continueWatching
                                        }
                                    }
                                val nextUpDeferred =
                                    async {
                                        val fallback =
                                            cachedState?.nextUp
                                                ?: repository.cachedNextUp(limit = HOME_SECTION_ITEM_LIMIT)
                                        try {
                                            repository.refreshNextUp(
                                                limit = HOME_SECTION_ITEM_LIMIT,
                                                libraryId = showsLibraryId,
                                            )
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (_: Throwable) {
                                            fallback
                                        }
                                    }
                                val firstPageDeferred =
                                    selectedId?.let { id ->
                                        async {
                                            val cached = repository.cachedLibraryPage(id, page = 0, pageSize = pageSize)
                                            if (!forceRefresh && cached.isNotEmpty()) {
                                                return@async LibraryPage(items = cached, totalRecordCount = null)
                                            }
                                            repository.loadLibraryPage(id, page = 0, pageSize = pageSize, refresh = true)
                                        }
                                    }

                                val continueWatchingResult = continueWatchingDeferred.await()
                                val nextUpResult = nextUpDeferred.await()
                                val firstPageResult: LibraryPage =
                                    firstPageDeferred?.await() ?: LibraryPage(emptyList(), null)
                                Triple(continueWatchingResult, nextUpResult, firstPageResult)
                            }
                        val (firstPageItems, firstPageTotal) = firstPage
                        val newTotalBootstrap = firstPageTotal ?: mutableState.value.totalLibraryItemCount
                        JellystackLog.d(
                            "Home bootstrap loaded ${continueWatching.size} continueWatching items and ${nextUp.size} nextUp items",
                        )
                        val recentShows =
                            showsLibraryId?.let { id ->
                                if (forceRefresh || cachedState?.recentShows.isNullOrEmpty()) {
                                    repository.refreshRecentlyAddedShows(id, limit = HOME_SECTION_ITEM_LIMIT)
                                } else {
                                    cachedState!!.recentShows
                                }
                            } ?: emptyList()
                        val recentMovies =
                            moviesLibraryId?.let { id ->
                                if (forceRefresh || cachedState?.recentMovies.isNullOrEmpty()) {
                                    repository.refreshRecentlyAddedMovies(id, limit = HOME_SECTION_ITEM_LIMIT)
                                } else {
                                    cachedState!!.recentMovies
                                }
                            } ?: emptyList()

                        mutableState.value =
                            mutableState.value.copy(
                                isInitialLoading = false,
                                isPageLoading = false,
                                libraries = libraries,
                                continueWatching = continueWatching,
                                nextUp = nextUp,
                                recentShows = recentShows,
                                recentMovies = recentMovies,
                                selectedLibraryId = selectedId,
                                libraryItems = firstPageItems,
                                currentPage = 0,
                                endReached = firstPageItems.size < pageSize,
                                errorMessage = null,
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                                totalLibraryItemCount = newTotalBootstrap,
                            )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (t: Throwable) {
                        val imageBaseUrl = repository.currentServerBaseUrl()
                        val imageAccessToken = repository.currentAccessToken()
                        mutableState.value =
                            mutableState.value.copy(
                                isInitialLoading = false,
                                isPageLoading = false,
                                errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "",
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                            )
                    }
                }
            }
    }

    fun shutdown() {
        invalidateBrowseLoad()
        browseHistory.clear()
        favoritesParentSnapshot = null
        libraryListRefreshJob?.cancel()
        libraryListRefreshJob = null
        refreshJob?.cancel()
        refreshJob = null
    }

    fun selectLibrary(libraryId: String) {
        val current = mutableState.value
        if (libraryId == current.selectedLibraryId && current.browsePath.isEmpty()) {
            return
        }
        invalidateBrowseLoad()
        browseHistory.clear()
        favoritesParentSnapshot = null
        scope.launch {
            loadMutex.withLock {
                mutableState.value =
                    mutableState.value.copy(
                        selectedLibraryId = libraryId,
                        browsePath = emptyList(),
                        libraryItems = emptyList(),
                        currentPage = 0,
                        recentShows = emptyList(),
                        recentMovies = emptyList(),
                        endReached = false,
                        isInitialLoading = true,
                        errorMessage = null,
                    )
            }
            launchLibraryPageLoad(page = 0, refresh = true)
        }
    }

    fun refreshSelectedLibrary() {
        invalidateBrowseLoad()
        launchLibraryPageLoad(page = 0, refresh = true)
    }

    fun refreshLibraries() {
        libraryListRefreshJob?.cancel()
        libraryListRefreshJob =
            scope.launch {
                try {
                    val libraries = repository.refreshLibraries()
                    mutableState.value = mutableState.value.copy(libraries = libraries, errorMessage = null)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    mutableState.value =
                        mutableState.value.copy(errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "")
                }
            }
    }

    fun openContainer(item: JellyfinItem) {
        val current = mutableState.value
        if (current.selectedLibraryId == null || !item.isBrowseContainer()) return
        browseHistory.addLast(current.snapshot())
        invalidateBrowseLoad()
        mutableState.value =
            current.copy(
                browsePath = current.browsePath + LibraryBrowseEntry(item.id, item.name),
                libraryItems = emptyList(),
                currentPage = 0,
                endReached = false,
                totalLibraryItemCount = null,
                isInitialLoading = false,
                isPageLoading = false,
                errorMessage = null,
            )
        launchLibraryPageLoad(page = 0, refresh = true)
    }

    fun navigateUp(): Boolean {
        val current = mutableState.value
        if (current.browsePath.isEmpty()) return false
        val parent = browseHistory.removeLastOrNull() ?: return false
        invalidateBrowseLoad()
        mutableState.value =
            current.copy(
                selectedLibraryId = parent.selectedLibraryId,
                browsePath = parent.browsePath,
                libraryItems = parent.libraryItems,
                currentPage = parent.currentPage,
                endReached = parent.endReached,
                totalLibraryItemCount = parent.totalLibraryItemCount,
                isInitialLoading = false,
                isPageLoading = false,
                errorMessage = null,
            )
        return true
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (current.selectedLibraryId == null || current.isPageLoading || current.endReached) {
            return
        }
        launchLibraryPageLoad(page = current.currentPage + 1, refresh = false)
    }

    private fun invalidateBrowseLoad() {
        browseLoadGeneration += 1
        browseLoadJob?.cancel()
        browseLoadJob = null
    }

    private fun launchLibraryPageLoad(
        page: Int,
        refresh: Boolean,
        filters: String? = null,
    ) {
        val expectedGeneration = browseLoadGeneration
        browseLoadJob?.cancel()
        browseLoadJob =
            scope.launch {
                loadLibraryPage(
                    page = page,
                    refresh = refresh,
                    filters = filters,
                    expectedGeneration = expectedGeneration,
                )
            }
    }

    private suspend fun loadLibraryPage(
        page: Int,
        refresh: Boolean,
        filters: String? = null,
        expectedGeneration: Long,
    ) {
        loadMutex.withLock {
            if (expectedGeneration != browseLoadGeneration) return@withLock
            // When a filters value is supplied (e.g. Favorites) we use a sentinel libraryId so the
            // storage paths still work without binding the result to a real library on the server.
            val selectedId =
                if (filters != null) {
                    FAVORITES_LIBRARY_SENTINEL
                } else {
                    mutableState.value.selectedLibraryId ?: return
                }
            val stateBefore = mutableState.value
            val imageBaseUrl = repository.currentServerBaseUrl()
            val imageAccessToken = repository.currentAccessToken()
            mutableState.value =
                stateBefore.copy(
                    isInitialLoading = refresh && page == 0,
                    isPageLoading = !refresh,
                    errorMessage = null,
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                )
            try {
                val libraryPage: LibraryPage =
                    stateBefore.browsePath.lastOrNull()?.let { parent ->
                        repository.loadChildrenPage(
                            libraryId = selectedId,
                            parentId = parent.id,
                            page = page,
                            pageSize = pageSize,
                            refresh = refresh,
                        )
                    } ?: repository.loadLibraryPage(
                        libraryId = selectedId,
                        page = page,
                        pageSize = pageSize,
                        refresh = refresh,
                        filters = filters,
                    )
                val (items, totalRecordCount) = libraryPage
                val totalCandidate = totalRecordCount
                val newTotal =
                    when {
                        page == 0 && refresh && filters == null -> totalCandidate ?: stateBefore.totalLibraryItemCount
                        else -> stateBefore.totalLibraryItemCount
                    }
                val showsLibraryId = preferredLibraryId(stateBefore.libraries, "tvshows", "series") ?: selectedId
                val moviesLibraryId = preferredLibraryId(stateBefore.libraries, "movies") ?: selectedId
                val refreshedNextUp =
                    if (page == 0 && refresh && filters == null && stateBefore.browsePath.isEmpty()) {
                        try {
                            repository.refreshNextUp(
                                limit = HOME_SECTION_ITEM_LIMIT,
                                libraryId = showsLibraryId,
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            repository.cachedNextUp(limit = HOME_SECTION_ITEM_LIMIT)
                        }
                    } else {
                        null
                    }
                refreshedNextUp?.let { items ->
                    JellystackLog.d("Refreshed Next Up after library load with ${items.size} items (page=$page)")
                }
                val recentShows =
                    if (
                        page == 0 &&
                        (refresh || stateBefore.recentShows.isEmpty()) &&
                        filters == null &&
                        stateBefore.browsePath.isEmpty()
                    ) {
                        showsLibraryId?.let { id ->
                            repository.refreshRecentlyAddedShows(id, limit = HOME_SECTION_ITEM_LIMIT)
                        }
                    } else {
                        null
                    }
                val recentMovies =
                    if (
                        page == 0 &&
                        (refresh || stateBefore.recentMovies.isEmpty()) &&
                        filters == null &&
                        stateBefore.browsePath.isEmpty()
                    ) {
                        moviesLibraryId?.let { id ->
                            repository.refreshRecentlyAddedMovies(id, limit = HOME_SECTION_ITEM_LIMIT)
                        }
                    } else {
                        null
                    }
                val merged =
                    if (page == 0) {
                        items
                    } else {
                        (stateBefore.libraryItems + items).distinctBy { it.id }
                    }
                if (expectedGeneration != browseLoadGeneration) return@withLock
                mutableState.value =
                    mutableState.value.copy(
                        isInitialLoading = false,
                        isPageLoading = false,
                        libraryItems = merged,
                        currentPage = page,
                        endReached = items.size < pageSize,
                        imageBaseUrl = imageBaseUrl,
                        imageAccessToken = imageAccessToken,
                        nextUp = refreshedNextUp ?: mutableState.value.nextUp,
                        recentShows = recentShows ?: mutableState.value.recentShows,
                        recentMovies = recentMovies ?: mutableState.value.recentMovies,
                        totalLibraryItemCount = newTotal,
                    )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                if (expectedGeneration != browseLoadGeneration) return@withLock
                mutableState.value =
                    mutableState.value.copy(
                        isInitialLoading = false,
                        isPageLoading = false,
                        errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "",
                        imageBaseUrl = imageBaseUrl,
                        imageAccessToken = imageAccessToken,
                    )
            }
        }
    }

    private suspend fun loadCachedState(): JellyfinHomeState? {
        val libraries = repository.listLibraries()
        if (libraries.isEmpty()) return null
        val selectedId = selectDefaultLibrary(libraries)
        val imageBaseUrl = repository.currentServerBaseUrl()
        val imageAccessToken = repository.currentAccessToken()
        val continueWatching = repository.cachedContinueWatching(limit = HOME_SECTION_ITEM_LIMIT)
        val nextUp = repository.cachedNextUp(limit = HOME_SECTION_ITEM_LIMIT)
        val showsLibraryId = preferredLibraryId(libraries, "tvshows", "series") ?: selectedId
        val moviesLibraryId = preferredLibraryId(libraries, "movies") ?: selectedId
        val firstPage =
            selectedId?.let { id ->
                repository.cachedLibraryPage(id, page = 0, pageSize = pageSize)
            } ?: emptyList()
        val recentShows = repository.cachedRecentShows(showsLibraryId, limit = HOME_SECTION_ITEM_LIMIT)
        val recentMovies = repository.cachedRecentMovies(moviesLibraryId, limit = HOME_SECTION_ITEM_LIMIT)
        return JellyfinHomeState(
            isInitialLoading = false,
            isPageLoading = false,
            libraries = libraries,
            continueWatching = continueWatching,
            nextUp = nextUp,
            recentShows = recentShows,
            recentMovies = recentMovies,
            selectedLibraryId = selectedId,
            libraryItems = firstPage,
            currentPage = 0,
            endReached = selectedId == null || firstPage.size < pageSize,
            errorMessage = null,
            imageBaseUrl = imageBaseUrl,
            imageAccessToken = imageAccessToken,
        )
    }

    private fun preferredLibraryId(
        libraries: List<JellyfinLibrary>,
        vararg collectionTypes: String,
    ): String? {
        if (libraries.isEmpty()) return null
        val desiredTypes = collectionTypes.map { it.lowercase() }.toSet()
        return libraries
            .firstOrNull { library ->
                library.collectionType?.lowercase()?.let(desiredTypes::contains) == true
            }?.id
    }

    private fun selectDefaultLibrary(libraries: List<JellyfinLibrary>): String? =
        preferredLibraryId(libraries, "tvshows", "series")
            ?: preferredLibraryId(libraries, "movies")
            ?: mutableState.value.selectedLibraryId?.takeIf { id -> libraries.any { it.id == id } }
            ?: libraries.firstOrNull()?.id

    /**
     * Test-only hook for replacing [JellyfinHomeState.libraryItems] directly. Avoids forcing the
     * Favorites page-load network path when a unit test wants to simulate the user being on the
     * Favorites subview with a known list.
     */
    fun setLibraryItemsForTest(items: List<JellyfinItem>) {
        mutableState.value = mutableState.value.copy(libraryItems = items)
    }

    /**
     * Toggles the favourite state for [item] optimistically. On success the local snapshot is preserved.
     * On failure the optimistic update is reverted and [favoriteError] is set to a short, user-facing
     * message. No-ops when no environment is available (no API + userId).
     *
     * Also mirrors the toggle into [JellyfinHomeState.libraryItems] so the Favorites subview grid
     * updates immediately without waiting for a refetch. JellyfinHomeState does not yet track the
     * current destination, so this is applied unconditionally; for non-Favorites destinations the
     * item either isn't rendered or is rendered as a non-favorite row whose heart still reflects
     * the new state (which is what the UI wants).
     */
    suspend fun toggleFavorite(item: JellyfinItem) {
        val api = repository.currentApi() ?: return
        val userId = repository.currentUserId() ?: return
        val current = favorites.value
        val currentlyFavorite = item.id in current
        val next =
            if (currentlyFavorite) {
                current - item.id
            } else {
                current + item.id
            }
        favoritesStore.replaceAll(next)
        updateFavorites(next)
        // Mirror the toggle into the rendered grid so the Favorites subview updates without a refetch.
        val previousItems = mutableState.value.libraryItems
        val updatedItems =
            if (currentlyFavorite) {
                if (previousItems.none { it.id == item.id }) {
                    previousItems
                } else {
                    previousItems.filter { it.id != item.id }
                }
            } else {
                if (previousItems.any { it.id == item.id }) {
                    previousItems
                } else {
                    previousItems + item
                }
            }
        if (updatedItems !== previousItems) {
            mutableState.value = mutableState.value.copy(libraryItems = updatedItems)
        }
        try {
            if (currentlyFavorite) {
                api.removeFavorite(userId, item.id)
            } else {
                api.addFavorite(userId, item.id)
            }
            mutableFavoriteError.value = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            favoritesStore.replaceAll(current)
            updateFavorites(current)
            // Revert the optimistic libraryItems mutation so the grid matches the rolled-back
            // favourites set.
            mutableState.value = mutableState.value.copy(libraryItems = previousItems)
            mutableFavoriteError.value = t.message?.takeIf { it.isNotBlank() } ?: ""
        }
    }

    /**
     * Fetches the favourite ids from the API and replaces the cache. Network failures are swallowed (the
     * cached snapshot is preserved). Intended to be called from `bootstrap` and on pull-to-refresh.
     */
    suspend fun loadFavorites() {
        val api = repository.currentApi() ?: return
        val userId = repository.currentUserId() ?: return
        // Warm the observer — even when the snapshot is non-empty we call replaceAll so the
        // StateFlow pushes the same value through, guaranteeing subscribers see fresh data after a
        // background refresh.
        val cached = favoritesStore.snapshot()
        if (cached.isNotEmpty()) {
            favoritesStore.replaceAll(cached)
            updateFavorites(cached)
        }
        try {
            val ids = api.fetchFavoriteIds(userId)
            favoritesStore.replaceAll(ids)
            updateFavorites(ids)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Preserve the cached snapshot on a recoverable network failure.
        }
    }

    /** Saves the visible Library page and starts one generation-owned Favorites refresh. */
    fun selectFavorites() {
        if (favoritesParentSnapshot == null) {
            favoritesParentSnapshot = mutableState.value.snapshot()
        }
        refreshFavorites()
    }

    /** Refreshes favorite ids and the filtered page within the same tracked generation. */
    fun refreshFavorites() {
        invalidateBrowseLoad()
        val expectedGeneration = browseLoadGeneration
        browseLoadJob =
            scope.launch {
                if (!loadFavorites(expectedGeneration)) return@launch
                loadLibraryPage(
                    page = 0,
                    refresh = true,
                    filters = FAVORITES_FILTER,
                    expectedGeneration = expectedGeneration,
                )
            }
    }

    fun leaveFavorites(): Boolean {
        val parent = favoritesParentSnapshot ?: return false
        invalidateBrowseLoad()
        favoritesParentSnapshot = null
        mutableState.value =
            mutableState.value.copy(
                selectedLibraryId = parent.selectedLibraryId,
                browsePath = parent.browsePath,
                libraryItems = parent.libraryItems,
                currentPage = parent.currentPage,
                endReached = parent.endReached,
                totalLibraryItemCount = parent.totalLibraryItemCount,
                isInitialLoading = false,
                isPageLoading = false,
                errorMessage = null,
            )
        return true
    }

    private suspend fun loadFavorites(expectedGeneration: Long): Boolean {
        if (expectedGeneration != browseLoadGeneration) return false
        val api = repository.currentApi() ?: return expectedGeneration == browseLoadGeneration
        val userId = repository.currentUserId() ?: return expectedGeneration == browseLoadGeneration
        return try {
            val ids = api.fetchFavoriteIds(userId)
            if (expectedGeneration != browseLoadGeneration) return false
            favoritesStore.replaceAll(ids)
            if (expectedGeneration != browseLoadGeneration) return false
            updateFavorites(ids)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            expectedGeneration == browseLoadGeneration
        }
    }

    private companion object {
        private const val HOME_SECTION_ITEM_LIMIT = 12
        private const val FAVORITES_FILTER = "IsFavorite"
        private const val FAVORITES_LIBRARY_SENTINEL = "__favorites__"
    }
}
