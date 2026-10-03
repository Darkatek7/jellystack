@file:Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvCinematicBrowse(
    state: TvCinematicBrowseState,
    actionLabels: TvSelectedItemActionLabels,
    onCardFocused: (TvFocusAnchor, TvCinematicCard) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
    modifier: Modifier = Modifier,
    selectedItemActions: TvSelectedItemActions? = null,
    showFocusedMetadata: Boolean = true,
    resetVerticalFocusToFirstCard: Boolean = false,
    topHeaderContent: (@Composable () -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    inlineStatusAction: (@Composable () -> Unit)? = null,
    previewing: Boolean = false,
    previewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
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
    val focusedCard = state.focusedCard
    var actionOriginRequester by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.focus.FocusRequester?>(null) }
    val focusContext = LocalTvFocusContext.current
    val columnState = rememberLazyListState()
    val rowStates = remember { mutableStateMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val rowHeaderCount =
        (if (headerContent != null) 1 else 0) +
            (if (state.inlineStatus != null) 1 else 0)
    val targetLocations =
        remember(state.rows, rowHeaderCount) {
            buildMap {
                state.rows.forEachIndexed { rowIndex, row ->
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

    Box(modifier.fillMaxSize().background(TvBackground)) {
        TvCinematicPreviewStage(
            backdrop = backdrop,
            hero = state.hero,
            focusedCard = focusedCard.takeIf { showFocusedMetadata },
            actions = selectedItemActions,
            labels = actionLabels,
            reducedMotion = focusAppearance.reducedMotion,
            contentTopInset = if (topHeaderContent != null) TV_CINEMATIC_FIXED_HEADER_HEIGHT else 0.dp,
            onActionDown = {
                actionOriginRequester?.let { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) }
                    ?: false
            },
            previewing = previewing,
            previewEngine = previewEngine,
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress,
        )
        val rowScrollSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides rememberTvRowAlignedBringIntoViewSpec(TV_CINEMATIC_ROW_CARD_OFFSET),
        ) {
            LazyColumn(
                state = columnState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = TvLayoutTokens.ContentStart,
                            end = TvLayoutTokens.SafeInsets.horizontal,
                            top = TV_CINEMATIC_ROWS_TOP,
                            bottom = TvLayoutTokens.SafeInsets.vertical,
                        ),
                contentPadding = PaddingValues(bottom = TvLayoutTokens.FocusHaloPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                headerContent?.let { content -> item(key = "cinematic-header") { content() } }
                state.inlineStatus?.let { status ->
                    item(key = "cinematic-status") { TvCinematicStatusAnchor(status, inlineStatusAction) }
                }
                items(items = state.rows, key = TvCinematicRow::id) { row ->
                    val rowState = rememberLazyListState()
                    DisposableEffect(row.id, rowState) {
                        rowStates[row.id] = rowState
                        onDispose { rowStates.remove(row.id, rowState) }
                    }
                    TvRowScroll(rowScrollSpec) {
                        TvCinematicBrowseRow(
                            row = row,
                            rowState = rowState,
                            onVerticalMove = { direction ->
                                if (!resetVerticalFocusToFirstCard || focusContext == null) {
                                    false
                                } else {
                                    val rowIndex = state.rows.indexOfFirst { it.id == row.id }
                                    val targetRowIndex =
                                        rowIndex +
                                            when (direction) {
                                                TvHomeVerticalDirection.UP -> -1
                                                TvHomeVerticalDirection.DOWN -> 1
                                            }
                                    val targetRow = state.rows.getOrNull(targetRowIndex)
                                    val targetCard = targetRow?.cards?.firstOrNull()
                                    if (targetRow == null || targetCard == null) {
                                        false
                                    } else {
                                        scope.launch {
                                            focusContext.coordinator.restoreFocus(
                                                routeKey = focusContext.routeKey,
                                                preferredTargetId = tvCinematicFocusTargetId(targetRow.id, targetCard.id),
                                                includeFallback = false,
                                                requestFocus = { requester ->
                                                    runCatching { requester.requestFocus() }.getOrDefault(false)
                                                },
                                            )
                                        }
                                        true
                                    }
                                }
                            },
                            onCardFocused = { card, requester ->
                                val anchor = TvFocusAnchor(row.id, card.id, TvFocusDestination.SECTION_ITEM)
                                actionOriginRequester = requester
                                backdropController.focus(card)
                                onCardFocused(anchor, card)
                            },
                            onCardClick = onCardClick,
                        )
                    }
                }
            }
        }
        topHeaderContent?.let { content ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TvLayoutTokens.ContentStart,
                        end = TvLayoutTokens.SafeInsets.horizontal,
                        top = TvLayoutTokens.SafeInsets.vertical,
                    ),
            ) {
                content()
            }
        }
    }
}

private val TV_CINEMATIC_FIXED_HEADER_HEIGHT = 70.dp

/** Row title (24 dp) and title spacing (8 dp) above each cinematic card. */
private val TV_CINEMATIC_ROW_CARD_OFFSET = 32.dp

internal fun tvCinematicMaterializationColumnIndex(
    rowIndex: Int,
    headerCount: Int,
): Int = if (rowIndex == headerCount) 0 else rowIndex

@Composable
internal fun TvSelectedItemActionStrip(
    card: TvCinematicCard,
    labels: TvSelectedItemActionLabels,
    actions: TvSelectedItemActions,
    onDown: (() -> Boolean)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .onPreviewKeyEvent { event ->
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                        shouldHandleTvHomeVerticalKey(event.nativeKeyEvent.repeatCount) &&
                        onDown?.invoke() == true
                }.testTag("cinematic-action-strip"),
    ) {
        TvActionButton(
            label = actions.primaryLabel ?: if (card.resumeFraction != null) labels.resume else labels.play,
            onClick = actions.onPlayOrResume,
            primary = true,
            leading = { Icon(Icons.Default.PlayArrow, null) },
            modifier = Modifier.testTag("cinematic-action-play"),
        )
        TvActionButton(
            label = labels.details,
            onClick = actions.onDetails,
            leading = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.testTag("cinematic-action-details"),
        )
        actions.onToggleSaved?.let { onToggleSaved ->
            TvActionButton(
                label = if (card.selected) labels.removeFromList else labels.addToList,
                onClick = onToggleSaved,
                selected = card.selected,
                leading = { Icon(if (card.selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                modifier = Modifier.testTag("cinematic-action-saved"),
            )
        }
        actions.onTogglePlayed?.let { onTogglePlayed ->
            TvActionButton(
                label = if (card.played) labels.markUnplayed else labels.markPlayed,
                onClick = onTogglePlayed,
                selected = card.played,
                leading = { Icon(Icons.Default.CheckCircle, null) },
                modifier = Modifier.testTag("cinematic-action-played"),
            )
        }
    }
}

@Composable
private fun TvCinematicBrowseRow(
    row: TvCinematicRow,
    rowState: androidx.compose.foundation.lazy.LazyListState,
    onVerticalMove: (TvHomeVerticalDirection) -> Boolean,
    onCardFocused: (TvCinematicCard, androidx.compose.ui.focus.FocusRequester) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            row.title,
            color = TvText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }.testTag("cinematic-row-title-${row.id}"),
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = TvLayoutTokens.FocusHaloPadding),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
        ) {
            itemsIndexed(items = row.cards, key = { _, card -> card.id }) { index, card ->
                val cardFocusRequester =
                    remember(card.id) {
                        androidx.compose.ui.focus
                            .FocusRequester()
                    }
                TvMediaCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    imageUrl = card.artworkUrl,
                    selected = card.selected,
                    onClick = { onCardClick(card) },
                    onFocused = { onCardFocused(card, cardFocusRequester) },
                    focusTargetId = tvCinematicFocusTargetId(row.id, card.id),
                    modifier =
                        Modifier
                            .tvReturnToNavigationRailOnLeft(enabled = index == 0)
                            .tvCinematicVerticalFocus(onVerticalMove)
                            .testTag("cinematic-card-${row.id}-${card.id}"),
                    providedFocusRequester = cardFocusRequester,
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

internal fun tvCinematicFocusTargetId(
    rowId: String,
    cardId: String,
): String = "cinematic:row:$rowId:item:$cardId"

@Composable
private fun TvCinematicStatusAnchor(
    status: TvCinematicInlineStatus,
    action: (@Composable () -> Unit)?,
) {
    val color = if (status.kind == TvCinematicStatusKind.ERROR) Color(0xFFFFB4AB) else TvTextMuted
    Row(
        modifier = Modifier.fillMaxWidth().testTag("cinematic-status"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        action?.invoke()
    }
}
