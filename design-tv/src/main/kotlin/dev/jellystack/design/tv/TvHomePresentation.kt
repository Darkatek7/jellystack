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
    data object Hero : TvHomeFocusOrigin

    data class Row(val id: String) : TvHomeFocusOrigin
}

internal sealed interface TvHomeFocusDestination {
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

internal class TvHomeVerticalFocusCoordinator(rows: List<TvHomeFocusRow>) {
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
            TvHomeFocusOrigin.Hero ->
                if (direction == TvHomeVerticalDirection.DOWN) rows.firstOrNull()?.destination() else null
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

private fun List<TvHomeFocusRow>.nonEmptyRows(): List<TvHomeFocusRow> =
    filter { !it.firstItemId.isNullOrBlank() }

private fun TvHomeFocusRow.destination(): TvHomeFocusDestination.Row =
    TvHomeFocusDestination.Row(
        id = id,
        lazyColumnIndex = lazyColumnIndex,
        firstItemId = requireNotNull(firstItemId),
    )
