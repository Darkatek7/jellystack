@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal const val TV_HOME_HERO_INDICATOR_TAG = "tv-home-hero-indicator"

/** Zero-based [index] of the shown spotlight among [count]. */
internal data class TvHomeHeroPage(
    val index: Int,
    val count: Int,
)

/** How many dots show at most; longer carousels slide a window of this size along their pages. */
internal const val TV_HOME_HERO_INDICATOR_MAX_DOTS = 5

internal enum class TvHeroIndicatorDotSize { SELECTED, NORMAL, SMALL }

/** One dot of the indicator for the page at [pageIndex]. */
internal data class TvHeroIndicatorDot(
    val pageIndex: Int,
    val size: TvHeroIndicatorDotSize,
)

/**
 * The dots to draw: one per page up to [TV_HOME_HERO_INDICATOR_MAX_DOTS], else a window that follows the selected
 * page, whose outer dots are small where more pages lie beyond it.
 */
internal fun tvHomeHeroIndicatorDots(page: TvHomeHeroPage): List<TvHeroIndicatorDot> {
    val shown = minOf(page.count, TV_HOME_HERO_INDICATOR_MAX_DOTS)
    val start = (page.index - shown / 2).coerceIn(0, page.count - shown)
    return (start until start + shown).map { index ->
        val size =
            when {
                index == page.index -> TvHeroIndicatorDotSize.SELECTED
                (index == start && start > 0) || (index == start + shown - 1 && start + shown < page.count) ->
                    TvHeroIndicatorDotSize.SMALL
                else -> TvHeroIndicatorDotSize.NORMAL
            }
        TvHeroIndicatorDot(index, size)
    }
}

/** Page dots of the home spotlight; the spotlight card around them pages with Left and Right. */
@Composable
internal fun TvHomeHeroIndicator(
    page: TvHomeHeroPage,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(TV_HOME_HERO_INDICATOR_TAG)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tvHomeHeroIndicatorDots(page).forEach { dot ->
            Box(
                Modifier
                    .size(
                        width =
                            when (dot.size) {
                                TvHeroIndicatorDotSize.SELECTED -> 20.dp
                                TvHeroIndicatorDotSize.NORMAL -> 7.dp
                                TvHeroIndicatorDotSize.SMALL -> 5.dp
                            },
                        height = if (dot.size == TvHeroIndicatorDotSize.SMALL) 5.dp else 7.dp,
                    ).background(
                        if (dot.size == TvHeroIndicatorDotSize.SELECTED) TvText else TvTextMuted.copy(alpha = 0.55f),
                        TvShapes.Pill,
                    ),
            )
        }
    }
}
