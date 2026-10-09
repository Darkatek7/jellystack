package dev.jellystack.design.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner shapes of the TV UI. New and touched code uses these instead of inline radii. */
internal object TvShapes {
    /** Fully rounded buttons, chips, and status pills. */
    val Pill = RoundedCornerShape(50)

    /** Small badges on artwork. */
    val Badge = RoundedCornerShape(8.dp)

    /** Default focus shape of generic controls. */
    val Control = RoundedCornerShape(16.dp)

    /** Media cards and compact action tiles. */
    val Card = RoundedCornerShape(18.dp)

    /** Setting tiles and grouped surfaces. */
    val Surface = RoundedCornerShape(20.dp)

    /** Side panels and large containers. */
    val Panel = RoundedCornerShape(26.dp)

    /** Dialogs. */
    val Dialog = RoundedCornerShape(28.dp)
}
