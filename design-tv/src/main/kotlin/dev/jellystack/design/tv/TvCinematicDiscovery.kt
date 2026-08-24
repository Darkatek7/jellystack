@file:Suppress("FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

private const val SEARCH_RESULTS_ROW = "search-results"
private const val DISCOVER_REQUESTS_ROW = "discover-requests"

@Composable
internal fun TvCinematicSearchContent(
    searchState: TvSearchUiState,
    presentation: TvSearchPresentation,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    onJellyfinItem: (JellyfinItem) -> Unit,
    onSeerrItem: (JellyseerrSearchItem) -> Unit,
    onPlayJellyfin: (JellyfinItem) -> Unit,
    onToggleJellyfinSaved: ((JellyfinItem) -> Unit)?,
    onToggleJellyfinPlayed: ((JellyfinItem, Boolean) -> Unit)?,
    onToggleSeerrSaved: ((JellyseerrSearchItem) -> Unit)?,
    isJellyfinSaved: (JellyfinItem) -> Boolean,
    isSeerrSaved: (JellyseerrSearchItem) -> Boolean,
    onRetryFailures: (() -> Unit)?,
    headerContent: @Composable () -> Unit,
) {
    val results = presentation.results
    var focusedKey by remember(searchState.session.query, searchState.session.source, results) {
        mutableStateOf(results.first().key)
    }
    val focused = results.firstOrNull { it.key == focusedKey } ?: results.first()
    val cards =
        results.map { result ->
            result.toCinematicCard(homeState, isJellyfinSaved, isSeerrSaved)
        }
    val row = TvCinematicRow(SEARCH_RESULTS_ROW, strings.search, cards)
    val status = searchInlineStatus(presentation, strings)
    TvCinematicBrowse(
        state =
            TvCinematicBrowseState(
                hero = TvCinematicHero(title = strings.search, overview = searchState.session.query),
                rows = listOf(row),
                focusedAnchor = TvFocusAnchor(SEARCH_RESULTS_ROW, focused.key, TvFocusDestination.SECTION_ITEM),
                inlineStatus = status,
            ),
        actionLabels = cinematicActionLabels(strings),
        onCardFocused = { anchor, card ->
            focusedKey = card.id
            val index = cards.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
            focusMemory.remember("search", anchor.sectionId, anchor.itemId, horizontalIndex = index)
        },
        onCardClick = { card ->
            results.firstOrNull { it.key == card.id }?.open(onJellyfinItem, onSeerrItem)
        },
        selectedItemActions =
            focused.actions(
                strings = strings,
                onJellyfinItem = onJellyfinItem,
                onSeerrItem = onSeerrItem,
                onPlayJellyfin = onPlayJellyfin,
                onToggleJellyfinSaved = onToggleJellyfinSaved,
                onToggleJellyfinPlayed = onToggleJellyfinPlayed,
                onToggleSeerrSaved = onToggleSeerrSaved,
            ),
        headerContent = headerContent,
        inlineStatusAction =
            onRetryFailures?.let { retry ->
                {
                    TvActionButton(
                        label = strings.retry,
                        onClick = retry,
                        focusTargetId =
                            if (presentation.showJellyfinFailure) {
                                TV_SEARCH_JELLYFIN_RETRY_TARGET
                            } else {
                                TV_SEARCH_SEERR_RETRY_TARGET
                            },
                    )
                }
            },
    )
}

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
    var focusedKey by remember(rows) {
        mutableStateOf(
            rows
                .first()
                .cards
                .first()
                .id,
        )
    }
    val focusedItem = itemsByKey[focusedKey] ?: itemsByKey.values.first()
    val focusedRow = rows.firstOrNull { row -> row.cards.any { it.id == focusedKey } } ?: rows.first()
    TvCinematicBrowse(
        state =
            TvCinematicBrowseState(
                hero = TvCinematicHero(title = strings.discover),
                rows = rows,
                focusedAnchor = TvFocusAnchor(focusedRow.id, focusedKey, TvFocusDestination.SECTION_ITEM),
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

private fun TvSearchResult.toCinematicCard(
    homeState: JellyfinHomeState,
    isJellyfinSaved: (JellyfinItem) -> Boolean,
    isSeerrSaved: (JellyseerrSearchItem) -> Boolean,
): TvCinematicCard {
    val jellyfin = jellyfinItem
    val seerr = seerrItem
    val artwork = jellyfin?.let(::resolveTvJellyfinArtwork)
    return TvCinematicCard(
        id = key,
        title = jellyfin?.name ?: requireNotNull(seerr).title,
        subtitle = jellyfin?.subtitleText() ?: seerr?.releaseYear,
        artworkUrl =
            if (jellyfin != null) {
                jellyfinImageUrl(homeState.imageBaseUrl, homeState.imageAccessToken, artwork)
            } else {
                tmdbImageUrl(seerr?.backdropPath ?: seerr?.posterPath, backdrop = seerr?.backdropPath != null)
            },
        backdropUrl =
            if (jellyfin != null) {
                jellyfinImageUrl(homeState.imageBaseUrl, homeState.imageAccessToken, artwork, TvArtworkSize.HERO)
            } else {
                tmdbImageUrl(seerr?.backdropPath ?: seerr?.posterPath, backdrop = seerr?.backdropPath != null)
            },
        selected = jellyfin?.let(isJellyfinSaved) ?: seerr?.let(isSeerrSaved) == true,
        played = (jellyfin?.playedPercentage ?: 0.0) >= 99.5,
        resumeFraction =
            jellyfin
                ?.playedPercentage
                ?.takeIf { it in 0.1..99.4 }
                ?.div(100.0)
                ?.toFloat(),
    )
}

private fun JellyseerrSearchItem.toCinematicCard(
    key: String,
    selected: Boolean,
): TvCinematicCard =
    TvCinematicCard(
        id = key,
        title = title,
        subtitle = releaseYear,
        artworkUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        backdropUrl = tmdbImageUrl(backdropPath ?: posterPath, backdrop = backdropPath != null),
        selected = selected,
    )

private fun TvSearchResult.open(
    onJellyfinItem: (JellyfinItem) -> Unit,
    onSeerrItem: (JellyseerrSearchItem) -> Unit,
) {
    jellyfinItem?.let(onJellyfinItem) ?: seerrItem?.let(onSeerrItem)
}

private fun TvSearchResult.actions(
    strings: TvStrings,
    onJellyfinItem: (JellyfinItem) -> Unit,
    onSeerrItem: (JellyseerrSearchItem) -> Unit,
    onPlayJellyfin: (JellyfinItem) -> Unit,
    onToggleJellyfinSaved: ((JellyfinItem) -> Unit)?,
    onToggleJellyfinPlayed: ((JellyfinItem, Boolean) -> Unit)?,
    onToggleSeerrSaved: ((JellyseerrSearchItem) -> Unit)?,
): TvSelectedItemActions {
    val jellyfin = jellyfinItem
    val seerr = seerrItem
    return TvSelectedItemActions(
        onPlayOrResume = {
            if (jellyfin != null) onPlayJellyfin(jellyfin) else seerr?.let(onSeerrItem)
        },
        onDetails = { open(onJellyfinItem, onSeerrItem) },
        onToggleSaved =
            if (jellyfin != null) {
                onToggleJellyfinSaved?.let { toggle -> { toggle(jellyfin) } }
            } else {
                seerr?.let { item -> onToggleSeerrSaved?.let { toggle -> { toggle(item) } } }
            },
        onTogglePlayed =
            jellyfin?.let { item ->
                onToggleJellyfinPlayed?.let { toggle ->
                    { toggle(item, (item.playedPercentage ?: 0.0) < 99.5) }
                }
            },
        primaryLabel = if (jellyfin == null) strings.request else null,
    )
}

private fun searchInlineStatus(
    presentation: TvSearchPresentation,
    strings: TvStrings,
): TvCinematicInlineStatus? {
    val failures =
        buildList {
            if (presentation.showJellyfinFailure) add(strings.jellyfinSearchFailed)
            if (presentation.showSeerrFailure) add(strings.seerrSearchFailed)
        }
    return when {
        failures.isNotEmpty() -> TvCinematicInlineStatus(failures.joinToString(" "), TvCinematicStatusKind.ERROR)
        presentation.showSearching -> TvCinematicInlineStatus(strings.searching, TvCinematicStatusKind.LOADING)
        else -> null
    }
}

private fun cinematicActionLabels(strings: TvStrings): TvSelectedItemActionLabels =
    TvSelectedItemActionLabels(
        play = strings.play,
        resume = strings.continueLabel,
        details = strings.details,
        addToList = strings.addToMyList,
        removeFromList = strings.removeFromMyList,
        markPlayed = strings.markPlayed,
        markUnplayed = strings.markUnplayed,
    )

internal fun JellyseerrSearchItem.cinematicKey(): String = "${mediaType.name.lowercase()}:$tmdbId"
