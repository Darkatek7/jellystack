@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.Instant
import java.util.Date
import java.util.TimeZone

/** The zone the guide shows its times in; the screenshot tests pin it. */
internal val LocalTvGuideTimeZone = staticCompositionLocalOf { TimeZone.getDefault() }

/** The header button of the screen and the grid reach each other through this: the grid sets [enter] while it shows. */
internal class TvLiveTvGuideEntry(
    val toggleRequester: FocusRequester,
) {
    var enter: (() -> Unit)? = null
}

/** The wall clock and how the guide writes a time of it. */
internal class TvLiveTvGuideClock(
    val nowMillis: () -> Long,
    val formatTime: (Instant) -> String,
)

/** What every row and cell of the grid shares; remembered, so rows skip recomposition while it is unchanged. */
internal class TvLiveTvGuideEnv(
    val guide: TvLiveTvGuideState,
    val minuteWidth: Dp,
    val context: TvLiveTvContext,
    val clock: TvLiveTvGuideClock,
    val onPlay: (LiveTvChannel) -> Unit,
) {
    val gridWidth: Dp = minuteWidth * GUIDE_VISIBLE_MINUTES
    val copy: TvLiveTvStrings get() = context.strings.messages.liveTv

    fun timeRange(program: LiveTvProgram): String {
        val format = clock.formatTime
        return copy.timeRange(format(program.start), format(program.end))
    }
}

/**
 * The guide grid: a time ruler, one row per channel (number, logo, name, then its programs as cells sized by their
 * duration), the now line over the rows, and a pane for the focused program.
 *
 * **Why a LazyColumn of plain rows.** The grid shows a window of three hours and moves by half hours. Each row is one
 * `Row` with the few cells (about six) that fall in the window, and a step recomposes the rows on screen with the
 * cells of the new window. A `LazyRow` per row would need a scroll offset shared between rows (every row measures and
 * scrolls on each frame of a move) and keep cells off screen composed; a plain `Row` is a single measure pass, and a
 * step is one jump with no animation to drive. Focus moves by the keys (see [guideMove]), not by the focus search of
 * the framework, which would pick the geometrically nearest cell instead of the one at the same time.
 */
@Composable
internal fun TvLiveTvGuide(
    state: TvLiveTvState.Ready,
    context: TvLiveTvContext,
    actions: TvLiveTvActions,
    entry: TvLiveTvGuideEntry,
    modifier: Modifier = Modifier,
) {
    val nowMillis = LocalTvNowMillis.current
    val zone = LocalTvGuideTimeZone.current
    val origin =
        remember(zone) {
            val now = nowMillis()
            guideOrigin(Instant.fromEpochMilliseconds(now), zone.getOffset(now) / MILLIS_PER_SECOND)
        }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val registry = remember(scope) { GuideFocusRegistry(scope) }
    val guide = remember(origin, registry) { TvLiveTvGuideState(origin, actions.guide.memory, registry) }
    val channels = state.channels
    val railOpener = LocalTvNavigationRailOpener.current
    SideEffect {
        guide.update(channels.size) { row -> state.guide[channels[row].id].orEmpty() }
        guide.exits =
            GuideExits(
                scrollToRow = { scrollRowIntoComposition(listState, scope, it) },
                toHeader = { entry.toggleRequester.requestFocus() },
                toRail = { railOpener?.invoke() != null },
            )
    }
    val remembered = actions.guide.memory
    LaunchedEffect(entry, guide) {
        entry.enter = { guide.enter(listState.firstVisibleItemIndex, nowMinutes(guide.window, nowMillis())) }
        val left = remembered.focus?.takeIf { remembered.origin == origin }
        if (left != null) {
            listState.scrollToItem(left.row.coerceIn(0, channels.size - 1))
            guide.enter(0, 0f)
        }
    }
    GuideDataRequests(listState, channels, guide, actions)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val env = rememberGuideEnv(guide, (maxWidth - GUIDE_CHANNEL_WIDTH) / GUIDE_VISIBLE_MINUTES, context, actions)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvLiveTvGuideRuler(env)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("tv-live-tv-guide")
                            .onPreviewKeyEvent { event -> event.guideDirection()?.let(guide::onDirection) ?: false },
                    verticalArrangement = Arrangement.spacedBy(GUIDE_ROW_GAP),
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { index, _ ->
                        TvLiveTvGuideRow(index, state, env)
                    }
                }
                TvLiveTvGuideNowLine(env, Modifier.padding(start = GUIDE_CHANNEL_WIDTH))
            }
            TvLiveTvGuidePane(channels, env)
        }
    }
}

@Composable
private fun rememberGuideEnv(
    guide: TvLiveTvGuideState,
    minuteWidth: Dp,
    context: TvLiveTvContext,
    actions: TvLiveTvActions,
): TvLiveTvGuideEnv {
    val androidContext = LocalContext.current
    val zone = LocalTvGuideTimeZone.current
    val nowMillis = LocalTvNowMillis.current
    val clock =
        remember(androidContext, zone, nowMillis) {
            val format = DateFormat.getTimeFormat(androidContext).apply { timeZone = zone }
            TvLiveTvGuideClock(nowMillis) { format.format(Date(it.toEpochMilliseconds())) }
        }
    return remember(guide, minuteWidth, context, clock, actions) {
        TvLiveTvGuideEnv(guide, minuteWidth, context, clock, actions.onPlay)
    }
}

/** Asks for the programs of the rows on screen (and one row either side) in the window, once scrolling has settled. */
@Composable
private fun GuideDataRequests(
    listState: LazyListState,
    channels: List<LiveTvChannel>,
    guide: TvLiveTvGuideState,
    actions: TvLiveTvActions,
) {
    LaunchedEffect(listState, channels, guide) {
        snapshotFlow {
            val shown = listState.layoutInfo.visibleItemsInfo.map { it.index }
            Triple(shown.minOrNull() ?: 0, shown.maxOrNull() ?: -1, guide.window)
        }.collectLatest { (first, last, window) ->
            delay(GUIDE_SETTLE_MS)
            val ids = (first - 1..last + 1).mapNotNull { channels.getOrNull(it)?.id }
            actions.guide.onWindow(ids, window.origin, window.loadUntil)
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.guideDirection(): GuideDirection? {
    if (nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return null
    return when (nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GuideDirection.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GuideDirection.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GuideDirection.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GuideDirection.RIGHT
        else -> null
    }
}

private fun nowMinutes(
    window: GuideWindow,
    nowMillis: Long,
): Float = window.minutesFromOrigin(Instant.fromEpochMilliseconds(nowMillis))

internal val GUIDE_CHANNEL_WIDTH = 168.dp
internal val GUIDE_ROW_HEIGHT = 56.dp
internal val GUIDE_ROW_GAP = 4.dp
private const val GUIDE_SETTLE_MS = 250L
private const val MILLIS_PER_SECOND = 1000
