@file:Suppress("TooManyFunctions")

package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
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

@Immutable
internal data class TvSearchSourceResult<T>(
    val query: String = "",
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
internal data class TvSearchUiState(
    val session: TvSearchSessionState = TvSearchSessionState(),
    val jellyfin: TvSearchSourceResult<JellyfinItem> = TvSearchSourceResult(),
    val seerr: TvSearchSourceResult<JellyseerrSearchItem> = TvSearchSourceResult(),
    val voiceAvailability: TvVoiceSearchAvailability = TvVoiceSearchAvailability.UNAVAILABLE,
    val isVoiceListening: Boolean = false,
    val voiceError: String? = null,
) {
    val showVoiceAction: Boolean
        get() = voiceAvailability == TvVoiceSearchAvailability.AVAILABLE

    companion object {
        fun completed(
            query: String,
            source: TvSearchSource = TvSearchSource.ALL,
            jellyfin: List<JellyfinItem> = emptyList(),
            seerr: List<JellyseerrSearchItem> = emptyList(),
        ): TvSearchUiState =
            TvSearchUiState(
                session = TvSearchSessionState(query = query, source = source, mode = TvSearchMode.BROWSE),
                jellyfin = TvSearchSourceResult(query = query, items = jellyfin),
                seerr = TvSearchSourceResult(query = query, items = seerr),
            )
    }
}

internal data class TvSearchSources(
    val jellyfin: suspend (String) -> List<JellyfinItem>,
    val seerr: suspend (String) -> List<JellyseerrSearchItem>,
)

internal class TvSearchCoordinator(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300L,
    initialSession: TvSearchSessionState = TvSearchSessionState(),
    private val voiceSearch: TvVoiceSearchPort = UnsupportedTvVoiceSearch,
    private val sources: TvSearchSources,
) {
    private val normalizedInitialSession = initialSession.copy(query = initialSession.query.trim())
    private val mutableState =
        MutableStateFlow(
            TvSearchUiState(
                session = normalizedInitialSession,
                voiceAvailability = voiceSearch.availability,
            ),
        )
    val state: StateFlow<TvSearchUiState> = mutableState.asStateFlow()

    private val mutableSession = MutableStateFlow(normalizedInitialSession)
    val session: StateFlow<TvSearchSessionState> = mutableSession.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L
    private var restoredQuerySubmitted = false

    init {
        if (normalizedInitialSession.query.isNotEmpty()) restoreQuery(normalizedInitialSession.query)
    }

    fun search(rawQuery: String) {
        restoredQuerySubmitted = true
        submitQuery(rawQuery, SearchTargets.BOTH)
    }

    fun restoreQuery(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty() || restoredQuerySubmitted) return
        restoredQuerySubmitted = true
        submitQuery(query, SearchTargets.BOTH)
    }

    fun selectSource(source: TvSearchSource) = updateSession { it.copy(source = source) }

    fun enterEditMode() = updateSession { it.copy(mode = TvSearchMode.EDIT) }

    fun enterBrowseMode() = updateSession { it.copy(mode = TvSearchMode.BROWSE) }

    fun retryJellyfin() {
        if (mutableState.value.session.query
                .isNotEmpty()
        ) {
            submitQuery(mutableState.value.session.query, SearchTargets.JELLYFIN)
        }
    }

    fun retrySeerr() {
        if (mutableState.value.session.query
                .isNotEmpty()
        ) {
            submitQuery(mutableState.value.session.query, SearchTargets.SEERR)
        }
    }

    fun launchVoiceSearch() {
        val available = voiceSearch.availability == TvVoiceSearchAvailability.AVAILABLE
        if (!available || mutableState.value.isVoiceListening) return
        mutableState.value = mutableState.value.copy(isVoiceListening = true, voiceError = null)
        voiceSearch.launch(::handleVoiceResult)
    }

    fun clearVoiceError() {
        mutableState.value = mutableState.value.copy(voiceError = null)
    }

    private fun handleVoiceResult(result: TvVoiceSearchResult) {
        when (result) {
            is TvVoiceSearchResult.Success -> {
                mutableState.value = mutableState.value.copy(isVoiceListening = false, voiceError = null)
                val query = result.text.trim()
                if (query.isNotEmpty()) {
                    submitQuery(query, SearchTargets.BOTH)
                    enterBrowseMode()
                }
            }
            TvVoiceSearchResult.Cancelled ->
                mutableState.value = mutableState.value.copy(isVoiceListening = false, voiceError = null)
            is TvVoiceSearchResult.Error ->
                mutableState.value =
                    mutableState.value.copy(
                        isVoiceListening = false,
                        voiceError = result.message ?: "Voice search failed",
                    )
        }
    }

    private fun submitQuery(
        rawQuery: String,
        targets: SearchTargets,
    ) {
        val query = rawQuery.trim()
        val requestGeneration = ++generation
        searchJob?.cancel()
        val current = mutableState.value
        val sameQuery = current.session.query == query
        val nextSession =
            current.session.copy(
                query = query,
                queryGeneration = current.session.queryGeneration + 1L,
            )
        mutableSession.value = nextSession
        if (query.isEmpty()) {
            searchJob = null
            mutableState.value =
                current.copy(
                    session = nextSession,
                    jellyfin = TvSearchSourceResult(),
                    seerr = TvSearchSourceResult(),
                    voiceError = null,
                )
            return
        }
        mutableState.value =
            current.copy(
                session = nextSession,
                jellyfin = current.jellyfin.start(query, targets.includesJellyfin, sameQuery),
                seerr = current.seerr.start(query, targets.includesSeerr, sameQuery),
                voiceError = null,
            )
        searchJob =
            scope.launch {
                delay(debounceMillis)
                if (targets.includesJellyfin) launch { completeJellyfin(query, requestGeneration) }
                if (targets.includesSeerr) launch { completeSeerr(query, requestGeneration) }
            }
    }

    private suspend fun completeJellyfin(
        query: String,
        requestGeneration: Long,
    ) {
        val result = runCatching { sources.jellyfin(query) }
        if (requestGeneration != generation) return
        result.fold(
            onSuccess = { items ->
                mutableState.value = mutableState.value.copy(jellyfin = TvSearchSourceResult(query, items))
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                mutableState.value =
                    mutableState.value.copy(
                        jellyfin = mutableState.value.jellyfin.copy(isLoading = false, errorMessage = error.message),
                    )
            },
        )
    }

    private suspend fun completeSeerr(
        query: String,
        requestGeneration: Long,
    ) {
        val result = runCatching { sources.seerr(query) }
        if (requestGeneration != generation) return
        result.fold(
            onSuccess = { items ->
                mutableState.value = mutableState.value.copy(seerr = TvSearchSourceResult(query, items))
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                mutableState.value =
                    mutableState.value.copy(
                        seerr = mutableState.value.seerr.copy(isLoading = false, errorMessage = error.message),
                    )
            },
        )
    }

    private fun updateSession(transform: (TvSearchSessionState) -> TvSearchSessionState) {
        val next = transform(mutableState.value.session)
        mutableSession.value = next
        mutableState.value = mutableState.value.copy(session = next)
    }

    fun shutdown() {
        generation += 1L
        searchJob?.cancel()
        searchJob = null
    }

    private enum class SearchTargets(
        val includesJellyfin: Boolean,
        val includesSeerr: Boolean,
    ) {
        BOTH(true, true),
        JELLYFIN(true, false),
        SEERR(false, true),
    }
}

private fun <T> TvSearchSourceResult<T>.start(
    query: String,
    included: Boolean,
    sameQuery: Boolean,
): TvSearchSourceResult<T> =
    when {
        !included -> this
        sameQuery -> copy(query = query, isLoading = true, errorMessage = null)
        else -> TvSearchSourceResult(query = query, isLoading = true)
    }

internal enum class TvSearchResultAction { PLAY, REQUEST }

@Immutable
internal data class TvSearchResult(
    val key: String,
    val jellyfinItem: JellyfinItem? = null,
    val seerrItem: JellyseerrSearchItem? = null,
    val action: TvSearchResultAction,
)

@Immutable
internal data class TvSearchPresentation(
    val results: List<TvSearchResult>,
    val jellyfinItems: List<JellyfinItem>,
    val seerrItems: List<JellyseerrSearchItem>,
    val showSearching: Boolean,
    val showNoResults: Boolean,
    val showJellyfinFailure: Boolean,
    val showSeerrFailure: Boolean,
)

internal fun tvSearchPresentation(state: TvSearchUiState): TvSearchPresentation {
    val query = state.session.query.trim()
    val jellyfinItems =
        state.jellyfin.items
            .takeIf { state.jellyfin.query == query }
            .orEmpty()
    val seerrItems =
        state.seerr.items
            .takeIf { state.seerr.query == query }
            .orEmpty()
    val results = reconcileSearchResults(state.session.source, jellyfinItems, seerrItems)
    val visibleJellyfin = results.mapNotNull(TvSearchResult::jellyfinItem)
    val visibleSeerr = results.filter { it.jellyfinItem == null }.mapNotNull(TvSearchResult::seerrItem)
    val isLoading = state.jellyfin.isLoading || state.seerr.isLoading
    val hasFailure = state.jellyfin.errorMessage != null || state.seerr.errorMessage != null
    return TvSearchPresentation(
        results = results,
        jellyfinItems = visibleJellyfin,
        seerrItems = visibleSeerr,
        showSearching = query.isNotEmpty() && results.isEmpty() && isLoading,
        showNoResults = query.isNotEmpty() && results.isEmpty() && !isLoading && !hasFailure,
        showJellyfinFailure = query.isNotEmpty() && state.jellyfin.errorMessage != null,
        showSeerrFailure = query.isNotEmpty() && state.seerr.errorMessage != null,
    )
}

private fun reconcileSearchResults(
    source: TvSearchSource,
    jellyfinItems: List<JellyfinItem>,
    seerrItems: List<JellyseerrSearchItem>,
): List<TvSearchResult> =
    when (source) {
        TvSearchSource.JELLYFIN -> jellyfinItems.map { it.toSearchResult() }
        TvSearchSource.SEERR -> seerrItems.map { it.toSearchResult() }
        TvSearchSource.ALL -> {
            val remainingSeerr = seerrItems.toMutableList()
            buildList {
                jellyfinItems.forEach { jellyfin ->
                    val matchIndex = remainingSeerr.indexOfFirst { seerr -> exactProviderMatch(jellyfin, seerr) }
                    val seerr = if (matchIndex >= 0) remainingSeerr.removeAt(matchIndex) else null
                    add(jellyfin.toSearchResult(seerr))
                }
                remainingSeerr.forEach { add(it.toSearchResult()) }
            }
        }
    }

private fun JellyfinItem.toSearchResult(seerrItem: JellyseerrSearchItem? = null): TvSearchResult =
    TvSearchResult(
        key = "jellyfin:$id",
        jellyfinItem = this,
        seerrItem = seerrItem,
        action = TvSearchResultAction.PLAY,
    )

private fun JellyseerrSearchItem.toSearchResult(): TvSearchResult =
    TvSearchResult(
        key = "seerr:${mediaType.name.lowercase()}:$tmdbId",
        seerrItem = this,
        action = TvSearchResultAction.REQUEST,
    )

private fun exactProviderMatch(
    jellyfin: JellyfinItem,
    seerr: JellyseerrSearchItem,
): Boolean {
    if (jellyfin.searchMediaType() != seerr.mediaType) return false
    val jellyfinTmdb = jellyfin.providerIds.tmdbId.normalizedProviderId()
    val jellyfinTvdb = jellyfin.providerIds.tvdbId.normalizedProviderId()
    val seerrTmdb = seerr.tmdbId.takeIf { it > 0 }?.toString()
    val seerrTvdb = seerr.tvdbId?.takeIf { it > 0 }?.toString()
    return (jellyfinTmdb != null && jellyfinTmdb == seerrTmdb) ||
        (jellyfinTvdb != null && jellyfinTvdb == seerrTvdb)
}

private fun JellyfinItem.searchMediaType(): JellyseerrMediaType? =
    when (type.lowercase()) {
        "movie" -> JellyseerrMediaType.MOVIE
        "series" -> JellyseerrMediaType.TV
        else -> null
    }

private fun String?.normalizedProviderId(): String? = this?.trim()?.trimStart('0')?.takeIf(String::isNotEmpty)

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
