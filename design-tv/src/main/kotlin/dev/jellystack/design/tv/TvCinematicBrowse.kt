@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A browse screen in the home screen's look: the focused card's artwork or trailer fills the screen, its text sits
 * where the home spotlight shows its text, and the rows start where the home rows start. A playing [trailer] takes
 * the screen after a few seconds without input, as on home. OK on a card calls [onCardClick]; the Play key plays
 * it through [playback].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvCinematicBrowse(
    state: TvCinematicBrowseState,
    onCardFocused: (TvFocusAnchor, TvCinematicCard) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
    modifier: Modifier = Modifier,
    playback: TvCinematicCardPlayback? = null,
    resetVerticalFocusToFirstCard: Boolean = false,
    trailer: TvCinematicTrailer? = null,
) {
    val focusAppearance = LocalTvFocusAppearance.current
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val scope = rememberCoroutineScope()
    val currentReducedMotion by rememberUpdatedState(focusAppearance.reducedMotion)
    val backdropController =
        remember(scope, imageLoader, platformContext) {
            TvBackdropController(
                scope = scope,
                imageLoader =
                    TvBackdropImageLoader { url ->
                        imageLoader.execute(
                            ImageRequest
                                .Builder(platformContext)
                                .data(url)
                                .size(width = 1920, height = 1080)
                                .build(),
                        ) is SuccessResult
                    },
                reducedMotion = { currentReducedMotion },
            )
        }
    DisposableEffect(backdropController) {
        onDispose(backdropController::cancelPending)
    }
    LaunchedEffect(state.focusedCard?.id) {
        state.focusedCard?.let(backdropController::focus)
    }
    val loadedBackdrop by backdropController.state.collectAsState()
    val backdrop = loadedBackdrop.takeIf { it.url != null } ?: state.backdrop
    val focusContext = LocalTvFocusContext.current
    val columnState = rememberLazyListState()
    val rowStates = remember { mutableStateMapOf<String, LazyListState>() }
    // The list below depends on these parts of the state only. The focused card changes the state on every move; the
    // rows must not recompose for it, so they get handlers that stay the same and read the latest callbacks.
    val rows = state.rows
    val inlineStatus = state.inlineStatus
    val currentOnCardFocused by rememberUpdatedState(onCardFocused)
    val currentOnCardClick by rememberUpdatedState(onCardClick)
    val handleCardFocused: (TvFocusAnchor, TvCinematicCard) -> Unit =
        remember(backdropController) {
            { anchor, card ->
                backdropController.focus(card)
                currentOnCardFocused(anchor, card)
            }
        }
    val handleCardClick: (TvCinematicCard) -> Unit = remember { { card -> currentOnCardClick(card) } }
    val rowHeaderCount = if (inlineStatus != null) 1 else 0
    val targetLocations =
        remember(rows, rowHeaderCount) {
            buildMap {
                rows.forEachIndexed { rowIndex, row ->
                    row.cards.forEachIndexed { cardIndex, card ->
                        put(tvCinematicFocusTargetId(row.id, card.id), Triple(row.id, rowHeaderCount + rowIndex, cardIndex))
                    }
                }
            }
        }
    TvRouteFocusMaterializer(
        ownerId = "cinematic-browse",
        targetIds = targetLocations.keys,
        fallbackTargetIds = setOfNotNull(targetLocations.keys.firstOrNull()),
    ) { targetId ->
        val location = targetLocations[targetId] ?: return@TvRouteFocusMaterializer false
        columnState.scrollToItem(tvCinematicMaterializationColumnIndex(location.second, rowHeaderCount))
        val rowState =
            rowStates[location.first]
                ?: withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
                    snapshotFlow { rowStates[location.first] }.first { it != null }
                }
        rowState?.scrollToItem(location.third)
        rowState != null
    }

    val interactions = remember { TvInteractionCounter() }
    val uiAlpha = rememberTvHomeUiAlpha(trailerPlaying = trailer != null, interactions = interactions)
    Box(
        modifier.fillMaxSize().background(TvBackground).onPreviewKeyEvent {
            // Any remote input brings the UI back over a playing trailer.
            interactions.record()
            false
        },
    ) {
        TvCinematicBackdropLayer(backdrop, trailer?.engine, uiAlpha)
        TvCinematicStage(state.hero, state.focusedCard, playback, uiAlpha)
        TvHomeFadeLayer(uiAlpha, Modifier.align(Alignment.TopEnd)) { TvHomeClock(rememberTvClockLabel()) }
        trailer?.let { TvTrailerPillUnderClock(it.label, it.soundEnabled, it.progress, Modifier.align(Alignment.TopEnd)) }
        val rowScrollSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides rememberTvRowAlignedBringIntoViewSpec(TV_HOME_ROW_CARD_OFFSET),
        ) {
            LazyColumn(
                state = columnState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = TV_CINEMATIC_ROWS_TOP)
                        .tvFadeRowsCutAtTop(columnState)
                        // The rows stay composed so the focused card keeps its focus while the trailer has the screen.
                        .graphicsLayer { alpha = uiAlpha.value },
                contentPadding = TvHomeRowsPadding,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                inlineStatus?.let { status ->
                    item(key = "cinematic-status") { TvCinematicStatusAnchor(status) }
                }
                items(items = rows, key = TvCinematicRow::id) { row ->
                    val rowState = rememberLazyListState()
                    DisposableEffect(row.id, rowState) {
                        rowStates[row.id] = rowState
                        onDispose { rowStates.remove(row.id, rowState) }
                    }
                    TvRowScroll(rowScrollSpec) {
                        TvCinematicBrowseRow(
                            row = row,
                            rowState = rowState,
                            playback = playback,
                            onVerticalMove = { direction ->
                                resetVerticalFocusToFirstCard &&
                                    focusContext != null &&
                                    scope.focusFirstCardOfNextRow(rows, row.id, direction, focusContext)
                            },
                            onCardFocused = { card ->
                                handleCardFocused(TvFocusAnchor(row.id, card.id, TvFocusDestination.SECTION_ITEM), card)
                            },
                            onCardClick = handleCardClick,
                        )
                    }
                }
            }
        }
    }
}

internal fun tvCinematicMaterializationColumnIndex(
    rowIndex: Int,
    headerCount: Int,
): Int = if (rowIndex == headerCount) 0 else rowIndex

/** Up and Down land on the first card of the next row; false when there is no row in [direction]. */
private fun CoroutineScope.focusFirstCardOfNextRow(
    rows: List<TvCinematicRow>,
    rowId: String,
    direction: TvHomeVerticalDirection,
    focusContext: TvFocusContext,
): Boolean {
    val rowIndex = rows.indexOfFirst { it.id == rowId }
    val targetRow =
        rows.getOrNull(
            rowIndex +
                when (direction) {
                    TvHomeVerticalDirection.UP -> -1
                    TvHomeVerticalDirection.DOWN -> 1
                },
        )
    val targetCard = targetRow?.cards?.firstOrNull() ?: return false
    launch {
        focusContext.coordinator.restoreFocus(
            routeKey = focusContext.routeKey,
            preferredTargetId = tvCinematicFocusTargetId(targetRow.id, targetCard.id),
            includeFallback = false,
            requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
        )
    }
    return true
}

@Composable
private fun TvCinematicBrowseRow(
    row: TvCinematicRow,
    rowState: LazyListState,
    playback: TvCinematicCardPlayback?,
    onVerticalMove: (TvHomeVerticalDirection) -> Boolean,
    onCardFocused: (TvCinematicCard) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSectionTitle(row.title, Modifier.testTag("cinematic-row-title-${row.id}"))
        LazyRow(
            state = rowState,
            modifier = Modifier.tvRowBleed(),
            contentPadding = tvBleedRowPadding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(items = row.cards, key = { _, card -> card.id }) { index, card ->
                val onPlay = playback?.takeIf { it.canPlay(card) }?.let { cardPlayback -> { cardPlayback.onPlay(card) } }
                TvMediaCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    progress = card.resumeFraction,
                    watched = card.played,
                    imageUrl = card.artworkUrl,
                    selected = card.selected,
                    onClick = { onCardClick(card) },
                    onFocused = { onCardFocused(card) },
                    focusTargetId = tvCinematicFocusTargetId(row.id, card.id),
                    modifier =
                        Modifier
                            .tvReturnToNavigationRailOnLeft(enabled = index == 0)
                            .tvCinematicVerticalFocus(onVerticalMove)
                            .tvCinematicPlayKey(onPlay)
                            .testTag("cinematic-card-${row.id}-${card.id}"),
                )
            }
        }
    }
}

private fun Modifier.tvCinematicVerticalFocus(onVerticalMove: (TvHomeVerticalDirection) -> Boolean): Modifier =
    onPreviewKeyEvent { event ->
        if (
            event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN ||
            !shouldHandleTvHomeVerticalKey(event.nativeKeyEvent.repeatCount)
        ) {
            false
        } else {
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> onVerticalMove(TvHomeVerticalDirection.UP)
                KeyEvent.KEYCODE_DPAD_DOWN -> onVerticalMove(TvHomeVerticalDirection.DOWN)
                else -> false
            }
        }
    }

/** The remote's Play key plays the focused card; a card without [onPlay] leaves the key to others. */
private fun Modifier.tvCinematicPlayKey(onPlay: (() -> Unit)?): Modifier =
    if (onPlay == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val playKey = key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            if (playKey && key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) onPlay()
            playKey
        }
    }

internal fun tvCinematicFocusTargetId(
    rowId: String,
    cardId: String,
): String = "cinematic:row:$rowId:item:$cardId"

@Composable
private fun TvCinematicStatusAnchor(status: TvCinematicInlineStatus) {
    val color = if (status.kind == TvCinematicStatusKind.ERROR) Color(0xFFFFB4AB) else TvTextMuted
    Row(
        modifier = Modifier.fillMaxWidth().testTag("cinematic-status"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status.message,
            color = color,
            fontSize = 15.sp,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = status.message
                    }.padding(vertical = 8.dp),
        )
    }
}
