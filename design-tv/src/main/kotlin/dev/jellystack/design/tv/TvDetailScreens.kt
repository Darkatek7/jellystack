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

@Composable
internal fun TvDetailFocusLayout(
    routeKey: String,
    heroContentDescription: String,
    bodyFocusItemIndex: Int = 1,
    nextBodyItemIndex: Int? = null,
    modifier: Modifier = Modifier,
    heroContent: @Composable BoxScope.(primaryActionModifier: Modifier, actionRowModifier: Modifier) -> Unit,
    content: LazyListScope.(bodyFocusModifier: Modifier, lowerContentFocusModifier: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroFocusRequester = remember(routeKey) { FocusRequester() }
    val primaryActionFocusRequester = remember(routeKey) { FocusRequester() }
    val bodyFocusRequester = remember(routeKey) { FocusRequester() }
    val lowerContentFocusRequester = remember(routeKey) { FocusRequester() }

    fun focusHero() {
        scope.launch {
            listState.scrollToItem(0)
            heroFocusRequester.requestFocus()
        }
    }

    fun focusPrimaryAction() {
        scope.launch {
            listState.scrollToItem(0)
            withFrameNanos { }
            primaryActionFocusRequester.requestFocus()
        }
    }

    fun focusBody() {
        scope.launch {
            listState.scrollToItem(bodyFocusItemIndex)
            withFrameNanos { }
            bodyFocusRequester.requestFocus()
        }
    }

    fun focusLowerContent() {
        val itemIndex = nextBodyItemIndex ?: return
        scope.launch {
            listState.scrollToItem(itemIndex)
            withFrameNanos { }
            lowerContentFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(routeKey) {
        listState.scrollToItem(0)
        heroFocusRequester.requestFocus()
    }

    val primaryActionModifier =
        Modifier
            .focusRequester(primaryActionFocusRequester)
            .testTag("tv-detail-primary-action")
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
                    focusPrimaryAction()
                    true
                } else if (nextBodyItemIndex != null && isInitialDownPress &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    focusLowerContent()
                    true
                } else {
                    false
                }
            }.focusable()
    val lowerContentFocusModifier =
        Modifier
            .focusRequester(lowerContentFocusRequester)
            .testTag("tv-detail-lower-content-focus")
            .onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                    focusBody()
                    true
                } else {
                    false
                }
            }

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
                    .onKeyEvent { event ->
                        if (
                            event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            primaryActionFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }.tvFocusable(
                        onClick = { primaryActionFocusRequester.requestFocus() },
                        shape = RoundedCornerShape(0.dp),
                        scale = 1f,
                    ),
            ) {
                heroContent(primaryActionModifier, actionRowModifier)
            }
        }
        content(bodyFocusModifier, lowerContentFocusModifier)
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
        routeKey = route.itemId,
        heroContentDescription = currentDetail.name,
        bodyFocusItemIndex = 2,
        nextBodyItemIndex =
            if (episodes.isNotEmpty() || currentDetail.people.isNotEmpty() || similar.isNotEmpty()) 3 else null,
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
                        1800,
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
                                700,
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
    ) { bodyFocusModifier, lowerContentFocusModifier ->
        item("facts") {
            Row(Modifier.padding(start = 108.dp, end = 42.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOfNotNull(
                    currentDetail.productionYear?.toString(),
                    currentDetail.runTimeTicks?.let { "${it / 600_000_000L} min" },
                    currentDetail.officialRating,
                    currentDetail.communityRating?.let { "★ %.1f".format(it) },
                    currentDetail.mediaSources
                        .firstOrNull()
                        ?.streams
                        ?.firstOrNull { it.width != null }
                        ?.height
                        ?.let { "${it}p" },
                ).forEach { Text(it, color = TvTextMuted, fontSize = 18.sp) }
            }
        }
        item("overview") {
            Column(
                bodyFocusModifier.padding(start = 108.dp, end = 42.dp).fillMaxWidth(0.78f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvSectionTitle(strings.overview)
                currentDetail.taglines.firstOrNull()?.let { Text(it, color = TvPurple, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                Text(currentDetail.overview ?: strings.noOverview, color = TvText, fontSize = 20.sp, lineHeight = 29.sp)
            }
        }
        if (episodes.isNotEmpty()) {
            if (seasonGroups.size > 1) {
                item("seasons") {
                    Column(Modifier.padding(start = 108.dp, end = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSectionTitle(strings.seasons)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            itemsIndexed(
                                seasonGroups,
                                key = { _, group -> "season-${group.seasonNumber ?: Int.MAX_VALUE}" },
                            ) { index, group ->
                                val label =
                                    group.seasonNumber
                                        ?.let { season -> strings.seasonNumber.format(season) }
                                        ?: strings.specials
                                TvActionButton(
                                    label,
                                    { selectedSeasonIndex = index },
                                    primary = index == selectedSeasonIndex.coerceIn(seasonGroups.indices),
                                    modifier = Modifier.widthIn(min = 150.dp),
                                )
                            }
                        }
                    }
                }
            }
            item("episodes") {
                TvDetailItemRow(
                    strings.episodes,
                    visibleEpisodes,
                    homeState,
                    onOpenItem,
                    firstItemModifier = lowerContentFocusModifier,
                )
            }
        }
        if (currentDetail.people.isNotEmpty()) {
            item("cast") {
                Column(Modifier.padding(start = 108.dp, end = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSectionTitle(strings.cast)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(currentDetail.people.take(16), key = { it.id }) { person ->
                            TvMediaCard(
                                title = person.name,
                                subtitle = person.role,
                                landscape = false,
                                focusable = false,
                                imageUrl =
                                    jellyfinImageUrl(
                                        homeState.imageBaseUrl,
                                        homeState.imageAccessToken,
                                        person.id,
                                        person.primaryImageTag,
                                        "Primary",
                                        400,
                                    ),
                                onClick = {},
                                modifier =
                                    if (episodes.isEmpty() && person.id == currentDetail.people.first().id) {
                                        lowerContentFocusModifier
                                    } else {
                                        Modifier
                                    },
                            )
                        }
                    }
                }
            }
        }
        if (similar.isNotEmpty()) {
            item("similar") {
                TvDetailItemRow(
                    strings.similar,
                    similar,
                    homeState,
                    onOpenItem,
                    firstItemModifier =
                        if (episodes.isEmpty() && currentDetail.people.isEmpty()) lowerContentFocusModifier else Modifier,
                )
            }
        }
        item { Spacer(Modifier.height(50.dp)) }
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

@Composable
private fun TvDetailItemRow(
    title: String,
    items: List<JellyfinItem>,
    homeState: JellyfinHomeState,
    onOpenItem: (JellyfinItem) -> Unit,
    firstItemModifier: Modifier = Modifier,
) {
    Column(Modifier.padding(start = 108.dp, end = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvSectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = { it.id }) { item ->
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
                            if (item.seriesThumbImageTag != null || item.thumbImageTag != null) "Thumb" else "Primary",
                        ),
                    onClick = { onOpenItem(item) },
                    modifier = if (item.id == items.first().id) firstItemModifier else Modifier,
                )
            }
        }
    }
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            item("hero") {
                Box(Modifier.fillMaxWidth().height(520.dp)) {
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
                                canRequestStandard || canRequest4k ->
                                    TvActionButton(
                                        strings.request,
                                        primary = true,
                                        onClick = { showRequestDialog = true },
                                        modifier = Modifier.width(230.dp),
                                    )
                                readyRequests != null -> Text(strings.cannotRequest, color = TvTextMuted)
                            }
                            (detail?.trailer?.url ?: detail?.videos?.firstOrNull { it.url != null }?.url)?.let { trailerUrl ->
                                TvActionButton(strings.trailer, { runCatching { uriHandler.openUri(trailerUrl) } })
                            }
                        }
                    }
                }
            }
            item("overview") {
                Column(Modifier.padding(horizontal = 58.dp).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSectionTitle(strings.overview)
                    detail?.tagline?.let { Text(it, color = TvPurple, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    Text(
                        detail?.overview ?: route.overview ?: strings.noOverview,
                        color = TvText,
                        fontSize = 20.sp,
                        lineHeight = 29.sp,
                    )
                }
            }
            detail?.ratings?.let { ratings ->
                item("ratings") {
                    Row(Modifier.padding(horizontal = 58.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        listOfNotNull(
                            ratings.tmdb?.let { "TMDB %.1f".format(it) },
                            ratings.imdb?.let { "IMDb %.1f".format(it) },
                            ratings.rottenTomatoesCritics?.let { "RT Critics %.0f%%".format(it) },
                            ratings.rottenTomatoesAudience?.let { "RT Audience %.0f%%".format(it) },
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
            }
            if (!detail?.cast.isNullOrEmpty()) {
                item("cast") {
                    Column(Modifier.padding(horizontal = 58.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSectionTitle(strings.cast)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(detail!!.cast.take(16), key = { it.id }) { person ->
                                TvMediaCard(
                                    person.name,
                                    tmdbImageUrl(person.profilePath),
                                    {},
                                    subtitle = person.character,
                                    landscape = false,
                                    focusable = false,
                                )
                            }
                        }
                    }
                }
            }
            val similar = detail?.enrichment?.similar.orEmpty()
            if (similar.isNotEmpty()) {
                item("similar") {
                    Column(Modifier.padding(horizontal = 58.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSectionTitle(strings.similar)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(similar, key = { "${it.mediaType}:${it.tmdbId}" }) { item ->
                                TvMediaCard(
                                    item.title,
                                    tmdbImageUrl(item.backdropPath ?: item.posterPath, item.backdropPath != null),
                                    { onOpenItem(item) },
                                    subtitle = item.releaseYear,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(50.dp)) }
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
