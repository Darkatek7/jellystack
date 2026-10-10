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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
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
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.profile.MediaIdentity
import dev.jellystack.core.profile.MyListEntry
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

internal const val TV_HOME_HERO_HEIGHT_DP = 300

/** Row title (24 dp), title spacing (8 dp), and row content padding (6 dp) above each home card. */
private val TV_HOME_ROW_CARD_OFFSET = 38.dp
internal const val TV_FOCUS_MATERIALIZATION_TIMEOUT_MS = 1_000L

private data class TvLazyFocusLocation(
    val verticalIndex: Int,
    val rowId: String? = null,
    val horizontalIndex: Int? = null,
)

@Composable
private fun rememberTvLazyRowStates(rowIds: List<String>): Map<String, LazyListState> {
    val states = linkedMapOf<String, LazyListState>()
    for (rowId in rowIds) {
        key(rowId) { states[rowId] = rememberLazyListState() }
    }
    return states
}

private suspend fun materializeTvLazyTarget(
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
    val focusRows =
        remember(visibleSections, homeSections, state.continueWatching, state.nextUp, state.libraries, myList) {
            buildList {
                var lazyColumnIndex = 0
                if (myList.isNotEmpty()) {
                    add(
                        TvHomeFocusRow(
                            id = "my-list",
                            lazyColumnIndex = lazyColumnIndex++,
                            itemIds = myList.map { it.identity.tvMyListKey() },
                            landscape = true,
                        ),
                    )
                }
                if (homeSections is HomeSectionsState.Ready) {
                    visibleSections.forEach { section ->
                        add(
                            TvHomeFocusRow(
                                id = "plugin:${section.id}",
                                lazyColumnIndex = lazyColumnIndex++,
                                itemIds = section.items.map { it.id },
                                landscape = true,
                            ),
                        )
                    }
                } else {
                    if (state.continueWatching.isNotEmpty()) {
                        add(TvHomeFocusRow("continue", lazyColumnIndex++, state.continueWatching.map { it.id }, landscape = true))
                    }
                    if (state.nextUp.isNotEmpty()) {
                        add(TvHomeFocusRow("next", lazyColumnIndex++, state.nextUp.map { it.id }, landscape = true))
                    }
                    if (state.libraries.isNotEmpty()) {
                        add(TvHomeFocusRow("libraries", lazyColumnIndex, state.libraries.map { it.id }, landscape = true))
                    }
                }
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
    LaunchedEffect(heroHasFocus, homeRowIds) {
        // Rows that load while the spotlight has focus can land above the visible ones, and the list keeps
        // its first visible row; the rows under the spotlight start at the first row instead.
        if (heroHasFocus) homeListState.scrollToItem(0)
    }
    val errorLazyColumnIndex =
        (if (myList.isNotEmpty()) 1 else 0) +
            if (homeSections is HomeSectionsState.Ready) {
                visibleSections.size
            } else {
                (if (state.continueWatching.isNotEmpty()) 1 else 0) +
                    (if (state.nextUp.isNotEmpty()) 1 else 0) +
                    1
            }
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
    val verticalFocusCoordinator = remember { TvHomeVerticalFocusCoordinator(focusRows) }
    var pendingFocusMove by remember { mutableStateOf<TvHomeFocusMove?>(null) }
    LaunchedEffect(focusRows) {
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
    var focusedStageItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var focusedStagePresentationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(candidateIds) {
        val selectedId = reconcileTvHomeCarouselSelection(candidateIds, carouselState.selectedId)
        if (selectedId != carouselState.selectedId) {
            carouselState = carouselState.copy(selectedId = selectedId)
        }
    }
    var heroInteraction by remember { mutableStateOf(0) }
    val autoAdvanceHero =
        shouldAutoAdvanceTvHomeHero(
            enabled = spotlightAutoAdvance,
            candidateCount = candidateIds.size,
            reducedMotion = LocalTvFocusAppearance.current.reducedMotion,
            cardStaged = focusedStageItem != null,
            trailerActive = trailerPreviewState is TvTrailerPreviewState.Armed || trailerPreviewState is TvTrailerPreviewState.Playing,
        )
    LaunchedEffect(autoAdvanceHero, candidateIds, carouselState.selectedId, heroInteraction) {
        if (!autoAdvanceHero) return@LaunchedEffect
        delay(TV_HOME_HERO_AUTO_ADVANCE_MS)
        carouselState = carouselState.copy(selectedId = nextTvHomeAutoAdvanceId(candidateIds, carouselState.selectedId))
    }
    val openNavigationRail = LocalTvNavigationRailOpener.current
    val heroModel =
        heroCandidates.getOrNull(spotlightIndex)?.let { candidate ->
            val stageItem = focusedStageItem ?: candidate.actionItem
            TvHomeHeroModel(
                stageItem = stageItem,
                stagePresentationId =
                    focusedStagePresentationId
                        ?: (trailerPreviewState as? TvTrailerPreviewState.Playing)
                            ?.request
                            ?.takeIf { it.target.itemId == stageItem.id }
                            ?.presentationId,
                mode = heroPresentation.mode,
                page = TvHomeHeroPage(index = spotlightIndex, count = heroCandidates.size),
                showCarouselContext = focusedStageItem == null,
                imageBaseUrl = state.imageBaseUrl,
                imageAccessToken = state.imageAccessToken,
            )
        }
    val heroTrailer =
        TvHomeHeroTrailer(
            state = trailerPreviewState,
            soundEnabled = previewSoundEnabled,
            progress = previewProgress,
            surface = { surfaceModifier -> TvTrailerPreviewSurface(trailerPreviewEngine, surfaceModifier) },
        )
    Box(
        modifier.fillMaxSize().onPreviewKeyEvent {
            // Any remote input restarts the spotlight timer.
            heroInteraction += 1
            false
        },
    ) {
        heroModel?.let { TvHomeBackdrop(it, heroTrailer) }
        Box(Modifier.fillMaxWidth().onFocusChanged { heroHasFocus = it.hasFocus }) {
            if (heroModel != null) {
                TvHeroCarousel(
                    model = heroModel,
                    trailer = heroTrailer,
                    callbacks =
                        TvHomeHeroCallbacks(
                            onPlay = { onPlayItem(heroModel.stageItem) },
                            onDetails = { onItem(heroModel.stageItem) },
                            onVerticalMove = { direction -> onVerticalMove(TvHomeFocusOrigin.Hero, direction, null) },
                            onHeroFocused = {
                                // The hero shows its own spotlight again, so Play and Details act on it.
                                focusedStageItem = null
                                focusedStagePresentationId = null
                            },
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
        TvHomeClock(rememberTvClockLabel(), Modifier.align(Alignment.TopEnd))
        val rowScrollSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides rememberTvRowAlignedBringIntoViewSpec(TV_HOME_ROW_CARD_OFFSET),
        ) {
            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize().padding(top = TV_CINEMATIC_ROWS_TOP),
                contentPadding = TvHomeRowsPadding,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (myList.isNotEmpty()) {
                    item(key = "my-list", contentType = "media-row") {
                        TvRowScroll(rowScrollSpec) {
                            TvMyListRow(
                                entries = myList,
                                state = state,
                                strings = strings,
                                focusMemory = focusMemory,
                                listState = rowListStates.getValue("my-list"),
                                onEntry = onMyListEntry,
                                onPreviewFocus = { item, presentationId ->
                                    focusedStageItem = item
                                    focusedStagePresentationId = presentationId
                                    onPreviewFocus(TvTrailerPreviewOwner.CARD, item, presentationId)
                                },
                                onPreviewBlur = { item, presentationId ->
                                    onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                },
                                onVerticalMove = { entry, direction ->
                                    onVerticalMove(
                                        TvHomeFocusOrigin.Row("my-list", entry.identity.tvMyListKey()),
                                        direction,
                                        entry.jellyfinItem,
                                    )
                                },
                            )
                        }
                    }
                }
                when (homeSections) {
                    is HomeSectionsState.Ready -> {
                        items(
                            visibleSections,
                            key = { "plugin:${it.id}" },
                            contentType = { "media-row" },
                        ) { section ->
                            val rowId = "plugin:${section.id}"
                            TvRowScroll(rowScrollSpec) {
                                TvHomeSectionRow(
                                    section = section,
                                    strings = strings,
                                    imageBaseUrl = homeSections.imageBaseUrl,
                                    imageAccessToken = homeSections.imageAccessToken,
                                    focusMemory = focusMemory,
                                    onItem = onItem,
                                    onSeerrItem = onSeerrItem,
                                    onPreviewFocus = { item, presentationId ->
                                        focusedStageItem = item
                                        focusedStagePresentationId = presentationId
                                        onPreviewFocus(TvTrailerPreviewOwner.CARD, item, presentationId)
                                    },
                                    onPreviewBlur = { item, presentationId ->
                                        onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                    },
                                    onHomeLibrary = onHomeLibrary,
                                    listState = rowListStates.getValue(rowId),
                                    onVerticalMove = { itemId, item, direction ->
                                        onVerticalMove(TvHomeFocusOrigin.Row(rowId, itemId), direction, item)
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        if (state.continueWatching.isNotEmpty()) {
                            item("continue") {
                                TvRowScroll(rowScrollSpec) {
                                    TvJellyfinRow(
                                        strings.continueWatching,
                                        state.continueWatching,
                                        state,
                                        strings,
                                        focusMemory,
                                        onItem,
                                        onPreviewFocus = { item, presentationId ->
                                            focusedStageItem = item
                                            focusedStagePresentationId = presentationId
                                            onPreviewFocus(TvTrailerPreviewOwner.CARD, item, presentationId)
                                        },
                                        onPreviewBlur = { item, presentationId ->
                                            onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                        },
                                        listState = rowListStates.getValue("continue"),
                                        focusTargetId = { itemId -> tvHomeCardTargetId("continue", itemId) },
                                        onVerticalMove = { item, direction ->
                                            onVerticalMove(TvHomeFocusOrigin.Row("continue", item.id), direction, item)
                                        },
                                    )
                                }
                            }
                        }
                        if (state.nextUp.isNotEmpty()) {
                            item("next") {
                                TvRowScroll(rowScrollSpec) {
                                    TvJellyfinRow(
                                        strings.nextUp,
                                        state.nextUp,
                                        state,
                                        strings,
                                        focusMemory,
                                        onItem,
                                        onPreviewFocus = { item, presentationId ->
                                            focusedStageItem = item
                                            focusedStagePresentationId = presentationId
                                            onPreviewFocus(TvTrailerPreviewOwner.CARD, item, presentationId)
                                        },
                                        onPreviewBlur = { item, presentationId ->
                                            onPreviewBlur(TvTrailerPreviewOwner.CARD, item, presentationId)
                                        },
                                        listState = rowListStates.getValue("next"),
                                        focusTargetId = { itemId -> tvHomeCardTargetId("next", itemId) },
                                        onVerticalMove = { item, direction ->
                                            onVerticalMove(TvHomeFocusOrigin.Row("next", item.id), direction, item)
                                        },
                                    )
                                }
                            }
                        }
                        item("libraries") {
                            TvRowScroll(rowScrollSpec) {
                                TvLibraryRow(
                                    state.libraries,
                                    state,
                                    strings.myMedia,
                                    strings,
                                    focusMemory,
                                    onLibrary,
                                    listState = rowListStates.getValue("libraries"),
                                    onVerticalMove = { libraryId, direction ->
                                        onVerticalMove(TvHomeFocusOrigin.Row("libraries", libraryId), direction, null)
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
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
private fun TvJellyfinRow(
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
    onVerticalMove: (JellyfinItem, TvHomeVerticalDirection) -> Unit = { _, _ -> },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(edgePadding),
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
                    watched = displayItem.isPlayed,
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
                        focusMemory.remember(routeKey, title, item.id, horizontalIndex = index)
                        onPreviewFocus(item, targetId)
                    },
                    onFocusChanged = { focused -> if (!focused) onPreviewBlur(item, targetId) },
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(
                libraries,
                key = { _, it -> it.id },
                contentType = { _, _ -> "library-card" },
            ) { index, library ->
                val targetId = tvHomeCardTargetId(rowKey, library.id)
                TvMediaCard(
                    title = library.name,
                    subtitle = library.itemCount?.let(strings::itemCount),
                    imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, library.id, library.primaryImageTag),
                    artworkFit = TvMediaCardArtworkFit.CONTAIN_PORTRAIT,
                    onClick = { onLibrary(library) },
                    onFocused = { focusMemory.remember("home", "libraries", library.id, horizontalIndex = index) },
                    modifier =
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(section.title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(6.dp),
        ) {
            itemsIndexed(
                section.items,
                key = { _, it -> it.id },
                contentType = { _, _ -> "media-card" },
            ) { index, item ->
                val targetId = tvHomeCardTargetId(rowId, item.id)
                val previewItem = item.jellyfinItem?.takeIf { item.action == HomeSectionAction.JELLYFIN }
                val cardText = item.jellyfinItem?.tvCardText(strings)
                TvMediaCard(
                    title = cardText?.title ?: item.name,
                    subtitle =
                        cardText?.subtitle ?: listOfNotNull(
                            item.productionYear?.toString(),
                            tvRatingLabel(item.communityRating),
                        ).joinToString("  •  ").ifBlank { null },
                    progress = cardText?.progress,
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
                        focusMemory.remember("home", section.id, item.id, horizontalIndex = index)
                        previewItem?.let { onPreviewFocus(it, targetId) }
                    },
                    onFocusChanged = { focused -> if (!focused) previewItem?.let { onPreviewBlur(it, targetId) } },
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
    onToggleFavorite: (JellyfinItem) -> Unit = {},
    onTogglePlayed: (JellyfinItem, Boolean) -> Unit = { _, _ -> },
    cinematicModesEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    trailerPreviewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
    onPreviewFocus: (JellyfinItem, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(route.libraryId) { route.libraryId?.let(onSelectLibrary) }
    if (route.libraryId != null && cinematicModesEnabled) {
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
            onToggleFavorite = onToggleFavorite,
            onTogglePlayed = onTogglePlayed,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            modifier = modifier,
            trailerPreviewState = trailerPreviewState,
            trailerPreviewEngine = trailerPreviewEngine,
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress,
            onPreviewFocus = onPreviewFocus,
        )
        return
    }
    val routeKey = route.focusRouteKey(state.browsePath.map { it.id })
    val gridState = rememberLazyGridState()
    val itemTargetIds =
        if (route.libraryId == null) {
            state.libraries.map { tvLibraryTargetId(it.id, "libraries") }
        } else {
            state.libraryItems.map { tvLibraryTargetId(it.id) }
        }
    val terminalTarget =
        tvLibraryTerminalFocusTarget(
            libraryId = route.libraryId,
            itemCount = itemTargetIds.size,
            isLibraryLoading = state.isLibraryLoading,
            isPageLoading = state.isPageLoading,
            hasError = state.libraryErrorMessage != null,
        )
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
        if (route.libraryId == null) return@LaunchedEffect
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
        if (route.libraryId == null) {
            gridItemsIndexed(
                state.libraries,
                key = { _, library -> library.id },
                contentType = { _, _ -> "library-card" },
            ) { index, library ->
                val targetId = tvLibraryTargetId(library.id, "libraries")
                TvMediaCard(
                    title = library.name,
                    subtitle = library.itemCount?.let(strings::itemCount),
                    imageUrl = jellyfinImageUrl(state.imageBaseUrl, state.imageAccessToken, library.id, library.primaryImageTag),
                    artworkFit = TvMediaCardArtworkFit.CONTAIN_PORTRAIT,
                    onClick = { onSelectLibrary(library.id) },
                    onFocused = { focusMemory.remember(routeKey, "libraries", library.id, index + 1, index) },
                    modifier =
                        Modifier
                            .tvScreenEntryFocus(index == 0, targetId),
                    focusTargetId = targetId,
                    fillWidth = true,
                    focusToNavigationRailOnLeft = isTvGridLeftEdge(index, 4),
                )
            }
            if (state.libraries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TvFocusPlaceholder(
                        if (state.isInitialLoading) strings.loading else strings.noResults,
                        TV_LIBRARY_EMPTY_TARGET,
                    )
                }
            }
        } else {
            gridItemsIndexed(
                state.libraryItems,
                key = { _, item -> item.id },
                contentType = { _, _ -> "media-card" },
            ) { index, item ->
                val targetId = tvLibraryTargetId(item.id)
                val artwork = resolveTvJellyfinArtwork(item)
                TvMediaCard(
                    title = item.name,
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
            if (state.isLibraryLoading || state.isPageLoading) {
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
                        Text(strings.libraryLoadFailed, color = Color(0xFFFFA59E), fontSize = 18.sp)
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
                    TvFocusPlaceholder(strings.noResults, TV_LIBRARY_EMPTY_TARGET)
                }
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

private data class TvRetryFocusRequest(
    val revision: Long,
    val preferredTargetId: String,
)

private data class TvDiscoverRetryFocusRequest(
    val revision: Long,
    val initialTargetId: String,
)

@Composable
private fun TvRetryFocusRecovery(request: TvRetryFocusRequest?) {
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
internal fun TvSearchScreen(
    searchState: TvSearchUiState = TvSearchUiState(),
    homeState: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onQueryChanged: (String) -> Unit,
    onSourceChanged: (TvSearchSource) -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onEnterBrowseMode: () -> Unit = {},
    onRetryJellyfin: () -> Unit,
    onRetrySeerr: () -> Unit,
    onVoiceSearch: () -> Unit = {},
    onJellyfinItem: (JellyfinItem) -> Unit,
    onSeerrItem: (JellyseerrSearchItem) -> Unit,
    onPlayJellyfin: (JellyfinItem) -> Unit = onJellyfinItem,
    onToggleJellyfinSaved: ((JellyfinItem) -> Unit)? = null,
    onToggleJellyfinPlayed: ((JellyfinItem, Boolean) -> Unit)? = null,
    onToggleSeerrSaved: ((JellyseerrSearchItem) -> Unit)? = null,
    isJellyfinSaved: (JellyfinItem) -> Boolean = { false },
    isSeerrSaved: (JellyseerrSearchItem) -> Boolean = { false },
    modifier: Modifier = Modifier,
    trailerPreviewState: TvTrailerPreviewState = TvTrailerPreviewState.Idle,
    trailerPreviewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
    onPreviewFocus: (JellyfinItem, String) -> Unit = { _, _ -> },
) {
    val sessionState = searchState.session
    val query = sessionState.query
    val source = sessionState.source
    val queryFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var retryFocusRequest by remember { mutableStateOf<TvRetryFocusRequest?>(null) }
    val presentation = tvSearchPresentation(searchState)
    val visibleJellyfin = presentation.jellyfinItems.isNotEmpty()
    val visibleSeerr = presentation.seerrItems.isNotEmpty()
    val outerState = rememberLazyListState()
    val rowStates = rememberTvLazyRowStates(listOf("jellyfin", "seerr"))
    var nextOuterIndex = 1
    val searchingIndex = if (presentation.showSearching) nextOuterIndex++ else null
    val jellyfinFailureIndex = if (presentation.showJellyfinFailure) nextOuterIndex++ else null
    val jellyfinRowIndex = if (visibleJellyfin) nextOuterIndex++ else null
    val seerrFailureIndex = if (presentation.showSeerrFailure) nextOuterIndex++ else null
    val seerrRowIndex = if (visibleSeerr) nextOuterIndex++ else null
    val emptyIndex = if (presentation.showNoResults) nextOuterIndex else null
    val searchLocations =
        buildMap {
            put(TV_SEARCH_QUERY_TARGET, TvLazyFocusLocation(0))
            if (searchState.showVoiceAction) put(TV_SEARCH_VOICE_TARGET, TvLazyFocusLocation(0))
            TvSearchSource.entries.forEach { put(tvSearchSourceTargetId(it.name.lowercase()), TvLazyFocusLocation(0)) }
            jellyfinFailureIndex?.let {
                put(TV_SEARCH_JELLYFIN_RETRY_TARGET, TvLazyFocusLocation(it))
            }
            jellyfinRowIndex?.let { rowIndex ->
                presentation.jellyfinItems.forEachIndexed { index, item ->
                    put(tvSearchResultTargetId("jellyfin", item.id), TvLazyFocusLocation(rowIndex, "jellyfin", index))
                }
            }
            seerrFailureIndex?.let {
                put(TV_SEARCH_SEERR_RETRY_TARGET, TvLazyFocusLocation(it))
            }
            seerrRowIndex?.let { rowIndex ->
                presentation.seerrItems.forEachIndexed { index, item ->
                    put(
                        tvSearchResultTargetId("seerr", "${item.mediaType}:${item.tmdbId}"),
                        TvLazyFocusLocation(rowIndex, "seerr", index),
                    )
                }
            }
        }
    TvRouteFocusMaterializer(
        ownerId = "search-lists",
        targetIds = searchLocations.keys,
        fallbackTargetIds = setOf(TV_SEARCH_QUERY_TARGET),
    ) { targetId -> searchLocations[targetId]?.let { materializeTvLazyTarget(outerState, rowStates, it) } ?: false }
    TvRetryFocusRecovery(retryFocusRequest)
    BackHandler(enabled = sessionState.mode == TvSearchMode.EDIT) {
        keyboardController?.hide()
        onEnterBrowseMode()
    }
    LaunchedEffect(sessionState.mode) {
        // The field and source controls are emitted by the same lazy item. Waiting for
        // placement prevents a fresh Search route from losing its initial focus request.
        withFrameNanos { }
        when (sessionState.mode) {
            TvSearchMode.EDIT -> {
                queryFocusRequester.requestFocus()
                keyboardController?.show()
            }
            TvSearchMode.BROWSE -> {
                keyboardController?.hide()
                sourceFocusRequester.requestFocus()
            }
        }
    }

    fun retryFailedSearchSources() {
        if (presentation.showJellyfinFailure) onRetryJellyfin()
        if (presentation.showSeerrFailure) onRetrySeerr()
        retryFocusRequest =
            TvRetryFocusRequest(
                revision = (retryFocusRequest?.revision ?: 0L) + 1L,
                preferredTargetId = TV_SEARCH_QUERY_TARGET,
            )
    }
    if (presentation.results.isNotEmpty()) {
        TvCinematicSearchContent(
            searchState = searchState,
            presentation = presentation,
            homeState = homeState,
            strings = strings,
            focusMemory = focusMemory,
            onJellyfinItem = onJellyfinItem,
            onSeerrItem = onSeerrItem,
            onPlayJellyfin = onPlayJellyfin,
            onToggleJellyfinSaved = onToggleJellyfinSaved,
            onToggleJellyfinPlayed = onToggleJellyfinPlayed,
            onToggleSeerrSaved = onToggleSeerrSaved,
            isJellyfinSaved = isJellyfinSaved,
            isSeerrSaved = isSeerrSaved,
            onRetryFailures =
                if (presentation.showJellyfinFailure || presentation.showSeerrFailure) {
                    ::retryFailedSearchSources
                } else {
                    null
                },
            headerContent = {
                TvCinematicSearchHeader(
                    sessionState = sessionState,
                    searchState = searchState,
                    strings = strings,
                    queryFocusRequester = queryFocusRequester,
                    sourceFocusRequester = sourceFocusRequester,
                    onQueryChanged = onQueryChanged,
                    onSourceChanged = onSourceChanged,
                    onEnterEditMode = onEnterEditMode,
                    onVoiceSearch = onVoiceSearch,
                )
            },
            trailerPreviewState = trailerPreviewState,
            trailerPreviewEngine = trailerPreviewEngine,
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress,
            onPreviewFocus = onPreviewFocus,
        )
        return
    }
    LazyColumn(
        state = outerState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item("header") {
            Text(strings.search, modifier = Modifier.tvHeading(), color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = { Text(strings.searchHint) },
                singleLine = true,
                readOnly = sessionState.mode == TvSearchMode.BROWSE,
                colors = tvOutlinedTextFieldColors(),
                modifier =
                    Modifier
                        .tvScreenEntryFocus(focusTargetId = TV_SEARCH_QUERY_TARGET)
                        .tvFocusTarget(queryFocusRequester, focusTargetId = TV_SEARCH_QUERY_TARGET)
                        .focusRequester(queryFocusRequester)
                        .fillMaxWidth(0.66f)
                        .height(64.dp)
                        .testTag("tv-search-query")
                        .onPreviewKeyEvent { event ->
                            if (
                                sessionState.mode == TvSearchMode.BROWSE &&
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            ) {
                                onEnterEditMode()
                                true
                            } else {
                                false
                            }
                        }.tvReturnToNavigationRailOnLeft()
                        .focusProperties {
                            down = sourceFocusRequester
                            right = sourceFocusRequester
                        },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    strings.all,
                    { onSourceChanged(TvSearchSource.ALL) },
                    modifier = Modifier.focusRequester(sourceFocusRequester).testTag("tv-search-source-all"),
                    primary = source == TvSearchSource.ALL,
                    selected = source == TvSearchSource.ALL,
                    focusToNavigationRailOnLeft = true,
                    focusTargetId = tvSearchSourceTargetId("all"),
                )
                TvActionButton(
                    TV_BRAND_JELLYFIN,
                    { onSourceChanged(TvSearchSource.JELLYFIN) },
                    modifier = Modifier.testTag("tv-search-source-jellyfin"),
                    primary = source == TvSearchSource.JELLYFIN,
                    selected = source == TvSearchSource.JELLYFIN,
                    focusTargetId = tvSearchSourceTargetId("jellyfin"),
                )
                TvActionButton(
                    TV_BRAND_SEERR,
                    { onSourceChanged(TvSearchSource.SEERR) },
                    modifier = Modifier.testTag("tv-search-source-seerr"),
                    primary = source == TvSearchSource.SEERR,
                    selected = source == TvSearchSource.SEERR,
                    focusTargetId = tvSearchSourceTargetId("seerr"),
                )
                if (searchState.showVoiceAction) {
                    TvActionButton(
                        label = if (searchState.isVoiceListening) strings.searching else strings.search,
                        onClick = onVoiceSearch,
                        enabled = !searchState.isVoiceListening,
                        leading = { Icon(Icons.Default.Mic, contentDescription = null, tint = TvText) },
                        modifier = Modifier.testTag("tv-search-voice"),
                        focusTargetId = TV_SEARCH_VOICE_TARGET,
                    )
                }
            }
            searchState.voiceError?.let { message ->
                Spacer(Modifier.height(12.dp))
                TvStatusAnchor("${strings.requestFailed}: $message")
            }
        }
        searchingIndex?.let {
            item("searching") { TvStatusAnchor(strings.searching) }
        }
        jellyfinFailureIndex?.let {
            item("jellyfin-error") {
                TvSearchSourceFailure(
                    message = strings.jellyfinSearchFailed,
                    retryLabel = strings.retry,
                    focusTargetId = TV_SEARCH_JELLYFIN_RETRY_TARGET,
                    onRetry = {
                        onRetryJellyfin()
                        retryFocusRequest =
                            TvRetryFocusRequest(
                                revision = (retryFocusRequest?.revision ?: 0L) + 1L,
                                preferredTargetId = TV_SEARCH_QUERY_TARGET,
                            )
                    },
                )
            }
        }
        jellyfinRowIndex?.let {
            item("jellyfin-results") {
                TvJellyfinRow(
                    TV_BRAND_JELLYFIN,
                    presentation.jellyfinItems,
                    homeState,
                    strings,
                    focusMemory,
                    onJellyfinItem,
                    routeKey = "search",
                    listState = rowStates.getValue("jellyfin"),
                    focusTargetId = { itemId -> tvSearchResultTargetId("jellyfin", itemId) },
                    edgePadding = 12.dp,
                )
            }
        }
        seerrFailureIndex?.let {
            item("seerr-error") {
                TvSearchSourceFailure(
                    message = strings.seerrSearchFailed,
                    retryLabel = strings.retry,
                    focusTargetId = TV_SEARCH_SEERR_RETRY_TARGET,
                    onRetry = {
                        onRetrySeerr()
                        retryFocusRequest =
                            TvRetryFocusRequest(
                                revision = (retryFocusRequest?.revision ?: 0L) + 1L,
                                preferredTargetId = TV_SEARCH_QUERY_TARGET,
                            )
                    },
                )
            }
        }
        seerrRowIndex?.let {
            item("seerr-results") {
                TvSeerrRow(
                    TV_BRAND_SEERR,
                    presentation.seerrItems,
                    focusMemory,
                    "search",
                    onSeerrItem,
                    listState = rowStates.getValue("seerr"),
                    focusTargetId = { id -> tvSearchResultTargetId("seerr", id) },
                    edgePadding = 12.dp,
                )
            }
        }
        emptyIndex?.let {
            item("empty") { TvStatusAnchor(strings.noResults) }
        }
    }
}

@Composable
private fun TvSearchSourceFailure(
    message: String,
    retryLabel: String,
    focusTargetId: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = TvTextMuted, modifier = Modifier.weight(1f).tvStatusSemantics(message))
        TvActionButton(
            label = retryLabel,
            onClick = onRetry,
            focusTargetId = focusTargetId,
        )
    }
}

@Composable
private fun TvCinematicSearchHeader(
    sessionState: TvSearchSessionState,
    searchState: TvSearchUiState,
    strings: TvStrings,
    queryFocusRequester: FocusRequester,
    sourceFocusRequester: FocusRequester,
    onQueryChanged: (String) -> Unit,
    onSourceChanged: (TvSearchSource) -> Unit,
    onEnterEditMode: () -> Unit,
    onVoiceSearch: () -> Unit,
) {
    OutlinedTextField(
        value = sessionState.query,
        onValueChange = onQueryChanged,
        placeholder = { Text(strings.searchHint) },
        singleLine = true,
        readOnly = sessionState.mode == TvSearchMode.BROWSE,
        colors = tvOutlinedTextFieldColors(),
        modifier =
            Modifier
                .tvFocusTarget(queryFocusRequester, focusTargetId = TV_SEARCH_QUERY_TARGET)
                .focusRequester(queryFocusRequester)
                .fillMaxWidth(0.66f)
                .height(64.dp)
                .testTag("tv-search-query")
                .onPreviewKeyEvent { event ->
                    if (
                        sessionState.mode == TvSearchMode.BROWSE &&
                        event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    ) {
                        onEnterEditMode()
                        true
                    } else {
                        false
                    }
                }.tvReturnToNavigationRailOnLeft()
                .focusProperties {
                    down = sourceFocusRequester
                    right = sourceFocusRequester
                },
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvSearchSource.entries.forEachIndexed { index, source ->
            val label =
                when (source) {
                    TvSearchSource.ALL -> strings.all
                    TvSearchSource.JELLYFIN -> TV_BRAND_JELLYFIN
                    TvSearchSource.SEERR -> TV_BRAND_SEERR
                }
            TvActionButton(
                label = label,
                onClick = { onSourceChanged(source) },
                modifier =
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(sourceFocusRequester) else Modifier)
                        .testTag("tv-search-source-${source.name.lowercase()}"),
                primary = sessionState.source == source,
                selected = sessionState.source == source,
                focusToNavigationRailOnLeft = index == 0,
                focusTargetId = tvSearchSourceTargetId(source.name.lowercase()),
            )
        }
        if (searchState.showVoiceAction) {
            TvActionButton(
                label = if (searchState.isVoiceListening) strings.searching else strings.search,
                onClick = onVoiceSearch,
                enabled = !searchState.isVoiceListening,
                leading = { Icon(Icons.Default.Mic, contentDescription = null, tint = TvText) },
                modifier = Modifier.testTag("tv-search-voice"),
                focusTargetId = TV_SEARCH_VOICE_TARGET,
            )
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
    onToggleSaved: ((JellyseerrSearchItem) -> Unit)? = null,
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
    if (hasCinematicDiscoverContent(ready, requestItems)) {
        val cinematicTargetId = tvCinematicDiscoverInitialTargetId(requireNotNull(ready), requestItems)
        TvCinematicDiscoverContent(
            state = ready,
            requestItems = requestItems,
            hasPartialFailure = content?.hasRailFailures == true,
            strings = strings,
            focusMemory = focusMemory,
            onItem = onItem,
            onToggleSaved = onToggleSaved,
            isSaved = isSaved,
        )
        if (cinematicTargetId != null) {
            TvDiscoverRetryFocusRecovery(
                request = retryFocusRequest,
                currentTargetId = cinematicTargetId,
                isRefreshing = false,
                onCompleted = { revision ->
                    if (retryFocusRequest?.revision == revision) retryFocusRequest = null
                },
            )
        }
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
                put(TV_DISCOVER_EMPTY_TARGET, TvLazyFocusLocation(outerIndex))
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
                } ?: TV_DISCOVER_EMPTY_TARGET
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
                item("loading") { TvFocusPlaceholder(strings.loading, TV_DISCOVER_LOADING_TARGET) }
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
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(
                            requestItems,
                            key = { _, request -> request.id },
                            contentType = { _, _ -> "media-card" },
                        ) { index, request ->
                            val item = request.toSearchItem()
                            val targetId = tvDiscoverItemTargetId("requests", request.id.toString())
                            TvMediaCard(
                                title = request.title ?: request.originalTitle ?: "Request ${request.id}",
                                subtitle =
                                    listOfNotNull(
                                        request.requestStatus.label(strings),
                                        request.availability.standard.label(strings),
                                    ).joinToString(" - "),
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
            item("empty") { TvFocusPlaceholder(strings.noResults, TV_DISCOVER_EMPTY_TARGET) }
        }
    }
}

@Composable
internal fun TvFocusPlaceholder(
    label: String,
    @Suppress("UNUSED_PARAMETER") focusTargetId: String,
) {
    TvStatusAnchor(
        label = label,
        modifier = Modifier.height(112.dp),
    )
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
private fun TvSeerrRow(
    title: String,
    items: List<JellyseerrSearchItem>,
    focusMemory: TvFocusMemory,
    routeKey: String,
    onItem: (JellyseerrSearchItem) -> Unit,
    listState: LazyListState,
    focusTargetId: (String) -> String,
    screenEntry: Boolean = false,
    edgePadding: Dp = 6.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(title)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(edgePadding),
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
                    imageUrl = tmdbImageUrl(artwork.path, backdrop = artwork.isBackdrop),
                    artworkFit = artwork.fit,
                    onClick = { onItem(item) },
                    onFocused = { focusMemory.remember(routeKey, title, id, horizontalIndex = index) },
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
        if (type.equals("Episode", true)) "S${parentIndexNumber ?: 0} E${indexNumber ?: 0}" else null,
        tvRatingLabel(communityRating),
    ).joinToString("  •  ").ifBlank { null }

internal fun JellyseerrRequestSummary.toSearchItem(): JellyseerrSearchItem? {
    val id = tmdbId ?: return null
    return JellyseerrSearchItem(
        tmdbId = id,
        mediaType = mediaType,
        title = title ?: originalTitle ?: "Request $id",
        overview = null,
        releaseYear = null,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = mediaId,
        tvdbId = tvdbId,
        availability = availability,
        requests = listOf(this),
    )
}

internal fun tvCinematicDiscoverInitialTargetId(
    state: JellyseerrRecommendationsState.Ready,
    requestItems: List<JellyseerrRequestSummary>,
): String? {
    val railTarget =
        JellyseerrRecommendationRail.entries.firstNotNullOfOrNull { rail ->
            state.rails[rail]?.items?.firstOrNull()?.let { item ->
                tvCinematicFocusTargetId("discover-${rail.name.lowercase()}", item.cinematicKey())
            }
        }
    if (railTarget != null) return railTarget
    return requestItems
        .firstNotNullOfOrNull { it.toSearchItem() }
        ?.let { requestItem ->
            tvCinematicFocusTargetId(DISCOVER_REQUESTS_ROW, "request:${requestItem.cinematicKey()}")
        }
}
