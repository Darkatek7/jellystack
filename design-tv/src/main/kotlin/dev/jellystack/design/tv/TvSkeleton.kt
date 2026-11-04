@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Alpha of the placeholders while content loads: a very slow pulse between these, or the middle without motion. */
private const val TV_SKELETON_ALPHA_LOW = 0.55f
private const val TV_SKELETON_ALPHA_HIGH = 0.95f
private const val TV_SKELETON_STATIC_ALPHA = 0.8f
private const val TV_SKELETON_PULSE_MS = 1_800
private const val TV_SKELETON_ROW_CARDS = 8
internal const val TV_SKELETON_ROW_COUNT = 3

/**
 * The alpha of the loading placeholders. Read it inside `graphicsLayer { }` so the pulse is applied in the draw phase
 * and never recomposes anything. It does not move when the user asks for reduced motion.
 */
@Composable
internal fun rememberTvSkeletonAlpha(): State<Float> {
    if (LocalTvFocusAppearance.current.reducedMotion) return remember { mutableFloatStateOf(TV_SKELETON_STATIC_ALPHA) }
    return rememberInfiniteTransition(label = "tv-skeleton").animateFloat(
        initialValue = TV_SKELETON_ALPHA_LOW,
        targetValue = TV_SKELETON_ALPHA_HIGH,
        animationSpec = infiniteRepeatable(tween(TV_SKELETON_PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "tv-skeleton-alpha",
    )
}

/**
 * A flat stand-in for a media card with the card's exact size. It is a plain fill, with no gradient or image, so it is
 * cheap on weak GPUs, and it is neither focusable nor announced: the container that holds it says what is loading.
 */
@Composable
internal fun TvSkeletonCard(
    modifier: Modifier = Modifier,
    format: TvMediaCardFormat = TvMediaCardFormat.LANDSCAPE,
    fillWidth: Boolean = false,
) {
    val alpha = rememberTvSkeletonAlpha()
    // Room for the caption a real card shows below its artwork, so nothing moves when content arrives.
    Column(modifier) {
        Box(
            Modifier
                .tvMediaCardSize(format, fillWidth)
                .graphicsLayer { this.alpha = alpha.value }
                .background(TvSurfaceRaised, TvShapes.Card),
        )
        Spacer(Modifier.height(TV_CARD_CAPTION_HEIGHT))
    }
}

/** Rows of placeholder cards laid out like [TvJellyfinRow]s, announced once as [label]. */
@Composable
internal fun TvSkeletonRows(
    label: String,
    modifier: Modifier = Modifier,
    rowCount: Int = TV_SKELETON_ROW_COUNT,
) {
    Column(
        modifier.tvStatusSemantics(label),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(rowCount) { TvSkeletonRow() }
    }
}

/** One placeholder row: a title bar of the section title's height over cards of the media card's size. */
@Composable
internal fun TvSkeletonRow(
    modifier: Modifier = Modifier,
    format: TvMediaCardFormat = TvMediaCardFormat.LANDSCAPE,
) {
    val alpha = rememberTvSkeletonAlpha()
    Column(modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.padding(horizontal = 6.dp).height(TV_SKELETON_TITLE_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .width(TV_SKELETON_TITLE_WIDTH)
                    .height(TV_SKELETON_TITLE_BAR_HEIGHT)
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(TvSurfaceRaised, TvShapes.Badge),
            )
        }
        LazyRow(
            modifier = Modifier.tvRowBleed(),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(6.dp),
        ) {
            items(TV_SKELETON_ROW_CARDS) { TvSkeletonCard(format = format) }
        }
    }
}

/** Placeholder cells for a card grid, sized like its cards; the first one announces [label]. */
internal fun LazyGridScope.tvSkeletonGridCells(
    label: String,
    count: Int,
    format: TvMediaCardFormat = TvMediaCardFormat.LANDSCAPE,
    keyPrefix: String = "skeleton",
) {
    items(count, key = { "$keyPrefix-$it" }) { index ->
        TvSkeletonCard(
            format = format,
            fillWidth = true,
            modifier = if (index == 0) Modifier.tvStatusSemantics(label) else Modifier,
        )
    }
}

/** Placeholder rows of a vertical list, with the row's height, announced once as [label]. */
@Composable
internal fun TvSkeletonListRows(
    label: String,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    count: Int = TV_SKELETON_LIST_ROWS,
) {
    val alpha = rememberTvSkeletonAlpha()
    Column(modifier.tvStatusSemantics(label), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(TvSurfaceRaised, TvShapes.Control),
            )
        }
    }
}

private const val TV_SKELETON_LIST_ROWS = 8
private val TV_SKELETON_TITLE_HEIGHT = 24.dp
private val TV_SKELETON_TITLE_BAR_HEIGHT = 16.dp
private val TV_SKELETON_TITLE_WIDTH = 200.dp
