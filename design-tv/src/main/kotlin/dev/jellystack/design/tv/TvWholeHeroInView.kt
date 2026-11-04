package dev.jellystack.design.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Answers every bring-into-view request from inside the detail hero with the whole hero.
 *
 * The TV list otherwise pivots the focused control to 30 % of the screen, so moving from the primary action to
 * "Start over" scrolled the title off the top. Asking for the whole hero keeps the page at its top instead.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class TvWholeHeroInView : BringIntoViewResponder {
    var size: IntSize = IntSize.Zero

    override fun calculateRectForParent(localRect: Rect): Rect = Rect(Offset.Zero, size.toSize())

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tvWholeHeroInView(responder: TvWholeHeroInView): Modifier =
    onSizeChanged { responder.size = it }.bringIntoViewResponder(responder)
