package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

internal val TvStatusKey = SemanticsPropertyKey<Boolean>("TvStatus")
private var SemanticsPropertyReceiver.tvStatus by TvStatusKey

internal fun Modifier.tvHeading(): Modifier = semantics { heading() }

internal fun Modifier.tvStatusSemantics(
    label: String,
    announce: Boolean = true,
    asHeading: Boolean = false,
): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = label
        tvStatus = true
        if (announce) liveRegion = LiveRegionMode.Polite
        if (asHeading) heading()
    }

@Composable
internal fun TvStatusAnchor(
    label: String,
    modifier: Modifier = Modifier,
    announce: Boolean = true,
    asHeading: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TvLayoutTokens.MinimumActionSize)
                .tvStatusSemantics(label, announce, asHeading),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = TvTextMuted, fontSize = 18.sp)
    }
}

internal fun tvRatingLabel(rating: Double?): String? =
    rating
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { "★ %.1f".format(it) }
