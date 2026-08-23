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

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.DetailTrailerContext
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrRequestVariant
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.formatPlaybackTime
import kotlinx.coroutines.launch

internal data class TvJellyfinDetailBase(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail,
)

/** Pending "resume or restart?" decision captured from the primary play action. */
internal data class ResumeAskRequest(
    val positionLabel: String,
)

private const val TV_TICKS_PER_MILLISECOND = 10_000L

/** Formats a Jellyfin resume position (100-ns ticks) for the resume prompt. */
internal fun tvResumePositionLabel(positionTicks: Long?): String =
    formatPlaybackTime((positionTicks ?: 0L).coerceAtLeast(0L) / TV_TICKS_PER_MILLISECOND)

internal data class TvJellyfinHeroTitlePresentation(
    val useGraphicLogo: Boolean,
    val textColor: Color,
)

/** Typed detail-load failures so the UI can show localized copy instead of raw exception text. */
internal enum class TvDetailLoadErrorKind { ITEM_UNAVAILABLE, DETAILS_UNAVAILABLE }

internal class TvDetailLoadException(
    val kind: TvDetailLoadErrorKind,
) : Exception(kind.name)

internal fun tvDetailErrorMessage(
    error: Throwable,
    strings: TvStrings,
): String =
    when ((error as? TvDetailLoadException)?.kind) {
        TvDetailLoadErrorKind.ITEM_UNAVAILABLE -> strings.detailUnavailable
        TvDetailLoadErrorKind.DETAILS_UNAVAILABLE -> strings.detailLoadFailed
        null -> strings.detailLoadFailed
    }

internal fun tvJellyfinHeroTitlePresentation(
    itemType: String,
    logoTag: String?,
): TvJellyfinHeroTitlePresentation =
    TvJellyfinHeroTitlePresentation(
        useGraphicLogo = !itemType.equals("Episode", ignoreCase = true) && logoTag != null,
        textColor = TvText,
    )

internal fun tvVisibleOfficialRating(rating: String?): String? {
    val trimmed = rating?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val numericRating = trimmed.toDoubleOrNull()
    return trimmed.takeIf { numericRating == null || numericRating > 0.0 }
}

internal data class TvSeasonGroup(
    val seasonNumber: Int?,
    val episodes: List<JellyfinItem>,
)

internal fun buildTvSeasonGroups(episodes: List<JellyfinItem>): List<TvSeasonGroup> =
    episodes
        .groupBy { episode -> episode.parentIndexNumber?.takeIf { it > 0 } }
        .map { (seasonNumber, episodesInSeason) ->
            TvSeasonGroup(
                seasonNumber = seasonNumber,
                episodes =
                    episodesInSeason.sortedWith(
                        compareBy<JellyfinItem> { it.indexNumber ?: Int.MAX_VALUE }.thenBy { it.id },
                    ),
            )
        }.sortedWith(
            compareBy<TvSeasonGroup> { group -> group.seasonNumber ?: Int.MAX_VALUE },
        )

/** Prefers the season that holds an in-progress or next-up episode, else the first season. */
internal fun defaultTvSeasonIndex(
    groups: List<TvSeasonGroup>,
    fallbackEpisodeId: String? = null,
): Int {
    val byFallback =
        fallbackEpisodeId
            ?.let { targetId -> groups.indexOfFirst { group -> group.episodes.any { it.id == targetId } } }
            ?.takeIf { it >= 0 }
    if (byFallback != null) return byFallback
    val hasProgress: (JellyfinItem) -> Boolean = { (it.positionTicks ?: 0L) > 0L || it.playedPercentage != null }
    return groups.indexOfFirst { group -> group.episodes.any(hasProgress) }.takeIf { it >= 0 } ?: 0
}

internal suspend fun loadTvJellyfinDetailBase(
    itemId: String,
    initialItem: JellyfinItem?,
    cachedItem: suspend (String) -> JellyfinItem?,
    loadDetail: suspend (String) -> JellyfinItemDetail?,
): TvJellyfinDetailBase {
    val item = initialItem ?: cachedItem(itemId) ?: throw TvDetailLoadException(TvDetailLoadErrorKind.ITEM_UNAVAILABLE)
    val detail = loadDetail(itemId) ?: throw TvDetailLoadException(TvDetailLoadErrorKind.DETAILS_UNAVAILABLE)
    return TvJellyfinDetailBase(item, detail)
}

internal data class TvDetailSectionFocusModifiers(
    val navigationModifier: Modifier,
    val itemModifiers: Map<String, Modifier>,
    val itemFocusRequesters: Map<String, FocusRequester>,
) {
    fun itemModifier(itemId: String): Modifier = itemModifiers[itemId] ?: Modifier

    fun itemFocusRequester(itemId: String): FocusRequester? = itemFocusRequesters[itemId]
}

private data class TvPendingFocusRecovery(
    val source: TvFocusAnchor,
    val target: TvFocusAnchor,
)

private fun TvDetailSection.participatesInSectionFocus(): Boolean =
    this is TvDetailSection.Episodes || this is TvDetailSection.Cast || this is TvDetailSection.Similar

@Composable
internal fun TvDetailFocusLayout(
    uiState: TvDetailUiState,
    heroContentDescription: String,
    hasPrimaryAction: Boolean = true,
    modifier: Modifier = Modifier,
    heroContent: @Composable BoxScope.(primaryActionModifier: Modifier, actionRowModifier: Modifier) -> Unit,
    content: LazyListScope.(bodyFocusModifier: Modifier, sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val routeKey = uiState.routeKey
    val heroFocusRequester = remember(routeKey) { FocusRequester() }
    val primaryActionFocusRequester = remember(routeKey) { FocusRequester() }
    val bodyFocusRequester = remember(routeKey) { FocusRequester() }
    val itemFocusRequesters = remember(routeKey) { mutableMapOf<String, FocusRequester>() }
    val focusSections = uiState.sections.filter { it.participatesInSectionFocus() && it.itemIds.isNotEmpty() }
    val bodySectionIndex = uiState.sections.indexOfFirst { it is TvDetailSection.Overview }.coerceAtLeast(0)
    val bodyLazyItemIndex = bodySectionIndex + 1
    var lastConfirmedItemAnchor by remember(routeKey) { mutableStateOf<TvFocusAnchor?>(null) }
    var semanticItemIsActive by remember(routeKey) { mutableStateOf(false) }
    var pendingFocusRecovery by remember(routeKey) { mutableStateOf<TvPendingFocusRecovery?>(null) }
    var activeSectionIndex by remember(routeKey) { mutableStateOf(0) }
    var activeItemIndex by remember(routeKey) { mutableStateOf(0) }

    fun requester(sectionId: String, itemId: String): FocusRequester =
        itemFocusRequesters.getOrPut("$sectionId\u0000$itemId") { FocusRequester() }

    fun focusHero() {
        pendingFocusRecovery = null
        scope.launch {
            listState.scrollToItem(0)
            heroFocusRequester.requestFocus()
        }
    }

    fun focusPrimaryAction() {
        pendingFocusRecovery = null
        scope.launch {
            listState.scrollToItem(0)
            withFrameNanos { }
            primaryActionFocusRequester.requestFocus()
        }
    }

    fun focusBodyFromHero() {
        pendingFocusRecovery = null
        scope.launch {
            listState.scrollToItem(bodyLazyItemIndex)
            withFrameNanos { }
            bodyFocusRequester.requestFocus()
        }
    }

    fun focusBody() {
        pendingFocusRecovery = null
        scope.launch {
            listState.scrollToItem(bodyLazyItemIndex)
            withFrameNanos { }
            bodyFocusRequester.requestFocus()
        }
    }

    fun focusSection(
        sectionId: String,
        preferredItemId: String? = null,
    ) {
        pendingFocusRecovery = null
        val section = uiState.section(sectionId) ?: return focusBody()
        val itemId = preferredItemId?.takeIf(section.itemIds::contains) ?: section.itemIds.firstOrNull() ?: return focusBody()
        val lazyItemIndex = uiState.sections.indexOfFirst { it.id == sectionId }.takeIf { it >= 0 }?.plus(1) ?: return focusBody()
        val focusRequester = requester(sectionId, itemId)
        scope.launch {
            listState.scrollToItem(lazyItemIndex)
            withFrameNanos { }
            val focused = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
            if (!focused) {
                listState.scrollToItem(bodyLazyItemIndex)
                withFrameNanos { }
                bodyFocusRequester.requestFocus()
            }
        }
    }

    fun recoveryTarget(source: TvFocusAnchor): TvFocusAnchor {
        val resolvedSource = uiState.resolve(source)
        if (resolvedSource != null) return source
        val sameSection = uiState.section(source.sectionId.orEmpty())?.takeIf { it.participatesInSectionFocus() }
        val fallbackSection =
            sameSection ?: focusSections.getOrNull(activeSectionIndex.coerceIn(0, focusSections.lastIndex.coerceAtLeast(0)))
        val fallbackItem =
            fallbackSection?.itemIds?.getOrNull(activeItemIndex.coerceIn(0, fallbackSection.itemIds.lastIndex.coerceAtLeast(0)))
        return if (fallbackSection != null && fallbackItem != null) {
            TvFocusAnchor(fallbackSection.id, fallbackItem, TvFocusDestination.SECTION_ITEM)
        } else {
            TvFocusAnchor("overview", null, TvFocusDestination.BODY)
        }
    }

    val recoverySource =
        lastConfirmedItemAnchor?.takeIf { source ->
            semanticItemIsActive && uiState.resolve(source) == null
        }

    LaunchedEffect(routeKey) {
        listState.scrollToItem(0)
        heroFocusRequester.requestFocus()
    }
    LaunchedEffect(uiState.sections, recoverySource) {
        val source = recoverySource ?: return@LaunchedEffect
        pendingFocusRecovery = TvPendingFocusRecovery(source, recoveryTarget(source))
    }
    LaunchedEffect(pendingFocusRecovery, uiState.sections) {
        val pending = pendingFocusRecovery ?: return@LaunchedEffect
        val currentTarget = recoveryTarget(pending.source)
        if (currentTarget != pending.target) {
            pendingFocusRecovery = TvPendingFocusRecovery(pending.source, currentTarget)
            return@LaunchedEffect
        }
        if (currentTarget.destination == TvFocusDestination.SECTION_ITEM) {
            val sectionId = currentTarget.sectionId ?: return@LaunchedEffect
            val itemId = currentTarget.itemId ?: return@LaunchedEffect
            val lazyItemIndex = uiState.sections.indexOfFirst { it.id == sectionId }.takeIf { it >= 0 }?.plus(1)
            if (lazyItemIndex == null) {
                pendingFocusRecovery = TvPendingFocusRecovery(
                    pending.source,
                    TvFocusAnchor("overview", null, TvFocusDestination.BODY),
                )
                return@LaunchedEffect
            }
            listState.scrollToItem(lazyItemIndex)
            withFrameNanos { }
            runCatching { requester(sectionId, itemId).requestFocus() }
        } else {
            listState.scrollToItem(bodyLazyItemIndex)
            withFrameNanos { }
            bodyFocusRequester.requestFocus()
        }
    }

    val primaryActionModifier =
        Modifier
            .focusRequester(primaryActionFocusRequester)
            .testTag("tv-detail-primary-action")
            .onFocusChanged { focusState ->
                if (focusState.isFocused && pendingFocusRecovery == null && recoverySource == null) {
                    semanticItemIsActive = false
                }
            }
            .onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    focusHero()
                    true
                } else {
                    false
                }
            }

    val actionRowModifier =
        Modifier.onPreviewKeyEvent { event ->
            if (
                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
                focusBody()
                true
            } else {
                false
            }
        }
    val bodyFocusModifier =
        Modifier
            .focusRequester(bodyFocusRequester)
            .testTag("tv-detail-body-focus")
            .onPreviewKeyEvent { event ->
                val isInitialDownPress =
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0
                if (isInitialDownPress && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (hasPrimaryAction) {
                        focusPrimaryAction()
                    } else {
                        focusHero()
                    }
                    true
                } else if (focusSections.isNotEmpty() &&
                    isInitialDownPress &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    focusSection(focusSections.first().id)
                    true
                } else {
                    false
                }
            }.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    val pending = pendingFocusRecovery
                    if (pending?.target?.destination == TvFocusDestination.BODY) {
                        semanticItemIsActive = false
                        pendingFocusRecovery = null
                    } else if (pending == null && recoverySource == null) {
                        semanticItemIsActive = false
                    }
                }
            }.focusable()
    val sectionFocusModifiers =
        focusSections.mapIndexed { sectionIndex, section ->
            val focusRequesters = section.itemIds.associateWith { itemId -> requester(section.id, itemId) }
            val itemModifiers =
                section.itemIds.associateWith { itemId ->
                    Modifier
                        .testTag("tv-detail-section-${section.id}-item-$itemId")
                        .onFocusChanged { focusState ->
                            val itemAnchor = TvFocusAnchor(section.id, itemId, TvFocusDestination.SECTION_ITEM)
                            if (focusState.isFocused) {
                                activeSectionIndex = sectionIndex
                                activeItemIndex = section.itemIds.indexOf(itemId).coerceAtLeast(0)
                                lastConfirmedItemAnchor = itemAnchor
                                semanticItemIsActive = true
                                if (pendingFocusRecovery?.target == itemAnchor) pendingFocusRecovery = null
                            }
                        }
                }
            val navigationModifier =
                Modifier
                    .onPreviewKeyEvent { event ->
                        val isInitialPress =
                            event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.repeatCount == 0
                        if (!isInitialPress) {
                            false
                        } else {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (sectionIndex == 0) focusBody() else focusSection(focusSections[sectionIndex - 1].id)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (sectionIndex < focusSections.lastIndex) {
                                        focusSection(focusSections[sectionIndex + 1].id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                    }
            section.id to
                TvDetailSectionFocusModifiers(
                    navigationModifier = navigationModifier,
                    itemModifiers = itemModifiers,
                    itemFocusRequesters = focusRequesters,
                )
        }.toMap()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item("hero-$routeKey") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .focusRequester(heroFocusRequester)
                    .testTag("tv-detail-hero")
                    .semantics { contentDescription = heroContentDescription }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && pendingFocusRecovery == null && recoverySource == null) {
                            semanticItemIsActive = false
                        }
                    }
                    .onKeyEvent { event ->
                        if (
                            event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            if (hasPrimaryAction) {
                                primaryActionFocusRequester.requestFocus()
                            } else {
                                focusBodyFromHero()
                            }
                            true
                        } else {
                            false
                        }
                    }.tvFocusable(
                        onClick = {
                            if (hasPrimaryAction) {
                                primaryActionFocusRequester.requestFocus()
                            } else {
                                focusBodyFromHero()
                            }
                        },
                        shape = RoundedCornerShape(0.dp),
                        scale = 1f,
                        showFocusBorder = false,
                    ),
            ) {
                heroContent(primaryActionModifier, actionRowModifier)
            }
        }
        content(bodyFocusModifier, sectionFocusModifiers)
    }
}

@Composable
internal fun TvJellyfinDetailScreen(
    route: TvRoute.JellyfinDetail,
    initialItem: JellyfinItem?,
    homeState: JellyfinHomeState,
    repository: JellyfinBrowseRepository,
    browseCoordinator: JellyfinBrowseCoordinator,
    environmentProvider: JellyfinEnvironmentProvider,
    playbackController: PlaybackController,
    trailerResolver: DetailTrailerResolver,
    settings: AppSettings,
    strings: TvStrings,
    onOpenItem: (JellyfinItem) -> Unit,
    onPlaybackStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var item by remember(route.itemId) { mutableStateOf<JellyfinItem?>(null) }
    var detail by remember(route.itemId) { mutableStateOf<JellyfinItemDetail?>(null) }
    var episodes by remember(route.itemId) { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedSeasonIndex by remember(route.itemId) { mutableStateOf(0) }
    var similar by remember(route.itemId) { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var trailer by remember(route.itemId) { mutableStateOf<DetailTrailerSource?>(null) }
    var trailerError by remember(route.itemId) { mutableStateOf(false) }
    var error by remember(route.itemId) { mutableStateOf<String?>(null) }
    var loadRevision by remember(route.itemId) { mutableStateOf(0) }
    var resumeAskRequest by remember(route.itemId) { mutableStateOf<ResumeAskRequest?>(null) }
    val uriHandler = LocalUriHandler.current
    val seasonGroups = remember(episodes) { buildTvSeasonGroups(episodes) }
    val activeSeason =
        seasonGroups.getOrElse(selectedSeasonIndex) { seasonGroups.firstOrNull() }
    val visibleEpisodes = activeSeason?.episodes ?: episodes
    LaunchedEffect(seasonGroups) {
        if (selectedSeasonIndex !in seasonGroups.indices) selectedSeasonIndex = defaultTvSeasonIndex(seasonGroups)
    }
    LaunchedEffect(route.itemId, initialItem, loadRevision) {
        error = null
        runCatching {
            val loaded =
                loadTvJellyfinDetailBase(
                    itemId = route.itemId,
                    initialItem = initialItem,
                    cachedItem = repository::cachedItem,
                    loadDetail = repository::getItemDetail,
                )
            val loadedItem = loaded.item
            val loadedDetail = loaded.detail
            item = loadedItem
            detail = loadedDetail
            if (loadedItem.type.equals("Series", true)) episodes = repository.refreshEpisodesForSeries(route.itemId)
            selectedSeasonIndex = 0
            similar = repository.fetchSimilarItems(route.itemId, 12)
            trailer =
                trailerResolver.resolve(
                    DetailTrailerContext(
                        itemId = loadedItem.id,
                        isEpisode = loadedItem.type.equals("Episode", true),
                        isSeries = loadedItem.type.equals("Series", true),
                        seriesId = loadedItem.seriesId,
                        detail = loadedDetail,
                    ),
                )
        }.onFailure { currentError -> error = tvDetailErrorMessage(currentError, strings) }
    }
    val currentItem = item
    val currentDetail = detail
    if (currentItem == null || currentDetail == null) {
        error?.let { message ->
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(message, color = TvTextMuted)
                TvActionButton(strings.retry, onClick = { loadRevision += 1 }, primary = true)
            }
        } ?: TvLoading(strings.loading, modifier)
        return
    }
    val heroId = if (currentItem.type.equals("Episode", true)) currentItem.seriesId ?: currentItem.id else currentItem.id
    val backdropTag =
        if (currentItem.type.equals("Episode", true)) {
            currentItem.seriesBackdropImageTag ?: currentDetail.parentBackdropImageTags.firstOrNull() ?: currentItem.backdropImageTag
        } else {
            currentDetail.backdropImageTags.firstOrNull() ?: currentItem.backdropImageTag
        }
    val logoId = if (currentItem.type.equals("Episode", true)) currentItem.seriesId ?: currentItem.id else currentItem.id
    val logoTag = currentItem.seriesLogoImageTag ?: currentDetail.logoImageTag ?: currentItem.logoImageTag ?: currentItem.parentLogoImageTag
    val titlePresentation = tvJellyfinHeroTitlePresentation(currentItem.type, logoTag)
    val hasResumePosition = (currentItem.positionTicks ?: 0L) > 0L
    val facts =
        listOfNotNull(
            currentDetail.productionYear?.toString(),
            currentDetail.runTimeTicks?.let { "${it / 600_000_000L} min" },
            tvVisibleOfficialRating(currentDetail.officialRating),
            currentDetail.communityRating?.let { "★ %.1f".format(it) },
            currentDetail.mediaSources
                .firstOrNull()
                ?.streams
                ?.firstOrNull { it.width != null }
                ?.height
                ?.let { "${it}p" },
        )
    val uiState =
        buildTvJellyfinDetailUiState(
            routeKey = route.itemId,
            facts = facts,
            overview = currentDetail.overview,
            tagline = currentDetail.taglines.firstOrNull(),
            seasonGroups = seasonGroups,
            selectedSeasonIndex = selectedSeasonIndex,
            episodes = visibleEpisodes,
            cast = currentDetail.people,
            similar = similar,
        )

    fun startPlayback(startPolicy: PlaybackStartPolicy) {
        scope.launch {
            val environment = environmentProvider.current() ?: return@launch
            playbackController.play(
                PlaybackRequest.from(currentItem, currentDetail, startPolicy = startPolicy),
                environment,
            )
            playbackController.setPlaybackSpeed(settings.defaultPlaybackSpeed)
            playbackController.setStatsForNerdsEnabled(settings.statsForNerdsEnabled)
            onPlaybackStarted()
        }
    }
    TvDetailFocusLayout(
        uiState = uiState,
        heroContentDescription = currentDetail.name,
        modifier = modifier,
        heroContent = { primaryActionModifier, actionRowModifier ->
            AsyncImage(
                model =
                    jellyfinImageUrl(
                        homeState.imageBaseUrl,
                        homeState.imageAccessToken,
                        heroId,
                        backdropTag ?: currentDetail.primaryImageTag,
                        if (backdropTag != null) "Backdrop" else "Primary",
                        TvArtworkSize.HERO.maxWidth,
                    ),
                contentDescription = currentDetail.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(TvBackground.copy(0.96f), TvBackground.copy(0.68f), Color.Transparent)),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), TvBackground.copy(alpha = 0.12f), TvBackground)),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 108.dp, end = 42.dp, bottom = 38.dp).widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (titlePresentation.useGraphicLogo) {
                    AsyncImage(
                        model =
                            jellyfinImageUrl(
                                homeState.imageBaseUrl,
                                homeState.imageAccessToken,
                                logoId,
                                checkNotNull(logoTag),
                                "Logo",
                                TvArtworkSize.LOGO.maxWidth,
                            ),
                        contentDescription = currentDetail.name,
                        modifier = Modifier.widthIn(max = 380.dp).heightIn(max = 120.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        currentDetail.name,
                        color = titlePresentation.textColor,
                        fontSize = 46.sp,
                        lineHeight = 49.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                }
                if (currentItem.type.equals("Episode", true)) {
                    Text(
                        "${currentItem.seriesName.orEmpty()}  •  " +
                            "S${currentItem.parentIndexNumber ?: 0} E${currentItem.indexNumber ?: 0}",
                        color = TvTextMuted,
                        fontSize = 19.sp,
                    )
                } else {
                    Text(currentDetail.genres.take(4).joinToString("  •  "), color = TvTextMuted, fontSize = 19.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TV_DETAIL_ACTION_GAP_DP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvActionButton(
                        if (hasResumePosition) strings.continueLabel else strings.play,
                        primary = true,
                        leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
                        onClick = {
                            if (hasResumePosition && settings.resumeMode == ResumeMode.ASK) {
                                resumeAskRequest = ResumeAskRequest(tvResumePositionLabel(currentItem.positionTicks))
                            } else {
                                startPlayback(PlaybackStartPolicy.INHERIT)
                            }
                        },
                        modifier = primaryActionModifier.then(actionRowModifier).width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                    )
                    TvCompactActionButton(
                        label = strings.favorite,
                        onClick = { scope.launch { browseCoordinator.toggleFavorite(currentItem) } },
                        icon =
                            if (currentItem.id in homeState.favorites ||
                                currentDetail.isFavorite
                            ) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                        selected = currentItem.id in homeState.favorites || currentDetail.isFavorite,
                        modifier = actionRowModifier,
                    )
                    TvCompactActionButton(
                        label = strings.watched,
                        icon = Icons.Default.CheckCircle,
                        selected = currentDetail.isPlayed,
                        onClick = {
                            scope.launch {
                                detail = repository.setPlayedStatus(currentItem.id, !currentDetail.isPlayed)
                            }
                        },
                        modifier = actionRowModifier,
                    )
                    trailer?.let { source ->
                        TvCompactActionButton(
                            label = strings.trailer,
                            icon = Icons.Default.LocalMovies,
                            onClick = {
                                when (source) {
                                    is DetailTrailerSource.Local -> {
                                        scope.launch {
                                            val environment = environmentProvider.current() ?: return@launch
                                            playbackController.play(PlaybackRequest.from(source.item, source.detail), environment)
                                            onPlaybackStarted()
                                        }
                                    }
                                    is DetailTrailerSource.YouTube ->
                                        source.trailer.url?.let { trailerUrl ->
                                            trailerError = runCatching { uriHandler.openUri(trailerUrl) }.isFailure
                                        }
                                }
                            },
                            modifier = actionRowModifier,
                        )
                    }
                }
                if (trailerError) {
                    Text(strings.trailerOpenFailed, color = Color(0xFFFFA59E), fontSize = 16.sp)
                }
            }
        },
    ) { bodyFocusModifier, sectionFocusModifiers ->
        tvJellyfinDetailSections(
            uiState = uiState,
            homeState = homeState,
            strings = strings,
            bodyFocusModifier = bodyFocusModifier,
            sectionFocusModifiers = sectionFocusModifiers,
            onOpenItem = onOpenItem,
            onSelectSeason = { selectedSeasonIndex = it },
        )
    }
    resumeAskRequest?.let { request ->
        Dialog(onDismissRequest = { resumeAskRequest = null }) {
            Column(
                Modifier
                    .width(620.dp)
                    .background(TvSurfaceRaised, RoundedCornerShape(28.dp))
                    .padding(34.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(strings.resumeAskTitle, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvText)
                Text(
                    strings.continueFrom.format(request.positionLabel),
                    fontSize = 19.sp,
                    color = TvTextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvActionButton(
                        strings.continueLabel,
                        primary = true,
                        modifier = Modifier.width(230.dp),
                        onClick = {
                            resumeAskRequest = null
                            startPlayback(PlaybackStartPolicy.RESUME)
                        },
                    )
                    TvActionButton(
                        strings.restart,
                        modifier = Modifier.width(230.dp),
                        onClick = {
                            resumeAskRequest = null
                            startPlayback(PlaybackStartPolicy.RESTART)
                        },
                    )
                    TvActionButton(strings.cancel, onClick = { resumeAskRequest = null })
                }
            }
        }
    }
}

internal fun LazyListScope.tvJellyfinDetailSections(
    uiState: TvDetailUiState,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    bodyFocusModifier: Modifier,
    sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>,
    onOpenItem: (JellyfinItem) -> Unit,
    onSelectSeason: (Int) -> Unit,
) {
    uiState.sections.forEach { section ->
        when (section) {
            is TvDetailSection.Facts ->
                item(section.id) {
                    Row(Modifier.padding(start = 108.dp, end = 42.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        section.values.forEach { Text(it, color = TvTextMuted, fontSize = 18.sp) }
                    }
                }
            is TvDetailSection.Overview ->
                item(section.id) {
                    Column(
                        bodyFocusModifier.padding(start = 108.dp, end = 42.dp).fillMaxWidth(0.78f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSectionTitle(strings.overview)
                        section.tagline?.let { Text(it, color = TvPurple, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                        Text(section.text ?: strings.noOverview, color = TvText, fontSize = 20.sp, lineHeight = 29.sp)
                    }
                }
            is TvDetailSection.Seasons ->
                item(section.id) {
                    Column(Modifier.padding(start = 108.dp, end = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSectionTitle(strings.seasons)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            itemsIndexed(section.groups, key = { _, group -> "season-${group.seasonNumber ?: Int.MAX_VALUE}" }) { index, group ->
                                val label =
                                    group.seasonNumber
                                        ?.let { season -> strings.seasonNumber.format(season) }
                                        ?: strings.specials
                                TvActionButton(
                                    label,
                                    { onSelectSeason(index) },
                                    primary = index == section.selectedIndex.coerceIn(section.groups.indices),
                                    modifier = Modifier.widthIn(min = 150.dp),
                                )
                            }
                        }
                    }
                }
            is TvDetailSection.Episodes -> {
                val focusModifiers = sectionFocusModifiers.getValue(section.id)
                item(section.id) {
                    TvDetailItemRow(strings.episodes, section.items, homeState, onOpenItem, focusModifiers)
                }
            }
            is TvDetailSection.Cast -> {
                val focusModifiers = sectionFocusModifiers.getValue(section.id)
                val people = section.items.mapNotNull { (it as? TvDetailCastItem.Jellyfin)?.person }.take(16)
                item(section.id) {
                    Column(
                        Modifier
                            .padding(start = 108.dp, end = 42.dp)
                            .then(focusModifiers.navigationModifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSectionTitle(strings.cast)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(people, key = { it.id }, contentType = { "cast-card" }) { person ->
                                TvMediaCard(
                                    title = person.name,
                                    subtitle = person.role,
                                    format = TvMediaCardFormat.CAST_PORTRAIT,
                                    imageUrl =
                                        jellyfinImageUrl(
                                            homeState.imageBaseUrl,
                                            homeState.imageAccessToken,
                                            person.id,
                                            person.primaryImageTag,
                                            "Primary",
                                            TvArtworkSize.PORTRAIT_CARD.maxWidth,
                                    ),
                                    onClick = null,
                                    modifier = focusModifiers.itemModifier(person.id),
                                    providedFocusRequester = focusModifiers.itemFocusRequester(person.id),
                                )
                            }
                        }
                    }
                }
            }
            is TvDetailSection.Similar -> {
                val focusModifiers = sectionFocusModifiers.getValue(section.id)
                val items = section.items.mapNotNull { (it as? TvDetailSimilarItem.Jellyfin)?.item }
                item(section.id) {
                    TvDetailItemRow(strings.similar, items, homeState, onOpenItem, focusModifiers)
                }
            }
            is TvDetailSection.Ratings -> Unit
        }
    }
    item("detail-bottom-spacer") { Spacer(Modifier.height(50.dp)) }
}

@Composable
private fun TvDetailItemRow(
    title: String,
    items: List<JellyfinItem>,
    homeState: JellyfinHomeState,
    onOpenItem: (JellyfinItem) -> Unit,
    focusModifiers: TvDetailSectionFocusModifiers,
) {
    Column(
        Modifier
            .padding(start = 108.dp, end = 42.dp)
            .then(focusModifiers.navigationModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = { it.id }, contentType = { "media-card" }) { item ->
                val hasLandscapeArtwork = item.seriesThumbImageTag != null || item.thumbImageTag != null
                TvMediaCard(
                    title = item.episodeTitle ?: item.name,
                    subtitle =
                        listOfNotNull(
                            item.productionYear?.toString(),
                            item.communityRating?.let { "★ %.1f".format(it) },
                        ).joinToString("  •  "),
                    imageUrl =
                        jellyfinImageUrl(
                            homeState.imageBaseUrl,
                            homeState.imageAccessToken,
                            item.seriesId ?: item.id,
                            item.seriesThumbImageTag ?: item.thumbImageTag ?: item.primaryImageTag,
                            if (hasLandscapeArtwork) "Thumb" else "Primary",
                        ),
                    artworkFit =
                        if (hasLandscapeArtwork) {
                            TvMediaCardArtworkFit.CROP
                        } else {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        },
                    onClick = { onOpenItem(item) },
                    modifier = focusModifiers.itemModifier(item.id),
                    providedFocusRequester = focusModifiers.itemFocusRequester(item.id),
                )
            }
        }
    }
}

internal fun LazyListScope.tvSeerrDetailSections(
    uiState: TvDetailUiState,
    strings: TvStrings,
    bodyFocusModifier: Modifier,
    sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>,
    onOpenItem: (JellyseerrSearchItem) -> Unit,
) {
    uiState.sections.forEach { section ->
        when (section) {
            is TvDetailSection.Overview ->
                item(section.id) {
                    Column(
                        bodyFocusModifier.padding(horizontal = 58.dp).fillMaxWidth(0.72f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSectionTitle(strings.overview)
                        section.tagline?.let { Text(it, color = TvPurple, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                        Text(section.text ?: strings.noOverview, color = TvText, fontSize = 20.sp, lineHeight = 29.sp)
                    }
                }
            is TvDetailSection.Ratings ->
                item(section.id) {
                    Row(Modifier.padding(horizontal = 58.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        listOfNotNull(
                            section.values.tmdb?.let { "TMDB %.1f".format(it) },
                            section.values.imdb?.let { "IMDb %.1f".format(it) },
                            section.values.rottenTomatoesCritics?.let { "RT Critics %.0f%%".format(it) },
                            section.values.rottenTomatoesAudience?.let { "RT Audience %.0f%%".format(it) },
                        ).forEach { value ->
                            Box(
                                Modifier
                                    .background(TvSurfaceRaised, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 22.dp, vertical = 16.dp),
                            ) {
                                Text(value, color = TvText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            is TvDetailSection.Cast -> {
                val focusModifiers = sectionFocusModifiers.getValue(section.id)
                val people = section.items.mapNotNull { (it as? TvDetailCastItem.Seerr)?.person }.take(16)
                item(section.id) {
                    Column(
                        Modifier
                            .padding(horizontal = 58.dp)
                            .then(focusModifiers.navigationModifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSectionTitle(strings.cast)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(people, key = { it.id }, contentType = { "cast-card" }) { person ->
                                TvMediaCard(
                                    title = person.name,
                                    imageUrl = tmdbImageUrl(person.profilePath),
                                    onClick = null,
                                    subtitle = person.character,
                                    format = TvMediaCardFormat.CAST_PORTRAIT,
                                    modifier = focusModifiers.itemModifier("person-${person.id}"),
                                    providedFocusRequester = focusModifiers.itemFocusRequester("person-${person.id}"),
                                )
                            }
                        }
                    }
                }
            }
            is TvDetailSection.Similar -> {
                val focusModifiers = sectionFocusModifiers.getValue(section.id)
                val items = section.items.mapNotNull { (it as? TvDetailSimilarItem.Seerr)?.item }
                item(section.id) {
                    Column(
                        Modifier
                            .padding(horizontal = 58.dp)
                            .then(focusModifiers.navigationModifier),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSectionTitle(strings.similar)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(items, key = { "${it.mediaType}:${it.tmdbId}" }, contentType = { "media-card" }) { item ->
                                TvMediaCard(
                                    title = item.title,
                                    imageUrl = tmdbImageUrl(item.backdropPath ?: item.posterPath, item.backdropPath != null),
                                    onClick = { onOpenItem(item) },
                                    subtitle = item.releaseYear,
                                    artworkFit =
                                        if (item.backdropPath == null && item.posterPath != null) {
                                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                                        } else {
                                            TvMediaCardArtworkFit.CROP
                                        },
                                    modifier = focusModifiers.itemModifier("${item.mediaType.name.lowercase()}:${item.tmdbId}"),
                                    providedFocusRequester =
                                        focusModifiers.itemFocusRequester("${item.mediaType.name.lowercase()}:${item.tmdbId}"),
                                )
                            }
                        }
                    }
                }
            }
            is TvDetailSection.Facts,
            is TvDetailSection.Seasons,
            is TvDetailSection.Episodes,
            -> Unit
        }
    }
    item("detail-bottom-spacer") { Spacer(Modifier.height(50.dp)) }
}

@Composable
internal fun TvSeerrDetailScreen(
    route: TvRoute.SeerrDetail,
    detailState: JellyseerrMediaDetailState?,
    requestsState: JellyseerrRequestsState,
    requestsCoordinator: JellyseerrRequestsCoordinator,
    strings: TvStrings,
    onOpenItem: (JellyseerrSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val fallbackItem =
        remember(route) {
            JellyseerrSearchItem(
                tmdbId = route.tmdbId,
                mediaType = route.mediaType,
                title = route.title,
                overview = route.overview,
                releaseYear = route.releaseYear,
                posterPath = route.posterPath,
                backdropPath = route.backdropPath,
                mediaInfoId = null,
                tvdbId = route.tvdbId,
                availability =
                    dev.jellystack.core.jellyseerr
                        .JellyseerrMediaAvailability(null, null),
                requests = emptyList(),
            )
        }
    val loaded = detailState as? JellyseerrMediaDetailState.Loaded
    val detail = loaded?.detail
    val readyRequests = requestsState as? JellyseerrRequestsState.Ready
    val activeRequest = readyRequests?.currentRequestsByMedia?.get(route.mediaType to route.tmdbId)
    val canRequestStandard = readyRequests?.capabilities?.canRequest(route.mediaType) == true
    val canRequest4k = readyRequests?.capabilities?.canRequest(route.mediaType, JellyseerrRequestVariant.FOUR_K) == true
    var showRequestDialog by remember(route) { mutableStateOf(false) }
    LaunchedEffect(activeRequest?.id) {
        if (activeRequest != null) showRequestDialog = false
    }
    val similar = detail?.enrichment?.similar.orEmpty()
    val hasCast = !detail?.cast.isNullOrEmpty()
    val hasRequestAction = activeRequest == null && (canRequestStandard || canRequest4k)
    val trailerUrl = detail?.trailer?.url ?: detail?.videos?.firstOrNull { it.url != null }?.url
    val uiState =
        buildTvSeerrDetailUiState(
            routeKey = route.focusRouteKey(),
            overview = detail?.overview ?: route.overview,
            tagline = detail?.tagline,
            ratings = detail?.ratings,
            cast = detail?.cast.orEmpty(),
            similar = similar,
        )
    TvDetailFocusLayout(
        uiState = uiState,
        heroContentDescription = detail?.title ?: route.title,
        hasPrimaryAction = hasRequestAction || trailerUrl != null,
        modifier = modifier,
        heroContent = { primaryActionModifier, actionRowModifier ->
            AsyncImage(
                model =
                    tmdbImageUrl(
                        detail?.backdropPath ?: route.backdropPath ?: detail?.posterPath ?: route.posterPath,
                        backdrop = (detail?.backdropPath ?: route.backdropPath) != null,
                    ),
                contentDescription = detail?.title ?: route.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.1f), TvBackground.copy(0.2f), TvBackground),
                        ),
                    ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 58.dp, bottom = 38.dp).fillMaxWidth(0.62f),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(
                    detail?.title ?: route.title,
                    color = TvText,
                    fontSize = 46.sp,
                    lineHeight = 49.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text((detail?.genres.orEmpty()).take(4).joinToString("  •  "), color = TvTextMuted, fontSize = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        activeRequest != null ->
                            TvActionButton(
                                activeRequest.availability.standard.label(strings),
                                {},
                                enabled = false,
                                modifier = Modifier.width(250.dp),
                            )
                        hasRequestAction ->
                            TvActionButton(
                                strings.request,
                                primary = true,
                                onClick = { showRequestDialog = true },
                                modifier =
                                    primaryActionModifier
                                        .then(actionRowModifier)
                                        .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                            )
                        readyRequests != null -> Text(strings.cannotRequest, color = TvTextMuted)
                    }
                    trailerUrl?.let { url ->
                        TvActionButton(
                            strings.trailer,
                            { runCatching { uriHandler.openUri(url) } },
                            modifier =
                                if (hasRequestAction) {
                                    actionRowModifier
                                } else {
                                    primaryActionModifier.then(actionRowModifier)
                                },
                        )
                    }
                }
            }
        },
    ) { bodyFocusModifier, sectionFocusModifiers ->
        tvSeerrDetailSections(
            uiState = uiState,
            strings = strings,
            bodyFocusModifier = bodyFocusModifier,
            sectionFocusModifiers = sectionFocusModifiers,
            onOpenItem = onOpenItem,
        )
    }
    if (showRequestDialog && readyRequests != null) {
        TvRequestDialog(
            title = detail?.title ?: route.title,
            mediaType = route.mediaType,
            seasons =
                detail
                    ?.seasons
                    .orEmpty()
                    .filter { it.seasonNumber > 0 }
                    .map { it.seasonNumber },
            requestsState = readyRequests,
            strings = strings,
            onDismiss = { showRequestDialog = false },
            onSubmit = { profile, selection, variant ->
                requestsCoordinator.submitRequest(
                    item = fallbackItem,
                    profileSelection = profile,
                    seasons = selection,
                    variant = variant,
                )
            },
        )
    }
}

@Composable
private fun TvRequestDialog(
    title: String,
    mediaType: JellyseerrMediaType,
    seasons: List<Int>,
    requestsState: JellyseerrRequestsState.Ready,
    strings: TvStrings,
    onDismiss: () -> Unit,
    onSubmit: (JellyseerrRequestProfileSelection, JellyseerrCreateSelection?, JellyseerrRequestVariant) -> Unit,
) {
    var variant by remember { mutableStateOf(JellyseerrRequestVariant.STANDARD) }
    var profile by remember { mutableStateOf<JellyseerrRequestProfileSelection>(JellyseerrRequestProfileSelection.ServerDefault) }
    var allSeasons by remember { mutableStateOf(true) }
    var selectedSeasons by remember(seasons) { mutableStateOf(seasons.toSet()) }
    val capabilities = requestsState.capabilities
    val canStandard = capabilities.canRequest(mediaType, JellyseerrRequestVariant.STANDARD)
    val can4k = capabilities.canRequest(mediaType, JellyseerrRequestVariant.FOUR_K)
    LaunchedEffect(Unit) {
        if (!canStandard && can4k) variant = JellyseerrRequestVariant.FOUR_K
    }
    val profiles =
        if (!capabilities.canUseAdvancedRequests) {
            emptyList()
        } else {
            when (mediaType) {
                JellyseerrMediaType.MOVIE -> requestsState.languageProfiles.movies
                JellyseerrMediaType.TV -> requestsState.languageProfiles.tv
                else -> emptyList()
            }.filter { it.is4k == (variant == JellyseerrRequestVariant.FOUR_K) }
        }
    LaunchedEffect(variant) { profile = JellyseerrRequestProfileSelection.ServerDefault }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .heightIn(max = 470.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "${strings.request} $title",
                color = TvText,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (canStandard && can4k) {
                TvSectionTitle(strings.version)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        strings.standard,
                        { variant = JellyseerrRequestVariant.STANDARD },
                        primary = variant == JellyseerrRequestVariant.STANDARD,
                    )
                    TvActionButton(
                        "4K",
                        { variant = JellyseerrRequestVariant.FOUR_K },
                        primary = variant == JellyseerrRequestVariant.FOUR_K,
                    )
                }
            }
            if (mediaType == JellyseerrMediaType.TV && seasons.isNotEmpty()) {
                TvSectionTitle(strings.seasons)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    item { TvActionButton(strings.all, { allSeasons = true }, primary = allSeasons) }
                    items(seasons) { season ->
                        val selected = allSeasons || season in selectedSeasons
                        TvActionButton(
                            "${strings.season} $season",
                            {
                                if (allSeasons) {
                                    allSeasons = false
                                    selectedSeasons = setOf(season)
                                } else {
                                    selectedSeasons =
                                        if (season in selectedSeasons) selectedSeasons - season else selectedSeasons + season
                                }
                            },
                            primary = selected,
                        )
                    }
                }
            }
            if (capabilities.canUseAdvancedRequests && profiles.isNotEmpty()) {
                TvSectionTitle(strings.requestProfile)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    item {
                        TvActionButton(
                            strings.serverDefault,
                            { profile = JellyseerrRequestProfileSelection.ServerDefault },
                            primary = profile is JellyseerrRequestProfileSelection.ServerDefault,
                        )
                    }
                    items(profiles, key = { "${it.serviceId}:${it.languageProfileId}:${it.profileId}" }) { option ->
                        TvActionButton(
                            option.name,
                            { profile = JellyseerrRequestProfileSelection.Profile(option) },
                            primary = (profile as? JellyseerrRequestProfileSelection.Profile)?.option == option,
                        )
                    }
                }
            }
            requestsState.message?.takeIf { it.kind.name == "ERROR" }?.let {
                Text(it.detail ?: strings.requestFailed, color = Color(0xFFFFA59E), fontSize = 17.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    if (requestsState.isPerformingAction) strings.requesting else strings.sendRequest,
                    modifier = Modifier.width(230.dp),
                    primary = true,
                    enabled =
                        !requestsState.isPerformingAction &&
                            (mediaType != JellyseerrMediaType.TV || allSeasons || selectedSeasons.isNotEmpty()),
                    onClick = {
                        onSubmit(
                            profile,
                            when {
                                mediaType != JellyseerrMediaType.TV -> null
                                allSeasons -> JellyseerrCreateSelection.AllSeasons
                                else -> JellyseerrCreateSelection.Seasons(selectedSeasons.sorted())
                            },
                            variant,
                        )
                    },
                )
                TvActionButton(strings.cancel, onDismiss, modifier = Modifier.width(150.dp))
            }
        }
    }
}
