@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.players.PlaybackChapter
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.chapterAt
import dev.jellystack.players.formatPlaybackTime

/** One row of the chapters or episodes panel. */
internal data class TvPlayerMediaRowModel(
    val key: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val current: Boolean,
    val watched: Boolean = false,
    val progress: Float? = null,
)

internal data class TvPlayerMediaList(
    val title: String,
    val rows: List<TvPlayerMediaRowModel>,
)

/** Chapters with their start time; the chapter at [positionMs] is marked current. */
internal fun tvChapterRows(
    chapters: List<PlaybackChapter>,
    positionMs: Long,
    chapterNumberFormat: String,
    imageUrlFor: (PlaybackChapter) -> String?,
): List<TvPlayerMediaRowModel> {
    val current = chapters.chapterAt(positionMs)
    return chapters.map { chapter ->
        TvPlayerMediaRowModel(
            key = "chapter-${chapter.index}",
            title = chapter.name ?: chapterNumberFormat.format(chapter.index + 1),
            subtitle = formatPlaybackTime(chapter.startPositionMs),
            imageUrl = imageUrlFor(chapter),
            current = chapter == current,
        )
    }
}

/** Episodes of the playing season in order, with time left, watched state, and the playing one marked. */
internal fun tvEpisodeRows(
    episodes: List<JellyfinItem>,
    currentId: String,
    strings: TvStrings,
    imageUrlFor: (JellyfinItem) -> String?,
): List<TvPlayerMediaRowModel> =
    episodes
        .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
        .map { episode ->
            val text = episode.tvEpisodeCardText(strings)
            TvPlayerMediaRowModel(
                key = episode.id,
                title = text.title,
                subtitle = text.subtitle,
                imageUrl = imageUrlFor(episode),
                current = episode.id == currentId,
                watched = episode.isPlayed,
                progress = text.progress,
            )
        }

/** Side panel listing [list]; focus starts on the current row. */
@Composable
internal fun TvPlayerMediaListPanel(
    list: TvPlayerMediaList,
    strings: TvStrings,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusIndex = list.rows.indexOfFirst { it.current }.coerceAtLeast(0)
    LaunchedEffect(list.title, list.rows.size) {
        if (list.rows.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(focusIndex)
        focusRequester.requestFocus()
    }
    Column(
        modifier
            .width(TV_PLAYER_PANEL_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(TvBackground.copy(alpha = 0.985f))
            .padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvPlayerPanelHeader(title = list.title, root = false, strings = strings, onBack = onBack)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(list.rows, key = { _, row -> row.key }) { index, row ->
                TvPlayerMediaRow(
                    row = row,
                    onClick = { onSelect(row.key) },
                    modifier = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerMediaRow(
    row: TvPlayerMediaRowModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = TvShapes.Control
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
            }.background(TvSurfaceRaised, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) TvPurple else Color.White.copy(alpha = 0.08f), shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOfNotNull(row.title, row.subtitle).joinToString(", ")
                selected = row.current
            }.clickable(onClick = onClick)
            .focusable()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPlayerMediaRowImage(row)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.title,
                color = if (row.current) TvPurple else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = TvTextSize.Subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(
                    it,
                    color = TvTextMuted,
                    fontSize = TvTextSize.BodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val stateIcon =
            when {
                row.current -> Icons.Default.PlayArrow
                row.watched -> Icons.Default.Check
                else -> null
            }
        stateIcon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = if (row.current) TvPurple else TvTextMuted,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun TvPlayerMediaRowImage(row: TvPlayerMediaRowModel) {
    Box(
        Modifier
            .width(MEDIA_ROW_IMAGE_WIDTH_DP.dp)
            .height((MEDIA_ROW_IMAGE_WIDTH_DP * 9 / 16).dp)
            .clip(TvShapes.Badge)
            .background(TvSurface),
    ) {
        row.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        row.progress?.let { progress ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(TvPurple),
            )
        }
    }
}

internal const val TV_PLAYER_PANEL_WIDTH_DP = 560
private const val MEDIA_ROW_IMAGE_WIDTH_DP = 128

internal data class TvPlayerImages(
    val baseUrl: String?,
    val accessToken: String?,
)

/** What the chapters and episodes panels list for the playing item. */
internal data class TvPlayerMediaSources(
    val chapters: List<PlaybackChapter>,
    val episodes: List<JellyfinItem>,
    val images: TvPlayerImages,
)

/** The chapters or episodes list for [panel]; null for every other panel. */
internal fun tvPlayerMediaList(
    panel: TvPlayerPanel,
    active: PlaybackState.Active,
    strings: TvStrings,
    sources: TvPlayerMediaSources,
): TvPlayerMediaList? {
    val images = sources.images
    return when (panel) {
        TvPlayerPanel.CHAPTERS -> {
            val rows =
                tvChapterRows(sources.chapters, active.positionMs, strings.player.chapterNumber) { chapter ->
                    chapter.imageTag?.let { tag ->
                        val type = "Chapter/${chapter.index}"
                        jellyfinImageUrl(images.baseUrl, images.accessToken, active.mediaId, tag, type)
                    }
                }
            TvPlayerMediaList(title = strings.player.chapters, rows = rows)
        }
        TvPlayerPanel.EPISODES -> {
            val season = active.metadata?.seasonNumber
            val seasonEpisodes =
                sources.episodes
                    .filter { season == null || it.parentIndexNumber == season }
                    .ifEmpty { sources.episodes }
            val rows =
                tvEpisodeRows(seasonEpisodes, active.mediaId, strings) { episode ->
                    jellyfinImageUrl(images.baseUrl, images.accessToken, episode.id, episode.primaryImageTag)
                }
            TvPlayerMediaList(title = strings.episodes, rows = rows)
        }
        else -> null
    }
}

/** A chapter seeks; another episode starts through the launcher, the playing one only closes the panel. */
internal fun TvPlayerMediaSources.select(
    panel: TvPlayerPanel,
    key: String,
    currentMediaId: String,
    onSeekTo: (Long) -> Unit,
    onPlayEpisode: (JellyfinItem) -> Unit,
) {
    when (panel) {
        TvPlayerPanel.CHAPTERS ->
            chapters.firstOrNull { "chapter-${it.index}" == key }?.let { onSeekTo(it.startPositionMs) }
        TvPlayerPanel.EPISODES ->
            episodes.firstOrNull { it.id == key && it.id != currentMediaId }?.let(onPlayEpisode)
        else -> Unit
    }
}
