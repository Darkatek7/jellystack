@file:Suppress(
    "CyclomaticComplexMethod",
    "ExplicitItLambdaParameter",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.jellyfin.LibraryLoadErrorKind
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.HomeRowPreferences
import dev.jellystack.core.preferences.visibleRowIds
import dev.jellystack.core.profile.MediaIdentity
import dev.jellystack.core.profile.MyListEntry
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

internal const val TV_HOME_HERO_HEIGHT_DP = 300

/** Row title (24 dp), title spacing (8 dp), and row content padding (6 dp) above each home and browse card. */
internal val TV_HOME_ROW_CARD_OFFSET = 38.dp
internal const val TV_FOCUS_MATERIALIZATION_TIMEOUT_MS = 1_000L

/** Placeholder cards shown while a grid loads: two rows of the four columns of the library list. */
internal const val TV_SKELETON_GRID_CELLS = 8

internal data class TvLazyFocusLocation(
    val verticalIndex: Int,
    val rowId: String? = null,
    val horizontalIndex: Int? = null,
)

@Composable
internal fun rememberTvLazyRowStates(rowIds: List<String>): Map<String, LazyListState> {
    val states = linkedMapOf<String, LazyListState>()
    for (rowId in rowIds) {
        key(rowId) { states[rowId] = rememberLazyListState() }
    }
    return states
}

internal suspend fun materializeTvLazyTarget(
    outerState: LazyListState,
    rowStates: Map<String, LazyListState>,
    location: TvLazyFocusLocation,
): Boolean {
    outerState.scrollToItem(location.verticalIndex)
    val rowState = location.rowId?.let(rowStates::get)
    return when {
        location.rowId == null || location.horizontalIndex == null -> true
        rowState == null -> false
        else -> materializeTvRowItem(rowState, requireNotNull(location.horizontalIndex))
    }
}

private suspend fun materializeTvRowItem(
    rowState: LazyListState,
    horizontalIndex: Int,
): Boolean {
    val rowAttached =
        withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
            snapshotFlow { rowState.layoutInfo.totalItemsCount }.first { it > horizontalIndex }
            true
        } ?: false
    if (rowAttached) rowState.scrollToItem(horizontalIndex)
    return rowAttached
}

internal fun tvHomeHeroHeightDp(): Int = TV_HOME_HERO_HEIGHT_DP

internal fun tvHomeFirstCardTopDp(): Int = (TV_CINEMATIC_ROWS_TOP + TV_HOME_ROW_CARD_OFFSET).value.toInt()

internal enum class TvSearchSource { ALL, JELLYFIN, SEERR }

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvHomeScreen(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    strings: TvStrings,
    trailerPreviewState: TvTrailerPreviewState,
    focusMemory: TvFocusMemory,
    onRefresh: () -> Unit,
    onPreviewFocus: (TvTrailerPreviewOwner, JellyfinItem, String?) -> Unit,
    onPreviewBlur: (TvTrailerPreviewOwner, JellyfinItem, String?) -> Unit,
    onCancelPreview: (TvTrailerPreviewOwner) -> Unit,
    trailerPreviewEngine: AndroidPlayerEngine,
    previewSoundEnabled: Boolean,
    previewProgress: State<Float>,
    onPlayItem: (JellyfinItem) -> Unit,
    onItem: (JellyfinItem) -> Unit,
    onHomeLibrary: (String, String) -> Unit,
    onLibrary: (JellyfinLibrary) -> Unit,
    onSeerrItem: (TvRoute.SeerrDetail) -> Unit,
    myList: List<MyListEntry> = emptyList(),
    onMyListEntry: (MyListEntry) -> Unit = {},
    spotlightAutoAdvance: Boolean = false,
    rowPreferences: HomeRowPreferences = HomeRowPreferences(),
    heroHintsSeen: Boolean = true,
    onHeroHintsUsed: () -> Unit = {},
    homeBack: TvHomeBackState? = null,
    modifier: Modifier = Modifier,
) {
    val heroPresentation =
        remember(state, homeSections) {
            buildTvHomeHeroPresentation(state, homeSections, Clock.System.now())
        }
    val heroCandidates = heroPresentation.candidates
    val visibleSections =
        (homeSections as? HomeSectionsState.Ready)
            ?.sections
            .orEmpty()
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.id }
    // The rows as built, then as the user arranged them: hidden ones drop out, new ones follow at the end.
    val orderedRowIds =
        remember(state, homeSections, myList, strings, rowPreferences) {
            rowPreferences.visibleRowIds(tvHomeRowOptions(state, homeSections, myList.isNotEmpty(), strings).map { it.id })
        }
    val focusRows =
        remember(visibleSections, state.continueWatching, state.nextUp, state.libraries, myList, orderedRowIds) {
            val sectionsById = visibleSections.associateBy { tvHomePluginRowId(it.id) }
            orderedRowIds.mapIndexedNotNull { lazyColumnIndex, rowId ->
                val itemIds =
                    when (rowId) {
                        TV_HOME_MY_LIST_ROW -> myList.map { it.identity.tvMyListKey() }
                        TV_HOME_CONTINUE_ROW -> state.continueWatching.map { it.id }
                        TV_HOME_NEXT_UP_ROW -> state.nextUp.map { it.id }
                        TV_HOME_LIBRARIES_ROW -> state.libraries.map { it.id }
                        else -> sectionsById[rowId]?.items?.map { it.id }
                    }
                itemIds?.let { TvHomeFocusRow(rowId, lazyColumnIndex, it, landscape = true) }
            }
        }
    val homeListState = rememberLazyListState()
    val focusContext = LocalTvFocusContext.current
    val heroPrimaryFocusRequester = remember { FocusRequester() }
    val homeRowIds =
        buildList {
            addAll(focusRows.map { it.id })
            if (homeSections !is HomeSectionsState.Ready && "libraries" !in this) add("libraries")
        }
    val rowListStates = rememberTvLazyRowStates(homeRowIds)
    var heroHasFocus by remember { mutableStateOf(false) }
    var rowsHaveFocus by remember { mutableStateOf(false) }
    var laidOutRowIds by remember { mutableStateOf(homeRowIds) }
    if (homeBack != null) {
        DisposableEffect(homeBack, heroPrimaryFocusRequester) {
            // The hero's focus effect below then scrolls the list to its first item without animation.
            homeBack.bind { runCatching { heroPrimaryFocusRequester.requestFocus() } }
            onDispose { homeBack.unbind() }
        }
    }
    LaunchedEffect(heroHasFocus, homeRowIds) {
        // Rows that load late, as on a cold start, can land above the visible ones, and the list keeps its first
        // visible row. Unless a card in the rows has focus, the rows start at the first row instead.
        val rowsChanged = homeRowIds != laidOutRowIds
        laidOutRowIds = homeRowIds
        if (heroHasFocus || (rowsChanged && !rowsHaveFocus)) homeListState.scrollToItem(0)
    }
    val errorLazyColumnIndex = orderedRowIds.size
    val homeFocusLocations =
        buildMap {
            put(TV_HOME_PRIMARY_TARGET, TvLazyFocusLocation(verticalIndex = 0))
            focusRows.forEach { row ->
                row.itemIds.forEachIndexed { index, itemId ->
                    put(
                        tvHomeCardTargetId(row.id, itemId),
                        TvLazyFocusLocation(row.lazyColumnIndex, row.id, index),
                    )
                }
            }
            if (state.homeErrorMessage != null && heroCandidates.isNotEmpty()) {
                put(TV_HOME_RETRY_TARGET, TvLazyFocusLocation(errorLazyColumnIndex))
            }
        }
    val homeFallbackTarget =
        focusRows
            .firstOrNull()
            ?.itemIds
            ?.firstOrNull()
            ?.let { tvHomeCardTargetId(focusRows.first().id, it) }
            ?: TV_HOME_PRIMARY_TARGET
    TvRouteFocusMaterializer(
        ownerId = "home-lists",
        targetIds = homeFocusLocations.keys,
        fallbackTargetIds = setOf(homeFallbackTarget),
    ) { targetId ->
        homeFocusLocations[targetId]?.let { materializeTvLazyTarget(homeListState, rowListStates, it) } ?: false
    }
    val rowFocusMemory = focusMemory.rows
    val verticalFocusCoordinator =
        remember(rowFocusMemory) {
            TvHomeVerticalFocusCoordinator(focusRows) { row -> rowFocusMemory.landingIndex("home", row.id, row.itemIds) }
        }
    var pendingFocusMove by remember { mutableStateOf<TvHomeFocusMove?>(null) }
    LaunchedEffect(focusRows) {
        rowFocusMemory.retainRows("home", focusRows.map { it.id })
        val reconciled = verticalFocusCoordinator.replaceRows(focusRows)
        if (reconciled != pendingFocusMove) pendingFocusMove = reconciled
    }
    LaunchedEffect(pendingFocusMove, focusRows) {
        val move = pendingFocusMove ?: return@LaunchedEffect
        val coordinator = focusContext?.coordinator ?: return@LaunchedEffect

        suspend fun requestTarget(targetId: String): Boolean =
            coordinator.restoreFocus(
                routeKey = focusContext.routeKey,
                preferredTargetId = targetId,
                includeFallback = false,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            ) is TvFocusRestoration.Focused
        val completion = verticalFocusCoordinator.completeMove(move.requestId, ::requestTarget)
        if (completion != null) {
            if (pendingFocusMove?.requestId == move.requestId) pendingFocusMove = null
        }
    }
    val onVerticalMove: (TvHomeFocusOrigin, TvHomeVerticalDirection, JellyfinItem?) -> Unit =
        { origin, direction, previewItem ->
            val previewOwner =
                when (origin) {
                    TvHomeFocusOrigin.Hero -> TvTrailerPreviewOwner.HERO
                    is TvHomeFocusOrigin.Row -> TvTrailerPreviewOwner.CARD
                }
            // The card being left is where Up and back Down return to.
            (origin as? TvHomeFocusOrigin.Row)?.let { row ->
                row.itemId?.let { itemId -> rowFocusMemory.remember("home", row.id, itemId) }
            }
            val move =
                verticalFocusCoordinator.beginMove(
                    origin = origin,
                    direction = direction,
                    onAccepted = { onCancelPreview(previewOwner) },
                )
            if (move != null) {
                val presentationId =
                    (origin as? TvHomeFocusOrigin.Row)?.let { row ->
                        row.itemId?.let { itemId -> tvHomeCardTargetId(row.id, itemId) }
                    }
                previewItem?.let { onPreviewBlur(TvTrailerPreviewOwner.CARD, it, presentationId) }
                pendingFocusMove = move
            }
        }
    var carouselState by remember { mutableStateOf(TvHomeCarouselState()) }
    val candidateIds = heroCandidates.map { it.actionItem.id }
    val spotlightIndex = heroCandidates.indexOfFirst { it.actionItem.id == carouselState.selectedId }.takeIf { it >= 0 } ?: 0
    // The stage follows a focused card only after focus rested on it; the trailer for that card starts then, too.
    val stageDwell =
        rememberTvHomeStageDwell { target -> onPreviewFocus(TvTrailerPreviewOwner.CARD, target.item, target.presentationId) }
    val stagedCard = stageDwell.staged
    LaunchedEffect(candidateIds) {
        val selectedId = reconcileTvHomeCarouselSelection(candidateIds, carouselState.selectedId)
        if (selectedId != carouselState.selectedId) {
            carouselState = carouselState.copy(selectedId = selectedId)
        }
    }
    val heroInteractions = remember { TvInteractionCounter() }
    val autoAdvanceHero =
        shouldAutoAdvanceTvHomeHero(
            enabled = spotlightAutoAdvance,
            candidateCount = candidateIds.size,
            reducedMotion = LocalTvFocusAppearance.current.reducedMotion,
            // Rows with focus pause the carousel; so does a staged card. An idle screen without focus keeps moving.
            heroFocused = !rowsHaveFocus && stagedCard == null,
            trailerActive = trailerPreviewState is TvTrailerPreviewState.Armed || trailerPreviewState is TvTrailerPreviewState.Playing,
        )
    LaunchedEffect(autoAdvanceHero, candidateIds, carouselState.selectedId, heroInteractions) {
        if (!autoAdvanceHero) return@LaunchedEffect
        // A key press starts the wait again.
        heroInteractions.changes.collectLatest {
            delay(TV_HOME_HERO_AUTO_ADVANCE_MS)
            carouselState = carouselState.copy(selectedId = nextTvHomeAutoAdvanceId(candidateIds, carouselState.selectedId))
        }
    }
    val openNavigationRail = LocalTvNavigationRailOpener.current
    val heroModel =
        heroCandidates.getOrNull(spotlightIndex)?.let { candidate ->
            val stageItem = stagedCard?.item ?: candidate.actionItem
            TvHomeHeroModel(
                stageItem = stageItem,
                stagePresentationId =
                    stagedCard?.presentationId
                        ?: (trailerPreviewState as? TvTrailerPreviewState.Playing)
                            ?.request
                            ?.takeIf { it.target.itemId == stageItem.id }
                            ?.presentationId,
                mode = heroPresentation.mode,
                page = TvHomeHeroPage(index = spotlightIndex, count = heroCandidates.size),
                showCarouselContext = stagedCard == null,
                imageBaseUrl = state.imageBaseUrl,
                imageAccessToken = state.imageAccessToken,
                showKeyHints = !heroHintsSeen,
            )
        }
    val stageTrailerPlaying =
        heroModel != null &&
            trailerPreviewState.showsTvHomeStagePreview(heroModel.stageItem.id, heroModel.stagePresentationId)
    val homeUiAlpha = rememberTvHomeUiAlpha(stageTrailerPlaying, heroInteractions)
    val heroTrailer =
        TvHomeHeroTrailer(
            state = trailerPreviewState,
            soundEnabled = previewSoundEnabled,
            progress = previewProgress,
            video =
                TvTrailerVideo(
                    surface = { surfaceModifier -> TvTrailerPreviewSurface(trailerPreviewEngine, surfaceModifier) },
                    firstFrameShown = trailerPreviewEngine.firstFrameRendered.collectAsState(),
                ),
            homeUiAlpha = homeUiAlpha,
        )
    Box(
        modifier.fillMaxSize().onPreviewKeyEvent {
            // Any remote input restarts the spotlight timer and brings the home UI back over a trailer.
            heroInteractions.record()
            false
        },
    ) {
        heroModel?.let { TvHomeBackdrop(it, heroTrailer) }
        Box(
            Modifier.fillMaxWidth().onFocusChanged {
                heroHasFocus = it.hasFocus
                if (it.hasFocus) homeBack?.atTop = true
            },
        ) {
            if (heroModel != null) {
                TvHeroCarousel(
                    model = heroModel,
                    trailer = heroTrailer,
                    callbacks =
                        TvHomeHeroCallbacks(
                            onPlay = { onPlayItem(heroModel.stageItem) },
                            onDetails = { onItem(heroModel.stageItem) },
                            onVerticalMove = { direction -> onVerticalMove(TvHomeFocusOrigin.Hero, direction, null) },
                            // The hero shows its own spotlight again at once, so Play and Details act on it.
                            onHeroFocused = stageDwell::onSpotlightFocused,
                            onHintsUsed = onHeroHintsUsed,
                            onCarouselMove = { direction ->
                                val move = moveTvHomeCarouselManually(candidateIds, carouselState, direction)
                                if (move.openNavigationRail) openNavigationRail?.invoke() else carouselState = move.state
                            },
                        ),
                    strings = strings,
                    primaryFocusRequester = heroPrimaryFocusRequester,
                )
            } else {
                TvEmptyHomeHero(
                    state = state,
                    strings = strings,
                    onRefresh = onRefresh,
                    primaryFocusRequester = heroPrimaryFocusRequester,
                    onVerticalMove = { direction -> onVerticalMove(TvHomeFocusOrigin.Hero, direction, null) },
                )
            }
        }
        TvHomeFadeLayer(homeUiAlpha, Modifier.align(Alignment.TopEnd)) { TvHomeClock(rememberTvClockLabel()) }
        if (stageTrailerPlaying) {
            TvTrailerPillUnderClock(
                strings.trailer,
                heroTrailer.soundEnabled,
                heroTrailer.progress,
                Modifier.align(Alignment.TopEnd),
            )
        }
        val rowScrollSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides rememberTvRowAlignedBringIntoViewSpec(TV_HOME_ROW_CARD_OFFSET),
        ) {
            LazyColumn(
                state = homeListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = TV_CINEMATIC_ROWS_TOP)
                        .tvFadeRowsCutAtTop(homeListState)
                        .onFocusChanged {
                            rowsHaveFocus = it.hasFocus
                            if (it.hasFocus) homeBack?.atTop = false
                        }
                        // The rows stay composed so the focused card keeps its focus while the trailer has the screen.
                        .graphicsLayer { alpha = homeUiAlpha.value },
                contentPadding = TvHomeRowsPadding,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(orderedRowIds, key = { it }, contentType = { "media-row" }) { rowId ->
                    val section = visibleSections.firstOrNull { tvHomePluginRowId(it.id) == rowId }
                    TvRowScroll(rowScrollSpec) {
                        when {
                            rowId == TV_HOME_MY_LIST_ROW ->
                                TvMyListRow(
                                    entries = myList,
                                    state = state,
                                    strings = strings,
                                    focusMemory = focusMemory,
                                    listState = rowListStates.getValue(rowId),
                                    onEntry = onMyListEntry,
                                    onPreviewFocus = stageDwell::onCardFocused,
                                    onPreviewBlur = { item, presentationId ->
                                        stageDwell.onCardBlurred(item, presentationId)
                                        onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                    },
                                    onVerticalMove = { entry, direction ->
                                        onVerticalMove(
                                            TvHomeFocusOrigin.Row(rowId, entry.identity.tvMyListKey()),
                                            direction,
                                            entry.jellyfinItem,
                                        )
                                    },
                                )
                            section != null && homeSections is HomeSectionsState.Ready ->
                                TvHomeSectionRow(
                                    section = section,
                                    strings = strings,
                                    imageBaseUrl = homeSections.imageBaseUrl,
                                    imageAccessToken = homeSections.imageAccessToken,
                                    focusMemory = focusMemory,
                                    onItem = onItem,
                                    onSeerrItem = onSeerrItem,
                                    onPreviewFocus = stageDwell::onCardFocused,
                                    onPreviewBlur = { item, presentationId ->
                                        stageDwell.onCardBlurred(item, presentationId)
                                        onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                    },
                                    onHomeLibrary = onHomeLibrary,
                                    listState = rowListStates.getValue(rowId),
                                    onVerticalMove = { itemId, item, direction ->
                                        onVerticalMove(TvHomeFocusOrigin.Row(rowId, itemId), direction, item)
                                    },
                                )
                            rowId == TV_HOME_LIBRARIES_ROW ->
                                TvLibraryRow(
                                    state.libraries,
                                    state,
                                    strings.myMedia,
                                    strings,
                                    focusMemory,
                                    onLibrary,
                                    listState = rowListStates.getValue(rowId),
                                    onVerticalMove = { libraryId, direction ->
                                        onVerticalMove(TvHomeFocusOrigin.Row(rowId, libraryId), direction, null)
                                    },
                                )
                            else -> {
                                val isContinue = rowId == TV_HOME_CONTINUE_ROW
                                TvJellyfinRow(
                                    if (isContinue) strings.continueWatching else strings.nextUp,
                                    if (isContinue) state.continueWatching else state.nextUp,
                                    state,
                                    strings,
                                    focusMemory,
                                    onItem,
                                    onPreviewFocus = stageDwell::onCardFocused,
                                    onPreviewBlur = { item, presentationId ->
                                        stageDwell.onCardBlurred(item, presentationId)
                                        onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                    },
                                    listState = rowListStates.getValue(rowId),
                                    focusTargetId = { itemId -> tvHomeCardTargetId(rowId, itemId) },
                                    onVerticalMove = { item, direction ->
                                        onVerticalMove(TvHomeFocusOrigin.Row(rowId, item.id), direction, item)
                                    },
                                )
                            }
                        }
                    }
                }
                state.homeErrorMessage?.takeIf { heroCandidates.isNotEmpty() }?.let { message ->
                    item("error") {
                        TvActionButton(
                            "${strings.retry}: $message",
                            onRefresh,
                            focusToNavigationRailOnLeft = true,
                            focusTargetId = TV_HOME_RETRY_TARGET,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMyListRow(
    entries: List<MyListEntry>,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    listState: LazyListState,
    onEntry: (MyListEntry) -> Unit,
    onPreviewFocus: (JellyfinItem, String) -> Unit,
    onPreviewBlur: (JellyfinItem, String) -> Unit,
    onVerticalMove: (MyListEntry, TvHomeVerticalDirection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(strings.myList)
        LazyRow(
            state = listState,
            modifier = Modifier.tvRowBleed(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(6.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.identity.tvMyListKey() }) { index, entry ->
                val item = entry.jellyfinItem
                val artwork = item?.let(::resolveTvJellyfinArtwork)
                val targetId = tvHomeCardTargetId("my-list", entry.identity.tvMyListKey())
                TvMediaCard(
                    title = entry.title,
                    subtitle = if (entry.available) strings.play else strings.request,
                    imageUrl =
                        if (item != null) {
                            jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, artwork)
                        } else {
                            val saved = requireNotNull(entry.savedMedia)
                            tmdbImageUrl(saved.backdropPath ?: saved.posterPath, backdrop = saved.backdropPath != null)
                        },
                    onClick = { onEntry(entry) },
                    artworkFit =
                        if (item != null && artwork?.imageType == "Primary") {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        } else {
                            TvMediaCardArtworkFit.CROP
                        },
                    onFocused = {
                        focusMemory.remember("home", "my-list", entry.identity.tvMyListKey(), horizontalIndex = index)
                        item?.let { onPreviewFocus(it, targetId) }
                    },
                    onFocusChanged = { focused -> if (!focused) item?.let { onPreviewBlur(it, targetId) } },
                    modifier = Modifier.tvHomeVerticalFocus { direction -> onVerticalMove(entry, direction) },
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

private fun MediaIdentity.tvMyListKey(): String = "$mediaType:${provider.storageValue}:$providerId"

internal fun shouldRequestHomeEntryFocus(
    hasRecentContent: Boolean,
    railOpen: Boolean,
): Boolean = hasRecentContent && !railOpen

internal class TvHomeEntryFocusGate {
    private var consumed = false

    fun consume(
        hasRecentContent: Boolean,
        railOpen: Boolean,
    ): Boolean {
        if (consumed || !shouldRequestHomeEntryFocus(hasRecentContent, railOpen)) return false
        consumed = true
        return true
    }
}

internal fun Modifier.tvHomeVerticalFocus(onVerticalMove: (TvHomeVerticalDirection) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (shouldHandleTvHomeVerticalKey(event.nativeKeyEvent.repeatCount)) {
                    onVerticalMove(TvHomeVerticalDirection.UP)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (shouldHandleTvHomeVerticalKey(event.nativeKeyEvent.repeatCount)) {
                    onVerticalMove(TvHomeVerticalDirection.DOWN)
                }
                true
            }
            else -> false
        }
    }

internal fun shouldHandleTvHomeVerticalKey(repeatCount: Int): Boolean = repeatCount == 0

@Composable
internal fun TvJellyfinRow(
    title: String,
    items: List<JellyfinItem>,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onItem: (JellyfinItem) -> Unit,
    displayItemsById: Map<String, JellyfinItem> = emptyMap(),
    onPreviewFocus: (JellyfinItem, String) -> Unit = { _, _ -> },
    onPreviewBlur: (JellyfinItem, String) -> Unit = { _, _ -> },
    routeKey: String = "home",
    listState: LazyListState,
    focusTargetId: (String) -> String,
    screenEntry: Boolean = false,
    edgePadding: Dp = 6.dp,
    bleedStart: Dp = TvLayoutTokens.ContentStart,
    onVerticalMove: (JellyfinItem, TvHomeVerticalDirection) -> Unit = { _, _ -> },
) {
    val focusKeeper = rememberTvRowFocusKeeper(listState, remember(items) { items.map { it.id } })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            modifier = Modifier.tvRowBleed(start = bleedStart),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(edgePadding, start = bleedStart),
        ) {
            itemsIndexed(
                items,
                key = { _, item -> item.id },
                contentType = { _, _ -> "media-card" },
            ) { index, item ->
                val targetId = focusTargetId(item.id)
                val displayItem = displayItemsById[item.id] ?: item
                val artwork = resolveTvJellyfinArtwork(displayItem)
                val cardText = displayItem.tvCardText(strings)
                TvMediaCard(
                    title = cardText.title,
                    subtitle = cardText.subtitle,
                    progress = cardText.progress,
                    badge = tvJellyfinCardBadge(displayItem, strings),
                    imageUrl =
                        jellyfinImageUrl(
                            state.imageBaseUrl,
                            state.imageAccessToken,
                            artwork,
                        ),
                    onClick = { onItem(item) },
                    artworkFit =
                        if (artwork?.imageType == "Primary") {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        } else {
                            TvMediaCardArtworkFit.CROP
                        },
                    onFocused = {
                        focusKeeper.onFocused(item.id)
                        focusMemory.remember(routeKey, title, item.id, horizontalIndex = index)
                        onPreviewFocus(item, targetId)
                    },
                    onFocusChanged = { focused ->
                        if (!focused) {
                            focusKeeper.onBlurred(item.id)
                            onPreviewBlur(item, targetId)
                        }
                    },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0, targetId)
                            .tvHomeVerticalFocus { direction -> onVerticalMove(item, direction) },
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

@Composable
private fun TvLibraryRow(
    libraries: List<JellyfinLibrary>,
    state: JellyfinHomeState,
    title: String,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onLibrary: (JellyfinLibrary) -> Unit,
    listState: LazyListState,
    screenEntry: Boolean = false,
    onVerticalMove: (String, TvHomeVerticalDirection) -> Unit = { _, _ -> },
) {
    if (libraries.isEmpty()) return
    val rowKey = "libraries"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            modifier = Modifier.tvRowBleed(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(6.dp),
        ) {
            itemsIndexed(
                libraries,
                key = { _, it -> it.id },
                contentType = { _, _ -> "library-card" },
            ) { index, library ->
                val targetId = tvHomeCardTargetId(rowKey, library.id)
                TvLibraryTile(
                    name = library.name,
                    collectionType = library.collectionType,
                    imageUrl = tvLibraryTileImageUrl(state.imageBaseUrl, state.imageAccessToken, library),
                    subtitle = library.itemCount?.let(strings::itemCount),
                    onClick = { onLibrary(library) },
                    onFocused = { focusMemory.remember("home", "libraries", library.id, horizontalIndex = index) },
                    tileModifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0, targetId)
                            .tvHomeVerticalFocus { direction -> onVerticalMove(library.id, direction) },
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

@Composable
private fun TvHomeSectionRow(
    section: HomeSection,
    strings: TvStrings,
    imageBaseUrl: String?,
    imageAccessToken: String?,
    focusMemory: TvFocusMemory,
    onItem: (JellyfinItem) -> Unit,
    onHomeLibrary: (String, String) -> Unit,
    onSeerrItem: (TvRoute.SeerrDetail) -> Unit,
    onPreviewFocus: (JellyfinItem, String) -> Unit,
    onPreviewBlur: (JellyfinItem, String) -> Unit,
    listState: LazyListState,
    screenEntry: Boolean = false,
    onVerticalMove: (String, JellyfinItem?, TvHomeVerticalDirection) -> Unit = { _, _, _ -> },
) {
    val rowId = "plugin:${section.id}"
    val focusKeeper = rememberTvRowFocusKeeper(listState, remember(section.items) { section.items.map { it.id } })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(section.title)
        LazyRow(
            state = listState,
            modifier = Modifier.tvRowBleed(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(6.dp),
        ) {
            itemsIndexed(
                section.items,
                key = { _, it -> it.id },
                contentType = { _, _ -> "media-card" },
            ) { index, item ->
                val targetId = tvHomeCardTargetId(rowId, item.id)
                val previewItem = item.tvStagePreviewItem()
                val cardText = item.jellyfinItem?.tvCardText(strings)
                val libraryDestination =
                    item.jellyfinItem?.let(::tvHomeJellyfinDestination) as? TvHomeJellyfinDestination.Library
                if (item.action == HomeSectionAction.JELLYFIN && libraryDestination != null) {
                    // Libraries look the same whether the Home Sections plugin or the built-in row lists them.
                    TvLibraryTile(
                        name = item.name,
                        collectionType = null,
                        imageUrl = resolveTvHomeSectionImageUrl(item, imageBaseUrl, imageAccessToken),
                        subtitle = null,
                        onClick = { onHomeLibrary(libraryDestination.libraryId, libraryDestination.title) },
                        onFocused = {
                            focusKeeper.onFocused(item.id)
                            focusMemory.remember("home", section.id, item.id, horizontalIndex = index)
                        },
                        onFocusChanged = { focused -> if (!focused) focusKeeper.onBlurred(item.id) },
                        tileModifier =
                            Modifier
                                .tvScreenEntryFocus(screenEntry && index == 0, targetId)
                                .tvHomeVerticalFocus { direction -> onVerticalMove(item.id, previewItem, direction) },
                        focusTargetId = targetId,
                        focusToNavigationRailOnLeft = index == 0,
                    )
                    return@itemsIndexed
                }
                TvMediaCard(
                    title = cardText?.title ?: item.name,
                    subtitle =
                        cardText?.subtitle ?: listOfNotNull(
                            item.productionYear?.toString(),
                            tvRatingLabel(item.communityRating),
                        ).joinToString("  •  ").ifBlank { null },
                    progress = cardText?.progress,
                    badge = item.jellyfinItem?.let { tvJellyfinCardBadge(it, strings) },
                    imageUrl = resolveTvHomeSectionImageUrl(item, imageBaseUrl, imageAccessToken),
                    artworkFit =
                        if (section.viewMode == HomeSectionViewMode.PORTRAIT ||
                            (
                                item.imageUrl.isNullOrBlank() &&
                                    item.jellyfinItem?.let(::resolveTvJellyfinArtwork)?.imageType == "Primary"
                            )
                        ) {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        } else {
                            TvMediaCardArtworkFit.CROP
                        },
                    onFocused = {
                        focusKeeper.onFocused(item.id)
                        focusMemory.remember("home", section.id, item.id, horizontalIndex = index)
                        previewItem?.let { onPreviewFocus(it, targetId) }
                    },
                    onFocusChanged = { focused ->
                        if (!focused) {
                            focusKeeper.onBlurred(item.id)
                            previewItem?.let { onPreviewBlur(it, targetId) }
                        }
                    },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0, targetId)
                            .tvHomeVerticalFocus { direction -> onVerticalMove(item.id, previewItem, direction) },
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = index == 0,
                    onClick = {
                        when (item.action) {
                            HomeSectionAction.JELLYFIN ->
                                item.jellyfinItem?.let { jellyfinItem ->
                                    when (val destination = tvHomeJellyfinDestination(jellyfinItem)) {
                                        is TvHomeJellyfinDestination.Detail -> onItem(destination.item)
                                        is TvHomeJellyfinDestination.Library ->
                                            onHomeLibrary(destination.libraryId, destination.title)
                                    }
                                }
                            HomeSectionAction.SEERR -> {
                                val tmdbId = item.seerrTmdbId ?: return@TvMediaCard
                                onSeerrItem(
                                    TvRoute.SeerrDetail(
                                        tmdbId = tmdbId,
                                        mediaType =
                                            dev.jellystack.core.jellyseerr.JellyseerrMediaType
                                                .from(item.seerrMediaType),
                                        title = item.name,
                                        overview = item.overview,
                                        releaseYear = item.productionYear?.toString(),
                                    ),
                                )
                            }
                            HomeSectionAction.INFORMATION -> item.jellyfinItem?.let(onItem)
                        }
                    },
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun TvLibraryScreen(
    route: TvRoute.Library,
    state: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onSelectLibrary: (String) -> Unit,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    homeSections: HomeSectionsState = HomeSectionsState.Unavailable,
    myListItems: List<JellyfinItem> = emptyList(),
    collectionType: String? = null,
    rememberedQuery: LibraryBrowseQuery = LibraryBrowseQuery.DEFAULT,
    onModeChanged: (TvLibraryMode) -> Unit = {},
    onQueryChanged: (LibraryBrowseQuery) -> Unit = {},
    onPlayItem: (JellyfinItem) -> Unit = {},
    onJumpToLetter: (String) -> Unit = {},
    onLetterJumpHandled: (Long) -> Unit = {},
    onLoadLatest: (JellyfinLibrary) -> Unit = {},
    cinematicModesEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    trailerPreviewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
    onPreviewFocus: (JellyfinItem, String) -> Unit = { _, _ -> },
    onPreviewClear: () -> Unit = {},
) {
    LaunchedEffect(route.libraryId) { route.libraryId?.let(onSelectLibrary) }
    if (route.libraryId == null) {
        TvLibraryLandingScreen(
            state = state,
            strings = strings,
            focusMemory = focusMemory,
            routeKey = route.focusRouteKey(state.browsePath.map { it.id }),
            onSelectLibrary = onSelectLibrary,
            onOpenItem = onOpenItem,
            onRetry = onRetry,
            modifier = modifier,
            onLoadLatest = onLoadLatest,
        )
        return
    }
    if (cinematicModesEnabled) {
        TvSelectedLibraryScreen(
            route = route,
            state = state,
            homeSections = homeSections,
            myListItems = myListItems,
            strings = strings,
            focusMemory = focusMemory,
            collectionType = collectionType,
            rememberedQuery = rememberedQuery,
            onModeChanged = onModeChanged,
            onQueryChanged = onQueryChanged,
            onOpenItem = onOpenItem,
            onOpenContainer = onOpenContainer,
            onPlayItem = onPlayItem,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = modifier,
            onJumpToLetter = onJumpToLetter,
            onLetterJumpHandled = onLetterJumpHandled,
            trailerPreviewState = trailerPreviewState,
            trailerPreviewEngine = trailerPreviewEngine,
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress,
            onPreviewFocus = onPreviewFocus,
            onPreviewClear = onPreviewClear,
        )
        return
    }
    val routeKey = route.focusRouteKey(state.browsePath.map { it.id })
    val gridState = rememberLazyGridState()
    val itemTargetIds = state.libraryItems.map { tvLibraryTargetId(it.id) }
    // The empty state of this screen has an action, and focus belongs on it.
    val terminalTarget =
        tvLibraryTerminalFocusTarget(
            libraryId = route.libraryId,
            itemCount = itemTargetIds.size,
            isLibraryLoading = state.isLibraryLoading,
            isPageLoading = state.isPageLoading,
            hasError = state.libraryErrorMessage != null,
        ).let { target -> if (target == TV_LIBRARY_EMPTY_TARGET) TV_LIBRARY_EMPTY_ACTION_TARGET else target }
    val locations = tvLibraryGridFocusLocations(itemTargetIds, terminalTarget)
    TvRouteFocusMaterializer(
        ownerId = "library-grid:$routeKey",
        targetIds = locations.keys,
        fallbackTargetIds = setOfNotNull(itemTargetIds.firstOrNull(), terminalTarget),
    ) { targetId ->
        locations[targetId]?.let { index ->
            gridState.scrollToItem(index)
            withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
            } ?: false
        } ?: false
    }
    LaunchedEffect(
        route.libraryId,
        state.libraryItems.size,
        state.isLibraryLoading,
        state.isPageLoading,
        state.endReached,
        state.libraryErrorMessage,
    ) {
        snapshotFlow {
            val lastGridIndex =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            (lastGridIndex - 1).coerceAtMost(state.libraryItems.lastIndex)
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (
                shouldLoadNextLibraryPage(
                    lastVisibleIndex = lastVisibleIndex,
                    totalItemCount = state.libraryItems.size,
                    isLibraryLoading = state.isLibraryLoading,
                    isPageLoading = state.isPageLoading,
                    endReached = state.endReached,
                    hasError = state.libraryErrorMessage != null,
                )
            ) {
                onLoadMore()
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                route.title ?: strings.library,
                modifier = Modifier.tvHeading(),
                color = TvText,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        gridItemsIndexed(
            state.libraryItems,
            key = { _, item -> item.id },
            contentType = { _, _ -> "media-card" },
        ) { index, item ->
            val targetId = tvLibraryTargetId(item.id)
            val artwork = resolveTvJellyfinArtwork(item)
            TvMediaCard(
                title = item.name.ifBlank { strings.messages.untitled },
                subtitle = item.subtitleText(),
                imageUrl =
                    jellyfinImageUrl(
                        state.imageBaseUrl,
                        state.imageAccessToken,
                        artwork,
                    ),
                artworkFit =
                    if (artwork?.imageType == "Primary") {
                        TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                    } else {
                        TvMediaCardArtworkFit.CROP
                    },
                onClick = { if (item.isBrowseContainer()) onOpenContainer(item) else onOpenItem(item) },
                onFocused = { focusMemory.remember(routeKey, "items", item.id, index + 1, index) },
                modifier =
                    Modifier
                        .tvScreenEntryFocus(index == 0, targetId),
                focusTargetId = targetId,
                fillWidth = true,
                focusToNavigationRailOnLeft = isTvGridLeftEdge(index, 4),
            )
        }
        if ((state.isLibraryLoading || state.isPageLoading) && state.libraryItems.isEmpty()) {
            tvSkeletonGridCells(strings.loading, TV_SKELETON_GRID_CELLS, keyPrefix = "library-skeleton")
        } else if (state.isLibraryLoading || state.isPageLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TvPurple)
                }
            }
        } else if (state.libraryErrorMessage != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.libraryLoadFailed, color = TvError, fontSize = 18.sp)
                    TvActionButton(
                        strings.retry,
                        when (tvLibraryRetryAction(state.libraryErrorKind)) {
                            TvLibraryRetryAction.REFRESH -> onRetry
                            TvLibraryRetryAction.NEXT_PAGE -> onLoadMore
                        },
                        modifier =
                            Modifier
                                .tvScreenEntryFocus(state.libraryItems.isEmpty(), TV_LIBRARY_RETRY_TARGET)
                                .width(220.dp),
                        focusToNavigationRailOnLeft = true,
                        focusTargetId = TV_LIBRARY_RETRY_TARGET,
                    )
                }
            }
        } else if (state.libraryItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TvEmptyState(
                    icon = Icons.Default.VideoLibrary,
                    title = strings.emptyStates.nothingYet,
                    hint = strings.emptyStates.libraryHint,
                    action = TvEmptyStateAction(strings.retry, TV_LIBRARY_EMPTY_ACTION_TARGET, onRetry),
                )
            }
        }
    }
}

internal fun tvLibraryTerminalFocusTarget(
    libraryId: String?,
    itemCount: Int,
    isLibraryLoading: Boolean,
    isPageLoading: Boolean,
    hasError: Boolean,
): String? =
    when {
        libraryId == null && itemCount == 0 -> TV_LIBRARY_EMPTY_TARGET
        libraryId != null && (isLibraryLoading || isPageLoading) && itemCount == 0 -> TV_LIBRARY_LOADING_TARGET
        libraryId != null && hasError -> TV_LIBRARY_RETRY_TARGET
        libraryId != null && itemCount == 0 -> TV_LIBRARY_EMPTY_TARGET
        else -> null
    }

internal enum class TvLibraryRetryAction { REFRESH, NEXT_PAGE }

internal fun tvLibraryRetryAction(errorKind: LibraryLoadErrorKind?): TvLibraryRetryAction =
    if (errorKind == LibraryLoadErrorKind.NEXT_PAGE) {
        TvLibraryRetryAction.NEXT_PAGE
    } else {
        TvLibraryRetryAction.REFRESH
    }

internal fun tvLibraryGridFocusLocations(
    itemTargetIds: List<String>,
    terminalTarget: String?,
): Map<String, Int> =
    buildMap {
        itemTargetIds.forEachIndexed { index, targetId -> put(targetId, index + 1) }
        terminalTarget?.let { put(it, itemTargetIds.size + 1) }
    }

internal fun shouldLoadNextLibraryPage(
    lastVisibleIndex: Int,
    totalItemCount: Int,
    isLibraryLoading: Boolean,
    isPageLoading: Boolean,
    endReached: Boolean,
    hasError: Boolean,
    columnCount: Int = 4,
): Boolean {
    val pagingBlocked =
        listOf(
            totalItemCount <= 0,
            lastVisibleIndex < 0,
            isLibraryLoading,
            isPageLoading,
            endReached,
            hasError,
        ).any { it }
    if (pagingBlocked) return false
    val prefetchItemCount = columnCount.coerceAtLeast(1) * 2
    val thresholdIndex = (totalItemCount - 1 - prefetchItemCount).coerceAtLeast(0)
    return lastVisibleIndex >= thresholdIndex
}

internal data class TvRetryFocusRequest(
    val revision: Long,
    val preferredTargetId: String,
)

private data class TvDiscoverRetryFocusRequest(
    val revision: Long,
    val initialTargetId: String,
)

@Composable
internal fun TvRetryFocusRecovery(request: TvRetryFocusRequest?) {
    val focusContext = LocalTvFocusContext.current ?: return
    LaunchedEffect(request) {
        request ?: return@LaunchedEffect
        withFrameNanos { }
        focusContext.coordinator.restoreFocus(
            routeKey = focusContext.routeKey,
            preferredTargetId = request.preferredTargetId,
            requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
        )
    }
}

@Composable
private fun TvDiscoverRetryFocusRecovery(
    request: TvDiscoverRetryFocusRequest?,
    currentTargetId: String,
    isRefreshing: Boolean,
    onCompleted: (Long) -> Unit,
) {
    val focusContext = LocalTvFocusContext.current ?: return
    var transitionObserved by remember(request?.revision) { mutableStateOf(false) }
    LaunchedEffect(request?.revision, currentTargetId, isRefreshing) {
        val activeRequest = request ?: return@LaunchedEffect
        if (isRefreshing || currentTargetId != activeRequest.initialTargetId) {
            transitionObserved = true
        }
        if (!transitionObserved) return@LaunchedEffect
        withFrameNanos { }
        val restoration =
            focusContext.coordinator.restoreFocus(
                routeKey = focusContext.routeKey,
                preferredTargetId = currentTargetId,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        if (!isRefreshing && restoration is TvFocusRestoration.Focused) {
            onCompleted(activeRequest.revision)
        }
    }
}

@Composable
@Suppress("NestedBlockDepth") // Lazy-list branches mirror the mutually exclusive Discover presentation states.
internal fun TvDiscoverScreen(
    recommendations: JellyseerrRecommendationsState,
    requests: JellyseerrRequestsState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onItem: (JellyseerrSearchItem) -> Unit,
    onConnectSeerr: () -> Unit,
    onRetry: () -> Unit,
    isSaved: (JellyseerrSearchItem) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val availability = tvDiscoverAvailability(recommendations)
    val content = availability as? TvDiscoverAvailability.Content
    val ready = content?.state
    var retryFocusRequest by remember { mutableStateOf<TvDiscoverRetryFocusRequest?>(null) }
    val firstPopulatedRail =
        ready?.let { state ->
            JellyseerrRecommendationRail.entries.firstOrNull { rail -> state.rails[rail]?.items?.isNotEmpty() == true }
        }
    val populatedRails =
        ready
            ?.let { state ->
                JellyseerrRecommendationRail.entries.filter { rail -> state.rails[rail]?.items?.isNotEmpty() == true }
            }.orEmpty()
    val requestItems = (requests as? JellyseerrRequestsState.Ready)?.requests.orEmpty()
    // Rebuilt when the recommendations, requests or strings change, or when a title is saved or removed: isSaved reads
    // state, so the derived value follows it. A focus change in the rows does not come through here.
    val currentIsSaved by rememberUpdatedState(isSaved)
    val cinematicRows =
        remember(ready, requestItems, strings) {
            derivedStateOf { ready?.let { buildTvDiscoverRows(it, requestItems, strings, currentIsSaved) } }
        }.value
    val cinematicTargetId = cinematicRows?.initialTargetId
    if (cinematicRows != null && cinematicTargetId != null) {
        TvCinematicDiscoverContent(
            discover = cinematicRows,
            hasPartialFailure = content?.hasRailFailures == true,
            strings = strings,
            focusMemory = focusMemory,
            onItem = onItem,
        )
        TvDiscoverRetryFocusRecovery(
            request = retryFocusRequest,
            currentTargetId = cinematicTargetId,
            isRefreshing = false,
            onCompleted = { revision ->
                if (retryFocusRequest?.revision == revision) retryFocusRequest = null
            },
        )
        return
    }
    val outerState = rememberLazyListState()
    val rowStates = rememberTvLazyRowStates(populatedRails.map { it.name } + listOf("requests"))
    val discoverLocations =
        buildMap {
            var outerIndex = 1
            when (availability) {
                TvDiscoverAvailability.MissingConnection -> {
                    put(TV_DISCOVER_CONNECT_TARGET, TvLazyFocusLocation(outerIndex))
                    outerIndex++
                }
                TvDiscoverAvailability.Loading -> {
                    put(TV_DISCOVER_LOADING_TARGET, TvLazyFocusLocation(outerIndex))
                    outerIndex++
                }
                is TvDiscoverAvailability.Failure -> {
                    put(TV_DISCOVER_RETRY_TARGET, TvLazyFocusLocation(outerIndex))
                    outerIndex++
                }
                is TvDiscoverAvailability.Content -> {
                    if (availability.hasRailFailures) {
                        outerIndex++
                    }
                    populatedRails.forEach { rail ->
                        availability.state.rails[rail]?.items.orEmpty().forEachIndexed { index, item ->
                            put(
                                tvDiscoverItemTargetId(rail.name, "${item.mediaType}:${item.tmdbId}"),
                                TvLazyFocusLocation(outerIndex, rail.name, index),
                            )
                        }
                        outerIndex++
                    }
                }
            }
            if (requestItems.isNotEmpty()) {
                requestItems.forEachIndexed { index, request ->
                    put(
                        tvDiscoverItemTargetId("requests", request.id.toString()),
                        TvLazyFocusLocation(outerIndex, "requests", index),
                    )
                }
            } else if (
                availability is TvDiscoverAvailability.Content &&
                !availability.hasRailFailures &&
                firstPopulatedRail == null
            ) {
                put(TV_DISCOVER_EMPTY_ACTION_TARGET, TvLazyFocusLocation(outerIndex))
            }
        }
    val discoverFallback =
        when (availability) {
            TvDiscoverAvailability.MissingConnection -> TV_DISCOVER_CONNECT_TARGET
            TvDiscoverAvailability.Loading -> TV_DISCOVER_LOADING_TARGET
            is TvDiscoverAvailability.Failure -> TV_DISCOVER_RETRY_TARGET
            is TvDiscoverAvailability.Content ->
                firstPopulatedRail
                    ?.let { rail ->
                        availability.state.rails[rail]?.items?.firstOrNull()?.let {
                            tvDiscoverItemTargetId(rail.name, "${it.mediaType}:${it.tmdbId}")
                        }
                    } ?: requestItems.firstOrNull()?.let {
                    tvDiscoverItemTargetId("requests", it.id.toString())
                } ?: TV_DISCOVER_EMPTY_ACTION_TARGET
        }
    TvRouteFocusMaterializer(
        ownerId = "discover-lists",
        targetIds = discoverLocations.keys,
        fallbackTargetIds = setOf(discoverFallback),
    ) { targetId -> discoverLocations[targetId]?.let { materializeTvLazyTarget(outerState, rowStates, it) } ?: false }
    val discoverIsRefreshing =
        availability is TvDiscoverAvailability.Loading ||
            ready?.rails?.values?.any { rail -> rail.isLoading } == true
    TvDiscoverRetryFocusRecovery(
        request = retryFocusRequest,
        currentTargetId = discoverFallback,
        isRefreshing = discoverIsRefreshing,
        onCompleted = { revision ->
            if (retryFocusRequest?.revision == revision) retryFocusRequest = null
        },
    )

    fun retryAndRestoreFocus() {
        val initialTargetId = discoverFallback
        onRetry()
        retryFocusRequest =
            TvDiscoverRetryFocusRequest(
                revision = (retryFocusRequest?.revision ?: 0L) + 1L,
                initialTargetId = initialTargetId,
            )
    }
    LazyColumn(
        state = outerState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item("header") {
            Text(strings.discover, modifier = Modifier.tvHeading(), color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        when (availability) {
            TvDiscoverAvailability.MissingConnection ->
                item("connect") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(strings.connectSeerrPrompt, color = TvTextMuted)
                        TvActionButton(
                            strings.connectSeerr,
                            onConnectSeerr,
                            modifier = Modifier.tvScreenEntryFocus(focusTargetId = TV_DISCOVER_CONNECT_TARGET),
                            primary = true,
                            focusToNavigationRailOnLeft = true,
                            focusTargetId = TV_DISCOVER_CONNECT_TARGET,
                        )
                    }
                }
            TvDiscoverAvailability.Loading ->
                item("loading") { TvSkeletonRows(strings.loading) }
            is TvDiscoverAvailability.Failure ->
                item("error") {
                    TvSearchSourceFailure(
                        message = strings.discoverLoadFailed,
                        retryLabel = strings.retry,
                        focusTargetId = TV_DISCOVER_RETRY_TARGET,
                        onRetry = ::retryAndRestoreFocus,
                    )
                }
            is TvDiscoverAvailability.Content -> {
                if (availability.hasRailFailures) {
                    item("partial-error") {
                        TvStatusAnchor(strings.discoverLoadFailed)
                    }
                }
                JellyseerrRecommendationRail.entries.forEach { rail ->
                    val railState = availability.state.rails[rail]
                    if (railState != null && railState.items.isNotEmpty()) {
                        item(rail.name) {
                            TvSeerrRow(
                                rail.label(strings),
                                strings,
                                railState.items,
                                focusMemory,
                                "discover",
                                onItem,
                                listState = rowStates.getValue(rail.name),
                                focusTargetId = { id -> tvDiscoverItemTargetId(rail.name, id) },
                                screenEntry = rail == firstPopulatedRail,
                            )
                        }
                    }
                }
            }
        }
        if (requestItems.isNotEmpty()) {
            item("requests") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSectionTitle(strings.requests)
                    LazyRow(
                        state = rowStates.getValue("requests"),
                        modifier = Modifier.tvRowBleed(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = tvBleedRowPadding(6.dp),
                    ) {
                        itemsIndexed(
                            requestItems,
                            key = { _, request -> request.id },
                            contentType = { _, _ -> "media-card" },
                        ) { index, request ->
                            val item = request.toSearchItem(strings.messages::untitledRequest)
                            val targetId = tvDiscoverItemTargetId("requests", request.id.toString())
                            TvMediaCard(
                                title = request.titleOrUntitled(strings.messages::untitledRequest),
                                subtitle =
                                    listOfNotNull(
                                        request.requestStatus.label(strings),
                                        request.availability.standard.label(strings),
                                    ).distinct().joinToString(TV_CARD_SEPARATOR),
                                badge = tvSeerrCardBadge(request.availability, listOf(request), strings),
                                imageUrl = tmdbImageUrl(request.backdropPath ?: request.posterPath, request.backdropPath != null),
                                artworkFit =
                                    if (request.backdropPath == null && request.posterPath != null) {
                                        TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                                    } else {
                                        TvMediaCardArtworkFit.CROP
                                    },
                                onClick = { item?.let(onItem) },
                                modifier =
                                    Modifier.tvScreenEntryFocus(
                                        availability is TvDiscoverAvailability.Content &&
                                            firstPopulatedRail == null &&
                                            index == 0,
                                        targetId,
                                    ),
                                focusTargetId = targetId,
                                focusToNavigationRailOnLeft = index == 0,
                            )
                        }
                    }
                }
            }
        } else if (
            availability is TvDiscoverAvailability.Content &&
            !availability.hasRailFailures &&
            firstPopulatedRail == null
        ) {
            item("empty") {
                TvEmptyState(
                    icon = Icons.Default.Explore,
                    title = strings.emptyStates.nothingYet,
                    hint = strings.emptyStates.discoverHint,
                    action = TvEmptyStateAction(strings.retry, TV_DISCOVER_EMPTY_ACTION_TARGET, ::retryAndRestoreFocus),
                )
            }
        }
    }
}

internal data class TvSeerrCardArtwork(
    val path: String?,
    val isBackdrop: Boolean,
    val fit: TvMediaCardArtworkFit,
)

internal fun tvSeerrCardArtwork(
    posterPath: String?,
    backdropPath: String?,
): TvSeerrCardArtwork =
    TvSeerrCardArtwork(
        path = backdropPath ?: posterPath,
        isBackdrop = backdropPath != null,
        fit =
            if (backdropPath == null && posterPath != null) {
                TvMediaCardArtworkFit.CONTAIN_PORTRAIT
            } else {
                TvMediaCardArtworkFit.CROP
            },
    )

@Composable
internal fun TvSeerrRow(
    title: String,
    strings: TvStrings,
    items: List<JellyseerrSearchItem>,
    focusMemory: TvFocusMemory,
    routeKey: String,
    onItem: (JellyseerrSearchItem) -> Unit,
    listState: LazyListState,
    focusTargetId: (String) -> String,
    screenEntry: Boolean = false,
    edgePadding: Dp = 6.dp,
    bleedStart: Dp = TvLayoutTokens.ContentStart,
    onFocused: (JellyseerrSearchItem) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            modifier = Modifier.tvRowBleed(start = bleedStart),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(edgePadding, start = bleedStart),
        ) {
            itemsIndexed(
                items,
                key = { _, item -> "${item.mediaType}:${item.tmdbId}" },
                contentType = { _, _ -> "media-card" },
            ) { index, item ->
                val id = "${item.mediaType}:${item.tmdbId}"
                val targetId = focusTargetId(id)
                val artwork = tvSeerrCardArtwork(item.posterPath, item.backdropPath)
                TvMediaCard(
                    title = item.title,
                    subtitle = item.releaseYear,
                    badge = tvSeerrCardBadge(item.availability, item.requests, strings),
                    imageUrl = tmdbImageUrl(artwork.path, backdrop = artwork.isBackdrop),
                    artworkFit = artwork.fit,
                    onClick = { onItem(item) },
                    onFocused = {
                        focusMemory.remember(routeKey, title, id, horizontalIndex = index)
                        onFocused(item)
                    },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(screenEntry && index == 0, targetId),
                    focusTargetId = targetId,
                    focusToNavigationRailOnLeft = index == 0,
                )
            }
        }
    }
}

internal fun JellyseerrSearchItem.toTvRoute(): TvRoute.SeerrDetail =
    TvRoute.SeerrDetail(tmdbId, mediaType, title, overview, posterPath, backdropPath, releaseYear, tvdbId)

internal fun JellyfinItem.subtitleText(): String? =
    listOfNotNull(
        productionYear?.toString(),
        if (type.equals("Episode", true)) tvEpisodeCode(parentIndexNumber, indexNumber) else null,
        tvRatingLabel(communityRating),
    ).joinToString("  •  ").ifBlank { null }
