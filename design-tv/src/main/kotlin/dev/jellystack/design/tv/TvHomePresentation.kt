@file:Suppress("FunctionName", "FunctionNaming", "TooManyFunctions")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.SpotlightCandidate
import dev.jellystack.core.jellyfin.buildLatestSpotlightCandidates
import dev.jellystack.core.jellyfin.buildSpotlightCandidates
import kotlinx.datetime.Instant

internal enum class TvHomeHeroMode { RECENT, LATEST, LIBRARY, EMPTY }

internal data class TvHomeHeroPresentation(
    val mode: TvHomeHeroMode,
    val candidates: List<SpotlightCandidate>,
)

internal enum class TvHomeCarouselDirection { PREVIOUS, NEXT }

internal data class TvHomeCarouselState(
    val selectedId: String? = null,
)

internal data class TvHomeCarouselManualMove(
    val state: TvHomeCarouselState,
    val openNavigationRail: Boolean = false,
)

internal fun reconcileTvHomeCarouselSelection(
    candidateIds: List<String>,
    currentId: String?,
): String? = currentId?.takeIf { it in candidateIds } ?: candidateIds.firstOrNull()

/** Delay before the unfocused hero advances; null disables auto-advance entirely. */
internal const val TV_CAROUSEL_AUTO_ADVANCE_MS = 8_000L

internal fun tvCarouselAutoAdvanceDelayMs(
    heroFocused: Boolean,
    candidateCount: Int,
    previewActive: Boolean,
): Long? =
    when {
        candidateCount <= 1 -> null
        heroFocused -> null
        previewActive -> null
        else -> TV_CAROUSEL_AUTO_ADVANCE_MS
    }

/** Wrapping next-selection used by the auto-advancer (manual moves clamp instead). */
internal fun advanceTvHomeCarousel(
    candidateIds: List<String>,
    currentId: String?,
): String? {
    if (candidateIds.isEmpty()) return null
    val currentIndex = currentId?.let(candidateIds::indexOf).takeIf { it != null && it >= 0 } ?: -1
    return candidateIds[(currentIndex + 1) % candidateIds.size]
}

internal fun moveTvHomeCarouselManually(
    candidateIds: List<String>,
    state: TvHomeCarouselState,
    direction: TvHomeCarouselDirection,
): TvHomeCarouselManualMove {
    val selectedId =
        reconcileTvHomeCarouselSelection(candidateIds, state.selectedId)
            ?: return TvHomeCarouselManualMove(state.copy(selectedId = null))
    val selectedIndex = candidateIds.indexOf(selectedId)
    return when (direction) {
        TvHomeCarouselDirection.PREVIOUS ->
            if (selectedIndex == 0) {
                TvHomeCarouselManualMove(state.copy(selectedId = selectedId), openNavigationRail = true)
            } else {
                TvHomeCarouselManualMove(state.select(candidateIds[selectedIndex - 1]))
            }
        TvHomeCarouselDirection.NEXT ->
            if (selectedIndex == candidateIds.lastIndex) {
                TvHomeCarouselManualMove(state.copy(selectedId = selectedId))
            } else {
                TvHomeCarouselManualMove(state.select(candidateIds[selectedIndex + 1]))
            }
    }
}

private fun TvHomeCarouselState.select(itemId: String?): TvHomeCarouselState = if (selectedId == itemId) this else copy(selectedId = itemId)

internal fun SpotlightCandidate.tvHomeTrailerPreviewItem() = actionItem

internal fun TvTrailerPreviewState.showsTvHomeHeroPreview(
    itemId: String,
    heroFocused: Boolean,
): Boolean =
    heroFocused &&
        this is TvTrailerPreviewState.Playing &&
        request.owner == TvTrailerPreviewOwner.HERO &&
        request.target.itemId == itemId

internal fun TvTrailerPreviewState.showsTvMediaCardPreview(
    itemId: String,
    presentationId: String?,
): Boolean =
    this is TvTrailerPreviewState.Playing &&
        request.owner == TvTrailerPreviewOwner.CARD &&
        request.target.itemId == itemId &&
        request.presentationId == presentationId

internal sealed interface TvHomeJellyfinDestination {
    data class Detail(
        val item: JellyfinItem,
    ) : TvHomeJellyfinDestination

    data class Library(
        val libraryId: String,
        val title: String,
    ) : TvHomeJellyfinDestination
}

internal fun tvHomeJellyfinDestination(item: JellyfinItem): TvHomeJellyfinDestination =
    if (item.type.equals("CollectionFolder", ignoreCase = true)) {
        TvHomeJellyfinDestination.Library(item.id, item.name)
    } else {
        TvHomeJellyfinDestination.Detail(item)
    }

internal fun buildTvHomeHeroPresentation(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    now: Instant,
): TvHomeHeroPresentation {
    val strict = buildSpotlightCandidates(state.recentShows, state.recentMovies, now)
    val latestFromRecent =
        buildLatestSpotlightCandidates(
            recentShows = state.recentShows,
            recentMovies = state.recentMovies,
        )
    return when {
        strict.isNotEmpty() -> TvHomeHeroPresentation(TvHomeHeroMode.RECENT, strict)
        latestFromRecent.isNotEmpty() -> TvHomeHeroPresentation(TvHomeHeroMode.LATEST, latestFromRecent)
        else -> {
            val localSectionItems =
                (homeSections as? HomeSectionsState.Ready)
                    ?.sections
                    .orEmpty()
                    .flatMap { section -> section.items }
                    .mapNotNull { item ->
                        item.jellyfinItem?.takeIf { item.action == HomeSectionAction.JELLYFIN }
                    }
            val localCandidates =
                listOf(localSectionItems, state.continueWatching, state.nextUp, state.libraryItems)
                    .flatMap { tierItems ->
                        buildLatestSpotlightCandidates(
                            recentShows = emptyList(),
                            recentMovies = emptyList(),
                            additionalItems = tierItems,
                        )
                    }.distinctBy { candidate -> candidate.displayItem.id }
            TvHomeHeroPresentation(
                mode = if (localCandidates.isEmpty()) TvHomeHeroMode.EMPTY else TvHomeHeroMode.LIBRARY,
                candidates = localCandidates,
            )
        }
    }
}

internal data class TvHomeFocusRow(
    val id: String,
    val lazyColumnIndex: Int,
    val itemIds: List<String>,
    val landscape: Boolean,
)

internal enum class TvHomeVerticalDirection { UP, DOWN }

internal sealed interface TvHomeFocusOrigin {
    data object HeroCarousel : TvHomeFocusOrigin

    data object HeroActions : TvHomeFocusOrigin

    data class Row(
        val id: String,
        val itemId: String? = null,
    ) : TvHomeFocusOrigin
}

internal sealed interface TvHomeFocusDestination {
    data object HeroCarousel : TvHomeFocusDestination

    data object HeroPrimary : TvHomeFocusDestination

    data class Row(
        val id: String,
        val lazyColumnIndex: Int,
        val firstItemId: String,
        val horizontalIndex: Int,
    ) : TvHomeFocusDestination
}

internal data class TvHomeFocusMove(
    val requestId: Long,
    val destination: TvHomeFocusDestination,
)

internal data class TvHomeFocusCompletion(
    val requestId: Long,
    val focused: Boolean,
)

internal class TvHomeFocusTargetRegistry<T : Any>(
    private val targetFactory: () -> T,
) {
    private val targets = linkedMapOf<String, LinkedHashMap<String, T>>()

    fun reconcile(rows: List<TvHomeFocusRow>): Map<String, Map<String, T>> {
        targets.keys.retainAll(rows.mapTo(mutableSetOf()) { it.id })
        rows.forEach { row ->
            val rowTargets = targets.getOrPut(row.id) { linkedMapOf() }
            rowTargets.keys.retainAll(row.itemIds.toSet())
            row.itemIds.forEach { itemId -> rowTargets.getOrPut(itemId, targetFactory) }
        }
        return targets.mapValues { (_, rowTargets) -> rowTargets.toMap() }
    }
}

internal class TvHomeVerticalFocusCoordinator(
    rows: List<TvHomeFocusRow>,
) {
    private var rows = rows.nonEmptyRows()
    private var nextRequestId = 0L
    private var pendingMove: TvHomeFocusMove? = null

    fun replaceRows(rows: List<TvHomeFocusRow>): TvHomeFocusMove? {
        val replacement = rows.nonEmptyRows()
        if (this.rows == replacement) return pendingMove
        this.rows = replacement
        pendingMove = pendingMove?.reconcile(replacement)
        return pendingMove
    }

    fun beginMove(
        origin: TvHomeFocusOrigin,
        direction: TvHomeVerticalDirection,
        onAccepted: () -> Unit = {},
    ): TvHomeFocusMove? {
        val destination = destination(origin, direction) ?: return null
        pendingMove?.takeIf { it.destination == destination }?.let { return it }
        pendingMove = null
        onAccepted()
        val requestId = ++nextRequestId
        return TvHomeFocusMove(requestId, destination).also { pendingMove = it }
    }

    fun acceptCompletion(requestId: Long): Boolean {
        if (pendingMove?.requestId != requestId) return false
        pendingMove = null
        return true
    }

    suspend fun completeMove(
        requestId: Long,
        requestTarget: suspend (String) -> Boolean,
    ): TvHomeFocusCompletion? {
        val move = pendingMove?.takeIf { it.requestId == requestId } ?: return null
        val focused =
            when (val destination = move.destination) {
                TvHomeFocusDestination.HeroCarousel -> requestTarget(TV_HOME_HERO_TARGET)
                TvHomeFocusDestination.HeroPrimary -> requestTarget(TV_HOME_PRIMARY_TARGET)
                is TvHomeFocusDestination.Row -> {
                    val row = rows.firstOrNull { it.id == destination.id }
                    val targetIndex =
                        row?.itemIds?.indexOf(destination.firstItemId)?.takeIf { it >= 0 }
                            ?: destination.horizontalIndex
                    row
                        ?.itemIds
                        ?.indices
                        ?.sortedBy { index -> kotlin.math.abs(index - targetIndex) }
                        ?.any { index -> requestTarget(tvHomeCardTargetId(row.id, row.itemIds[index])) }
                        ?: false
                }
            }
        if (pendingMove?.requestId != requestId) return null
        pendingMove = null
        return TvHomeFocusCompletion(requestId, focused)
    }

    private fun destination(
        origin: TvHomeFocusOrigin,
        direction: TvHomeVerticalDirection,
    ): TvHomeFocusDestination? =
        when (origin) {
            TvHomeFocusOrigin.HeroCarousel ->
                if (direction == TvHomeVerticalDirection.DOWN) TvHomeFocusDestination.HeroPrimary else null
            TvHomeFocusOrigin.HeroActions ->
                if (direction == TvHomeVerticalDirection.UP) {
                    TvHomeFocusDestination.HeroCarousel
                } else {
                    rows.firstOrNull()?.destination()
                }
            is TvHomeFocusOrigin.Row -> {
                val currentIndex = rows.indexOfFirst { it.id == origin.id }
                if (currentIndex < 0) {
                    null
                } else if (direction == TvHomeVerticalDirection.UP) {
                    rows.getOrNull(currentIndex - 1)?.destination() ?: TvHomeFocusDestination.HeroPrimary
                } else {
                    rows.getOrNull(currentIndex + 1)?.destination()
                }
            }
        }

    private fun TvHomeFocusMove.reconcile(rows: List<TvHomeFocusRow>): TvHomeFocusMove? {
        val destination = destination as? TvHomeFocusDestination.Row ?: return this
        val row =
            rows.firstOrNull { it.id == destination.id }
                ?: rows.minByOrNull { kotlin.math.abs(it.lazyColumnIndex - destination.lazyColumnIndex) }
                ?: return null
        return copy(destination = row.destination())
    }
}

private fun List<TvHomeFocusRow>.nonEmptyRows(): List<TvHomeFocusRow> = filter { it.itemIds.isNotEmpty() }

private fun TvHomeFocusRow.destination(horizontalIndex: Int = 0): TvHomeFocusDestination.Row {
    val resolvedHorizontalIndex = horizontalIndex.coerceIn(0, itemIds.lastIndex)
    return TvHomeFocusDestination.Row(
        id = id,
        lazyColumnIndex = lazyColumnIndex,
        firstItemId = itemIds[resolvedHorizontalIndex],
        horizontalIndex = resolvedHorizontalIndex,
    )
}
