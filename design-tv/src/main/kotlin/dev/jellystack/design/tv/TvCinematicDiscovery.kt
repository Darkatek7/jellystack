@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

internal const val DISCOVER_REQUESTS_ROW = "discover-requests"

@Composable
internal fun TvCinematicDiscoverContent(
    state: JellyseerrRecommendationsState.Ready,
    requestItems: List<JellyseerrRequestSummary>,
    hasPartialFailure: Boolean,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onItem: (JellyseerrSearchItem) -> Unit,
    onToggleSaved: ((JellyseerrSearchItem) -> Unit)?,
    isSaved: (JellyseerrSearchItem) -> Boolean,
) {
    val itemsByKey = linkedMapOf<String, JellyseerrSearchItem>()
    val rows =
        buildList {
            JellyseerrRecommendationRail.entries.forEach { rail ->
                val items = state.rails[rail]?.items.orEmpty()
                if (items.isNotEmpty()) {
                    val rowId = "discover-${rail.name.lowercase()}"
                    val cards =
                        items.map { item ->
                            val key = item.cinematicKey()
                            itemsByKey[key] = item
                            item.toCinematicCard(key, isSaved(item))
                        }
                    add(TvCinematicRow(rowId, rail.label(strings), cards))
                }
            }
            val requests = requestItems.mapNotNull { it.toSearchItem() }
            if (requests.isNotEmpty()) {
                val cards =
                    requests.map { item ->
                        val key = "request:${item.cinematicKey()}"
                        itemsByKey[key] = item
                        item.toCinematicCard(key, isSaved(item))
                    }
                add(TvCinematicRow(DISCOVER_REQUESTS_ROW, strings.requests, cards))
            }
        }
    val availableKeys = itemsByKey.keys
    var focusedKey by remember {
        mutableStateOf(
            focusMemory
                .restore("discover")
                ?.anchor
                ?.itemId
                ?.takeIf(availableKeys::contains)
                ?: rows
                    .first()
                    .cards
                    .first()
                    .id,
        )
    }
    val effectiveFocusedKey =
        focusedKey.takeIf(availableKeys::contains) ?: rows
            .first()
            .cards
            .first()
            .id
    LaunchedEffect(effectiveFocusedKey) {
        if (focusedKey != effectiveFocusedKey) focusedKey = effectiveFocusedKey
    }
    val focusedItem = requireNotNull(itemsByKey[effectiveFocusedKey])
    val focusedRow = rows.first { row -> row.cards.any { it.id == effectiveFocusedKey } }
    TvCinematicBrowse(
        state =
            TvCinematicBrowseState(
                hero = TvCinematicHero(title = strings.discover),
                rows = rows,
                focusedAnchor = TvFocusAnchor(focusedRow.id, effectiveFocusedKey, TvFocusDestination.SECTION_ITEM),
                inlineStatus =
                    if (hasPartialFailure) {
                        TvCinematicInlineStatus(strings.discoverLoadFailed, TvCinematicStatusKind.ERROR)
                    } else {
                        null
                    },
            ),
        actionLabels = cinematicActionLabels(strings),
        onCardFocused = { anchor, card ->
            focusedKey = card.id
            val row = rows.first { it.id == anchor.sectionId }
            focusMemory.remember(
                routeKey = "discover",
                rowKey = anchor.sectionId,
                itemId = anchor.itemId,
                horizontalIndex = row.cards.indexOfFirst { it.id == card.id }.coerceAtLeast(0),
            )
        },
        onCardClick = { card -> itemsByKey[card.id]?.let(onItem) },
        showFocusedMetadata = true,
        resetVerticalFocusToFirstCard = true,
        selectedItemActions =
            TvSelectedItemActions(
                onPlayOrResume = { onItem(focusedItem) },
                onDetails = { onItem(focusedItem) },
                onToggleSaved = onToggleSaved?.let { toggle -> { toggle(focusedItem) } },
                onTogglePlayed = null,
                primaryLabel = strings.request,
            ),
    )
}

internal fun hasCinematicDiscoverContent(
    state: JellyseerrRecommendationsState.Ready?,
    requestItems: List<JellyseerrRequestSummary>,
): Boolean =
    state != null &&
        (state.rails.values.any { it.items.isNotEmpty() } || requestItems.any { it.tmdbId != null })

private fun JellyseerrSearchItem.toCinematicCard(
    key: String,
    selected: Boolean,
): TvCinematicCard =
    TvCinematicCard(
        id = key,
        title = title,
        subtitle = releaseYear,
        overview = overview,
        artworkUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        backdropUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        selected = selected,
    )

private fun cinematicActionLabels(strings: TvStrings): TvSelectedItemActionLabels =
    TvSelectedItemActionLabels(
        play = strings.play,
        resume = strings.continueLabel,
        details = strings.details,
        addToList = strings.addToMyList,
        removeFromList = strings.removeFromMyList,
        markPlayed = strings.markPlayed,
        markUnplayed = strings.markUnplayed,
        trailer = strings.trailer,
    )

internal fun JellyseerrSearchItem.cinematicKey(): String = "${mediaType.name.lowercase()}:$tmdbId"
