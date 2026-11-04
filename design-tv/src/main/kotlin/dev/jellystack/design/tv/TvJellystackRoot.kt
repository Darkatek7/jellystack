@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.AuthExpirySignal
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.core.jellyfin.isLiveTv
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.livetv.LiveTvRepository
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.MyListEntry
import dev.jellystack.core.profile.ProfileMyListRepository
import dev.jellystack.core.profile.ProfilePreferencesRepository
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackQueue
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator

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
internal fun TvAppBackHandler(
    dispatcher: TvAppBackDispatcher,
    onExitRequested: () -> Unit = {},
) {
    BackHandler(enabled = dispatcher.rootHandlerEnabled) {
        if (!dispatcher.dispatch()) onExitRequested()
    }
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
    coldLaunch: Boolean = true,
    voiceSearch: TvVoiceSearchPort = UnsupportedTvVoiceSearch,
    integrations: TvPlatformIntegrations = TvPlatformIntegrations(),
    onExitConfirmed: () -> Unit = {},
) {
    val koin = remember { JellystackDI.koin }
    val serverRepository = remember(koin) { koin.get<ServerRepository>() }
    val settingsRepository = remember(koin) { koin.get<AppSettingsRepository>() }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    JellystackTvTheme(
        motionPreference = settings.motionPreference,
        highContrastFocus = settings.highContrastFocus,
    ) {
        val strings = remember(settings.appLanguage) { TvStrings.current(settings.appLanguage) }
        TvProfileHost(
            handles =
                TvAppPlaybackHandles(
                    playbackController = playbackController,
                    playerEngine = playerEngine,
                    trailerPreviewController = trailerPreviewController,
                    trailerPreviewEngine = trailerPreviewEngine,
                    stopPlayback = stopPlayback,
                ),
            environment =
                TvAppEnvironment(
                    appVersion = appVersion,
                    strings = strings,
                    coldLaunch = coldLaunch,
                    platform = TvAppPlatform(voiceSearch, integrations, onExitConfirmed),
                ),
            settingsRepository = settingsRepository,
            serverRepository = serverRepository,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(UnstableApi::class)
internal fun TvAuthenticatedApp(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewPlaybackController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    strings: TvStrings,
    stopPlayback: () -> Unit,
    activeProfileName: String = strings.profile.profiles,
    activeProfileId: String? = null,
    activeProfileGeneration: Long = 0L,
    onOpenProfiles: () -> Unit = {},
    onAuthenticationExpired: () -> Unit = {},
    voiceSearch: TvVoiceSearchPort = UnsupportedTvVoiceSearch,
    integrations: TvPlatformIntegrations = TvPlatformIntegrations(),
    onExitConfirmed: () -> Unit = {},
) {
    val koin = remember { JellystackDI.koin }
    val rootScope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
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
    LaunchedEffect(activeProfileGeneration) { appStateHolder.resetForGeneration(activeProfileGeneration) }
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
    val seerrRepository = remember(accountGeneration) { koin.get<JellyseerrRepository>() }
    val seerrEnvironmentProvider = remember(accountGeneration) { koin.get<JellyseerrEnvironmentProvider>() }
    val sessionRepository =
        remember(accountGeneration) { koin.get<JellyfinSessionRepository>().isolatedSession() }
    val browseCoordinator =
        remember(accountGeneration) {
            JellyfinBrowseCoordinator(
                repository = browseRepository,
                scope = scope,
                favoritesStore =
                    koin
                        .get<JellyfinFavoritesStoreApi>()
                        .scoped(authenticatedEnvironmentIdentity.serverConnectionId),
            )
        }
    val homeSectionsRepository =
        remember(accountGeneration) { koin.get<HomeSectionsRepository>().isolatedSession() }
    val recommendationsCoordinator =
        remember(accountGeneration) {
            JellyseerrRecommendationsCoordinator(
                repository = seerrRepository,
                environmentProvider = seerrEnvironmentProvider,
                scope = scope,
            )
        }
    val requestsCoordinator =
        remember(accountGeneration) {
            JellyseerrRequestsCoordinator(
                repository = seerrRepository,
                environmentProvider = seerrEnvironmentProvider,
                scope = scope,
            )
        }
    val trailerWiring =
        rememberTvTrailerWiring(
            accountGeneration,
            browseRepository,
            environmentProvider,
            TvTrailerPreviewPlayback(trailerPreviewPlaybackController, trailerPreviewEngine),
            koin,
        )
    val detailTrailerResolver = trailerWiring.detailResolver
    val trailerPreviewCoordinator = trailerWiring.previewCoordinator
    val deviceSettings by settingsRepository.settings.collectAsStateWithLifecycle()
    val profilePreferencesRepository = remember(koin) { koin.get<ProfilePreferencesRepository>() }
    val preferenceProfileId = activeProfileId ?: "legacy-tv-profile"
    val profilePreferences by
        profilePreferencesRepository
            .preferences(preferenceProfileId)
            .collectAsStateWithLifecycle()
    val settings =
        if (activeProfileId == null) {
            deviceSettings
        } else {
            profilePreferences.applyTo(deviceSettings)
        }
    val currentSettings = {
        val currentDeviceSettings = settingsRepository.settings.value
        activeProfileId?.let { profilePreferencesRepository.preferences(it).value.applyTo(currentDeviceSettings) }
            ?: currentDeviceSettings
    }

    fun push(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.push(route)
    }

    val playbackQueue = remember(accountGeneration) { PlaybackQueue() }
    val playbackLauncher =
        remember(accountGeneration, activeProfileId, playbackController) {
            TvPlaybackLauncher(
                scope = scope,
                sources =
                    TvPlaybackSources(
                        loadDetail = browseRepository::getItemDetail,
                        loadEpisodes = browseRepository::latestEpisodesToPlay,
                        loadListEntries = browseRepository::listEntriesOf,
                        queue = playbackQueue,
                    ),
                starter = controllerPlaybackStarter(playbackController, environmentProvider),
                currentSettings = currentSettings,
                openPlayer = { push(TvRoute.Player) },
            )
        }
    val syncPlay =
        remember(accountGeneration) {
            SyncPlayCoordinator(
                environmentProvider = environmentProvider,
                playbackController = playbackController,
                // A queue started by another group member opens the player route like a local play action.
                playItem = { itemId, positionMs ->
                    browseRepository.cachedItem(itemId)?.let { playbackLauncher.playForGroup(it, positionMs) }
                },
                onAccessDenied = { sessionRepository.refresh() },
                scope = scope,
            )
        }
    val browseState by browseCoordinator.state.collectAsStateWithLifecycle()
    val liveTvRepository = remember(accountGeneration) { koin.get<LiveTvRepository>() }
    val liveTv = rememberTvLiveTvAccess(liveTvRepository, scope, browseState.libraries)
    val homeState = remember(browseState, liveTv) { liveTv.libraries(browseState) }
    val homeSections by homeSectionsRepository.state.collectAsStateWithLifecycle()
    val recommendations by recommendationsCoordinator.state.collectAsStateWithLifecycle()
    val requests by requestsCoordinator.state.collectAsStateWithLifecycle()
    val details by recommendationsCoordinator.details.collectAsStateWithLifecycle()
    val myListRepository = remember(accountGeneration) { koin.get<ProfileMyListRepository>() }
    val myListProfileId = requiredProfileId(activeProfileId)
    val savedMedia by
        myListRepository
            .observeSavedMedia(myListProfileId)
            .collectAsStateWithLifecycle(initialValue = emptyList())
    var favoriteItems by remember(accountGeneration) { mutableStateOf<Map<String, JellyfinItem>>(emptyMap()) }
    var myList by remember(accountGeneration) { mutableStateOf<List<MyListEntry>>(emptyList()) }
    LaunchedEffect(accountGeneration, homeState.favorites) {
        favoriteItems =
            runCatching { browseRepository.refreshFavoriteItems().associateBy(JellyfinItem::id) }
                .getOrElse { emptyMap() }
    }
    LaunchedEffect(accountGeneration, homeState.favorites, favoriteItems, savedMedia) {
        myList =
            myListRepository.reconcile(
                profileId = myListProfileId,
                jellyfinFavoriteIds = homeState.favorites,
                resolveFavorite = { itemId -> favoriteItems[itemId] ?: browseRepository.cachedItem(itemId) },
                resolveIdentity = browseRepository::cachedItem,
            )
    }
    // Held without reading it here: it changes with every position tick, only the player route reads the value.
    val playbackStates = playbackController.state
    val playbackStateHolder = playbackStates.collectAsStateWithLifecycle()
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
    TvAuthExpiryEffect(
        signal = remember(koin) { koin.get<AuthExpirySignal>() },
        environmentProvider = environmentProvider,
        sessionRepository = sessionRepository,
        playbackStates = playbackStates,
        syncPlayError = syncPlayState.error,
    )
    LaunchedEffect(sessionState) {
        if ((sessionState as? JellyfinSessionState.Error)?.authenticationExpired == true) {
            onAuthenticationExpired()
        }
    }
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
    val searchCoordinator =
        remember(accountGeneration, scope, browseRepository, seerrRepository, seerrEnvironmentProvider, voiceSearch) {
            TvSearchCoordinator(
                scope = scope,
                initialSession =
                    TvSearchSessionState(
                        query = savedSearchQuery,
                        source = TvSearchSource.entries.firstOrNull { it.name == savedSearchSource } ?: TvSearchSource.ALL,
                        mode = TvSearchMode.entries.firstOrNull { it.name == savedSearchMode } ?: TvSearchMode.EDIT,
                    ),
                voiceSearch = voiceSearch,
                sources =
                    TvSearchSources(
                        jellyfin = browseRepository::searchItems,
                        seerr = { query ->
                            seerrEnvironmentProvider
                                .current()
                                ?.let { environment ->
                                    seerrRepository.search(environment, query)
                                }.orEmpty()
                        },
                    ),
            )
        }
    val searchState by searchCoordinator.state.collectAsStateWithLifecycle()
    val searchSession = searchState.session
    LaunchedEffect(searchSession) {
        savedSearchQuery = searchSession.query
        savedSearchSource = searchSession.source.name
        savedSearchMode = searchSession.mode.name
    }
    DisposableEffect(searchCoordinator) {
        onDispose(searchCoordinator::shutdown)
    }
    val playbackCommandRouter =
        remember(playbackController, syncPlay) {
            TvPlaybackCommandRouter(
                isSyncPlayActive = { syncPlay.state.value.currentGroup != null },
                requestSyncSeek = syncPlay::requestSeek,
                requestLocalSeek = playbackController::seekTo,
                requestSyncNext = syncPlay::requestNext,
                pauseCommands =
                    TvPlaybackPauseCommands(
                        requestSyncPause = syncPlay::requestPause,
                        requestSyncUnpause = syncPlay::requestUnpause,
                        pauseLocal = playbackController::pause,
                        resumeLocal = playbackController::resume,
                    ),
            )
        }
    val playbackCoordinators =
        rememberTvJellyfinPlaybackCoordinators(
            identity = playbackIdentity,
            playbackStates = playbackStates,
            controller = playbackController,
            router = playbackCommandRouter,
            sources =
                TvPlaybackCoordinatorSources(
                    environmentProvider = environmentProvider,
                    browseRepository = browseRepository,
                    settings = currentSettings,
                    images = { TvPlayerImages(homeState.imageBaseUrl, homeState.imageAccessToken) },
                    queue = playbackQueue,
                ),
        )
    val segmentCoordinator = playbackCoordinators.segment
    val continuationCoordinator = playbackCoordinators.continuation
    val segmentState by segmentCoordinator.state.collectAsStateWithLifecycle()
    val continuationState by continuationCoordinator.state.collectAsStateWithLifecycle()
    val extrasState by playbackCoordinators.extras.state.collectAsStateWithLifecycle()
    val timelineSegments by segmentCoordinator.timelineSegments.collectAsStateWithLifecycle()
    val syncPlayAccess =
        (sessionState as? JellyfinSessionState.Ready)?.capabilities?.syncPlayAccess
            ?: JellyfinSyncPlayAccess.NONE

    TvPlayerRoutePopEffect(
        onPlayerRoute = appUiState.currentRoute == TvRoute.Player,
        playback = { playbackStateHolder.value },
        start = playbackLauncher.startState,
        onPop = appStateHolder::popPlayerRoute,
    )

    fun openLibrary(
        libraryId: String,
        title: String?,
    ) {
        if (homeState.libraries.firstOrNull { it.id == libraryId }?.isLiveTv() == true) {
            push(TvRoute.LiveTv)
            return
        }
        val myListItems = myList.mapNotNull { it.jellyfinItem }
        val mode = tvLibraryEntryMode(libraryId, homeState, homeSections, myListItems, strings.libraryBrowseLabels())
        push(TvRoute.Library(libraryId, title, mode))
    }

    fun openJellyfinDetail(item: JellyfinItem) {
        // Collections and playlists list their entries; a detail page has nothing to show for them.
        val listRoute = item.itemListRoute()
        if (listRoute != null) {
            push(listRoute)
            return
        }
        appStateHolder.rememberDetailSource(item)
        push(TvRoute.JellyfinDetail(item.id))
    }

    fun openPerson(person: JellyfinPerson) {
        push(TvRoute.Person(person.id, person.name))
    }

    fun selectTopLevel(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.selectTopLevel(route)
    }

    fun openSettingsConnections() {
        push(tvConnectionsSettingsRoute())
    }

    // The Seerr detail route loads its own detail, so My List entries and a restored back stack get one too.
    fun openSeerr(item: JellyseerrSearchItem) {
        push(item.toTvRoute())
    }

    fun openSeerr(route: TvRoute.SeerrDetail) {
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
    TvTrailerPreviewSettingsEffects(trailerPreviewCoordinator, settings, playbackStates, accountGeneration, serverRepository)
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            appStateHolder.onForegrounded()
        } else {
            appStateHolder.onBackgrounded()
            trailerPreviewCoordinator.onBackgrounded()
        }
    }
    DisposableEffect(accountGeneration) {
        onDispose {
            browseCoordinator.shutdown()
            homeSectionsRepository.close()
            sessionRepository.close()
            recommendationsCoordinator.shutdown()
            requestsCoordinator.shutdown()
            syncPlay.close()
            trailerPreviewCoordinator.release()
        }
    }

    TvWatchNextSyncEffect(
        publisher = integrations.watchNext,
        enabled = deviceSettings.watchNextEnabled,
        account =
            TvWatchNextAccount(
                serverKey = authenticatedEnvironmentIdentity.serverConnectionId,
                principalId = authenticatedEnvironmentIdentity.principalId,
                profileId = activeProfileId,
            ),
        home = homeState,
    )
    TvDeepLinkOpenEffect(integrations.deepLinks, authenticatedEnvironmentIdentity.serverConnectionId) { itemId ->
        if (appStateHolder.state.currentRoute == TvRoute.Player) stopPlayback()
        selectTopLevel(TvRoute.Home)
        push(TvRoute.JellyfinDetail(itemId))
    }

    val itemLists = remember(accountGeneration) { TvItemListCoordinator(scope, browseRepository.itemListLoader()) }
    val itemListStates by itemLists.states.collectAsStateWithLifecycle()
    val currentRoute = appUiState.currentRoute
    val jellyfinServerKey = serverRepository.activeServer(ServerType.JELLYFIN)?.id
    // Progress and watched marks from other devices show up while Home stays open.
    TvLiveRefreshEffect(active = currentRoute is TvRoute.Home) {
        browseCoordinator.refreshHomeFeeds()
        homeSectionsRepository.refresh(
            enabledByUser = settings.useServerHomeSections,
            language = settings.appLanguage.languageTag,
            silent = true,
        )
    }

    fun focusCinematicTrailer(
        item: JellyfinItem,
        presentationId: String,
    ) {
        if (!currentRoute.allowsTrailerPreview() || jellyfinServerKey == null) return
        trailerPreviewCoordinator.focus(
            TvTrailerPreviewRequest(
                owner = TvTrailerPreviewOwner.CARD,
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
    val showRail =
        currentRoute is TvRoute.Home ||
            currentRoute is TvRoute.Library ||
            currentRoute is TvRoute.Search ||
            currentRoute is TvRoute.Discover ||
            currentRoute is TvRoute.Settings ||
            currentRoute is TvRoute.LiveTv ||
            currentRoute.itemListSource() != null
    val focusCoordinator =
        remember(appUiState.activeProfileGeneration) {
            TvFocusCoordinator<FocusRequester>(
                awaitFocusFrame = { withFrameNanos { } },
            )
        }
    DisposableEffect(appStateHolder, focusCoordinator) {
        appStateHolder.onRouteForgotten = focusCoordinator::forgetRemembered
        onDispose { appStateHolder.onRouteForgotten = {} }
    }
    val currentFocusRouteKey =
        currentRoute.focusRouteKey(
            if (currentRoute is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
        )
    val focusContentAuthoritativelyLoaded =
        tvFocusContentAuthoritativelyLoaded(
            currentRoute,
            TvFocusLoadInputs(homeState, homeSections, searchState, recommendations, itemListStates),
        )
    val semanticRestorationSession =
        remember(focusCoordinator, currentFocusRouteKey) {
            TvSemanticFocusRestorationSession(
                snapshot = focusMemory.restore(currentFocusRouteKey),
                interactionRevision = focusCoordinator.currentInteractionRevision,
            )
        }
    val railRestorationSession =
        remember(focusCoordinator, appUiState.railExpanded, currentRoute) {
            TvSemanticFocusRestorationSession(
                snapshot = null,
                interactionRevision = focusCoordinator.currentInteractionRevision,
            )
        }
    val homeBackState = remember { TvHomeBackState() }
    val trailerBackState = remember(trailerPreviewCoordinator) { trailerPreviewCoordinator.backState() }
    val backDispatcher =
        TvAppBackDispatcher(
            holder = appStateHolder,
            library =
                TvLibraryBackAccess(
                    pathDepth = { homeState.browsePath.size },
                    selectedLibraryId = { homeState.selectedLibraryId },
                    popPath = browseCoordinator::navigateUp,
                ),
            cancelFocusRestoration = focusCoordinator::onUserMovement,
            homeBack = homeBackState,
            trailerBack = trailerBackState,
        )
    var showExitConfirmation by rememberSaveable(activeProfileGeneration) { mutableStateOf(false) }
    TvAppBackHandler(backDispatcher) { showExitConfirmation = true }
    TvRootFocusRestoration(
        focusCoordinator = focusCoordinator,
        appStateHolder = appStateHolder,
        focus =
            TvRootFocusState(
                currentRoute = currentRoute,
                routeKey = currentFocusRouteKey,
                showRail = showRail,
                railExpanded = appUiState.railExpanded,
                isForeground = appUiState.isForeground,
                contentAuthoritativelyLoaded = focusContentAuthoritativelyLoaded,
                queryControlsMayLead =
                    focusContentAuthoritativelyLoaded &&
                        tvLibraryQueryControlsMayLead(currentRoute, homeState),
            ),
        sessions = TvRootFocusSessions(semanticRestorationSession, railRestorationSession),
    )
    LaunchedEffect(appUiState.railExpanded, currentRoute) {
        if (appUiState.railExpanded || !currentRoute.allowsTrailerPreview()) trailerPreviewCoordinator.clearFocus()
    }

    // Built where a route's content composes, not here: the states it reads (home, search, trailer progress, ...)
    // then recompose only that route's content instead of the whole app shell, as they did before the routes moved
    // into their own files.
    fun routeContext(): TvRouteContext =
        TvRouteContext(
            ui =
                TvRouteUi(
                    strings = strings,
                    focusMemory = focusMemory,
                    settings = settings,
                    appVersion = appVersion,
                    profile =
                        TvRouteProfile(
                            activeProfileId = activeProfileId,
                            myListProfileId = myListProfileId,
                            watchNextAvailable = integrations.watchNext.supported,
                        ),
                ),
            nav =
                TvRouteNavigation(
                    push = ::push,
                    openLibrary = ::openLibrary,
                    openJellyfinDetail = ::openJellyfinDetail,
                    openPerson = ::openPerson,
                    openSeerr = ::openSeerr,
                ),
            data =
                TvRouteData(
                    browse =
                        TvRouteBrowseData(
                            homeState = homeState,
                            homeSections = homeSections,
                            myList = myList,
                            savedMedia = savedMedia,
                            lists = TvRouteListData(itemListStates, liveTv),
                        ),
                    seerr = TvRouteSeerrData(recommendations, requests, details, searchState),
                    trailer =
                        TvRouteTrailer(
                            state = trailerPreviewState,
                            engine = trailerPreviewEngine,
                            progress = trailerPreviewProgress,
                            coordinator = trailerPreviewCoordinator,
                            hooks =
                                TvRouteTrailerHooks(
                                    serverKey = jellyfinServerKey,
                                    currentRoute = currentRoute,
                                    focusCinematicTrailer = ::focusCinematicTrailer,
                                    detailResolver = detailTrailerResolver,
                                ),
                        ),
                    player =
                        TvRoutePlayerState(
                            stateHolder = playbackStateHolder,
                            syncPlayState = syncPlayState,
                            continuationState = continuationState,
                            extrasState = extrasState,
                            segments = TvRouteSegments(segmentState, timelineSegments),
                        ),
                    playback =
                        TvRoutePlayback(
                            controller = playbackController,
                            launcher = playbackLauncher,
                            syncPlay = syncPlay,
                            commandRouter = playbackCommandRouter,
                            extras =
                                TvRoutePlaybackExtras(
                                    coordinators = playbackCoordinators,
                                    queue = playbackQueue,
                                    stopPlayback = stopPlayback,
                                    engine = playerEngine,
                                ),
                        ),
                ),
            coordinators =
                TvRouteCoordinators(
                    browse = browseCoordinator,
                    recommendations = recommendationsCoordinator,
                    requests = requestsCoordinator,
                    search = searchCoordinator,
                    itemLists = itemLists,
                ),
            services =
                TvRouteServices(
                    repositories =
                        TvRouteRepositories(
                            browse = browseRepository,
                            myList = myListRepository,
                            settings = settingsRepository,
                            server = serverRepository,
                            profiles = TvRouteProfileRepositories(profilePreferencesRepository, profileRepository),
                        ),
                    scope = scope,
                    koin = koin,
                    appStateHolder = appStateHolder,
                    environmentProvider = environmentProvider,
                ),
        )
    Box(
        Modifier
            .fillMaxSize()
            .tvMenuKeyOpensProfilePicker(currentRoute, dialogOpen = showExitConfirmation, onOpenProfilePicker = onOpenProfiles)
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
            val reducedMotion = LocalTvFocusAppearance.current.reducedMotion
            val screenTransition = remember(reducedMotion) { tvScreenTransition<TvRoute>(reducedMotion) }
            NavDisplay(
                backStack = appUiState.backStack,
                onBack = { backDispatcher.dispatch() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = screenTransition,
                popTransitionSpec = screenTransition,
                entryProvider =
                    rememberLatestNavEntryProvider { route ->
                        NavEntry(route) {
                            val entryRouteKey =
                                route.focusRouteKey(
                                    if (route is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
                                )
                            TvRouteFocusScope(focusCoordinator, entryRouteKey, focusMemory) {
                                TvRouteContent(route, entryRouteKey, homeBackState, routeContext())
                            }
                        }
                    },
            )
        }
        if (showRail) {
            CompositionLocalProvider(
                LocalTvFocusContext provides TvFocusContext(focusCoordinator, TV_FOCUS_RAIL_ROUTE),
            ) {
                TvNavigationRail(
                    selected = currentRoute,
                    expanded = appUiState.railExpanded,
                    strings = strings,
                    profileName = activeProfileName,
                    callbacks =
                        TvNavigationRailCallbacks(
                            onProfile = onOpenProfiles,
                            onSelected = { route ->
                                selectTopLevel(route)
                                focusCoordinator.onUserMovement()
                                appStateHolder.closeRail()
                            },
                            onDismiss = {
                                focusCoordinator.onUserMovement()
                                appStateHolder.closeRail()
                            },
                        ),
                )
            }
        }
        playbackLauncher.pendingAsk?.let { ask ->
            TvResumeAskDialog(positionLabel = ask.positionLabel, strings = strings, onAnswer = playbackLauncher::answerAsk)
        }
        if (showExitConfirmation) {
            TvExitConfirmationDialog(
                strings = strings,
                onConfirm = {
                    showExitConfirmation = false
                    onExitConfirmed()
                },
                onDismiss = { showExitConfirmation = false },
            )
        }
    }
}

internal fun TvRoute.allowsTrailerPreview(): Boolean = this is TvRoute.Home || this is TvRoute.Library || this is TvRoute.Search
