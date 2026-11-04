@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyfin.mediaFeatures
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The services a Jellyfin detail page acts through. */
internal class TvJellyfinDetailServices(
    val repository: JellyfinBrowseRepository,
    val browseCoordinator: JellyfinBrowseCoordinator,
    val environmentProvider: JellyfinEnvironmentProvider,
    val playbackController: PlaybackController,
    val playbackLauncher: TvPlaybackLauncher,
    val trailerResolver: DetailTrailerResolver,
)

/** What the page tells the app to do: open another item or person, or show the player that just started. */
internal class TvJellyfinDetailNavigation(
    val onOpenItem: (JellyfinItem) -> Unit,
    val onOpenPerson: (JellyfinPerson) -> Unit,
    val onPlaybackStarted: () -> Unit,
)

@Composable
internal fun TvJellyfinDetailScreen(
    route: TvRoute.JellyfinDetail,
    initialItem: JellyfinItem?,
    homeState: JellyfinHomeState,
    repository: JellyfinBrowseRepository,
    browseCoordinator: JellyfinBrowseCoordinator,
    environmentProvider: JellyfinEnvironmentProvider,
    playbackController: PlaybackController,
    playbackLauncher: TvPlaybackLauncher,
    trailerResolver: DetailTrailerResolver,
    strings: TvStrings,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenPerson: (JellyfinPerson) -> Unit,
    onPlaybackStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(route.itemId) { TvJellyfinDetailState() }
    var loadRevision by remember(route.itemId) { mutableStateOf(0) }
    LaunchedEffect(route.itemId, initialItem, loadRevision) {
        state.load(route.itemId, initialItem, repository, trailerResolver, strings)
    }
    val item = state.item
    val detail = state.detail
    if (item == null || detail == null) {
        state.error?.let { message ->
            TvDetailLoadError(message, strings.retry, onRetry = { loadRevision += 1 }, modifier = modifier)
        } ?: TvLoading(strings.loading, modifier)
        return
    }
    TvJellyfinDetailContent(
        route = route,
        state = state,
        item = item,
        detail = detail,
        homeState = homeState,
        services =
            TvJellyfinDetailServices(
                repository,
                browseCoordinator,
                environmentProvider,
                playbackController,
                playbackLauncher,
                trailerResolver,
            ),
        navigation = TvJellyfinDetailNavigation(onOpenItem, onOpenPerson, onPlaybackStarted),
        strings = strings,
        modifier = modifier,
    )
}

/** Everything the page shows, derived from what is loaded and what the user picked. */
private class TvJellyfinDetailPresentation(
    val uiState: TvDetailUiState,
    val hero: TvDetailHeroModel,
    val actions: TvDetailHeroActionsState,
    val options: TvDetailPlaybackOptions?,
    val trackSummary: String?,
    val moreEntries: List<TvDetailMoreEntry>,
)

/** The inputs of [buildTvJellyfinDetailPresentation] that are not the item itself. */
private class TvJellyfinDetailInputs(
    val state: TvJellyfinDetailState,
    val homeState: JellyfinHomeState,
    val selection: TvDetailSelection,
    val chosenSeasonIndex: Int?,
    val endsAt: String?,
    val strings: TvStrings,
)

private fun tvJellyfinMetaLine(
    detail: JellyfinItemDetail,
    runTimeTicks: Long?,
    endsAt: String?,
    extra: String?,
    strings: TvStrings,
): String =
    tvDetailMetaLine(
        year = detail.productionYear?.toString(),
        runtimeMinutes = runTimeTicks?.div(TV_RUNTIME_TICKS_PER_MINUTE),
        officialRating = detail.officialRating,
        communityRating = detail.communityRating,
        endsAt = endsAt,
        strings = strings,
        extra = extra,
    )

private fun tvJellyfinActionsState(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    inputs: TvJellyfinDetailInputs,
    primaryLabel: String,
    showStartOver: Boolean,
) = TvDetailHeroActionsState(
    primaryLabel = primaryLabel,
    showStartOver = showStartOver,
    favorite = item.id in inputs.homeState.favorites || detail.isFavorite,
    played = detail.isPlayed,
    trailerError = inputs.state.trailerError,
    actionFailed = inputs.state.actionFailed,
)

private fun buildTvJellyfinDetailPresentation(
    routeKey: String,
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    inputs: TvJellyfinDetailInputs,
): TvJellyfinDetailPresentation {
    val strings = inputs.strings
    val episodes = inputs.state.episodes
    val isSeries = item.type.equals("Series", true)
    val isEpisode = item.type.equals("Episode", true)
    val seasonGroups = if (isSeries) buildTvSeasonGroups(episodes) else emptyList()
    // Until the user picks a season, follow the in-progress one as episodes arrive.
    val seasonIndex = inputs.chosenSeasonIndex?.takeIf { it in seasonGroups.indices } ?: defaultTvSeasonIndex(seasonGroups)
    val siblings = if (isEpisode) tvSeasonSiblings(item, episodes) else emptyList()
    val options = if (isSeries) null else tvPlaybackOptions(detail, inputs.selection, strings)
    val nextEpisode = if (isSeries) tvNextEpisodeOf(episodes) else null
    val hasResume = (item.positionTicks ?: 0L) > 0L || nextEpisode?.inProgress == true
    val runTimeTicks = detail.runTimeTicks ?: item.runTimeTicks
    val remaining = tvRemainingMinutes(runTimeTicks, item.positionTicks)
    val uiState =
        buildTvJellyfinDetailUiState(
            routeKey = routeKey,
            overview = detail.overview,
            tagline = detail.taglines.firstOrNull(),
            detailRows = tvJellyfinDetailRows(detail, strings),
            seasonGroups = seasonGroups,
            selectedSeasonIndex = seasonIndex,
            episodes = if (isEpisode) siblings else seasonGroups.getOrNull(seasonIndex)?.episodes ?: episodes,
            cast = detail.tvCastPeople(),
            similar = inputs.state.similar,
            moreFromSeason = (item.parentIndexNumber ?: 0).takeIf { isEpisode && siblings.isNotEmpty() },
            currentEpisodeId = item.id.takeIf { isEpisode },
        )
    val metaLine =
        tvJellyfinMetaLine(detail, runTimeTicks, inputs.endsAt, tvUnplayedEpisodesLabel(episodes, strings).takeIf { isSeries }, strings)
    return TvJellyfinDetailPresentation(
        uiState = uiState,
        hero = tvJellyfinHeroModel(item, detail, inputs.homeState, strings, metaLine),
        actions =
            tvJellyfinActionsState(
                item,
                detail,
                inputs,
                tvDetailPrimaryLabel(remaining, hasResume, nextEpisode, strings),
                tvDetailOffersStartOver(hasResume, nextEpisode),
            ),
        options = options,
        trackSummary = tvTrackLineSummary(options, strings),
        moreEntries = tvDetailMoreEntries(isEpisode, hasSeries = item.seriesId != null, versionCount = options?.versions?.size ?: 0),
    )
}

/** The actions and, when the item has tracks to pick, the track line below them; Down from the actions moves to it. */
@Composable
private fun TvJellyfinHeroActions(
    page: TvJellyfinDetailPresentation,
    callbacks: TvDetailHeroActionCallbacks,
    strings: TvStrings,
    primaryActionModifier: Modifier,
    actionRowModifier: Modifier,
    onOpenPicker: () -> Unit,
) {
    TvDetailHeroActions(
        state = page.actions,
        callbacks = callbacks,
        strings = strings,
        primaryActionModifier = primaryActionModifier,
        actionRowModifier = if (page.trackSummary != null) Modifier else actionRowModifier,
    )
    page.trackSummary?.let { TvDetailTrackLine(it, onClick = onOpenPicker, modifier = actionRowModifier) }
}

@Composable
private fun TvJellyfinDetailContent(
    route: TvRoute.JellyfinDetail,
    state: TvJellyfinDetailState,
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    homeState: JellyfinHomeState,
    services: TvJellyfinDetailServices,
    navigation: TvJellyfinDetailNavigation,
    strings: TvStrings,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope { tvCoroutineFailureHandler("the detail screen") }
    var chosenSeasonIndex by remember(route.itemId) { mutableStateOf<Int?>(null) }
    var selection by remember(route.itemId) { mutableStateOf(TvDetailSelection()) }
    var showPicker by remember(route.itemId) { mutableStateOf(false) }
    var showMore by remember(route.itemId) { mutableStateOf(false) }
    val isSeries = item.type.equals("Series", true)
    val endsAt =
        rememberTvEndsAtLabel(
            tvRemainingMinutes(detail.runTimeTicks ?: item.runTimeTicks, item.positionTicks).takeUnless { isSeries },
            strings,
        )
    val page =
        buildTvJellyfinDetailPresentation(
            route.itemId,
            item,
            detail,
            TvJellyfinDetailInputs(state, homeState, selection, chosenSeasonIndex, endsAt, strings),
        )
    val callbacks =
        tvJellyfinHeroCallbacks(
            scope = scope,
            state = state,
            item = item,
            detail = detail,
            choice = selection.toPlayChoice().takeUnless { isSeries },
            services = services,
            onPlaybackStarted = navigation.onPlaybackStarted,
            onMore = { showMore = true }.takeIf { page.moreEntries.isNotEmpty() },
        )
    TvDetailFocusLayout(
        uiState = page.uiState,
        heroContentDescription = detail.name,
        heroHeightDp = if (isSeries) TV_DETAIL_SERIES_HERO_HEIGHT_DP else TV_DETAIL_HERO_HEIGHT_DP,
        modifier = modifier,
        heroContent = { primaryActionModifier, actionRowModifier ->
            TvDetailHero(page.hero) {
                TvJellyfinHeroActions(page, callbacks, strings, primaryActionModifier, actionRowModifier) { showPicker = true }
            }
        },
    ) { bodyFocusModifier, sectionFocusModifiers ->
        tvJellyfinDetailSections(
            uiState = page.uiState,
            homeState = homeState,
            strings = strings,
            bodyFocusModifier = bodyFocusModifier,
            sectionFocusModifiers = sectionFocusModifiers,
            onOpenItem = navigation.onOpenItem,
            onOpenPerson = navigation.onOpenPerson,
            onSelectSeason = { chosenSeasonIndex = it },
        )
    }
    if (showMore) {
        TvDetailMoreDialog(
            entries = page.moreEntries,
            strings = strings,
            onEntry = { entry ->
                showMore = false
                when (entry) {
                    TvDetailMoreEntry.GO_TO_SERIES -> scope.launchOpenSeries(item, services.repository, navigation.onOpenItem)
                    TvDetailMoreEntry.VERSION -> showPicker = true
                }
            },
            onDismiss = { showMore = false },
        )
    }
    val options = page.options
    if (showPicker && options != null) {
        TvDetailTrackPickerDialog(
            options = options,
            strings = strings,
            onAudio = { selection = selection.withAudio(it) },
            onSubtitle = { selection = selection.withSubtitle(it) },
            onVersion = { selection = selection.withVersion(it) },
            onDismiss = { showPicker = false },
        )
    }
}

/** Opens the series of [episode]: the cached series item, else a stand-in built from the episode. */
private fun CoroutineScope.launchOpenSeries(
    episode: JellyfinItem,
    repository: JellyfinBrowseRepository,
    onOpenItem: (JellyfinItem) -> Unit,
) {
    launch {
        val cached = episode.seriesId?.let { id -> runSuspendCatching { repository.cachedItem(id) }.getOrNull() }
        (cached ?: tvSeriesStubOf(episode))?.let(onOpenItem)
    }
}

/** The hero of a Jellyfin item: backdrop and logo of the item (the series for an episode), meta line, genres, badges. */
internal fun tvJellyfinHeroModel(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    metaLine: String,
): TvDetailHeroModel {
    val isEpisode = item.type.equals("Episode", true)
    val artworkId = if (isEpisode) item.seriesId ?: item.id else item.id
    val backdropTag =
        if (isEpisode) {
            item.seriesBackdropImageTag ?: detail.parentBackdropImageTags.firstOrNull() ?: item.backdropImageTag
        } else {
            detail.backdropImageTags.firstOrNull() ?: item.backdropImageTag
        }
    val logoTag = item.seriesLogoImageTag ?: detail.logoImageTag ?: item.logoImageTag ?: item.parentLogoImageTag
    val titlePresentation = tvJellyfinHeroTitlePresentation(item.type, logoTag)
    return TvDetailHeroModel(
        title = detail.name,
        backdropUrl =
            jellyfinImageUrl(
                homeState.imageBaseUrl,
                homeState.imageAccessToken,
                artworkId,
                backdropTag ?: detail.primaryImageTag,
                if (backdropTag != null) "Backdrop" else "Primary",
                TvArtworkSize.HERO.maxWidth,
            ),
        logoUrl =
            logoTag?.takeIf { titlePresentation.useGraphicLogo }?.let { tag ->
                jellyfinImageUrl(homeState.imageBaseUrl, homeState.imageAccessToken, artworkId, tag, "Logo", TvArtworkSize.LOGO.maxWidth)
            },
        titleColor = titlePresentation.textColor,
        metaLine = metaLine,
        subtitle =
            if (isEpisode) {
                listOfNotNull(
                    item.seriesName,
                    tvEpisodeCode(item.parentIndexNumber, item.indexNumber),
                ).joinToString(TV_DETAIL_META_SEPARATOR)
            } else {
                detail.genres.take(4).joinToString(TV_DETAIL_META_SEPARATOR)
            },
        badges = detail.mediaFeatures().badgeLabels(strings),
        tagline = detail.taglines.firstOrNull()?.takeIf(String::isNotBlank),
        overview = detail.overview?.takeIf(String::isNotBlank),
    )
}

/** The button actions of a Jellyfin hero: playing, favorite, watched, trailer and the "More" menu. */
@Composable
private fun tvJellyfinHeroCallbacks(
    scope: CoroutineScope,
    state: TvJellyfinDetailState,
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    choice: TvPlayChoice?,
    services: TvJellyfinDetailServices,
    onPlaybackStarted: () -> Unit,
    onMore: (() -> Unit)?,
): TvDetailHeroActionCallbacks {
    val uriHandler = LocalUriHandler.current
    return TvDetailHeroActionCallbacks(
        onPlay = { services.playbackLauncher.play(item, detail, choice) },
        onStartOver = { services.playbackLauncher.play(item, detail, choice, fromStart = true) },
        onToggleFavorite = { scope.launch { services.browseCoordinator.toggleFavorite(item) } },
        onTogglePlayed = {
            scope.launch {
                // The server may be unreachable or reject the session; keep the screen and say so.
                runSuspendCatching { services.repository.setPlayedStatus(item.id, !detail.isPlayed) }
                    .onSuccess { updated ->
                        state.actionFailed = false
                        state.detail = updated
                    }.onFailure { failure ->
                        JellystackLog.w("Could not change the watched state", failure)
                        state.actionFailed = true
                    }
            }
        },
        onTrailer =
            state.trailer?.let { source ->
                {
                    when (source) {
                        is DetailTrailerSource.Local -> scope.launchLocalTrailer(state, source, services, onPlaybackStarted)
                        is DetailTrailerSource.YouTube ->
                            source.trailer.url?.let { trailerUrl ->
                                state.trailerError = runCatching { uriHandler.openUri(trailerUrl) }.isFailure
                            }
                    }
                }
            },
        onMore = onMore,
    )
}

private fun CoroutineScope.launchLocalTrailer(
    state: TvJellyfinDetailState,
    source: DetailTrailerSource.Local,
    services: TvJellyfinDetailServices,
    onPlaybackStarted: () -> Unit,
) {
    launch {
        runSuspendCatching {
            val environment = services.environmentProvider.current() ?: return@runSuspendCatching
            services.playbackController.play(PlaybackRequest.from(source.item, source.detail), environment)
            onPlaybackStarted()
        }.onFailure { failure ->
            JellystackLog.w("Could not start the local trailer", failure)
            state.trailerError = true
        }
    }
}
