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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(UnstableApi::class)
fun TvJellystackRoot(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
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
    appVersion: String,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    strings: TvStrings,
    stopPlayback: () -> Unit,
) {
    val koin = remember { JellystackDI.koin }
    val scope = rememberCoroutineScope()
    val browseRepository = remember { koin.get<JellyfinBrowseRepository>() }
    val environmentProvider = remember { koin.get<JellyfinEnvironmentProvider>() }
    val browseCoordinator =
        remember {
            JellyfinBrowseCoordinator(
                repository = browseRepository,
                scope = scope,
                favoritesStore = koin.get<JellyfinFavoritesStoreApi>(),
            )
        }
    val homeSectionsRepository = remember { koin.get<HomeSectionsRepository>() }
    val recommendationsCoordinator =
        remember {
            JellyseerrRecommendationsCoordinator(
                repository = koin.get<JellyseerrRepository>(),
                environmentProvider = koin.get<JellyseerrEnvironmentProvider>(),
                scope = scope,
            )
        }
    val requestsCoordinator =
        remember {
            JellyseerrRequestsCoordinator(
                repository = koin.get<JellyseerrRepository>(),
                environmentProvider = koin.get<JellyseerrEnvironmentProvider>(),
                scope = scope,
            )
        }
    val detailTrailerResolver =
        remember {
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
    val syncPlay =
        remember {
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
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val focusMemory = remember { TvFocusMemory() }
    val backStack = remember { mutableStateListOf<TvRoute>(TvRoute.Home) }
    var jellyfinSearchResults by remember { mutableStateOf(emptyList<dev.jellystack.core.jellyfin.JellyfinItem>()) }
    var jellyfinSearchJob by remember { mutableStateOf<Job?>(null) }
    val autoplayCoordinator =
        remember(playbackController, browseRepository, environmentProvider, settingsRepository) {
            TvAutoplayCoordinator(
                scope = scope,
                modeProvider = { settingsRepository.settings.value.autoplayNextMode },
                resolveNext = resolve@{ mediaId, seriesId ->
                    val resolvedSeriesId = seriesId ?: return@resolve null
                    val cached = browseRepository.episodesForSeries(resolvedSeriesId)
                    val episodes = if (cached.isEmpty()) browseRepository.refreshEpisodesForSeries(resolvedSeriesId) else cached
                    val next = selectNextTvEpisode(episodes, mediaId) ?: return@resolve null
                    val detail = browseRepository.getItemDetail(next.id) ?: return@resolve null
                    val environment = environmentProvider.current() ?: return@resolve null
                    TvAutoplayTarget(next.id, next.episodeTitle ?: next.name) {
                        playbackController.play(
                            PlaybackRequest.from(next, detail, startPolicy = PlaybackStartPolicy.RESTART),
                            environment,
                        )
                        playbackController.setPlaybackSpeed(settingsRepository.settings.value.defaultPlaybackSpeed)
                        playbackController.setStatsForNerdsEnabled(settingsRepository.settings.value.statsForNerdsEnabled)
                    }
                },
            )
        }
    val autoplayState by autoplayCoordinator.state.collectAsStateWithLifecycle()

    fun push(route: TvRoute) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun selectTopLevel(route: TvRoute) {
        backStack.clear()
        backStack.add(route)
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

    LaunchedEffect(settings.useServerHomeSections, settings.appLanguage, serverRepository.currentServers()) {
        homeSectionsRepository.refresh(
            enabledByUser = settings.useServerHomeSections,
            language = settings.appLanguage.languageTag,
        )
    }
    LaunchedEffect(playbackState) { autoplayCoordinator.onPlaybackState(playbackState) }
    LaunchedEffect(lifecycleState) {
        autoplayCoordinator.setForeground(lifecycleState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(Unit) {
        onDispose {
            browseCoordinator.shutdown()
            requestsCoordinator.shutdown()
            syncPlay.close()
            autoplayCoordinator.release()
        }
    }

    val currentRoute = backStack.last()
    val showRail =
        currentRoute is TvRoute.Home ||
            currentRoute is TvRoute.Library ||
            currentRoute is TvRoute.Search ||
            currentRoute is TvRoute.Discover ||
            currentRoute is TvRoute.Settings
    val railFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val railState = remember { TvNavigationRailState(initiallyVisible = true) }
    BackHandler(enabled = showRail && !railState.isVisible) {
        railState.onContentLeftEdge()
    }
    LaunchedEffect(showRail, railState.isVisible, currentRoute) {
        if (!showRail) return@LaunchedEffect
        if (railState.isVisible) railFocusRequester.requestFocus() else contentFocusRequester.requestFocus()
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalTvNavigationRailOpener provides railState::onContentLeftEdge,
            LocalTvScreenEntryFocusRequester provides contentFocusRequester,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                modifier = Modifier.fillMaxSize(),
                entryProvider = { route ->
                    NavEntry(route) {
                        when (route) {
                            TvRoute.Home ->
                                TvHomeScreen(
                                    state = homeState,
                                    homeSections = homeSections,
                                    strings = strings,
                                    autoCycle = settings.spotlightAutoCycle,
                                    intervalSeconds = settings.spotlightIntervalSeconds,
                                    focusMemory = focusMemory,
                                    onRefresh = { browseCoordinator.bootstrap(true) },
                                    onItem = { push(TvRoute.JellyfinDetail(it.id)) },
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
                                    onOpenItem = { push(TvRoute.JellyfinDetail(it.id)) },
                                    onOpenContainer = browseCoordinator::openContainer,
                                    onLoadMore = browseCoordinator::loadNextPage,
                                )
                            TvRoute.Search ->
                                TvSearchScreen(
                                    jellyfinResults = jellyfinSearchResults,
                                    requestsState = requests,
                                    homeState = homeState,
                                    strings = strings,
                                    focusMemory = focusMemory,
                                    onQueryChanged = { query ->
                                        requestsCoordinator.search(query)
                                        jellyfinSearchJob?.cancel()
                                        if (query.isBlank()) {
                                            jellyfinSearchResults = emptyList()
                                        } else {
                                            jellyfinSearchJob =
                                                scope.launch {
                                                    delay(300)
                                                    jellyfinSearchResults =
                                                        runCatching { browseRepository.searchItems(query.trim()) }
                                                            .getOrDefault(emptyList())
                                                }
                                        }
                                    },
                                    onJellyfinItem = { push(TvRoute.JellyfinDetail(it.id)) },
                                    onSeerrItem = ::openSeerr,
                                )
                            TvRoute.Discover ->
                                TvDiscoverScreen(
                                    recommendations,
                                    requests,
                                    strings,
                                    focusMemory,
                                    ::openSeerr,
                                    onConnectSeerr = { selectTopLevel(TvRoute.Settings()) },
                                )
                            is TvRoute.Settings ->
                                TvSettingsScreen(
                                    settings = settings,
                                    repository = settingsRepository,
                                    serverRepository = serverRepository,
                                    connectionCoordinator = koin.get<ServerConnectionCoordinator>(),
                                    quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                                    appVersion = appVersion,
                                    strings = strings,
                                    onServersChanged = {
                                        browseCoordinator.bootstrap(true)
                                        recommendationsCoordinator.refreshAll()
                                    },
                                )
                            is TvRoute.JellyfinDetail ->
                                TvJellyfinDetailScreen(
                                    route = route,
                                    homeState = homeState,
                                    repository = browseRepository,
                                    browseCoordinator = browseCoordinator,
                                    environmentProvider = environmentProvider,
                                    playbackController = playbackController,
                                    trailerResolver = detailTrailerResolver,
                                    settings = settings,
                                    strings = strings,
                                    onOpenItem = { push(TvRoute.JellyfinDetail(it.id)) },
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
                                    strings = strings,
                                    stopPlayback = stopPlayback,
                                    onClose = {
                                        stopPlayback()
                                        backStack.removeLastOrNull()
                                    },
                                )
                        }
                    }
                },
            )
        }
        if (showRail && railState.isVisible) {
            Box(
                Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color(0xFF080910), Color(0xF20E0F18), Color.Transparent),
                        ),
                    ),
            )
            TvNavigationRail(
                selected = currentRoute,
                strings = strings,
                onSelected = { route ->
                    selectTopLevel(route)
                    railState.onDestinationSelected()
                },
                selectedItemFocusRequester = railFocusRequester,
                onDismiss = railState::onDestinationSelected,
            )
        }
        (autoplayState as? TvAutoplayState.Countdown)?.let { countdown ->
            TvAutoplayPrompt(
                state = countdown,
                strings = strings,
                onPlayNow = autoplayCoordinator::playNow,
                onCancel = autoplayCoordinator::cancel,
            )
        }
    }
}

@Composable
private fun TvNavigationRail(
    selected: TvRoute,
    strings: TvStrings,
    onSelected: (TvRoute) -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focused) 214.dp else 82.dp, label = "tv-rail-width")
    val entries =
        listOf(
            Triple(TvRoute.Home as TvRoute, strings.home, Icons.Default.Home),
            Triple(TvRoute.Library() as TvRoute, strings.library, Icons.Default.VideoLibrary),
            Triple(TvRoute.Search as TvRoute, strings.search, Icons.Default.Search),
            Triple(TvRoute.Discover as TvRoute, strings.discover, Icons.Default.Explore),
            Triple(TvRoute.Settings() as TvRoute, strings.settings, Icons.Default.Settings),
        )
    Column(
        modifier =
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(Color(0xFF0E0F18))
                .onFocusChanged {
                    focused = it.hasFocus
                }.padding(horizontal = 12.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        entries.forEachIndexed { index, (route, label, icon) ->
            val isSelected = selected.sameTopLevel(route)
            Row(
                Modifier
                    .width(width - 24.dp)
                    .then(if (isSelected) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
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
                    }
                    .background(
                        if (isSelected) TvPurpleStrong.copy(alpha = 0.48f) else Color.Transparent,
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(18.dp),
                    ).tvFocusable(
                        onClick = { onSelected(route) },
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(18.dp),
                    ).padding(horizontal = 17.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(icon, label, tint = if (isSelected) TvPurple else TvTextMuted)
                if (focused) Text(label, color = if (isSelected) TvText else TvTextMuted, fontSize = 18.sp)
            }
        }
    }
}

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
