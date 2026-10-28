@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

/** Draws [discover], which is never empty: the caller shows another Discover state when no row has a card. */
@Composable
internal fun TvCinematicDiscoverContent(
    discover: TvDiscoverRows,
    hasPartialFailure: Boolean,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onItem: (JellyseerrSearchItem) -> Unit,
) {
    val rows = discover.rows
    val availableKeys = discover.cardIds
    val firstCardId =
        rows
            .firstOrNull()
            ?.cards
            ?.firstOrNull()
            ?.id
    var focusedKey by remember {
        mutableStateOf(
            focusMemory
                .restore("discover")
                ?.anchor
                ?.itemId
                ?.takeIf(availableKeys::contains)
                ?: firstCardId,
        )
    }
    val effectiveFocusedKey = focusedKey?.takeIf(availableKeys::contains) ?: firstCardId
    LaunchedEffect(effectiveFocusedKey) {
        if (focusedKey != effectiveFocusedKey) focusedKey = effectiveFocusedKey
    }
    val focusedRow = effectiveFocusedKey?.let { key -> rows.firstOrNull { row -> row.cards.any { it.id == key } } }
    TvCinematicBrowse(
        state =
            TvCinematicBrowseState(
                hero = TvCinematicHero(title = strings.discover, eyebrow = strings.discover),
                rows = rows,
                focusedAnchor =
                    if (focusedRow != null && effectiveFocusedKey != null) {
                        TvFocusAnchor(focusedRow.id, effectiveFocusedKey, TvFocusDestination.SECTION_ITEM)
                    } else {
                        null
                    },
                inlineStatus =
                    if (hasPartialFailure) {
                        TvCinematicInlineStatus(strings.discoverLoadFailed, TvCinematicStatusKind.ERROR)
                    } else {
                        null
                    },
            ),
        onCardFocused = { anchor, card ->
            focusedKey = card.id
            val row = rows.firstOrNull { it.id == anchor.sectionId }
            focusMemory.remember(
                routeKey = "discover",
                rowKey = anchor.sectionId,
                itemId = anchor.itemId,
                horizontalIndex = row?.cards?.indexOfFirst { it.id == card.id }?.coerceAtLeast(0) ?: 0,
            )
        },
        onCardClick = { card -> discover.item(card.id)?.let(onItem) },
        resetVerticalFocusToFirstCard = true,
    )
}
