package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class JellyseerrRecommendationsCoordinator(
    private val repository: JellyseerrRepository,
    private val environmentProvider: JellyseerrEnvironmentProvider,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val cacheTtl: Duration = 15.minutes,
    private val autoStart: Boolean = true,
) {
    private val mutex = Mutex()
    private val loadJobs = mutableMapOf<JellyseerrRecommendationRail, Job?>()
    private val detailJobs = mutableMapOf<Pair<JellyseerrMediaType, Int>, Job>()
    private val enrichmentJobs =
        mutableMapOf<Triple<JellyseerrMediaType, Int, JellyseerrDetailEnrichmentSection>, Job>()
    private val requestSummaries =
        mutableMapOf<Pair<JellyseerrMediaType, Int>, List<JellyseerrRequestSummary>>()
    private val detailVersions = mutableMapOf<Pair<JellyseerrMediaType, Int>, Long>()
    private val detailStates = mutableMapOf<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>()
    private val _state = MutableStateFlow<JellyseerrRecommendationsState>(JellyseerrRecommendationsState.Loading)
    val state: StateFlow<JellyseerrRecommendationsState> = _state
    private val _details =
        MutableStateFlow<Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>>(emptyMap())
    val details: StateFlow<Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>> = _details

    private var currentEnvironment: JellyseerrEnvironment? = null
    private var environmentJob: Job? = null

    init {
        if (autoStart) {
            start()
        }
    }

    fun start() {
        if (environmentJob?.isActive == true) return
        environmentJob =
            scope.launch {
                environmentProvider.observe().collect { environment ->
                    handleEnvironmentChange(environment)
                }
            }
    }

    fun refreshAll() {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            JellyseerrRecommendationRail.entries.forEach { rail ->
                loadRail(environment, rail, page = 1, reset = true)
            }
        }
    }

    fun refreshRail(rail: JellyseerrRecommendationRail) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            loadRail(environment, rail, page = 1, reset = true)
        }
    }

    fun loadNextPage(rail: JellyseerrRecommendationRail) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            val ready = state.value as? JellyseerrRecommendationsState.Ready ?: return@launch
            val railState = ready.rails[rail] ?: return@launch
            if (!railState.canLoadMore || railState.isLoading) {
                return@launch
            }
            loadRail(environment, rail, page = railState.nextPage, reset = false)
        }
    }

    fun retry(rail: JellyseerrRecommendationRail) {
        refreshRail(rail)
    }

    fun updateRequests(requests: List<JellyseerrRequestSummary>) {
        scope.launch {
            val grouped =
                requests
                    .filter { it.tmdbId != null }
                    .groupBy { it.mediaType to it.tmdbId!! }
            mutex.withLock {
                requestSummaries.clear()
                requestSummaries.putAll(grouped)
                val ready = _state.value as? JellyseerrRecommendationsState.Ready ?: return@withLock
                val updated = ready.rails.mapValues { (_, railState) -> applyRequestSummaries(railState) }
                _state.value = JellyseerrRecommendationsState.Ready(updated)
            }
        }
    }

    fun loadDetail(item: JellyseerrSearchItem) {
        loadDetail(item, forceRefresh = false)
    }

    fun reloadDetail(item: JellyseerrSearchItem) {
        loadDetail(item, forceRefresh = true)
    }

    fun retryDetailEnrichment(
        item: JellyseerrSearchItem,
        section: JellyseerrDetailEnrichmentSection,
    ) {
        val key = item.mediaType to item.tmdbId
        val jobKey = Triple(item.mediaType, item.tmdbId, section)
        val job =
            scope.launch {
                val environment = mutex.withLock { currentEnvironment } ?: return@launch
                val (current, version) =
                    mutex.withLock {
                        val loaded =
                            detailStates[key] as? JellyseerrMediaDetailState.Loaded
                                ?: return@launch
                        val currentVersion = detailVersions[key] ?: return@launch
                        detailStates[key] =
                            loaded.copy(
                                enrichmentLoadingSections =
                                    loaded.enrichmentLoadingSections + section,
                            )
                        _details.value = detailStates.toMap()
                        loaded.detail to currentVersion
                    }
                val result =
                    repository.fetchRecommendationDetailEnrichmentSection(
                        environment = environment,
                        primaryDetail = current,
                        section = section,
                    )
                mutex.withLock {
                    if (
                        currentEnvironment != environment ||
                        detailVersions[key] != version
                    ) {
                        return@withLock
                    }
                    val latest =
                        detailStates[key] as? JellyseerrMediaDetailState.Loaded
                            ?: return@withLock
                    detailStates[key] =
                        JellyseerrMediaDetailState.Loaded(
                            detail =
                                mergeDetailEnrichment(
                                    detail = latest.detail,
                                    update = result,
                                    section = section,
                                ),
                            enrichmentLoadingSections =
                                latest.enrichmentLoadingSections - section,
                        )
                    _details.value = detailStates.toMap()
                }
            }
        enrichmentJobs[jobKey]?.cancel()
        enrichmentJobs[jobKey] = job
    }

    private fun loadDetail(
        item: JellyseerrSearchItem,
        forceRefresh: Boolean,
    ) {
        val key = item.mediaType to item.tmdbId
        enrichmentJobs
            .filterKeys { (mediaType, tmdbId) ->
                mediaType == item.mediaType && tmdbId == item.tmdbId
            }.forEach { (_, job) -> job.cancel() }
        enrichmentJobs.keys.removeAll { (mediaType, tmdbId) ->
            mediaType == item.mediaType && tmdbId == item.tmdbId
        }
        val job =
            scope.launch {
                val environment = mutex.withLock { currentEnvironment } ?: return@launch
                val version =
                    mutex.withLock {
                        val existing = detailStates[key] as? JellyseerrMediaDetailState.Loaded
                        if (!forceRefresh && existing != null) {
                            return@launch
                        }
                        val nextVersion = (detailVersions[key] ?: 0L) + 1
                        detailVersions[key] = nextVersion
                        detailStates[key] =
                            existing?.copy(
                                enrichmentLoadingSections = ALL_ENRICHMENT_SECTIONS,
                            )
                                ?: JellyseerrMediaDetailState.Loading
                        _details.value = detailStates.toMap()
                        nextVersion
                    }
                val primaryResult =
                    runCatching {
                        repository.fetchRecommendationPrimaryDetail(
                            environment = environment,
                            tmdbId = item.tmdbId,
                            mediaType = item.mediaType,
                        )
                    }
                val primary =
                    mutex.withLock {
                        if (
                            currentEnvironment != environment ||
                            detailVersions[key] != version
                        ) {
                            return@withLock null
                        }
                        primaryResult
                            .onSuccess { detail ->
                                detailStates[key] =
                                    JellyseerrMediaDetailState.Loaded(
                                        detail = detail,
                                        enrichmentLoadingSections = ALL_ENRICHMENT_SECTIONS,
                                    )
                            }.onFailure { error ->
                                JellystackLog.e(
                                    "Failed to load Jellyseerr primary detail for ${environment.serverId} ${item.mediaType} ${item.tmdbId}: ${error.message}",
                                    error,
                                )
                                detailStates[key] =
                                    JellyseerrMediaDetailState.Error(
                                        error.message.orEmpty(),
                                    )
                            }
                        _details.value = detailStates.toMap()
                        primaryResult.getOrNull()
                    } ?: return@launch
                val enrichmentResult =
                    runCatching {
                        repository.fetchRecommendationDetailEnrichment(environment, primary)
                    }
                mutex.withLock {
                    if (currentEnvironment != environment) {
                        detailStates.remove(key)
                        _details.value = detailStates.toMap()
                        return@withLock
                    }
                    if (detailVersions[key] != version) return@withLock
                    enrichmentResult
                        .onSuccess { detail ->
                            detailStates[key] =
                                JellyseerrMediaDetailState.Loaded(
                                    detail =
                                        primary.copy(
                                            ratings = detail.ratings,
                                            enrichment = detail,
                                        ),
                                )
                        }.onFailure { error ->
                            JellystackLog.e(
                                "Failed to load Jellyseerr enrichment for ${environment.serverId} ${item.mediaType} ${item.tmdbId}: ${error.message}",
                                error,
                            )
                            detailStates[key] =
                                JellyseerrMediaDetailState.Loaded(
                                    detail = primary,
                                )
                        }
                    _details.value = detailStates.toMap()
                }
            }
        detailJobs[key]?.cancel()
        detailJobs[key] = job
    }

    fun shutdown() {
        scope.launch {
            mutex.withLock {
                loadJobs.values.forEach { it?.cancel() }
                loadJobs.clear()
                detailJobs.values.forEach(Job::cancel)
                detailJobs.clear()
                enrichmentJobs.values.forEach(Job::cancel)
                enrichmentJobs.clear()
            }
        }
        environmentJob?.cancel()
        environmentJob = null
    }

    private suspend fun handleEnvironmentChange(environment: JellyseerrEnvironment?) {
        val previousEnvironment =
            mutex.withLock {
                loadJobs.values.forEach { it?.cancel() }
                loadJobs.clear()
                detailJobs.values.forEach(Job::cancel)
                detailJobs.clear()
                enrichmentJobs.values.forEach(Job::cancel)
                enrichmentJobs.clear()
                currentEnvironment = environment
                requestSummaries.clear()
                detailVersions.clear()
                detailStates.clear()
                _details.value = emptyMap()
                when (environment) {
                    null -> _state.value = JellyseerrRecommendationsState.MissingServer
                    else -> _state.value = JellyseerrRecommendationsState.Loading
                }
                environment
            }
        if (previousEnvironment == null) {
            return
        }
        val now = clock.now()
        val cachedStates = mutableMapOf<JellyseerrRecommendationRail, JellyseerrRecommendationRailState>()
        for (rail in JellyseerrRecommendationRail.entries) {
            val pages = repository.cachedRecommendations(previousEnvironment, rail)
            val state = buildStateFromPages(rail, pages, now)
            cachedStates[rail] = applyRequestSummaries(state)
        }
        mutex.withLock {
            if (currentEnvironment != previousEnvironment) {
                return@withLock
            }
            val readyMap =
                JellyseerrRecommendationRail.entries.associateWith { rail ->
                    cachedStates[rail] ?: defaultRailState(rail)
                }
            _state.value = JellyseerrRecommendationsState.Ready(readyMap)
        }
        JellyseerrRecommendationRail.entries.forEach { rail ->
            val state = cachedStates[rail]
            val shouldRefresh = state == null || state.items.isEmpty() || state.isStale
            if (shouldRefresh) {
                loadRail(previousEnvironment, rail, page = 1, reset = true)
            }
        }
    }

    private fun loadRail(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
        reset: Boolean,
    ) {
        loadJobs[rail]?.cancel()
        val job =
            scope.launch {
                setLoadingState(rail, page, reset)
                val result = runCatching { repository.fetchRecommendations(environment, rail, page) }
                mutex.withLock {
                    if (currentEnvironment != environment) {
                        return@withLock
                    }
                    val current = _state.value as? JellyseerrRecommendationsState.Ready
                    val existing = current?.rails ?: JellyseerrRecommendationRail.entries.associateWith { defaultRailState(it) }
                    val previousState = existing[rail] ?: defaultRailState(rail)
                    val updated =
                        result.fold(
                            onSuccess = { pageResult ->
                                val merged = mergeRailState(previousState, pageResult, reset)
                                applyRequestSummaries(merged)
                            },
                            onFailure = { error ->
                                JellystackLog.e(
                                    "Failed to load Jellyseerr recommendations for ${environment.serverId} (${rail.name}): ${error.message}",
                                    error,
                                )
                                previousState.copy(
                                    isLoading = false,
                                    errorMessage = error.message.orEmpty(),
                                )
                            },
                        )
                    val newMap = existing + (rail to updated)
                    _state.value = JellyseerrRecommendationsState.Ready(newMap)
                }
            }
        loadJobs[rail] = job
    }

    private suspend fun setLoadingState(
        rail: JellyseerrRecommendationRail,
        page: Int,
        reset: Boolean,
    ) {
        mutex.withLock {
            val current = _state.value as? JellyseerrRecommendationsState.Ready
            val existing = current?.rails ?: JellyseerrRecommendationRail.entries.associateWith { defaultRailState(it) }
            val previous = existing[rail] ?: defaultRailState(rail)
            val updated =
                previous.copy(
                    isLoading = true,
                    errorMessage = if (reset || page == 1) null else previous.errorMessage,
                )
            val newMap = existing + (rail to updated)
            _state.value = JellyseerrRecommendationsState.Ready(newMap)
        }
    }

    private fun mergeRailState(
        existing: JellyseerrRecommendationRailState,
        page: JellyseerrRecommendationPage,
        reset: Boolean,
    ): JellyseerrRecommendationRailState {
        val baseItems = if (reset) emptyList() else existing.items
        val combined = (baseItems + page.items).distinctBy { it.mediaType to it.tmdbId }
        val canLoadMore = page.items.isNotEmpty() && page.page < page.totalPages
        return existing.copy(
            items = combined,
            isLoading = false,
            errorMessage = null,
            canLoadMore = canLoadMore,
            nextPage = page.page + 1,
            lastUpdated = page.fetchedAt,
            isStale = false,
        )
    }

    private fun buildStateFromPages(
        rail: JellyseerrRecommendationRail,
        pages: List<JellyseerrRecommendationPage>,
        now: Instant,
    ): JellyseerrRecommendationRailState {
        if (pages.isEmpty()) {
            return defaultRailState(rail)
        }
        val sorted = pages.sortedBy { it.page }
        val combined = sorted.flatMap { it.items }.distinctBy { it.mediaType to it.tmdbId }
        val lastPage = sorted.last()
        val lastUpdated = sorted.maxOf { it.fetchedAt }
        val canLoadMore = lastPage.items.isNotEmpty() && lastPage.page < lastPage.totalPages
        val isStale = now - lastUpdated >= cacheTtl
        return JellyseerrRecommendationRailState(
            rail = rail,
            items = combined,
            isLoading = false,
            errorMessage = null,
            canLoadMore = canLoadMore,
            nextPage = lastPage.page + 1,
            lastUpdated = lastUpdated,
            isStale = isStale,
        )
    }

    private fun defaultRailState(rail: JellyseerrRecommendationRail): JellyseerrRecommendationRailState =
        JellyseerrRecommendationRailState(
            rail = rail,
            items = emptyList(),
            isLoading = false,
            errorMessage = null,
            canLoadMore = true,
            nextPage = 1,
            lastUpdated = null,
            isStale = false,
        )

    private fun mergeDetailEnrichment(
        detail: JellyseerrMediaDetail,
        update: JellyseerrMediaDetailEnrichment,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetail {
        val previous = detail.enrichment
        val failed = section in update.failedSections
        val failedSections =
            if (failed) {
                previous.failedSections + section
            } else {
                previous.failedSections - section
            }
        val merged =
            previous.copy(
                ratings =
                    if (section == JellyseerrDetailEnrichmentSection.RATINGS && !failed) {
                        update.ratings
                    } else {
                        previous.ratings
                    },
                similar =
                    if (section == JellyseerrDetailEnrichmentSection.SIMILAR && !failed) {
                        update.similar
                    } else {
                        previous.similar
                    },
                recommendations =
                    if (section == JellyseerrDetailEnrichmentSection.RECOMMENDATIONS && !failed) {
                        update.recommendations
                    } else {
                        previous.recommendations
                    },
                failedSections = failedSections,
            )
        return detail.copy(
            ratings = merged.ratings,
            enrichment = merged,
        )
    }

    private companion object {
        val ALL_ENRICHMENT_SECTIONS = JellyseerrDetailEnrichmentSection.entries.toSet()
    }

    private fun applyRequestSummaries(state: JellyseerrRecommendationRailState): JellyseerrRecommendationRailState {
        if (requestSummaries.isEmpty()) {
            return state
        }
        val updatedItems =
            state.items.map { item ->
                val summaries = requestSummaries[item.mediaType to item.tmdbId]
                if (summaries != null && summaries != item.requests) {
                    item.copy(requests = summaries)
                } else {
                    item
                }
            }
        return state.copy(items = updatedItems)
    }
}
