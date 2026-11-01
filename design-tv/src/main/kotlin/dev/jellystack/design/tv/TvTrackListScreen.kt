@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal const val TV_TRACK_LIST_SHUFFLE_TARGET = "item-list:shuffle"

/**
 * An album (or a playlist of music) as a cover with Play all and Shuffle on the left and its tracks in order on the
 * right, with their numbers and lengths. Choosing a track plays the list from it.
 */
@Composable
internal fun TvTrackListScreen(
    content: TvItemListContent,
    actions: TvItemListActions,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    modifier: Modifier = Modifier,
) {
    val rows = buildTrackRows(content.items)
    val listState = rememberLazyListState()
    TvTrackListFocusMaterializer(content, rows, listState)
    Row(
        modifier.fillMaxSize().padding(TvScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        TvTrackListSide(content, actions, strings, Modifier.width(TRACK_LIST_SIDE_WIDTH))
        Box(Modifier.weight(1f).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("tv-track-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key() }) { _, row ->
                    when (row) {
                        is TvTrackRow.DiscHeader -> TvDiscHeader(strings.messages.music.disc(row.disc))
                        is TvTrackRow.Track -> TvTrackRowItem(row, content, actions, strings, focusMemory)
                    }
                }
            }
            TvTrackListStatus(content, actions.onRetry, strings)
        }
    }
}

private fun TvTrackRow.key(): String =
    when (this) {
        is TvTrackRow.DiscHeader -> "disc:$disc"
        is TvTrackRow.Track -> "track:${item.id}"
    }

/** Lets the focus restoration scroll to a remembered track; the buttons and status are always composed. */
@Composable
private fun TvTrackListFocusMaterializer(
    content: TvItemListContent,
    rows: List<TvTrackRow>,
    listState: LazyListState,
) {
    val locations = trackFocusLocations(rows)
    val staticTargets =
        setOfNotNull(
            TV_ITEM_LIST_PLAY_ALL_TARGET.takeIf { content.hasPlayAll },
            TV_TRACK_LIST_SHUFFLE_TARGET.takeIf { content.hasShuffle },
            TV_LIBRARY_RETRY_TARGET.takeIf { content.state is TvItemListState.Failed },
        )
    TvRouteFocusMaterializer(
        ownerId = "track-list:${content.routeKey}",
        targetIds = locations.keys + staticTargets,
        fallbackTargetIds = staticTargets + setOfNotNull(locations.keys.firstOrNull()),
    ) { targetId ->
        val index = locations[targetId]
        if (index == null) {
            targetId in staticTargets
        } else {
            listState.scrollToItem(index)
            withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
            } ?: false
        }
    }
}

@Composable
private fun TvTrackListSide(
    content: TvItemListContent,
    actions: TvItemListActions,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val music = strings.messages.music
    val label =
        when (val source = content.source) {
            is TvItemListSource.Container ->
                if (source.isPlaylist) strings.messages.playlistLabel else strings.messages.collectionLabel
            else -> music.album
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvTrackCover(content.source.id, content.images)
        Text(
            text = content.source.title,
            color = TvText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("tv-item-list-title"),
        )
        Text(trackListSubtitle(label, content.items, music), color = TvTextMuted, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        if (content.hasPlayAll) {
            TvActionButton(
                label = strings.messages.playAll,
                onClick = actions.onPlayAll,
                primary = true,
                focusToNavigationRailOnLeft = true,
                focusTargetId = TV_ITEM_LIST_PLAY_ALL_TARGET,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvScreenEntryFocus(true, TV_ITEM_LIST_PLAY_ALL_TARGET)
                        .testTag("tv-item-list-play-all"),
            )
        }
        if (content.hasShuffle) {
            TvActionButton(
                label = music.shuffle,
                onClick = actions.onShuffle,
                focusToNavigationRailOnLeft = true,
                focusTargetId = TV_TRACK_LIST_SHUFFLE_TARGET,
                modifier = Modifier.fillMaxWidth().testTag("tv-item-list-shuffle"),
            )
        }
    }
}

/** The square cover; a note stands in when the server has none. */
@Composable
private fun TvTrackCover(
    itemId: String,
    images: TvLibraryGridImages,
) {
    Box(
        Modifier.size(TRACK_COVER_SIZE).clip(TvShapes.Card).background(TvSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(72.dp))
        jellyfinImageUrl(images.baseUrl, images.accessToken, itemId, tag = null, maxWidth = COVER_WIDTH)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvDiscHeader(label: String) {
    Text(
        text = label,
        color = TvTextMuted,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun TvTrackRowItem(
    row: TvTrackRow.Track,
    content: TvItemListContent,
    actions: TvItemListActions,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
) {
    val title = row.item.name.ifBlank { strings.messages.untitled }
    val targetId = tvLibraryTargetId(row.item.id)
    val description = strings.messages.music.trackDescription(row.number, title, row.duration)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TRACK_ROW_HEIGHT)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .tvFocusable(
                    onClick = { actions.onPlayTrack(row.index) },
                    shape = TvShapes.Control,
                    onFocused = {
                        focusMemory.remember(content.routeKey, "items", row.item.id, row.index + 1, row.index)
                    },
                    focusTargetId = targetId,
                    fill = TvSurface,
                ).padding(horizontal = 20.dp)
                .testTag("tv-track-row-${row.item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = row.number.toString(),
            color = TvTextMuted,
            fontSize = 20.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = title,
            color = TvText,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.duration?.let { Text(it, color = TvTextMuted, fontSize = 20.sp) }
    }
}

/** The spinner while loading, or the failure with Retry, or the empty message; nothing once there are tracks. */
@Composable
private fun TvTrackListStatus(
    content: TvItemListContent,
    onRetry: () -> Unit,
    strings: TvStrings,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            content.state is TvItemListState.Loading && content.items.isEmpty() ->
                CircularProgressIndicator(color = TvPurple)
            content.state is TvItemListState.Failed ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(strings.messages.itemListLoadFailed, color = Color(0xFFFFA59E))
                    TvActionButton(
                        strings.retry,
                        onRetry,
                        modifier = Modifier.width(220.dp),
                        focusTargetId = TV_LIBRARY_RETRY_TARGET,
                    )
                }
            content.state is TvItemListState.Ready && content.items.isEmpty() ->
                TvEmptyState(icon = Icons.Default.MusicNote, title = strings.emptyStates.nothingYet, hint = null)
        }
    }
}

private val TRACK_LIST_SIDE_WIDTH = 260.dp
private val TRACK_COVER_SIZE = 180.dp
private val TRACK_ROW_HEIGHT = 60.dp
private const val COVER_WIDTH = 480
