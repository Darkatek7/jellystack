package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMessageCode
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class TvSearchMode { EDIT, BROWSE }

@Immutable
internal data class TvSearchSessionState(
    val query: String = "",
    val source: TvSearchSource = TvSearchSource.ALL,
    val mode: TvSearchMode = TvSearchMode.EDIT,
    val queryGeneration: Long = 0L,
)

internal sealed interface TvJellyfinSearchState {
    data object Idle : TvJellyfinSearchState

    data class Loading(
        val query: String,
        val previousItems: List<JellyfinItem> = emptyList(),
    ) : TvJellyfinSearchState

    data class Results(
        val query: String,
        val items: List<JellyfinItem>,
    ) : TvJellyfinSearchState

    data class Empty(
        val query: String,
    ) : TvJellyfinSearchState

    data class Failure(
        val query: String,
        val message: String?,
    ) : TvJellyfinSearchState
}

internal class TvJellyfinSearchCoordinator(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300L,
    initialSession: TvSearchSessionState = TvSearchSessionState(),
    private val submitSeerrSearch: (String) -> Unit = {},
    private val searchItems: suspend (String) -> List<JellyfinItem>,
) {
    private val mutableSession = MutableStateFlow(initialSession.copy(query = initialSession.query.trim()))
    val session: StateFlow<TvSearchSessionState> = mutableSession.asStateFlow()

    private val mutableState = MutableStateFlow<TvJellyfinSearchState>(TvJellyfinSearchState.Idle)
    val state: StateFlow<TvJellyfinSearchState> = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L
    private var lastQuery = ""
    private var restoredQuerySubmitted = false

    init {
        if (initialSession.query.isNotBlank()) restoreQuery(initialSession.query)
    }

    fun search(rawQuery: String) {
        restoredQuerySubmitted = true
        submitQuery(rawQuery, notifySeerr = true)
    }

    fun restoreQuery(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty() || restoredQuerySubmitted) return
        restoredQuerySubmitted = true
        submitQuery(query, notifySeerr = true)
    }

    fun selectSource(source: TvSearchSource) {
        mutableSession.value = mutableSession.value.copy(source = source)
    }

    fun enterEditMode() {
        mutableSession.value = mutableSession.value.copy(mode = TvSearchMode.EDIT)
    }

    fun enterBrowseMode() {
        mutableSession.value = mutableSession.value.copy(mode = TvSearchMode.BROWSE)
    }

    private fun submitQuery(
        rawQuery: String,
        notifySeerr: Boolean,
    ) {
        val query = rawQuery.trim()
        val requestGeneration = ++generation
        searchJob?.cancel()
        lastQuery = query
        mutableSession.value =
            mutableSession.value.copy(
                query = query,
                queryGeneration = mutableSession.value.queryGeneration + 1L,
            )
        if (notifySeerr) submitSeerrSearch(query)
        if (query.isEmpty()) {
            searchJob = null
            mutableState.value = TvJellyfinSearchState.Idle
            return
        }
        val previousItems =
            when (val current = mutableState.value) {
                is TvJellyfinSearchState.Results -> current.items.takeIf { current.query == query }.orEmpty()
                is TvJellyfinSearchState.Loading -> current.previousItems.takeIf { current.query == query }.orEmpty()
                else -> emptyList()
            }
        mutableState.value = TvJellyfinSearchState.Loading(query, previousItems)
        searchJob =
            scope.launch {
                delay(debounceMillis)
                if (requestGeneration != generation) return@launch
                val nextState =
                    runCatching { searchItems(query) }
                        .fold(
                            onSuccess = { items ->
                                if (items.isEmpty()) {
                                    TvJellyfinSearchState.Empty(query)
                                } else {
                                    TvJellyfinSearchState.Results(query, items)
                                }
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                TvJellyfinSearchState.Failure(query, error.message)
                            },
                        )
                if (requestGeneration == generation) mutableState.value = nextState
            }
    }

    fun retry() {
        if (lastQuery.isNotEmpty()) submitQuery(lastQuery, notifySeerr = false)
    }

    fun shutdown() {
        generation += 1L
        searchJob?.cancel()
        searchJob = null
    }
}

internal data class TvSearchPresentation(
    val jellyfinItems: List<JellyfinItem>,
    val seerrItems: List<JellyseerrSearchItem>,
    val showSearching: Boolean,
    val showNoResults: Boolean,
    val showJellyfinFailure: Boolean,
    val showSeerrFailure: Boolean,
)

private data class TvSearchSourceState<T>(
    val items: List<T>,
    val isLoading: Boolean,
    val hasFailure: Boolean,
)

internal fun tvSearchPresentation(
    rawQuery: String,
    source: TvSearchSource,
    jellyfinState: TvJellyfinSearchState,
    requestsState: JellyseerrRequestsState,
): TvSearchPresentation {
    val query = rawQuery.trim()
    val jellyfin = jellyfinSourceState(query, source, jellyfinState)
    val seerr = seerrSourceState(query, source, requestsState)
    val hasItems = jellyfin.items.isNotEmpty() || seerr.items.isNotEmpty()
    val isLoading = jellyfin.isLoading || seerr.isLoading
    val hasFailure = jellyfin.hasFailure || seerr.hasFailure
    return TvSearchPresentation(
        jellyfinItems = jellyfin.items,
        seerrItems = seerr.items,
        showSearching = query.isNotEmpty() && !hasItems && isLoading,
        showNoResults = query.isNotEmpty() && !hasItems && !isLoading && !hasFailure,
        showJellyfinFailure = query.isNotEmpty() && jellyfin.hasFailure,
        showSeerrFailure = query.isNotEmpty() && seerr.hasFailure,
    )
}

private fun jellyfinSourceState(
    query: String,
    source: TvSearchSource,
    state: TvJellyfinSearchState,
): TvSearchSourceState<JellyfinItem> {
    val isIncluded = source != TvSearchSource.SEERR
    val queryMatches = state.queryOrNull() == query
    val items =
        if (isIncluded && queryMatches) {
            when (state) {
                is TvJellyfinSearchState.Results -> state.items
                is TvJellyfinSearchState.Loading -> state.previousItems
                else -> emptyList()
            }
        } else {
            emptyList()
        }
    return TvSearchSourceState(
        items = items,
        isLoading = isIncluded && queryMatches && state is TvJellyfinSearchState.Loading,
        hasFailure = isIncluded && queryMatches && state is TvJellyfinSearchState.Failure,
    )
}

private fun seerrSourceState(
    query: String,
    source: TvSearchSource,
    state: JellyseerrRequestsState,
): TvSearchSourceState<JellyseerrSearchItem> {
    val isIncluded = source != TvSearchSource.JELLYFIN
    val ready = state as? JellyseerrRequestsState.Ready
    val queryMatches = ready?.query?.trim() == query
    val items =
        if (isIncluded && queryMatches) {
            ready?.searchResults.orEmpty()
        } else {
            emptyList()
        }
    val isLoading =
        isIncluded &&
            when (state) {
                JellyseerrRequestsState.Loading -> true
                is JellyseerrRequestsState.Ready -> !queryMatches || state.isSearching
                else -> false
            }
    return TvSearchSourceState(
        items = items,
        isLoading = isLoading,
        hasFailure = isIncluded && isSeerrSearchFailure(state, queryMatches, query),
    )
}

private fun isSeerrSearchFailure(
    state: JellyseerrRequestsState,
    queryMatches: Boolean,
    query: String,
): Boolean =
    when (state) {
        is JellyseerrRequestsState.Error -> true
        is JellyseerrRequestsState.Ready -> {
            val message = state.message
            queryMatches &&
                !state.isSearching &&
                message?.code == JellyseerrMessageCode.SearchFailed &&
                message.subject?.trim() == query
        }
        else -> false
    }

private fun TvJellyfinSearchState.queryOrNull(): String? =
    when (this) {
        TvJellyfinSearchState.Idle -> null
        is TvJellyfinSearchState.Loading -> query
        is TvJellyfinSearchState.Results -> query
        is TvJellyfinSearchState.Empty -> query
        is TvJellyfinSearchState.Failure -> query
    }

internal sealed interface TvDiscoverAvailability {
    data object Loading : TvDiscoverAvailability

    data object MissingConnection : TvDiscoverAvailability

    data class Failure(
        val message: String,
    ) : TvDiscoverAvailability

    data class Content(
        val state: JellyseerrRecommendationsState.Ready,
        val hasRailFailures: Boolean = false,
    ) : TvDiscoverAvailability
}

internal fun tvDiscoverAvailability(state: JellyseerrRecommendationsState): TvDiscoverAvailability =
    when (state) {
        JellyseerrRecommendationsState.Loading -> TvDiscoverAvailability.Loading
        JellyseerrRecommendationsState.MissingServer -> TvDiscoverAvailability.MissingConnection
        is JellyseerrRecommendationsState.Error -> TvDiscoverAvailability.Failure(state.message)
        is JellyseerrRecommendationsState.Ready -> {
            val rails = state.rails.values
            val hasItems = rails.any { it.items.isNotEmpty() }
            val hasRailFailures = rails.any { !it.errorMessage.isNullOrBlank() }
            val isLoading = rails.any { it.isLoading }
            when {
                hasItems -> TvDiscoverAvailability.Content(state, hasRailFailures)
                isLoading -> TvDiscoverAvailability.Loading
                hasRailFailures ->
                    TvDiscoverAvailability.Failure(
                        rails.firstNotNullOf { it.errorMessage?.takeIf(String::isNotBlank) },
                    )
                else -> TvDiscoverAvailability.Content(state)
            }
        }
    }
