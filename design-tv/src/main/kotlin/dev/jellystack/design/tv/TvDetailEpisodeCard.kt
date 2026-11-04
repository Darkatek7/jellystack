@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.watchProgress

private const val TV_DETAIL_EPISODE_WIDTH_DP = 208
private const val TV_DETAIL_EPISODE_HEIGHT_DP = 117
private const val TV_DETAIL_EPISODE_OVERVIEW_HEIGHT_DP = 48

/** The two lines under an episode still: "E3 · 52 min" (or the time left) and the episode title. */
internal data class TvDetailEpisodeLines(
    val meta: String,
    val title: String,
)

internal fun JellyfinItem.tvDetailEpisodeLines(strings: TvStrings): TvDetailEpisodeLines {
    val copy = strings.detailScreen
    val progress = watchProgress()
    val runtimeMinutes = runTimeTicks?.takeIf { it > 0L }?.let { it / TV_RUNTIME_TICKS_PER_MINUTE }
    val time =
        progress?.remainingMinutes?.let { strings.metadata.minutesLeft.format(it) }
            ?: runtimeMinutes?.takeIf { it > 0L }?.let { strings.metadata.minutesShort.format(it) }
    return TvDetailEpisodeLines(
        meta =
            listOfNotNull(indexNumber?.let { copy.episodeShortFormat.format(it) }, time)
                .joinToString(" · "),
        title = (episodeTitle ?: name).takeIf { it.isNotBlank() } ?: strings.messages.untitled,
    )
}

/** The episode still at card size; the series artwork stands in when the episode has no still. */
internal fun JellyfinItem.tvDetailEpisodeImageUrl(homeState: JellyfinHomeState): String? {
    val stillTag = primaryImageTag?.takeIf { type.equals("Episode", ignoreCase = true) }
    if (stillTag != null) {
        return jellyfinImageUrl(homeState.imageBaseUrl, homeState.imageAccessToken, id, stillTag, "Primary")
    }
    val seriesTag = seriesThumbImageTag ?: thumbImageTag
    return jellyfinImageUrl(
        homeState.imageBaseUrl,
        homeState.imageAccessToken,
        seriesId ?: id,
        seriesTag ?: primaryImageTag,
        if (seriesTag != null) "Thumb" else "Primary",
    )
}

/**
 * An episode: the landscape still with the watched check and progress, and the number, runtime and title below it so
 * no text lies on artwork that may already carry some. [current] marks the episode of the page on an episode page.
 */
@Composable
internal fun TvDetailEpisodeCard(
    item: JellyfinItem,
    imageUrl: String?,
    strings: TvStrings,
    current: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    providedFocusRequester: FocusRequester? = null,
) {
    val lines = item.tvDetailEpisodeLines(strings)
    val progress = item.watchProgress()?.fraction
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    Column(
        Modifier.width(TV_DETAIL_EPISODE_WIDTH_DP.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier
                .width(TV_DETAIL_EPISODE_WIDTH_DP.dp)
                .height(TV_DETAIL_EPISODE_HEIGHT_DP.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOf(lines.meta, lines.title).filter(String::isNotBlank).joinToString(", ")
                    this.selected = current
                }.tvFocusable(
                    onClick = onClick,
                    shape = TvShapes.Card,
                    onFocused = onFocused,
                    providedFocusRequester = providedFocusRequester,
                ).background(TvSurface, TvShapes.Card)
                .drawWithContent {
                    drawContent()
                    if (current) {
                        drawRoundRect(
                            color = TvPurple,
                            topLeft = Offset(3.dp.toPx(), size.height * 0.18f),
                            size = Size(5.dp.toPx(), size.height * 0.64f),
                            cornerRadius = CornerRadius(2.5.dp.toPx()),
                        )
                    }
                }.clip(TvShapes.Card),
        ) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    onError = { imageFailed = true },
                )
            }
            TvDetailEpisodeStillOverlays(progress, item.isPlayed)
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                lines.meta,
                color = TvTextMuted,
                fontSize = TvTextSize.BodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                lines.title,
                color = TvText,
                fontSize = TvTextSize.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Progress bar along the bottom of the still and the watched check in its corner. */
@Composable
private fun BoxScope.TvDetailEpisodeStillOverlays(
    progress: Float?,
    played: Boolean,
) {
    progress?.let { fraction ->
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.28f)),
        ) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(TvPurple))
        }
    }
    if (played) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .background(TvBackground.copy(alpha = 0.78f), TvShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = TvPurple, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The Episodes section: a [title] (null under season tabs), the row of episodes and, under the row, a fixed-height overview of the focused
 * episode (two lines) so the page below does not move while the user scrolls along the row.
 */
@Composable
internal fun TvDetailEpisodeRow(
    title: String?,
    section: TvDetailSection.Episodes,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    onOpenItem: (JellyfinItem) -> Unit,
    focusModifiers: TvDetailSectionFocusModifiers,
) {
    var focusedId by remember(section.id) { mutableStateOf<String?>(null) }
    val overviewOwner =
        section.items.firstOrNull { it.id == focusedId }
            ?: section.items.firstOrNull { it.id == section.currentEpisodeId }
            ?: section.items.firstOrNull { !it.isPlayed }
            ?: section.items.firstOrNull()
    Column(
        Modifier
            .padding(start = TV_DETAIL_START_DP.dp, end = 48.dp)
            .then(focusModifiers.navigationModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        title?.let { TvSectionTitle(it) }
        LazyRow(
            state = focusModifiers.horizontalListState,
            modifier = Modifier.tvRowBleed(start = TV_DETAIL_START_DP.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = TV_DETAIL_START_DP.dp, end = 48.dp, top = 8.dp, bottom = 4.dp),
        ) {
            items(section.items, key = JellyfinItem::id, contentType = { "episode-card" }) { episode ->
                TvDetailEpisodeCard(
                    item = episode,
                    imageUrl = episode.tvDetailEpisodeImageUrl(homeState),
                    strings = strings,
                    current = episode.id == section.currentEpisodeId,
                    onClick = { onOpenItem(episode) },
                    onFocused = { focusedId = episode.id },
                    modifier = focusModifiers.itemModifier(episode.id),
                    providedFocusRequester = focusModifiers.itemFocusRequester(episode.id),
                )
            }
        }
        Text(
            overviewOwner?.overview?.takeIf(String::isNotBlank).orEmpty(),
            modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth(0.7f).height(TV_DETAIL_EPISODE_OVERVIEW_HEIGHT_DP.dp),
            color = TvTextMuted,
            fontSize = TvTextSize.Body,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
