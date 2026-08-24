@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun TvCinematicBrowse(
    state: TvCinematicBrowseState,
    actionLabels: TvSelectedItemActionLabels,
    onCardFocused: (TvFocusAnchor, TvCinematicCard) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
    modifier: Modifier = Modifier,
    selectedItemActions: TvSelectedItemActions? = null,
    headerContent: (@Composable () -> Unit)? = null,
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
    val columnState = rememberLazyListState()
    val rowStates = remember { mutableStateMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val rowHeaderCount = 1 + (if (headerContent != null) 1 else 0) + (if (state.inlineStatus != null) 1 else 0)
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
        columnState.scrollToItem(location.second)
        val rowState =
            rowStates[location.first]
                ?: withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
                    snapshotFlow { rowStates[location.first] }.first { it != null }
                }
        rowState?.scrollToItem(location.third)
        rowState != null
    }

    Box(modifier.fillMaxSize().background(TvBackground)) {
        TvCinematicBackdropLayer(backdrop, focusAppearance.reducedMotion)
        LazyColumn(
            state = columnState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = TvLayoutTokens.SafeInsets.horizontal,
                        vertical = TvLayoutTokens.SafeInsets.vertical,
                    ),
            contentPadding = PaddingValues(bottom = TvLayoutTokens.FocusHaloPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "cinematic-metadata") {
                TvCinematicMetadata(
                    hero = state.hero,
                    focusedCard = focusedCard,
                    actions = selectedItemActions,
                    labels = actionLabels,
                )
            }
            headerContent?.let { content -> item(key = "cinematic-header") { content() } }
            state.inlineStatus?.let { status ->
                item(key = "cinematic-status") { TvCinematicStatusAnchor(status) }
            }
            items(items = state.rows, key = TvCinematicRow::id) { row ->
                val rowState = rememberLazyListState()
                DisposableEffect(row.id, rowState) {
                    rowStates[row.id] = rowState
                    onDispose { rowStates.remove(row.id, rowState) }
                }
                TvCinematicBrowseRow(
                    row = row,
                    rowState = rowState,
                    onCardFocused = { card ->
                        val anchor = TvFocusAnchor(row.id, card.id, TvFocusDestination.SECTION_ITEM)
                        backdropController.focus(card)
                        onCardFocused(anchor, card)
                    },
                    onCardClick = onCardClick,
                )
            }
        }
    }
}

@Composable
private fun TvCinematicBackdropLayer(
    backdrop: TvCinematicBackdrop,
    reducedMotion: Boolean,
) {
    val transitionMillis = if (reducedMotion) 0 else backdrop.transitionMillis
    AnimatedContent(
        targetState = backdrop.url,
        modifier = Modifier.fillMaxSize().testTag("cinematic-backdrop"),
        transitionSpec = {
            fadeIn(tween(transitionMillis)) togetherWith fadeOut(tween(transitionMillis))
        },
        label = "cinematic-backdrop-crossfade",
    ) { url ->
        if (url == null) {
            Box(Modifier.fillMaxSize().background(TvBackground))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(TvBackground, TvBackground.copy(alpha = 0.82f), TvBackground.copy(alpha = 0.35f)),
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, TvBackground.copy(alpha = 0.94f)))),
    )
}

@Composable
private fun TvCinematicMetadata(
    hero: TvCinematicHero?,
    focusedCard: TvCinematicCard?,
    actions: TvSelectedItemActions?,
    labels: TvSelectedItemActionLabels,
) {
    val title = focusedCard?.title ?: hero?.title
    val subtitle = focusedCard?.subtitle ?: hero?.overview
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 126.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        hero?.eyebrow?.takeIf { focusedCard == null }?.let {
            Text(it, color = TvPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        title?.let {
            Text(
                it,
                color = TvText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        subtitle?.let {
            Text(it, color = TvTextMuted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (focusedCard != null && actions != null) {
            TvSelectedItemActionStrip(focusedCard, labels, actions)
        }
    }
}

@Composable
internal fun TvSelectedItemActionStrip(
    card: TvCinematicCard,
    labels: TvSelectedItemActionLabels,
    actions: TvSelectedItemActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("cinematic-action-strip")) {
        TvActionButton(
            label = if (card.resumeFraction != null) labels.resume else labels.play,
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
        TvActionButton(
            label = if (card.selected) labels.removeFromList else labels.addToList,
            onClick = actions.onToggleSaved,
            selected = card.selected,
            leading = { Icon(if (card.selected) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
            modifier = Modifier.testTag("cinematic-action-saved"),
        )
        TvActionButton(
            label = if (card.played) labels.markUnplayed else labels.markPlayed,
            onClick = actions.onTogglePlayed,
            selected = card.played,
            leading = { Icon(Icons.Default.CheckCircle, null) },
            modifier = Modifier.testTag("cinematic-action-played"),
        )
    }
}

@Composable
private fun TvCinematicBrowseRow(
    row: TvCinematicRow,
    rowState: androidx.compose.foundation.lazy.LazyListState,
    onCardFocused: (TvCinematicCard) -> Unit,
    onCardClick: (TvCinematicCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            row.title,
            color = TvText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }.testTag("cinematic-row-title-${row.id}"),
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = TvLayoutTokens.FocusHaloPadding),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
        ) {
            items(items = row.cards, key = TvCinematicCard::id) { card ->
                TvMediaCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    imageUrl = card.artworkUrl,
                    selected = card.selected,
                    onClick = { onCardClick(card) },
                    onFocused = { onCardFocused(card) },
                    focusTargetId = tvCinematicFocusTargetId(row.id, card.id),
                    modifier = Modifier.testTag("cinematic-card-${row.id}-${card.id}"),
                )
            }
        }
    }
}

internal fun tvCinematicFocusTargetId(
    rowId: String,
    cardId: String,
): String = "cinematic:row:$rowId:item:$cardId"

@Composable
private fun TvCinematicStatusAnchor(status: TvCinematicInlineStatus) {
    val color = if (status.kind == TvCinematicStatusKind.ERROR) Color(0xFFFFB4AB) else TvTextMuted
    Text(
        text = status.message,
        color = color,
        fontSize = 15.sp,
        modifier =
            Modifier
                .testTag("cinematic-status")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(vertical = 8.dp),
    )
}
