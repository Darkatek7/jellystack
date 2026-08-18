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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JellyfinHomeState(
    val isInitialLoading: Boolean = false,
    val isHomeLoading: Boolean = false,
    val isLibraryLoading: Boolean = false,
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

private data class HomeFeedResults(
    val continueWatching: List<JellyfinItem>,
    val nextUp: List<JellyfinItem>,
    val recentShows: List<JellyfinItem>,
    val recentMovies: List<JellyfinItem>,
)

private suspend fun <T> loadHomeFeed(
    fallback: T,
    request: suspend () -> T,
): T =
    try {
        request()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        fallback
    }

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
    private val repository: JellyfinBrowseRepositoryApi,
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

    private val mutableState = MutableStateFlow(JellyfinHomeState(isInitialLoading = true, isHomeLoading = true))
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
        val bootstrapBrowseGeneration = browseLoadGeneration
        browseHistory.clear()
        favoritesParentSnapshot = null
        libraryListRefreshJob?.cancel()
        libraryListRefreshJob = null
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                val cachedLibraries = repository.listLibraries()
                val shouldRefreshLibraries =
                    forceRefresh ||
                        cachedLibraries.isEmpty() ||
                        cachedLibraries.all { it.primaryImageTag.isNullOrBlank() }
                val cachedState = loadCachedState()
                val shouldRefresh = forceRefresh || cachedState == null || shouldRefreshLibraries
                if (cachedState != null && bootstrapBrowseGeneration == browseLoadGeneration) {
                    mutableState.value =
                        cachedState.copy(
                            isInitialLoading = shouldRefresh,
                            isHomeLoading = shouldRefresh,
                            isLibraryLoading = false,
                            errorMessage = null,
                            favorites = mutableFavorites.value,
                        )
                } else {
                    mutableState.update {
                        it.copy(
                            isInitialLoading = true,
                            isHomeLoading = true,
                            errorMessage = null,
                        )
                    }
                }
                try {
                    val libraries =
                        if (shouldRefreshLibraries) {
                            repository.refreshLibraries()
                        } else {
                            cachedLibraries
                        }
                    val selectedId = selectDefaultLibrary(libraries)
                    val imageBaseUrl = repository.currentServerBaseUrl()
                    val imageAccessToken = repository.currentAccessToken()
                    val showsLibraryId = preferredLibraryId(libraries, "tvshows", "series") ?: selectedId
                    val moviesLibraryId = preferredLibraryId(libraries, "movies") ?: selectedId

                    mutableState.update { current ->
                        if (bootstrapBrowseGeneration == browseLoadGeneration) {
                            val cachedItems =
                                cachedState
                                    ?.takeIf { it.selectedLibraryId == selectedId }
                                    ?.libraryItems
                                    .orEmpty()
                            current.copy(
                                libraries = libraries,
                                selectedLibraryId = selectedId,
                                libraryItems = cachedItems,
                                currentPage = 0,
                                endReached = selectedId == null || cachedItems.size < pageSize,
                                totalLibraryItemCount = cachedState?.totalLibraryItemCount,
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                                errorMessage = null,
                            )
                        } else {
                            current.copy(
                                libraries = libraries,
                                imageBaseUrl = imageBaseUrl,
                                imageAccessToken = imageAccessToken,
                            )
                        }
                    }
                    if (selectedId != null && bootstrapBrowseGeneration == browseLoadGeneration) {
                        launchLibraryPageLoad(page = 0, refresh = true)
                    }

                    val (continueWatching, nextUp, recentShows, recentMovies) =
                        coroutineScope {
                            val continueWatchingDeferred =
                                async {
                                    loadHomeFeed(cachedState?.continueWatching.orEmpty()) {
                                        if (forceRefresh || cachedState?.continueWatching.isNullOrEmpty()) {
                                            repository.refreshContinueWatching(limit = HOME_SECTION_ITEM_LIMIT)
                                        } else {
                                            cachedState!!.continueWatching
                                        }
                                    }
                                }
                            val nextUpDeferred =
                                async {
                                    val fallback =
                                        cachedState?.nextUp
                                            ?: repository.cachedNextUp(limit = HOME_SECTION_ITEM_LIMIT)
                                    loadHomeFeed(fallback) {
                                        repository.refreshNextUp(
                                            limit = HOME_SECTION_ITEM_LIMIT,
                                            libraryId = showsLibraryId,
                                        )
                                    }
                                }
                            val recentShowsDeferred =
                                async {
                                    loadHomeFeed(cachedState?.recentShows.orEmpty()) {
                                        showsLibraryId?.let { id ->
                                            if (forceRefresh || cachedState?.recentShows.isNullOrEmpty()) {
                                                repository.refreshRecentlyAddedShows(id, limit = HOME_SECTION_ITEM_LIMIT)
                                            } else {
                                                cachedState!!.recentShows
                                            }
                                        }.orEmpty()
                                    }
                                }
                            val recentMoviesDeferred =
                                async {
                                    loadHomeFeed(cachedState?.recentMovies.orEmpty()) {
                                        moviesLibraryId?.let { id ->
                                            if (forceRefresh || cachedState?.recentMovies.isNullOrEmpty()) {
                                                repository.refreshRecentlyAddedMovies(id, limit = HOME_SECTION_ITEM_LIMIT)
                                            } else {
                                                cachedState!!.recentMovies
                                            }
                                        }.orEmpty()
                                    }
                                }
                            HomeFeedResults(
                                continueWatching = continueWatchingDeferred.await(),
                                nextUp = nextUpDeferred.await(),
                                recentShows = recentShowsDeferred.await(),
                                recentMovies = recentMoviesDeferred.await(),
                            )
                        }
                    JellystackLog.d(
                        "Home bootstrap loaded ${continueWatching.size} continueWatching items and ${nextUp.size} nextUp items",
                    )
                    mutableState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isHomeLoading = false,
                            continueWatching = continueWatching,
                            nextUp = nextUp,
                            recentShows = recentShows,
                            recentMovies = recentMovies,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    val imageBaseUrl = repository.currentServerBaseUrl()
                    val imageAccessToken = repository.currentAccessToken()
                    mutableState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isHomeLoading = false,
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
        mutableState.update {
            it.copy(
                selectedLibraryId = libraryId,
                browsePath = emptyList(),
                libraryItems = emptyList(),
                currentPage = 0,
                endReached = false,
                totalLibraryItemCount = null,
                errorMessage = null,
            )
        }
        launchLibraryPageLoad(page = 0, refresh = true)
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
                isLibraryLoading = false,
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
                isLibraryLoading = false,
                isPageLoading = false,
                errorMessage = null,
            )
        return true
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (current.selectedLibraryId == null || current.isLibraryLoading || current.isPageLoading || current.endReached) {
            return
        }
        invalidateBrowseLoad()
        launchLibraryPageLoad(page = current.currentPage + 1, refresh = false)
    }

    private fun invalidateBrowseLoad() {
        browseLoadGeneration += 1
        browseLoadJob?.cancel()
        browseLoadJob = null
        mutableState.update { it.copy(isLibraryLoading = false, isPageLoading = false) }
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
        if (expectedGeneration != browseLoadGeneration) return
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
                isLibraryLoading = page == 0 && stateBefore.libraryItems.isEmpty(),
                isPageLoading = page > 0,
                errorMessage = null,
                imageBaseUrl = imageBaseUrl,
                imageAccessToken = imageAccessToken,
            )
        try {
            val requestPage: suspend () -> LibraryPage = {
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
            }
            val libraryPage = requestPage()
            val (items, totalRecordCount) = libraryPage
            val newTotal =
                when {
                    page == 0 && refresh && filters == null -> totalRecordCount ?: stateBefore.totalLibraryItemCount
                    else -> stateBefore.totalLibraryItemCount
                }
            val merged =
                if (page == 0) {
                    items
                } else {
                    (stateBefore.libraryItems + items).distinctBy { it.id }
                }
            if (expectedGeneration != browseLoadGeneration) return
            mutableState.value =
                mutableState.value.copy(
                    isLibraryLoading = false,
                    isPageLoading = false,
                    libraryItems = merged,
                    currentPage = page,
                    endReached = items.size < pageSize,
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                    totalLibraryItemCount = newTotal,
                )
        } catch (cancellation: CancellationException) {
            if (expectedGeneration == browseLoadGeneration) {
                mutableState.update { it.copy(isLibraryLoading = false, isPageLoading = false) }
            }
            throw cancellation
        } catch (t: Throwable) {
            if (expectedGeneration != browseLoadGeneration) return
            mutableState.value =
                mutableState.value.copy(
                    isLibraryLoading = false,
                    isPageLoading = false,
                    errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "",
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                )
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
                isLibraryLoading = false,
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
