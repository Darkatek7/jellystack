package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class JellyseerrRequestsCoordinator(
    private val repository: JellyseerrRepository,
    private val environmentProvider: JellyseerrEnvironmentProvider,
    private val scope: CoroutineScope,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val enablePolling: Boolean = true,
    private val clock: Clock = Clock.System,
    private val autoStart: Boolean = true,
    private val searchDebounceMillis: Long = DEFAULT_SEARCH_DEBOUNCE_MILLIS,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<JellyseerrRequestsState>(JellyseerrRequestsState.Loading)
    val state: StateFlow<JellyseerrRequestsState> = _state

    private var environmentJob: Job? = null
    private var currentEnvironment: JellyseerrEnvironment? = null
    private var currentProfile: JellyseerrProfile? = null
    private var currentFilter: JellyseerrRequestFilter = JellyseerrRequestFilter.ALL
    private var lastRequests: List<JellyseerrRequestSummary> = emptyList()
    private val currentRequestsByMedia =
        mutableMapOf<Pair<JellyseerrMediaType, Int>, JellyseerrRequestSummary>()
    private var lastCounts: JellyseerrRequestCounts? = null
    private var currentQuery: String = ""
    private var lastSearchResults: List<JellyseerrSearchItem> = emptyList()
    private var lastLanguageProfiles: JellyseerrLanguageProfiles = JellyseerrLanguageProfiles.EMPTY
    private var pendingApprovalIds: Set<Int> = emptySet()
    private val pendingSubmitKeys = mutableSetOf<Pair<JellyseerrMediaType, Int>>()
    private var pollJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration: Long = 0L
    private var requestsRefreshGeneration: Long = 0L
    private var lastUpdated: Instant? = null
    private var nextMessageId: Long = 1L

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

    fun refresh() {
        scope.launch {
            val (environment, stateSnapshot, profile) =
                mutex.withLock { Triple(currentEnvironment, _state.value, currentProfile) }
            when {
                environment == null -> handleEnvironmentChange(null)
                stateSnapshot !is JellyseerrRequestsState.Ready || profile == null -> handleEnvironmentChange(environment)
                else -> refreshInternal(fetchCounts = true)
            }
        }
    }

    fun shutdown() {
        pollJob?.cancel()
        pollJob = null
        searchGeneration += 1
        searchJob?.cancel()
        searchJob = null
        environmentJob?.cancel()
        environmentJob = null
    }

    fun selectFilter(filter: JellyseerrRequestFilter) {
        scope.launch {
            mutex.withLock {
                if (currentFilter == filter) return@launch
                currentFilter = filter
                requestsRefreshGeneration += 1
                updateReadyState { it.copy(filter = filter) }
            }
            refreshInternal(fetchCounts = true)
        }
    }

    fun search(query: String) {
        searchGeneration += 1
        val generation = searchGeneration
        searchJob?.cancel()
        searchJob =
            scope.launch {
                val environment = mutex.withLock { currentEnvironment }
                if (environment == null) {
                    mutex.withLock {
                        if (generation != searchGeneration) return@withLock
                        currentQuery = ""
                        lastSearchResults = emptyList()
                        updateReadyState { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
                    }
                    return@launch
                }
                val normalizedQuery = query.trim()
                mutex.withLock {
                    if (generation != searchGeneration) return@launch
                    currentQuery = query
                    if (normalizedQuery.isEmpty()) {
                        lastSearchResults = emptyList()
                        updateReadyState { it.copy(query = query, searchResults = emptyList(), isSearching = false) }
                        return@launch
                    }
                    updateReadyState { it.copy(query = query, isSearching = true, searchResults = it.searchResults) }
                }
                delay(searchDebounceMillis)
                cancellationSafeRunCatching { repository.search(environment, normalizedQuery) }
                    .onSuccess { results ->
                        mutex.withLock {
                            if (generation != searchGeneration || currentQuery != query) return@withLock
                            lastSearchResults = results
                            updateReadyState {
                                it.copy(
                                    query = query,
                                    searchResults = results,
                                    isSearching = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        JellystackLog.e(
                            "Jellyseerr search failed for ${environment.serverId}: ${error.message}",
                            error,
                        )
                        mutex.withLock {
                            if (generation != searchGeneration || currentQuery != query) return@withLock
                            updateReadyState {
                                it.copy(
                                    query = query,
                                    searchResults = lastSearchResults,
                                    isSearching = false,
                                    message =
                                        nextMessage(
                                            kind = JellyseerrMessageKind.ERROR,
                                            code = JellyseerrMessageCode.SearchFailed,
                                            subject = normalizedQuery,
                                            detail = error.message,
                                        ),
                                )
                            }
                        }
                    }
            }
    }

    fun clearSearch() {
        searchGeneration += 1
        searchJob?.cancel()
        searchJob = null
        scope.launch {
            mutex.withLock {
                currentQuery = ""
                lastSearchResults = emptyList()
                updateReadyState { it.copy(query = "", searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun submitRequest(
        item: JellyseerrSearchItem,
        profileSelection: JellyseerrRequestProfileSelection,
        seasons: JellyseerrCreateSelection? = null,
        variant: JellyseerrRequestVariant? = null,
    ) {
        val submitKey = item.mediaType to item.tmdbId
        val operationKey = JellyseerrOperationKey.Submit(item.mediaType, item.tmdbId)
        scope.launch {
            val submitContext =
                mutex.withLock {
                    val resolvedEnvironment = currentEnvironment ?: return@launch
                    val capabilities =
                        currentProfile?.requestCapabilities()
                            ?: JellyseerrRequestCapabilities.NONE
                    val selectedProfile =
                        (profileSelection as? JellyseerrRequestProfileSelection.Profile)?.option
                    val resolvedVariant =
                        variant
                            ?: selectedProfile
                                ?.is4k
                                ?.let {
                                    if (it) {
                                        JellyseerrRequestVariant.FOUR_K
                                    } else {
                                        JellyseerrRequestVariant.STANDARD
                                    }
                                }
                            ?: JellyseerrRequestVariant.STANDARD
                    if (!capabilities.canRequest(item.mediaType, resolvedVariant)) {
                        updateReadyState {
                            it.copy(
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.RequestPermissionDenied,
                                        subject = item.title,
                                        operationKey = operationKey,
                                    ),
                            )
                        }
                        return@launch
                    }
                    if (!pendingSubmitKeys.add(submitKey)) return@launch
                    updateReadyState { it.copy(isPerformingAction = true) }
                    SubmitContext(
                        environment = resolvedEnvironment,
                        capabilities = capabilities,
                        variant = resolvedVariant,
                    )
                }
            val selectedProfile =
                (profileSelection as? JellyseerrRequestProfileSelection.Profile)
                    ?.option
                    ?.takeIf {
                        submitContext.capabilities.canUseAdvancedRequests &&
                            it.is4k == (submitContext.variant == JellyseerrRequestVariant.FOUR_K)
                    }
            val tmdbId = item.tmdbId
            val payload =
                JellyseerrCreateRequest(
                    mediaId = tmdbId,
                    tvdbId = item.tvdbId,
                    mediaType = item.mediaType,
                    is4k = submitContext.variant == JellyseerrRequestVariant.FOUR_K,
                    seasons = seasons,
                    serverId = selectedProfile?.serviceId,
                    profileId = selectedProfile?.profileId,
                    languageProfileId = selectedProfile?.languageProfileId,
                    title = item.title,
                )
            check(tmdbId == payload.mediaId) {
                "Selected TMDB id ($tmdbId) does not match Jellyseerr payload media id (${payload.mediaId})."
            }
            JellystackLog.d(
                "Submitting Jellyseerr request tmdbId=$tmdbId type=${payload.mediaType} title='${item.title}' server=${payload.serverId}",
            )
            when (val result = repository.createRequest(submitContext.environment, payload)) {
                is JellyseerrCreateResult.Success -> {
                    mutex.withLock {
                        pendingSubmitKeys.remove(submitKey)
                        lastRequests =
                            (
                                lastRequests.filterNot { it.id == result.request.id } +
                                    result.request
                            ).sortedBy { it.id }
                        currentRequestsByMedia[submitKey] = result.request
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                currentRequestsByMedia = currentRequestsByMedia.toMap(),
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.INFO,
                                        code = JellyseerrMessageCode.RequestSubmitted,
                                        subject = item.title,
                                        operationKey = operationKey,
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }
                is JellyseerrCreateResult.Duplicate -> {
                    mutex.withLock {
                        pendingSubmitKeys.remove(submitKey)
                        updateReadyState {
                            it.copy(
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.RequestDuplicate,
                                        subject = item.title,
                                        detail = result.message,
                                        recovery = JellyseerrMessageRecovery.RefreshRequests,
                                        operationKey = operationKey,
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = false)
                }
                is JellyseerrCreateResult.Failure -> {
                    mutex.withLock {
                        pendingSubmitKeys.remove(submitKey)
                        updateReadyState {
                            it.copy(
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.RequestFailed,
                                        subject = item.title,
                                        detail = result.message,
                                        recovery = JellyseerrMessageRecovery.RefreshRequests,
                                        operationKey = operationKey,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Pass an explicit JellyseerrRequestProfileSelection")
    fun submitRequest(
        item: JellyseerrSearchItem,
        languageProfile: JellyseerrLanguageProfileOption?,
        seasons: JellyseerrCreateSelection? = null,
    ) = submitRequest(
        item = item,
        profileSelection =
            languageProfile?.let(JellyseerrRequestProfileSelection::Profile)
                ?: JellyseerrRequestProfileSelection.ServerDefault,
        seasons = seasons,
        variant =
            languageProfile?.let {
                if (it.is4k) JellyseerrRequestVariant.FOUR_K else JellyseerrRequestVariant.STANDARD
            },
    )

    fun deleteRequest(requestId: Int) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            val subject =
                mutex.withLock {
                    lastRequests.firstOrNull { it.id == requestId }?.title
                        ?: currentRequestsByMedia.values
                            .firstOrNull { it.id == requestId }
                            ?.title
                }
            mutex.withLock { updateReadyState { it.copy(isPerformingAction = true) } }
            repository
                .deleteRequest(environment, requestId)
                .onSuccess {
                    mutex.withLock {
                        lastRequests = lastRequests.filterNot { it.id == requestId }
                        currentRequestsByMedia.entries.removeAll { it.value.id == requestId }
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                currentRequestsByMedia = currentRequestsByMedia.toMap(),
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.INFO,
                                        code = JellyseerrMessageCode.RequestRemoved,
                                        subject = subject,
                                        operationKey = JellyseerrOperationKey.Request(requestId),
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to delete Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.DeleteFailed,
                                        subject = subject,
                                        detail = error.message,
                                        operationKey = JellyseerrOperationKey.Request(requestId),
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun approveRequest(summary: JellyseerrRequestSummary) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            val requestId = summary.id
            var originalSummary = summary
            mutex.withLock {
                originalSummary =
                    lastRequests.firstOrNull { it.id == requestId }
                        ?: currentRequestsByMedia.values.firstOrNull { it.id == requestId }
                        ?: summary
                pendingApprovalIds = pendingApprovalIds + requestId
                lastRequests =
                    lastRequests.map { existing ->
                        if (existing.id == requestId) {
                            existing.copy(requestStatus = JellyseerrRequestStatus.APPROVED)
                        } else {
                            existing
                        }
                    }
                mergeCurrentRequests(
                    listOf(originalSummary.copy(requestStatus = JellyseerrRequestStatus.APPROVED)),
                )
                updateReadyState {
                    it.copy(
                        requests = lastRequests,
                        currentRequestsByMedia = currentRequestsByMedia.toMap(),
                        isPerformingAction = true,
                        pendingApprovals = pendingApprovalIds,
                    )
                }
            }
            repository
                .updateRequestStatus(environment, requestId, "approve")
                .onSuccess { updatedSummary ->
                    mutex.withLock {
                        lastRequests =
                            lastRequests.map { existing ->
                                if (existing.id == requestId) {
                                    updatedSummary
                                } else {
                                    existing
                                }
                            }
                        mergeCurrentRequests(listOf(updatedSummary))
                        pendingApprovalIds = pendingApprovalIds - requestId
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                currentRequestsByMedia = currentRequestsByMedia.toMap(),
                                isPerformingAction = hasPendingActions(),
                                pendingApprovals = pendingApprovalIds,
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.INFO,
                                        code = JellyseerrMessageCode.RequestApproved,
                                        subject = summary.title,
                                        operationKey = JellyseerrOperationKey.Request(requestId),
                                    ),
                            )
                        }
                    }
                    refreshInternal(fetchCounts = true)
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to approve Jellyseerr request $requestId for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        pendingApprovalIds = pendingApprovalIds - requestId
                        lastRequests =
                            lastRequests.map { existing ->
                                if (existing.id == requestId) {
                                    originalSummary
                                } else {
                                    existing
                                }
                            }
                        mergeCurrentRequests(listOf(originalSummary))
                        updateReadyState {
                            it.copy(
                                requests = lastRequests,
                                currentRequestsByMedia = currentRequestsByMedia.toMap(),
                                isPerformingAction = hasPendingActions(),
                                pendingApprovals = pendingApprovalIds,
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.ApprovalFailed,
                                        subject = summary.title,
                                        detail = error.message,
                                        operationKey = JellyseerrOperationKey.Request(requestId),
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun removeMedia(summary: JellyseerrRequestSummary) {
        scope.launch {
            val environment = mutex.withLock { currentEnvironment } ?: return@launch
            val mediaId =
                summary.mediaId
                    ?: run {
                        mutex.withLock {
                            updateReadyState {
                                it.copy(
                                    message =
                                        nextMessage(
                                            kind = JellyseerrMessageKind.ERROR,
                                            code = JellyseerrMessageCode.MediaIdMissing,
                                            subject = summary.title,
                                            operationKey =
                                                JellyseerrOperationKey.Request(summary.id),
                                        ),
                                )
                            }
                        }
                        return@launch
                    }
            mutex.withLock { updateReadyState { it.copy(isPerformingAction = true) } }
            repository
                .removeMediaFromService(environment, mediaId, summary.is4k)
                .onSuccess {
                    val retryResult = repository.retryRequest(environment, summary.id)
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind =
                                            if (retryResult.isSuccess) {
                                                JellyseerrMessageKind.INFO
                                            } else {
                                                JellyseerrMessageKind.ERROR
                                            },
                                        code =
                                            if (retryResult.isSuccess) {
                                                JellyseerrMessageCode.MediaRequeued
                                            } else {
                                                JellyseerrMessageCode.MediaRequeueFailed
                                            },
                                        subject = summary.title,
                                        detail = retryResult.exceptionOrNull()?.message,
                                        operationKey =
                                            JellyseerrOperationKey.Request(summary.id),
                                    ),
                            )
                        }
                    }
                    retryResult.onFailure { error ->
                        JellystackLog.e(
                            "Failed to retry Jellyseerr request ${summary.id} after media deletion for ${environment.serverId}: ${error.message}",
                            error,
                        )
                    }
                    refreshInternal(fetchCounts = true)
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to remove Jellyseerr media ${summary.mediaId} for ${environment.serverId}: ${error.message}",
                        error,
                    )
                    mutex.withLock {
                        updateReadyState {
                            it.copy(
                                isPerformingAction = hasPendingActions(),
                                message =
                                    nextMessage(
                                        kind = JellyseerrMessageKind.ERROR,
                                        code = JellyseerrMessageCode.RemoveMediaFailed,
                                        subject = summary.title,
                                        detail = error.message,
                                        operationKey =
                                            JellyseerrOperationKey.Request(summary.id),
                                    ),
                            )
                        }
                    }
                }
        }
    }

    fun acknowledgeMessage() {
        scope.launch {
            mutex.withLock {
                updateReadyState { it.copy(message = null) }
            }
        }
    }

    private suspend fun handleEnvironmentChange(environment: JellyseerrEnvironment?) {
        mutex.withLock {
            pollJob?.cancel()
            searchGeneration += 1
            searchJob?.cancel()
            searchJob = null
            requestsRefreshGeneration += 1
            currentEnvironment = environment
            currentProfile = null
            currentFilter = JellyseerrRequestFilter.ALL
            lastRequests = emptyList()
            currentRequestsByMedia.clear()
            lastCounts = null
            currentQuery = ""
            lastSearchResults = emptyList()
            lastLanguageProfiles = JellyseerrLanguageProfiles.EMPTY
            pendingApprovalIds = emptySet()
            pendingSubmitKeys.clear()
            lastUpdated = null
            _state.value =
                when (environment) {
                    null -> JellyseerrRequestsState.MissingServer
                    else -> JellyseerrRequestsState.Loading
                }
        }
        if (environment == null) {
            return
        }
        JellystackLog.d(
            "Loading Jellyseerr environment ${environment.serverId} at ${environment.baseUrl}",
        )

        data class InitialLoad(
            val profile: JellyseerrProfile,
            val page: JellyseerrRequestsPage,
            val counts: JellyseerrRequestCounts,
            val languageProfiles: JellyseerrLanguageProfiles,
            val capabilities: JellyseerrRequestCapabilities,
        )
        val loadResult =
            cancellationSafeRunCatching {
                val profile = repository.profile(environment)
                val capabilities = profile.requestCapabilities()
                val languageProfiles =
                    if (capabilities.canUseAdvancedRequests) {
                        repository.fetchLanguageProfiles(environment)
                    } else {
                        JellyseerrLanguageProfiles.EMPTY
                    }
                val page = repository.fetchRequests(environment, currentFilter)
                val counts = repository.fetchCounts(environment)
                InitialLoad(profile, page, counts, languageProfiles, capabilities)
            }
        loadResult
            .onSuccess { result ->
                JellystackLog.d(
                    "Loaded Jellyseerr environment ${environment.serverId} with ${result.page.results.size} requests",
                )
                mutex.withLock {
                    if (currentEnvironment != environment) return@withLock
                    currentProfile = result.profile
                    lastRequests = result.page.results
                    mergeCurrentRequests(result.page.results)
                    lastCounts = result.counts
                    lastLanguageProfiles = result.languageProfiles
                    lastUpdated = clock.now()
                    _state.value =
                        JellyseerrRequestsState.Ready(
                            filter = currentFilter,
                            requests = lastRequests,
                            counts = lastCounts,
                            query = currentQuery,
                            searchResults = lastSearchResults,
                            isSearching = false,
                            isRefreshing = false,
                            isPerformingAction = false,
                            pendingApprovals = pendingApprovalIds,
                            message = null,
                            isAdmin = result.capabilities.canManageRequests,
                            lastUpdated = lastUpdated,
                            languageProfiles = lastLanguageProfiles,
                            currentRequestsByMedia = currentRequestsByMedia.toMap(),
                            currentUserId = result.profile.id,
                            capabilities = result.capabilities,
                        )
                }
                startPolling()
            }.onFailure { error ->
                JellystackLog.e(
                    "Failed to load Jellyseerr environment ${environment.serverId}: ${error.message}",
                    error,
                )
                mutex.withLock {
                    if (currentEnvironment != environment) return@withLock
                    _state.value =
                        JellyseerrRequestsState.Error(error.message.orEmpty())
                }
            }
    }

    private fun startPolling() {
        if (!enablePolling) {
            return
        }
        pollJob?.cancel()
        pollJob =
            scope.launch {
                while (isActive) {
                    delay(pollIntervalMillis)
                    refreshInternal(fetchCounts = false)
                }
            }
    }

    private suspend fun refreshInternal(fetchCounts: Boolean) {
        data class RefreshContext(
            val environment: JellyseerrEnvironment,
            val filter: JellyseerrRequestFilter,
            val generation: Long,
        )
        val context =
            mutex.withLock {
                val environment = currentEnvironment ?: return
                requestsRefreshGeneration += 1
                RefreshContext(environment, currentFilter, requestsRefreshGeneration)
            }
        mutex.withLock {
            updateReadyState { it.copy(isRefreshing = true) }
        }
        val requestsResult =
            cancellationSafeRunCatching {
                repository.fetchRequests(context.environment, context.filter)
            }
        val countsResult =
            if (fetchCounts) {
                cancellationSafeRunCatching { repository.fetchCounts(context.environment) }
            } else {
                Result.success(mutex.withLock { lastCounts })
            }
        mutex.withLock {
            if (
                currentEnvironment != context.environment ||
                currentFilter != context.filter ||
                requestsRefreshGeneration != context.generation
            ) {
                return@withLock
            }
            requestsResult
                .onSuccess { page ->
                    lastRequests = page.results
                    mergeCurrentRequests(page.results)
                    lastUpdated = clock.now()
                }.onFailure { error ->
                    JellystackLog.e(
                        "Failed to refresh Jellyseerr requests for ${context.environment.serverId}: ${error.message}",
                        error,
                    )
                    updateReadyState {
                        it.copy(
                            isRefreshing = false,
                            message =
                                nextMessage(
                                    kind = JellyseerrMessageKind.ERROR,
                                    code = JellyseerrMessageCode.RefreshFailed,
                                    detail = error.message,
                                ),
                        )
                    }
                    return@withLock
                }
            countsResult.onSuccess { counts ->
                lastCounts = counts
            }
            updateReadyState {
                it.copy(
                    requests = lastRequests,
                    counts = lastCounts,
                    isRefreshing = false,
                    isPerformingAction = hasPendingActions(),
                    pendingApprovals = pendingApprovalIds,
                    lastUpdated = lastUpdated,
                    currentRequestsByMedia = currentRequestsByMedia.toMap(),
                )
            }
        }
    }

    private suspend fun updateReadyState(transform: (JellyseerrRequestsState.Ready) -> JellyseerrRequestsState.Ready) {
        _state.update { current ->
            if (current is JellyseerrRequestsState.Ready) {
                transform(current)
            } else {
                current
            }
        }
    }

    private fun mergeCurrentRequests(requests: List<JellyseerrRequestSummary>) {
        requests.forEach { summary ->
            summary.mediaKey()?.let { key ->
                currentRequestsByMedia[key] = summary
            }
        }
    }

    private fun JellyseerrRequestSummary.mediaKey(): Pair<JellyseerrMediaType, Int>? = tmdbId?.let { mediaType to it }

    private fun hasPendingActions(): Boolean = pendingApprovalIds.isNotEmpty() || pendingSubmitKeys.isNotEmpty()

    private fun nextMessage(
        kind: JellyseerrMessageKind,
        code: JellyseerrMessageCode,
        subject: String? = null,
        detail: String? = null,
        recovery: JellyseerrMessageRecovery = JellyseerrMessageRecovery.None,
        operationKey: JellyseerrOperationKey? = null,
    ): JellyseerrMessage =
        JellyseerrMessage(
            id = nextMessageId++,
            kind = kind,
            code = code,
            subject = subject,
            detail = detail,
            recovery = recovery,
            operationKey = operationKey,
        )

    private suspend inline fun <T> cancellationSafeRunCatching(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

    companion object {
        private const val DEFAULT_POLL_INTERVAL_MILLIS = 30_000L
        private const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private data class SubmitContext(
    val environment: JellyseerrEnvironment,
    val capabilities: JellyseerrRequestCapabilities,
    val variant: JellyseerrRequestVariant,
)
