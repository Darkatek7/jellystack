package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
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

internal enum class TvHomeCarouselFocus { NONE, CONTAINER, ACTION }

internal data class TvHomeCarouselState(
    val selectedId: String? = null,
    val intervalRevision: Long = 0,
)

internal data class TvHomeCarouselManualMove(
    val state: TvHomeCarouselState,
    val openNavigationRail: Boolean = false,
)

internal fun reconcileTvHomeCarouselSelection(
    candidateIds: List<String>,
    currentId: String?,
): String? = currentId?.takeIf { it in candidateIds } ?: candidateIds.firstOrNull()

internal fun moveTvHomeCarouselSelection(
    candidateIds: List<String>,
    currentId: String?,
    direction: TvHomeCarouselDirection,
): String? {
    val selectedId = reconcileTvHomeCarouselSelection(candidateIds, currentId) ?: return null
    val selectedIndex = candidateIds.indexOf(selectedId)
    return when (direction) {
        TvHomeCarouselDirection.NEXT -> candidateIds[(selectedIndex + 1) % candidateIds.size]
        TvHomeCarouselDirection.PREVIOUS -> candidateIds[(selectedIndex - 1 + candidateIds.size) % candidateIds.size]
    }
}

internal fun advanceTvHomeCarouselAutomatically(
    candidateIds: List<String>,
    state: TvHomeCarouselState,
): TvHomeCarouselState {
    val nextId =
        moveTvHomeCarouselSelection(
            candidateIds = candidateIds,
            currentId = state.selectedId,
            direction = TvHomeCarouselDirection.NEXT,
        )
    return state.select(nextId)
}

internal fun moveTvHomeCarouselManually(
    candidateIds: List<String>,
    state: TvHomeCarouselState,
    direction: TvHomeCarouselDirection,
): TvHomeCarouselManualMove {
    val selectedId = reconcileTvHomeCarouselSelection(candidateIds, state.selectedId)
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

private fun TvHomeCarouselState.select(itemId: String?): TvHomeCarouselState =
    if (selectedId == itemId) this else copy(selectedId = itemId, intervalRevision = intervalRevision + 1)

internal fun shouldAutoCycleTvHomeCarousel(
    enabled: Boolean,
    candidateCount: Int,
    railOpen: Boolean,
    focus: TvHomeCarouselFocus,
    previewState: TvTrailerPreviewState,
): Boolean {
    val canStart = enabled && candidateCount > 1
    val previewAllowsRotation =
        when (previewState) {
            TvTrailerPreviewState.Idle,
            is TvTrailerPreviewState.Armed,
            is TvTrailerPreviewState.Playing,
            is TvTrailerPreviewState.Unavailable,
            -> true
        }
    val paused = railOpen || focus == TvHomeCarouselFocus.ACTION
    return canStart && previewAllowsRotation && !paused
}

internal fun SpotlightCandidate.tvHomeTrailerPreviewItem() = actionItem

internal fun TvTrailerPreviewState.showsTvHomeHeroPreview(
    itemId: String,
    heroFocused: Boolean,
): Boolean =
    heroFocused &&
        this is TvTrailerPreviewState.Playing &&
        request.owner == TvTrailerPreviewOwner.HERO &&
        request.target.itemId == itemId

internal fun TvTrailerPreviewState.showsTvMediaCardPreview(itemId: String): Boolean =
    this is TvTrailerPreviewState.Playing &&
        request.owner == TvTrailerPreviewOwner.CARD &&
        request.target.itemId == itemId

internal fun tvHomeCarouselIntervalMillis(intervalSeconds: Int): Long = intervalSeconds.toLong() * 1_000L

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
    val firstItemId: String?,
    val landscape: Boolean,
)

internal enum class TvHomeVerticalDirection { UP, DOWN }

internal sealed interface TvHomeFocusOrigin {
    data object HeroCarousel : TvHomeFocusOrigin

    data object HeroActions : TvHomeFocusOrigin

    data class Row(
        val id: String,
    ) : TvHomeFocusOrigin
}

internal sealed interface TvHomeFocusDestination {
    data object HeroCarousel : TvHomeFocusDestination

    data object HeroPrimary : TvHomeFocusDestination

    data class Row(
        val id: String,
        val lazyColumnIndex: Int,
        val firstItemId: String,
    ) : TvHomeFocusDestination
}

internal data class TvHomeFocusMove(
    val requestId: Long,
    val destination: TvHomeFocusDestination,
)

internal class TvHomeVerticalFocusCoordinator(
    rows: List<TvHomeFocusRow>,
) {
    private var rows = rows.nonEmptyRows()
    private var nextRequestId = 0L
    private var pendingRequestId: Long? = null

    fun replaceRows(rows: List<TvHomeFocusRow>) {
        this.rows = rows.nonEmptyRows()
        pendingRequestId = null
    }

    fun beginMove(
        origin: TvHomeFocusOrigin,
        direction: TvHomeVerticalDirection,
        onAccepted: () -> Unit = {},
    ): TvHomeFocusMove? {
        pendingRequestId = null
        val destination = destination(origin, direction) ?: return null
        onAccepted()
        val requestId = ++nextRequestId
        pendingRequestId = requestId
        return TvHomeFocusMove(requestId, destination)
    }

    fun acceptCompletion(requestId: Long): Boolean {
        if (pendingRequestId != requestId) return false
        pendingRequestId = null
        return true
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
}

private fun List<TvHomeFocusRow>.nonEmptyRows(): List<TvHomeFocusRow> = filter { !it.firstItemId.isNullOrBlank() }

private fun TvHomeFocusRow.destination(): TvHomeFocusDestination.Row =
    TvHomeFocusDestination.Row(
        id = id,
        lazyColumnIndex = lazyColumnIndex,
        firstItemId = requireNotNull(firstItemId),
    )
