@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.core.livetv.LiveTvChannel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/** The header button between the two views; down, in the guide, goes into the grid to the cell that airs now. */
@Composable
internal fun TvLiveTvViewToggle(
    label: String,
    guideMode: Boolean,
    onClick: () -> Unit,
    entry: TvLiveTvGuideEntry,
) {
    TvActionButton(
        label = label,
        onClick = onClick,
        modifier =
            Modifier
                .testTag("tv-live-tv-view-toggle")
                // The list has its first channel for this; the grid has no cell to start on.
                .tvScreenEntryFocus(guideMode, TV_LIVE_TV_TOGGLE_TARGET)
                .onPreviewKeyEvent { event ->
                    val down =
                        guideMode &&
                            event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    val enter = entry.enter
                    if (down && enter != null) enter()
                    down && enter != null
                },
        focusToNavigationRailOnLeft = true,
        focusRequester = entry.toggleRequester,
    )
}

/** Half-hour marks over the grid; changes only when the window steps. */
@Composable
internal fun TvLiveTvGuideRuler(env: TvLiveTvGuideEnv) {
    val start = env.guide.window.start
    val labels =
        remember(start, env.clock) {
            List(GUIDE_VISIBLE_MINUTES / GUIDE_STEP_MINUTES) { mark ->
                env.clock.formatTime(start + (mark * GUIDE_STEP_MINUTES).minutes)
            }
        }
    Row(Modifier.padding(start = GUIDE_CHANNEL_WIDTH).height(GUIDE_RULER_HEIGHT)) {
        labels.forEach { label ->
            Box(Modifier.width(env.minuteWidth * GUIDE_STEP_MINUTES).fillMaxHeight()) {
                Box(Modifier.width(1.dp).height(10.dp).background(TvTextMuted))
                Text(label, Modifier.padding(start = 6.dp), color = TvTextMuted, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

/**
 * A 2 dp line at the current time. Its position is read while laying out, so the minute tick and a step of the window
 * move it without recomposing anything.
 */
@Composable
internal fun TvLiveTvGuideNowLine(
    env: TvLiveTvGuideEnv,
    modifier: Modifier = Modifier,
) {
    val clock = rememberGuideClock(env.clock.nowMillis)
    Box(
        modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val minuteWidth = env.minuteWidth.toPx()
                val minutes = env.guide.window.minutesFromStart(Instant.fromEpochMilliseconds(clock.value))
                val x = (minutes * minuteWidth).roundToInt()
                val inside = x in 0 until (minuteWidth * GUIDE_VISIBLE_MINUTES).roundToInt()
                val width = if (inside) NOW_LINE_WIDTH.roundToPx() else 0
                val line = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { line.place(x, 0) }
            },
    ) {
        Box(Modifier.fillMaxSize().background(TvPurple))
    }
}

/** The wall clock, to the minute. */
@Composable
private fun rememberGuideClock(nowMillis: () -> Long): State<Long> =
    produceState(nowMillis(), nowMillis) {
        while (true) {
            delay(MILLIS_PER_MINUTE - value % MILLIS_PER_MINUTE)
            value = nowMillis()
        }
    }

/** The focused program: title, time and channel, and with details open the overview. */
@Composable
internal fun TvLiveTvGuidePane(
    channels: List<LiveTvChannel>,
    env: TvLiveTvGuideEnv,
) {
    val guide = env.guide
    val info = guide.focusInfo
    val channel = info?.let { channels.getOrNull(it.row) }
    val program = info?.program
    val copy = env.copy
    Column(Modifier.fillMaxWidth().height(GUIDE_PANE_HEIGHT).testTag("tv-live-tv-guide-pane")) {
        if (channel == null) return@Column
        val channelName = listOfNotNull(channel.number, channel.name.takeIf(String::isNotBlank)).joinToString("  ")
        if (program == null) {
            GuidePaneTitle(channelName)
            GuidePaneLine(copy.noProgramInfo, maxLines = 1)
            return@Column
        }
        GuidePaneTitle(program.title)
        val range = env.timeRange(program)
        GuidePaneLine("$range  ·  $channelName", maxLines = 1)
        val open = guide.detailsOpen
        val detail = if (open) program.overview ?: program.subtitle else program.subtitle
        detail?.let { GuidePaneLine(it, maxLines = if (open) 2 else 1) }
    }
}

@Composable
private fun GuidePaneTitle(text: String) {
    Text(
        text,
        color = TvText,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GuidePaneLine(
    text: String,
    maxLines: Int,
) {
    Text(text, color = TvTextMuted, fontSize = 16.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private val GUIDE_RULER_HEIGHT = 28.dp
private val GUIDE_PANE_HEIGHT = 84.dp
private val NOW_LINE_WIDTH = 2.dp
private const val MILLIS_PER_MINUTE = 60_000L
private const val TV_LIVE_TV_TOGGLE_TARGET = "tv-live-tv-view-toggle"
