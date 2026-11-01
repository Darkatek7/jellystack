@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal const val TV_ITEM_LIST_PLAY_ALL_TARGET = "item-list:play-all"

/** What an item list screen shows. */
internal class TvItemListContent(
    val source: TvItemListSource,
    val state: TvItemListState,
    val images: TvLibraryGridImages,
    val favorites: Set<String>,
    val routeKey: String,
) {
    val items: List<JellyfinItem> = (state as? TvItemListState.Ready)?.items.orEmpty()

    /** A list of titles to play (everything but a filmography) starts on its Play all button once it has some. */
    val hasPlayAll: Boolean = source !is TvItemListSource.Person && items.isNotEmpty()

    /** Albums and artists, and playlists of music, can also be played in a random order. */
    val hasShuffle: Boolean =
        items.isNotEmpty() &&
            (
                source is TvItemListSource.Album ||
                    source is TvItemListSource.Artist ||
                    (source is TvItemListSource.Container && items.areTracks())
            )

    /** An album, or a playlist of music, lists its tracks as rows to play from instead of as cards. */
    val showsTrackList: Boolean =
        source is TvItemListSource.Album || (source is TvItemListSource.Container && items.areTracks())
}

internal class TvItemListActions(
    val onOpenItem: (JellyfinItem) -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    /** Plays the list from the track at this place, the rest following it. */
    val onPlayTrack: (index: Int) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The titles of a person, collection, or playlist as a grid of the library's cards. A person shows their photo and name
 * above the grid; a collection or playlist shows its name and a "Play all" button. Opening a card goes to that title.
 */
@Composable
internal fun TvItemListScreen(
    content: TvItemListContent,
    actions: TvItemListActions,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    TvItemListFocusMaterializer(content, gridState)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val reliablePosters = tvLibraryHasReliablePosters(content.items)
        val cardShape = tvLibraryCardShape(collectionType = null, reliablePosters = reliablePosters)
        val columns = tvLibraryGridColumns(cardShape, maxWidth.value, LocalDensity.current.fontScale)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = TvScreenPadding,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
        ) {
            item(key = "item-list-header", span = { GridItemSpan(maxLineSpan) }) {
                TvItemListHeader(content, strings) {
                    TvItemListPlayButtons(content, actions, strings)
                }
            }
            itemsIndexed(content.items, key = { _, item -> item.id }) { index, item ->
                TvLibraryGridCard(
                    item = item,
                    index = index,
                    columns = columns,
                    cardShape = cardShape,
                    images = content.images,
                    favorites = content.favorites,
                    strings = strings,
                    focusMemory = focusMemory,
                    routeKey = content.routeKey,
                    entryFocus = index == 0 && !content.hasPlayAll,
                    onClick = { actions.onOpenItem(item) },
                )
            }
            tvItemListStatus(content, strings, actions.onRetry)
        }
    }
}

/** Lets the focus restoration scroll the grid to a remembered card, the Play all button, or the status row. */
@Composable
private fun TvItemListFocusMaterializer(
    content: TvItemListContent,
    gridState: LazyGridState,
) {
    val itemTargetIds = content.items.map { tvLibraryTargetId(it.id) }
    val terminalTarget =
        tvLibraryTerminalFocusTarget(
            libraryId = content.source.id,
            itemCount = content.items.size,
            isLibraryLoading = content.state is TvItemListState.Loading,
            isPageLoading = false,
            hasError = content.state is TvItemListState.Failed,
        )
    val focusLocations =
        tvLibraryGridFocusLocations(itemTargetIds, terminalTarget) +
            buildMap<String, Int> {
                if (content.hasPlayAll) put(TV_ITEM_LIST_PLAY_ALL_TARGET, 0)
                if (content.hasShuffle) put(TV_TRACK_LIST_SHUFFLE_TARGET, 0)
            }
    TvRouteFocusMaterializer(
        ownerId = "item-list:${content.routeKey}",
        targetIds = focusLocations.keys,
        fallbackTargetIds =
            setOfNotNull(
                TV_ITEM_LIST_PLAY_ALL_TARGET.takeIf { content.hasPlayAll },
                itemTargetIds.firstOrNull(),
                terminalTarget,
            ),
    ) { targetId ->
        val index = focusLocations[targetId] ?: return@TvRouteFocusMaterializer false
        gridState.scrollToItem(index)
        withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        } ?: false
    }
}

/** The loading spinner, the failure with Retry, or the empty message under the cards. */
private fun LazyGridScope.tvItemListStatus(
    content: TvItemListContent,
    strings: TvStrings,
    onRetry: () -> Unit,
) {
    when (content.state) {
        TvItemListState.Loading ->
            item(key = "item-list-loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TvPurple)
                }
            }
        TvItemListState.Failed ->
            item(key = "item-list-error", span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.messages.itemListLoadFailed, color = Color(0xFFFFA59E))
                    TvActionButton(
                        strings.retry,
                        onRetry,
                        modifier = Modifier.width(220.dp),
                        focusTargetId = TV_LIBRARY_RETRY_TARGET,
                    )
                }
            }
        is TvItemListState.Ready ->
            if (content.items.isEmpty()) {
                item(key = "item-list-empty", span = { GridItemSpan(maxLineSpan) }) {
                    TvFocusPlaceholder(strings.noResults, TV_LIBRARY_EMPTY_TARGET)
                }
            }
    }
}

/** Play all, and Shuffle where the list is music, side by side under the title. */
@Composable
private fun TvItemListPlayButtons(
    content: TvItemListContent,
    actions: TvItemListActions,
    strings: TvStrings,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (content.hasPlayAll) {
            TvActionButton(
                label = strings.messages.playAll,
                onClick = actions.onPlayAll,
                primary = true,
                focusToNavigationRailOnLeft = true,
                focusTargetId = TV_ITEM_LIST_PLAY_ALL_TARGET,
                modifier =
                    Modifier
                        .width(240.dp)
                        .tvScreenEntryFocus(true, TV_ITEM_LIST_PLAY_ALL_TARGET)
                        .testTag("tv-item-list-play-all"),
            )
        }
        if (content.hasShuffle) {
            TvActionButton(
                label = strings.messages.music.shuffle,
                onClick = actions.onShuffle,
                focusTargetId = TV_TRACK_LIST_SHUFFLE_TARGET,
                modifier = Modifier.width(240.dp).testTag("tv-item-list-shuffle"),
            )
        }
    }
}

@Composable
private fun TvItemListHeader(
    content: TvItemListContent,
    strings: TvStrings,
    actions: @Composable () -> Unit,
) {
    val source = content.source
    val label =
        when (source) {
            is TvItemListSource.Person -> strings.messages.filmography
            is TvItemListSource.Container ->
                if (source.isPlaylist) strings.messages.playlistLabel else strings.messages.collectionLabel
            is TvItemListSource.Album -> strings.messages.music.album
            is TvItemListSource.Artist -> strings.messages.music.albums
        }
    val count =
        content.items.size
            .toLong()
            .takeIf { content.state is TvItemListState.Ready }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (source is TvItemListSource.Person || source is TvItemListSource.Artist) {
            TvPersonPhoto(source, content.images)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = source.title,
                color = TvText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("tv-item-list-title"),
            )
            Text(
                text = listOfNotNull(label, count?.let(strings::itemCount)).joinToString(TV_CARD_SEPARATOR),
                color = TvTextMuted,
                fontSize = 18.sp,
            )
            actions()
        }
    }
}

/** The person's portrait, with their initial underneath for people the server has no photo of. */
@Composable
private fun TvPersonPhoto(
    person: TvItemListSource,
    images: TvLibraryGridImages,
) {
    Box(
        Modifier.size(120.dp).clip(CircleShape).background(TvSurface),
        contentAlignment = Alignment.Center,
    ) {
        val initial =
            person.title
                .firstOrNull()
                ?.uppercase()
                .orEmpty()
        Text(initial, color = TvText, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        jellyfinImageUrl(images.baseUrl, images.accessToken, person.id, tag = null, maxWidth = 320)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
