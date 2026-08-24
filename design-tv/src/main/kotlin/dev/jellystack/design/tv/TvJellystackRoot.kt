@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
)

package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.core.jellyfin.LocalTrailerContext
import dev.jellystack.core.jellyfin.LocalTrailerResolver
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import kotlinx.coroutines.launch

@Composable
internal fun TvRouteFocusScope(
    focusCoordinator: TvFocusCoordinator<FocusRequester>,
    routeKey: String,
    focusMemory: TvFocusMemory? = null,
    content: @Composable () -> Unit,
) {
    val entryFocusRequester = remember(focusCoordinator, routeKey) { FocusRequester() }
    CompositionLocalProvider(
        LocalTvScreenEntryFocusRequester provides entryFocusRequester,
        LocalTvFocusContext provides TvFocusContext(focusCoordinator, routeKey, focusMemory),
        content = content,
    )
}

@Composable
internal fun TvAppBackHandler(dispatcher: TvAppBackDispatcher) {
    BackHandler(enabled = dispatcher.rootHandlerEnabled) { dispatcher.dispatch() }
}

@Composable
@OptIn(UnstableApi::class)
fun TvJellystackRoot(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    stopPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JellystackTvTheme {
        val koin = remember { JellystackDI.koin }
        val serverRepository = remember(koin) { koin.get<ServerRepository>() }
        val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
        val settingsRepository = remember(koin) { koin.get<AppSettingsRepository>() }
        val settings by settingsRepository.settings.collectAsStateWithLifecycle()
        val strings = remember(settings.appLanguage) { TvStrings.current(settings.appLanguage) }
        Box(modifier.fillMaxSize().background(TvBackground)) {
            if (servers.none { it.type == ServerType.JELLYFIN }) {
                TvConnectionScreen(
                    coordinator = koin.get<ServerConnectionCoordinator>(),
                    quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                    appVersion = appVersion,
                    strings = strings,
                    onConnected = {},
                )
            } else {
                TvAuthenticatedApp(
                    playbackController = playbackController,
                    playerEngine = playerEngine,
                    trailerPreviewPlaybackController = trailerPreviewController,
                    trailerPreviewEngine = trailerPreviewEngine,
                    appVersion = appVersion,
                    settingsRepository = settingsRepository,
                    serverRepository = serverRepository,
                    strings = strings,
                    stopPlayback = stopPlayback,
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun TvAuthenticatedApp(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewPlaybackController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    strings: TvStrings,
    stopPlayback: () -> Unit,
) {
    val koin = remember { JellystackDI.koin }
    val rootScope = rememberCoroutineScope()
    val activeJellyfinServer by
        serverRepository
            .observeActiveServer(ServerType.JELLYFIN)
            .collectAsStateWithLifecycle(initialValue = serverRepository.activeServer(ServerType.JELLYFIN))
    // Compose adapts the lifecycle-agnostic holder to process-death persistence at this root boundary.
    val appStateSaver =
        remember {
            Saver<TvAppStateHolder, String>(
                save = { TvAppStatePersistence.encode(it.snapshot()) },
                restore = { raw -> TvAppStatePersistence.decode(raw)?.let(::TvAppStateHolder) },
            )
        }
    val appStateHolder = rememberSaveable(saver = appStateSaver) { TvAppStateHolder() }
    val appUiState = appStateHolder.state
    val focusMemory = appStateHolder.focusMemory
    val authenticatedEnvironmentIdentity =
        activeJellyfinServer?.let { server ->
            (server.credentials as? StoredCredential.Jellyfin)?.let { credential ->
                TvAuthenticatedEnvironmentIdentity(
                    serverConnectionId = server.id,
                    principalId = credential.userId,
                )
            }
        }
    var lastBoundIdentity by remember { mutableStateOf<TvAuthenticatedEnvironmentIdentity?>(null) }
    LaunchedEffect(authenticatedEnvironmentIdentity) {
        if (lastBoundIdentity != null && lastBoundIdentity != authenticatedEnvironmentIdentity) stopPlayback()
        if (authenticatedEnvironmentIdentity == null) {
            appStateHolder.deactivateEnvironment()
        } else {
            appStateHolder.activateEnvironment(authenticatedEnvironmentIdentity)
        }
        lastBoundIdentity = authenticatedEnvironmentIdentity
    }
    // Never construct or compose account state while its restored owner is unvalidated. On a
    // principal change the old account group leaves composition before the clean generation enters.
    if (appUiState.environmentIdentity != authenticatedEnvironmentIdentity || authenticatedEnvironmentIdentity == null) {
        return
    }
    val accountGeneration =
        remember(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            TvAccountGeneration(authenticatedEnvironmentIdentity, rootScope)
        }
    val scope = accountGeneration.scope
    DisposableEffect(accountGeneration) {
        onDispose(accountGeneration::close)
    }
    val browseRepository = remember(accountGeneration) { koin.get<JellyfinBrowseRepository>() }
    val environmentProvider = remember(accountGeneration) { koin.get<JellyfinEnvironmentProvider>() }
    val sessionRepository =
        remember(accountGeneration) { koin.get<JellyfinSessionRepository>().isolatedSession() }
    val browseCoordinator =
        remember(accountGeneration) {
            JellyfinBrowseCoordinator(
                repository = browseRepository,
                scope = scope,
                favoritesStore = koin.get<JellyfinFavoritesStoreApi>(),
            )
        }
    val homeSectionsRepository =
        remember(accountGeneration) { koin.get<HomeSectionsRepository>().isolatedSession() }
    val recommendationsCoordinator =
        remember(accountGeneration) {
            JellyseerrRecommendationsCoordinator(
                repository = koin.get<JellyseerrRepository>(),
                environmentProvider = koin.get<JellyseerrEnvironmentProvider>(),
                scope = scope,
            )
        }
    val requestsCoordinator =
        remember(accountGeneration) {
            JellyseerrRequestsCoordinator(
                repository = koin.get<JellyseerrRepository>(),
                environmentProvider = koin.get<JellyseerrEnvironmentProvider>(),
                scope = scope,
            )
        }
    val detailTrailerResolver =
        remember(accountGeneration) {
            DetailTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
                fetchSeerrTrailer = { tmdbId, isShow ->
                    koin.get<JellyseerrEnvironmentProvider>().current()?.let { environment ->
                        koin
                            .get<JellyseerrRepository>()
                            .fetchRecommendationDetail(
                                environment,
                                tmdbId,
                                if (isShow) {
                                    dev.jellystack.core.jellyseerr.JellyseerrMediaType.TV
                                } else {
                                    dev.jellystack.core.jellyseerr.JellyseerrMediaType.MOVIE
                                },
                            ).trailer
                    }
                },
            )
        }
    val localTrailerResolver =
        remember(accountGeneration) {
            LocalTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
            )
        }
    val trailerPreviewPlayer =
        remember(accountGeneration, trailerPreviewPlaybackController, trailerPreviewEngine, environmentProvider) {
            TvPlaybackTrailerPreviewPlayer(
                controller = trailerPreviewPlaybackController,
                engine = trailerPreviewEngine,
                environmentProvider = environmentProvider,
            )
        }
    val trailerPreviewCoordinator =
        remember(accountGeneration, localTrailerResolver, trailerPreviewPlayer) {
            TvTrailerPreviewController(
                scope = scope,
                resolve = { target ->
                    if (environmentProvider.current()?.serverKey != target.serverKey) {
                        null
                    } else {
                        localTrailerResolver.resolve(
                            LocalTrailerContext(
                                itemId = target.itemId,
                                isEpisode = target.isEpisode,
                                seriesId = target.seriesId,
                            ),
                        )
                    }
                },
                player = trailerPreviewPlayer,
            )
        }
    val syncPlay =
        remember(accountGeneration) {
            SyncPlayCoordinator(
                environmentProvider = environmentProvider,
                playbackController = playbackController,
                playItem = { itemId, positionMs ->
                    val item = browseRepository.cachedItem(itemId) ?: return@SyncPlayCoordinator
                    val detail = browseRepository.getItemDetail(itemId) ?: return@SyncPlayCoordinator
                    val environment = environmentProvider.current() ?: return@SyncPlayCoordinator
                    playbackController.play(
                        PlaybackRequest
                            .from(item, detail, startPolicy = PlaybackStartPolicy.RESUME)
                            .copy(resumePositionTicks = positionMs * 10_000L),
                        environment,
                    )
                },
                onAccessDenied = { sessionRepository.refresh() },
                scope = scope,
            )
        }
    val homeState by browseCoordinator.state.collectAsStateWithLifecycle()
    val homeSections by homeSectionsRepository.state.collectAsStateWithLifecycle()
    val recommendations by recommendationsCoordinator.state.collectAsStateWithLifecycle()
    val requests by requestsCoordinator.state.collectAsStateWithLifecycle()
    val details by recommendationsCoordinator.details.collectAsStateWithLifecycle()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    val syncPlayState by syncPlay.state.collectAsStateWithLifecycle()
    val playbackIdentity =
        activeJellyfinServer?.let { server ->
            (server.credentials as? StoredCredential.Jellyfin)?.let { credential ->
                TvJellyfinPlaybackIdentity(serverKey = server.id, userId = credential.userId)
            }
        }
    val trailerPreviewState by trailerPreviewCoordinator.state.collectAsStateWithLifecycle()
    val trailerPreviewProgress = remember(accountGeneration) { mutableStateOf(0f) }
    LaunchedEffect(accountGeneration, trailerPreviewPlaybackController) {
        trailerPreviewPlaybackController.state.collect { playbackState ->
            trailerPreviewProgress.value =
                (playbackState as? PlaybackState.Active)?.let { active ->
                    active.durationMs?.takeIf { it > 0L }?.let { active.positionMs.toFloat() / it.toFloat() }
                } ?: 0f
        }
    }
    val sessionState by sessionRepository.state.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    var savedSearchQuery by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) { mutableStateOf("") }
    var savedSearchSource by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            mutableStateOf(TvSearchSource.ALL.name)
        }
    var savedSearchMode by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            mutableStateOf(TvSearchMode.EDIT.name)
        }
    val jellyfinSearchCoordinator =
        remember(accountGeneration, scope, browseRepository, requestsCoordinator) {
            TvJellyfinSearchCoordinator(
                scope = scope,
                initialSession =
                    TvSearchSessionState(
                        query = savedSearchQuery,
                        source = TvSearchSource.entries.firstOrNull { it.name == savedSearchSource } ?: TvSearchSource.ALL,
                        mode = TvSearchMode.entries.firstOrNull { it.name == savedSearchMode } ?: TvSearchMode.EDIT,
                    ),
                submitSeerrSearch = requestsCoordinator::search,
                searchItems = browseRepository::searchItems,
            )
        }
    val jellyfinSearchState by jellyfinSearchCoordinator.state.collectAsStateWithLifecycle()
    val searchSession by jellyfinSearchCoordinator.session.collectAsStateWithLifecycle()
    LaunchedEffect(searchSession) {
        savedSearchQuery = searchSession.query
        savedSearchSource = searchSession.source.name
        savedSearchMode = searchSession.mode.name
    }
    DisposableEffect(jellyfinSearchCoordinator) {
        onDispose(jellyfinSearchCoordinator::shutdown)
    }
    val segmentHttpClient =
        remember(accountGeneration) { NetworkClientFactory.create(ClientConfig(installLogging = false)) }
    val playbackCommandRouter =
        remember(playbackController, syncPlay) {
            TvPlaybackCommandRouter(
                isSyncPlayActive = { syncPlay.state.value.currentGroup != null },
                requestSyncSeek = syncPlay::requestSeek,
                requestLocalSeek = playbackController::seekTo,
                requestSyncNext = syncPlay::requestNext,
            )
        }
    val playbackCoordinators =
        rememberTvPlaybackCoordinators(
            identity = playbackIdentity,
            playbackState = playbackState,
            createSegmentCoordinator = { coordinatorScope ->
                PlaybackSegmentCoordinator(
                    scope = coordinatorScope,
                    segmentService = TvJellyfinMediaSegmentsService(environmentProvider, segmentHttpClient),
                    modeProvider = PlaybackSegmentModeProvider { type -> settingsRepository.settings.value.segmentSkipMode(type) },
                    seekAdapter = PlaybackSeekAdapter(playbackCommandRouter::seekTo),
                )
            },
            createContinuationCoordinator = { coordinatorScope ->
                PlaybackContinuationCoordinator(
                    scope = coordinatorScope,
                    modeProvider = { settingsRepository.settings.value.autoplayNextMode },
                    resolveNext = resolve@{ mediaId, seriesId ->
                        val cached = browseRepository.episodesForSeries(seriesId)
                        val episodes = if (cached.isEmpty()) browseRepository.refreshEpisodesForSeries(seriesId) else cached
                        val next = selectNextTvEpisode(episodes, mediaId) ?: return@resolve null
                        val detail = browseRepository.getItemDetail(next.id) ?: return@resolve null
                        val environment = environmentProvider.current() ?: return@resolve null
                        PlaybackContinuationTarget(next.id, next.episodeTitle ?: next.name) {
                            playbackCommandRouter.playNext {
                                playbackController.play(
                                    PlaybackRequest.from(next, detail, startPolicy = PlaybackStartPolicy.RESTART),
                                    environment,
                                )
                                playbackController.setPlaybackSpeed(settingsRepository.settings.value.defaultPlaybackSpeed)
                                playbackController.setStatsForNerdsEnabled(settingsRepository.settings.value.statsForNerdsEnabled)
                            }
                        }
                    },
                )
            },
        )
    val segmentCoordinator = playbackCoordinators.segment
    val continuationCoordinator = playbackCoordinators.continuation
    val segmentState by segmentCoordinator.state.collectAsStateWithLifecycle()
    val continuationState by continuationCoordinator.state.collectAsStateWithLifecycle()
    val syncPlayAccess =
        (sessionState as? JellyfinSessionState.Ready)?.capabilities?.syncPlayAccess
            ?: JellyfinSyncPlayAccess.NONE

    fun push(route: TvRoute) {
        appStateHolder.push(route)
    }

    fun openJellyfinDetail(item: JellyfinItem) {
        appStateHolder.rememberDetailSource(item)
        push(TvRoute.JellyfinDetail(item.id))
    }

    fun selectTopLevel(route: TvRoute) {
        appStateHolder.selectTopLevel(route)
    }

    fun openSettingsConnections() {
        push(tvConnectionsSettingsRoute())
    }

    fun openSeerr(item: JellyseerrSearchItem) {
        recommendationsCoordinator.loadDetail(item)
        push(item.toTvRoute())
    }

    fun openSeerr(route: TvRoute.SeerrDetail) {
        val item = route.toSearchItem()
        recommendationsCoordinator.loadDetail(item)
        push(route)
    }

    LaunchedEffect(
        accountGeneration,
        settings.useServerHomeSections,
        settings.appLanguage,
        serverRepository.currentServers(),
    ) {
        homeSectionsRepository.refresh(
            enabledByUser = settings.useServerHomeSections,
            language = settings.appLanguage.languageTag,
        )
    }
    LaunchedEffect(accountGeneration, serverRepository.currentServers()) {
        sessionRepository.refresh()
    }
    LaunchedEffect(syncPlayAccess) {
        syncPlay.updateAccess(syncPlayAccess)
    }
    LaunchedEffect(settings.trailerPreviewsEnabled) {
        trailerPreviewCoordinator.setEnabled(settings.trailerPreviewsEnabled)
    }
    LaunchedEffect(settings.trailerPreviewSoundEnabled) {
        trailerPreviewCoordinator.setSoundEnabled(settings.trailerPreviewSoundEnabled)
    }
    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Active || playbackState is PlaybackState.Preparing) {
            trailerPreviewCoordinator.clearFocus()
        }
    }
    LaunchedEffect(accountGeneration, serverRepository.currentServers()) { trailerPreviewCoordinator.invalidateCache() }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            appStateHolder.onForegrounded()
        } else {
            appStateHolder.onBackgrounded()
            trailerPreviewCoordinator.onBackgrounded()
        }
    }
    DisposableEffect(accountGeneration, segmentHttpClient) {
        onDispose {
            browseCoordinator.shutdown()
            homeSectionsRepository.close()
            sessionRepository.close()
            recommendationsCoordinator.shutdown()
            requestsCoordinator.shutdown()
            syncPlay.close()
            segmentHttpClient.close()
            trailerPreviewCoordinator.release()
        }
    }

    val currentRoute = appUiState.currentRoute
    val jellyfinServerKey = serverRepository.activeServer(ServerType.JELLYFIN)?.id
    val showRail =
        currentRoute is TvRoute.Home ||
            currentRoute is TvRoute.Library ||
            currentRoute is TvRoute.Search ||
            currentRoute is TvRoute.Discover ||
            currentRoute is TvRoute.Settings
    val focusCoordinator =
        remember(appUiState.activeProfileGeneration) {
            TvFocusCoordinator<FocusRequester>(
                awaitFocusFrame = { withFrameNanos { } },
            )
        }
    val currentFocusRouteKey =
        currentRoute.focusRouteKey(
            if (currentRoute is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
        )
    val focusContentAuthoritativelyLoaded =
        when (currentRoute) {
            TvRoute.Home ->
                !homeState.isInitialLoading &&
                    !homeState.isHomeLoading &&
                    homeSections !is HomeSectionsState.Loading
            is TvRoute.Library -> !homeState.isLibraryLoading && !homeState.isPageLoading
            TvRoute.Search ->
                jellyfinSearchState !is TvJellyfinSearchState.Loading &&
                    (requests as? dev.jellystack.core.jellyseerr.JellyseerrRequestsState.Ready)?.isSearching != true
            TvRoute.Discover ->
                recommendations !is JellyseerrRecommendationsState.Loading &&
                    (recommendations as? JellyseerrRecommendationsState.Ready)
                        ?.rails
                        ?.values
                        ?.none { it.isLoading } != false
            else -> true
        }
    val semanticRestorationSession =
        remember(focusCoordinator, currentFocusRouteKey) {
            TvSemanticFocusRestorationSession(
                snapshot = focusMemory.restore(currentFocusRouteKey),
                interactionRevision = focusCoordinator.currentInteractionRevision,
            )
        }
    val backDispatcher =
        TvAppBackDispatcher(
            holder = appStateHolder,
            libraryPathDepth = { homeState.browsePath.size },
            selectedLibraryId = { homeState.selectedLibraryId },
            popLibraryPath = browseCoordinator::navigateUp,
            cancelFocusRestoration = focusCoordinator::onUserMovement,
        )
    TvAppBackHandler(backDispatcher)
    val focusRegistrationRevision = focusCoordinator.registrationRevision
    LaunchedEffect(
        showRail,
        appUiState.railExpanded,
        currentFocusRouteKey,
        appUiState.isForeground,
        focusContentAuthoritativelyLoaded,
        focusRegistrationRevision,
        semanticRestorationSession,
    ) {
        if (!showRail || !appUiState.isForeground) return@LaunchedEffect
        if (appUiState.railExpanded) {
            val railRestoration =
                focusCoordinator.restoreFocus(
                    routeKey = TV_FOCUS_RAIL_ROUTE,
                    preferredTargetId = tvRailTargetId(currentRoute),
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            if (railRestoration is TvFocusRestoration.Failed) {
                appStateHolder.closeRail()
                focusCoordinator.onUserMovement()
                focusCoordinator.restoreFocus(
                    routeKey = currentFocusRouteKey,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        } else {
            // A missing content target must not turn route restoration (including Back) into a rail opener.
            semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
            if (!semanticRestorationSession.isPending) return@LaunchedEffect
            val semanticTargetId =
                semanticRestorationSession.preferredTargetId(
                    availableTargets = focusCoordinator.focusTargets(currentFocusRouteKey),
                    contentAuthoritativelyLoaded = focusContentAuthoritativelyLoaded,
                ) ?: return@LaunchedEffect
            val restoration =
                focusCoordinator.restoreFocus(
                    routeKey = currentFocusRouteKey,
                    preferredTargetId = semanticTargetId,
                    includeFallback = false,
                    requiredInteractionRevision = semanticRestorationSession.interactionRevision,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            when (restoration) {
                is TvFocusRestoration.Focused -> semanticRestorationSession.complete()
                TvFocusRestoration.Cancelled ->
                    semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
                TvFocusRestoration.Failed -> Unit
            }
        }
    }
    LaunchedEffect(appUiState.railExpanded, currentRoute) {
        if (appUiState.railExpanded || currentRoute !is TvRoute.Home) trailerPreviewCoordinator.clearFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    trailerPreviewCoordinator.onUserInteraction()
                }
                if (
                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode in
                    setOf(
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    )
                ) {
                    focusCoordinator.onUserMovement()
                }
                false
            },
    ) {
        CompositionLocalProvider(
            LocalTvNavigationRailOpener provides {
                if (!appStateHolder.state.railExpanded) {
                    focusCoordinator.onUserMovement()
                    appStateHolder.openRail()
                }
            },
        ) {
            NavDisplay(
                backStack = appUiState.backStack,
                onBack = { backDispatcher.dispatch() },
                modifier = Modifier.fillMaxSize(),
                entryProvider = { route ->
                    NavEntry(route) {
                        val entryRouteKey =
                            route.focusRouteKey(
                                if (route is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
                            )
                        TvRouteFocusScope(focusCoordinator, entryRouteKey, focusMemory) {
                            when (route) {
                                TvRoute.Home ->
                                    TvHomeScreen(
                                        state = homeState,
                                        homeSections = homeSections,
                                        strings = strings,
                                        trailerPreviewState = trailerPreviewState,
                                        focusMemory = focusMemory,
                                        onRefresh = {
                                            trailerPreviewCoordinator.invalidateCache()
                                            browseCoordinator.bootstrap(true)
                                        },
                                        onPreviewFocus = { owner, item, presentationId ->
                                            if (jellyfinServerKey != null) {
                                                trailerPreviewCoordinator.focus(
                                                    TvTrailerPreviewRequest(
                                                        owner = owner,
                                                        presentationId = presentationId,
                                                        target =
                                                            TvTrailerPreviewTarget(
                                                                serverKey = jellyfinServerKey,
                                                                itemId = item.id,
                                                                isEpisode = item.type.equals("Episode", true),
                                                                seriesId = item.seriesId,
                                                            ),
                                                    ),
                                                )
                                            }
                                        },
                                        onPreviewBlur = { owner, item, presentationId ->
                                            if (jellyfinServerKey != null) {
                                                trailerPreviewCoordinator.clearFocus(
                                                    TvTrailerPreviewRequest(
                                                        owner = owner,
                                                        presentationId = presentationId,
                                                        target =
                                                            TvTrailerPreviewTarget(
                                                                serverKey = jellyfinServerKey,
                                                                itemId = item.id,
                                                                isEpisode = item.type.equals("Episode", true),
                                                                seriesId = item.seriesId,
                                                            ),
                                                    ),
                                                )
                                            }
                                        },
                                        onCancelPreview = trailerPreviewCoordinator::clearFocus,
                                        trailerPreviewEngine = trailerPreviewEngine,
                                        previewSoundEnabled = settings.trailerPreviewSoundEnabled,
                                        previewProgress = trailerPreviewProgress,
                                        onPlayItem = { item ->
                                            trailerPreviewCoordinator.clearFocus()
                                            scope.launch {
                                                val detail = browseRepository.getItemDetail(item.id) ?: return@launch
                                                val environment = environmentProvider.current() ?: return@launch
                                                playbackController.play(PlaybackRequest.from(item, detail), environment)
                                                playbackController.setPlaybackSpeed(settings.defaultPlaybackSpeed)
                                                playbackController.setStatsForNerdsEnabled(settings.statsForNerdsEnabled)
                                                push(TvRoute.Player)
                                            }
                                        },
                                        onItem = {
                                            trailerPreviewCoordinator.clearFocus()
                                            openJellyfinDetail(it)
                                        },
                                        onHomeLibrary = { libraryId, title -> push(TvRoute.Library(libraryId, title)) },
                                        onLibrary = { push(TvRoute.Library(it.id, it.name)) },
                                        onSeerrItem = ::openSeerr,
                                    )
                                is TvRoute.Library ->
                                    TvLibraryScreen(
                                        route = route,
                                        state = homeState,
                                        strings = strings,
                                        focusMemory = focusMemory,
                                        onSelectLibrary = { id ->
                                            val library = homeState.libraries.firstOrNull { it.id == id }
                                            if (route.libraryId == null) {
                                                push(TvRoute.Library(id, library?.name))
                                            } else {
                                                browseCoordinator.selectLibrary(id)
                                            }
                                        },
                                        onOpenItem = ::openJellyfinDetail,
                                        onOpenContainer = browseCoordinator::openContainer,
                                        onLoadMore = browseCoordinator::loadNextPage,
                                        onRetry = browseCoordinator::refreshSelectedLibrary,
                                    )
                                TvRoute.Search ->
                                    TvSearchScreen(
                                        sessionState = searchSession,
                                        jellyfinState = jellyfinSearchState,
                                        requestsState = requests,
                                        homeState = homeState,
                                        strings = strings,
                                        focusMemory = focusMemory,
                                        onQueryChanged = jellyfinSearchCoordinator::search,
                                        onSourceChanged = jellyfinSearchCoordinator::selectSource,
                                        onEnterEditMode = jellyfinSearchCoordinator::enterEditMode,
                                        onEnterBrowseMode = jellyfinSearchCoordinator::enterBrowseMode,
                                        onRetryJellyfin = jellyfinSearchCoordinator::retry,
                                        onRetrySeerr = requestsCoordinator::retrySearch,
                                        onJellyfinItem = ::openJellyfinDetail,
                                        onSeerrItem = ::openSeerr,
                                    )
                                TvRoute.Discover ->
                                    TvDiscoverScreen(
                                        recommendations,
                                        requests,
                                        strings,
                                        focusMemory,
                                        ::openSeerr,
                                        onConnectSeerr = ::openSettingsConnections,
                                        onRetry = recommendationsCoordinator::refreshAll,
                                    )
                                is TvRoute.Settings ->
                                    TvSettingsScreen(
                                        section = route.section,
                                        settings = settings,
                                        repository = settingsRepository,
                                        serverRepository = serverRepository,
                                        connectionCoordinator = koin.get<ServerConnectionCoordinator>(),
                                        quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                                        appVersion = appVersion,
                                        strings = strings,
                                        onOpenCategory = { category -> push(tvSettingsRoute(category)) },
                                        onServersChanged = {
                                            browseCoordinator.bootstrap(true)
                                            recommendationsCoordinator.refreshAll()
                                        },
                                    )
                                is TvRoute.JellyfinDetail ->
                                    TvJellyfinDetailScreen(
                                        route = route,
                                        initialItem = appStateHolder.detailSource(route.itemId),
                                        homeState = homeState,
                                        repository = browseRepository,
                                        browseCoordinator = browseCoordinator,
                                        environmentProvider = environmentProvider,
                                        playbackController = playbackController,
                                        trailerResolver = detailTrailerResolver,
                                        settings = settings,
                                        strings = strings,
                                        onOpenItem = ::openJellyfinDetail,
                                        onPlaybackStarted = { push(TvRoute.Player) },
                                    )
                                is TvRoute.SeerrDetail -> {
                                    val key = route.mediaType to route.tmdbId
                                    TvSeerrDetailScreen(
                                        route = route,
                                        detailState = details[key],
                                        requestsState = requests,
                                        requestsCoordinator = requestsCoordinator,
                                        strings = strings,
                                        onOpenItem = ::openSeerr,
                                    )
                                }
                                TvRoute.Player ->
                                    TvPlaybackScreen(
                                        controller = playbackController,
                                        engine = playerEngine,
                                        syncPlay = syncPlay,
                                        playbackState = playbackState,
                                        syncState = syncPlayState,
                                        segmentState = segmentState,
                                        continuationState = continuationState,
                                        seekBackSeconds = settings.seekBackSeconds,
                                        seekForwardSeconds = settings.seekForwardSeconds,
                                        onSkipSegment = segmentCoordinator::skip,
                                        onPlayNext = continuationCoordinator::playNext,
                                        strings = strings,
                                        stopPlayback = stopPlayback,
                                        onClose = {
                                            stopPlayback()
                                            appStateHolder.popRoute()
                                        },
                                    )
                            }
                        }
                    }
                },
            )
        }
        if (showRail) {
            if (appUiState.railExpanded) {
                Box(
                    Modifier
                        .width(TvLayoutTokens.ExpandedRailWidth + 56.dp)
                        .fillMaxHeight()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(0xFF080910), Color(0xF20E0F18), Color.Transparent),
                            ),
                        ),
                )
            }
            CompositionLocalProvider(
                LocalTvFocusContext provides TvFocusContext(focusCoordinator, TV_FOCUS_RAIL_ROUTE),
            ) {
                TvNavigationRail(
                    selected = currentRoute,
                    expanded = appUiState.railExpanded,
                    strings = strings,
                    onSelected = { route ->
                        selectTopLevel(route)
                        focusCoordinator.onUserMovement()
                        appStateHolder.closeRail()
                    },
                    onDismiss = {
                        focusCoordinator.onUserMovement()
                        appStateHolder.closeRail()
                    },
                )
            }
        }
        TvPlaybackCompletionPrompt(
            continuationState = continuationState,
            strings = strings,
            onPlayNow = continuationCoordinator::playNext,
            onCancel = continuationCoordinator::cancelAutoplay,
        )
    }
}

@Composable
private fun TvNavigationRail(
    selected: TvRoute,
    expanded: Boolean,
    strings: TvStrings,
    onSelected: (TvRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    val width = if (expanded) TvLayoutTokens.ExpandedRailWidth else TvLayoutTokens.CollapsedRailWidth
    val entries =
        listOf(
            Triple(TvRoute.Home as TvRoute, strings.home, Icons.Default.Home),
            Triple(TvRoute.Library() as TvRoute, strings.library, Icons.Default.VideoLibrary),
            Triple(TvRoute.Search as TvRoute, strings.search, Icons.Default.Search),
            Triple(TvRoute.Discover as TvRoute, strings.discover, Icons.Default.Explore),
            Triple(TvRoute.Settings() as TvRoute, strings.settings, Icons.Default.Settings),
        )
    TvRouteFocusMaterializer(
        ownerId = "navigation-rail",
        targetIds = entries.map { tvRailTargetId(it.first) }.toSet(),
        fallbackTargetIds = setOf(tvRailTargetId(TvRoute.Home)),
    ) { true }
    Column(
        modifier =
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(Color(0xE60B0C14))
                .padding(horizontal = 10.dp, vertical = TvLayoutTokens.SafeInsets.vertical),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        entries.forEachIndexed { index, (route, label, icon) ->
            val isSelected = selected.sameTopLevel(route)
            val targetId = tvRailTargetId(route)
            Row(
                Modifier
                    .width(width - 20.dp)
                    .onPreviewKeyEvent { event ->
                        if (
                            isSelected &&
                            event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                        ) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }.background(
                        if (isSelected) TvPurpleStrong.copy(alpha = 0.48f) else Color.Transparent,
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(18.dp),
                    ).semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = isSelected
                    }.tvFocusable(
                        onClick = { onSelected(route) },
                        enabled = tvNavigationRailItemsFocusable(expanded),
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(18.dp),
                        focusTargetId = targetId.takeIf { expanded },
                    ).padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(icon, null, tint = if (isSelected) TvPurple else TvTextMuted)
                if (expanded) Text(label, color = if (isSelected) TvText else TvTextMuted, fontSize = 18.sp)
            }
        }
    }
}

internal fun tvNavigationRailItemsFocusable(expanded: Boolean): Boolean = expanded

private fun TvRoute.sameTopLevel(other: TvRoute): Boolean =
    (this is TvRoute.Home && other is TvRoute.Home) ||
        (this is TvRoute.Library && other is TvRoute.Library) ||
        (this is TvRoute.Search && other is TvRoute.Search) ||
        (this is TvRoute.Discover && other is TvRoute.Discover) ||
        (this is TvRoute.Settings && other is TvRoute.Settings)

private fun TvRoute.SeerrDetail.toSearchItem(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        overview = overview,
        releaseYear = releaseYear,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = null,
        tvdbId = tvdbId,
        availability = JellyseerrMediaAvailability(null, null),
        requests = emptyList(),
    )
