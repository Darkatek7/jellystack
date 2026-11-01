@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvProgram
import dev.jellystack.core.livetv.progressAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import java.util.Date

/**
 * The channels as a list (number, logo, name, the program on now) with a pane for the focused channel: the program on
 * now with a progress bar, and the one after it. There is no guide grid: a grid of programs is many more nodes and
 * images than a weak TV GPU draws smoothly, and the pane answers "what is on, and what is next".
 */
@Composable
internal fun TvLiveTvScreen(
    state: TvLiveTvState,
    context: TvLiveTvContext,
    actions: TvLiveTvActions,
    modifier: Modifier = Modifier,
) {
    val strings = context.strings
    val copy = strings.messages.liveTv
    Column(modifier.fillMaxSize().padding(TvScreenPadding), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            copy.title,
            modifier = Modifier.tvHeading(),
            color = TvText,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        when (state) {
            TvLiveTvState.Loading ->
                TvSkeletonListRows(copy.loading, rowHeight = CHANNEL_ROW_HEIGHT, modifier = Modifier.width(LIST_WIDTH))
            TvLiveTvState.Empty -> TvLiveTvMessage(copy.emptyTitle, copy.emptyHint, strings.retry, actions.onRetry)
            TvLiveTvState.Failed -> TvLiveTvMessage(copy.failedTitle, copy.failedHint, strings.retry, actions.onRetry)
            is TvLiveTvState.Ready -> TvLiveTvChannels(state, context, actions)
        }
    }
}

@Composable
private fun TvLiveTvMessage(
    title: String,
    hint: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    TvEmptyState(
        icon = Icons.Default.LiveTv,
        title = title,
        hint = hint,
        action = TvEmptyStateAction(retryLabel, TV_LIBRARY_RETRY_TARGET, onRetry),
    )
}

@Composable
private fun TvLiveTvChannels(
    state: TvLiveTvState.Ready,
    context: TvLiveTvContext,
    actions: TvLiveTvActions,
) {
    val routeKey = context.routeKey
    val focusMemory = context.focusMemory
    val channels = state.channels
    val listState = rememberLazyListState()
    TvLiveTvFocusMaterializer(channels, listState, routeKey)
    // Programs load for the rows on screen, once scrolling has settled.
    LaunchedEffect(listState, channels) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collectLatest { indices ->
            delay(VISIBLE_SETTLE_MS)
            actions.onVisibleChannels(indices.mapNotNull { channels.getOrNull(it)?.id })
        }
    }
    val remembered = focusMemory.restore(routeKey)?.itemId
    var focusedId by remember(channels) { mutableStateOf(remembered ?: channels.firstOrNull()?.id) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.width(LIST_WIDTH).fillMaxHeight().testTag("tv-live-tv-channels"),
            // Room for the focused row, which scales up.
            contentPadding = PaddingValues(all = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                TvLiveTvChannelRow(
                    channel = channel,
                    program = state.nowAndNext(channel.id).now,
                    context = context,
                    actions =
                        TvLiveTvRowActions(
                            onFocused = {
                                focusedId = channel.id
                                focusMemory.remember(routeKey, "items", channel.id, index + 1, index)
                            },
                            onClick = { actions.onPlay(channel) },
                        ),
                    modifier = Modifier.tvScreenEntryFocus(index == 0, tvLibraryTargetId(channel.id)),
                )
            }
        }
        val focused = channels.firstOrNull { it.id == focusedId }
        if (focused != null) {
            TvLiveTvDetail(focused, state, context.strings, Modifier.weight(1f))
        }
    }
}

/** Lets the focus restoration scroll to the remembered channel after Back from the player. */
@Composable
private fun TvLiveTvFocusMaterializer(
    channels: List<LiveTvChannel>,
    listState: LazyListState,
    routeKey: String,
) {
    val locations = channels.mapIndexed { index, channel -> tvLibraryTargetId(channel.id) to index }.toMap()
    TvRouteFocusMaterializer(
        ownerId = "live-tv:$routeKey",
        targetIds = locations.keys,
        fallbackTargetIds = setOfNotNull(locations.keys.firstOrNull()),
    ) { targetId ->
        val index = locations[targetId] ?: return@TvRouteFocusMaterializer false
        listState.scrollToItem(index)
        withTimeoutOrNull(TV_FOCUS_MATERIALIZATION_TIMEOUT_MS) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        } ?: false
    }
}

@Composable
private fun TvLiveTvChannelRow(
    channel: LiveTvChannel,
    program: LiveTvProgram?,
    context: TvLiveTvContext,
    actions: TvLiveTvRowActions,
    modifier: Modifier = Modifier,
) {
    val targetId = tvLibraryTargetId(channel.id)
    val images = context.images
    val description =
        context.strings.messages.liveTv
            .channelDescription(channel.number, channel.name, program?.title)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CHANNEL_ROW_HEIGHT)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .tvFocusable(
                    onClick = actions.onClick,
                    shape = TvShapes.Control,
                    onFocused = actions.onFocused,
                    focusToNavigationRailOnLeft = true,
                    focusTargetId = targetId,
                    fill = TvSurface,
                ).padding(horizontal = 16.dp)
                .testTag("tv-live-channel-${channel.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            channel.number.orEmpty(),
            color = TvTextMuted,
            fontSize = 20.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
        Box(Modifier.size(width = 64.dp, height = 40.dp), contentAlignment = Alignment.Center) {
            val logo = images.logoOf(channel)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = TvText, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            program?.let {
                Text(it.title, color = TvTextMuted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TvLiveTvDetail(
    channel: LiveTvChannel,
    state: TvLiveTvState.Ready,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val copy = strings.messages.liveTv
    val nowNext = state.nowAndNext(channel.id)
    val clock = DateFormat.getTimeFormat(LocalContext.current)

    fun time(instant: Instant): String = clock.format(Date(instant.toEpochMilliseconds()))
    // Minute by minute is exact enough for a progress bar, and recomposes only this pane.
    val nowMs = rememberTvClockMs()
    Column(modifier.testTag("tv-live-tv-detail"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            listOfNotNull(channel.number, channel.name.takeIf(String::isNotBlank)).joinToString("  "),
            color = TvText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val now = nowNext.now
        if (now == null && nowNext.next == null) {
            Text(copy.noProgramInfo, color = TvTextMuted, fontSize = 20.sp)
        }
        if (now != null) {
            TvLiveTvSectionLabel(copy.now)
            Text(now.title, color = TvText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            now.subtitle?.let { TvLiveTvDetailText(it, maxLines = 1) }
            Text(copy.timeRange(time(now.start), time(now.end)), color = TvTextMuted, fontSize = 18.sp)
            TvLiveTvProgress(now.progressAt(Instant.fromEpochMilliseconds(nowMs)))
            now.overview?.let { TvLiveTvDetailText(it, maxLines = 4) }
        }
        nowNext.next?.let { next ->
            Spacer(Modifier.height(8.dp))
            TvLiveTvSectionLabel(copy.next)
            Text(next.title, color = TvText, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(copy.startsAt(time(next.start)), color = TvTextMuted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TvLiveTvDetailText(
    text: String,
    maxLines: Int,
) {
    Text(text, color = TvTextMuted, fontSize = 18.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TvLiveTvSectionLabel(text: String) {
    Text(text.uppercase(), color = TvPurple, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/** A flat bar: two filled boxes, nothing animated. */
@Composable
private fun TvLiveTvProgress(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(TvShapes.Pill)
            .background(TvSurfaceRaised),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(TvPurple))
    }
}

private fun TvLibraryGridImages.logoOf(channel: LiveTvChannel) =
    jellyfinImageUrl(baseUrl, accessToken, channel.id, channel.imageTag, maxWidth = LOGO_WIDTH)

/** What a channel row does when it gets focus and when it is chosen. */
private class TvLiveTvRowActions(
    val onFocused: () -> Unit,
    val onClick: () -> Unit,
)

private val LIST_WIDTH = 460.dp
private val CHANNEL_ROW_HEIGHT = 68.dp
private const val LOGO_WIDTH = 160
private const val VISIBLE_SETTLE_MS = 250L
