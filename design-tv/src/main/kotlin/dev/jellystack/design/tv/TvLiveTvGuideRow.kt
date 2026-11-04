@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.datetime.Instant

/** What the cells of one row share. */
private class GuideRowContext(
    val env: TvLiveTvGuideEnv,
    val index: Int,
    val channel: LiveTvChannel,
    /** The guide of this row reaches the end of the window, so an empty row really has no programs. */
    val loaded: Boolean,
    val at: Instant,
)

/**
 * One channel: its label, then the cells of the window. A plain `Row` of at most a handful of cells; a step of the
 * window recomposes it with the cells of the new window.
 */
@Composable
internal fun TvLiveTvGuideRow(
    index: Int,
    state: TvLiveTvState.Ready,
    env: TvLiveTvGuideEnv,
) {
    val channel = state.channels[index]
    val programs = state.guide[channel.id].orEmpty()
    val window = env.guide.window
    val cells = remember(programs, window) { guideRowCells(programs, window) }
    val guideUntil = state.guideUntil[channel.id]
    val row = GuideRowContext(env, index, channel, guideUntil != null && guideUntil >= window.end, state.at)
    Row(Modifier.height(GUIDE_ROW_HEIGHT).testTag("tv-live-guide-row-${channel.id}")) {
        TvLiveTvGuideChannelLabel(channel, env)
        Row(Modifier.width(env.gridWidth).fillMaxHeight()) {
            var cursor = 0f
            for (cell in cells) {
                key(cell.key) {
                    val gap = cell.startMinute - cursor
                    if (gap > 0f) Spacer(Modifier.width(env.minuteWidth * gap))
                    TvLiveTvGuideCell(cell, row)
                }
                cursor = cell.endMinute
            }
        }
    }
}

/** Number, a small logo and the name: the only image of a row. */
@Composable
private fun TvLiveTvGuideChannelLabel(
    channel: LiveTvChannel,
    env: TvLiveTvGuideEnv,
) {
    Row(
        Modifier.width(GUIDE_CHANNEL_WIDTH).fillMaxHeight().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            channel.number.orEmpty(),
            color = TvTextMuted,
            fontSize = 15.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
        Box(Modifier.size(width = 40.dp, height = 28.dp), contentAlignment = Alignment.Center) {
            val logo = env.context.images.logoOf(channel)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            channel.name,
            color = TvText,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A program as one flat box: a fill, a ring when focused, a title and the time. The focus is drawn from a state that
 * only the draw phase reads, so moving focus recomposes nothing; no image, shadow or layer.
 */
@Composable
private fun TvLiveTvGuideCell(
    cell: GuideCell,
    row: GuideRowContext,
) {
    val env = row.env
    val program = cell.program
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val id = GuideCellId(row.index, cell.key)
    val registry = env.guide.registry
    DisposableEffect(id, requester) {
        registry.register(id, requester)
        onDispose { registry.unregister(id, requester) }
    }
    val airing = program != null && program.start <= row.at && row.at < program.end
    val fill = if (airing) TvSurfaceRaised else TvSurface
    val width = env.minuteWidth * cell.minutes
    val label = program?.let { guideCellLabel(it, env) }
    val description = label ?: env.copy.noProgramInfo
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    if (state.isFocused) env.guide.onCellFocused(row.index, cell)
                }
            }.clickable(interactionSource = null, indication = null) { cellClicked(program, row) }
            .drawBehind {
                val inset = CELL_GAP.toPx() / 2f
                val body = Size(size.width - 2f * inset, size.height)
                val corners = CornerRadius(CELL_RADIUS.toPx())
                drawRoundRect(if (focused) FOCUSED_FILL else fill, Offset(inset, 0f), body, corners)
                if (focused) {
                    val ring = FOCUS_RING.toPx()
                    drawRoundRect(
                        Color.White,
                        Offset(inset + ring / 2f, ring / 2f),
                        Size(body.width - ring, body.height - ring),
                        corners,
                        Stroke(ring),
                    )
                }
            }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (width >= MIN_TEXT_WIDTH) TvLiveTvGuideCellText(program, cell, row, width >= MIN_TIME_WIDTH)
    }
}

@Composable
private fun TvLiveTvGuideCellText(
    program: LiveTvProgram?,
    cell: GuideCell,
    row: GuideRowContext,
    showTime: Boolean,
) {
    val env = row.env
    if (program == null) {
        if (row.loaded) Text(env.copy.noProgramInfo, color = TvTextMuted, fontSize = 16.sp, maxLines = 1)
        return
    }
    val time = remember(program, env) { env.timeRange(program) }
    Column(Modifier.testTag("tv-live-guide-cell-${cell.key}")) {
        Text(program.title, color = TvText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showTime) Text(time, color = TvTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun guideCellLabel(
    program: LiveTvProgram,
    env: TvLiveTvGuideEnv,
): String = "${program.title}, ${env.timeRange(program)}"

/** OK plays the channel from a program that is on now (or a row without any); on a later one it opens the details. */
private fun cellClicked(
    program: LiveTvProgram?,
    row: GuideRowContext,
) {
    val now = row.env.clock.nowMillis()
    val onNow =
        program == null ||
            (program.start.toEpochMilliseconds() <= now && now < program.end.toEpochMilliseconds())
    if (onNow) row.env.onPlay(row.channel) else row.env.guide.toggleDetails()
}

private fun TvLibraryGridImages.logoOf(channel: LiveTvChannel) =
    jellyfinImageUrl(baseUrl, accessToken, channel.id, channel.imageTag, maxWidth = LOGO_WIDTH)

private val CELL_GAP = 4.dp
private val CELL_RADIUS = 6.dp
private val FOCUS_RING = 3.dp
private val MIN_TEXT_WIDTH = 48.dp
private val MIN_TIME_WIDTH = 120.dp
private val FOCUSED_FILL = Color(0xFF4A3C8C)
private const val LOGO_WIDTH = 120
