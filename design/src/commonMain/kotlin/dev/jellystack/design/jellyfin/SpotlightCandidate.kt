package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

internal data class SpotlightCandidate(
    val displayItem: JellyfinItem,
    val actionItem: JellyfinItem,
    val addedAt: Instant,
)

internal fun buildSpotlightCandidates(
    recentShows: List<JellyfinItem>,
    recentMovies: List<JellyfinItem>,
    now: Instant,
    window: Duration = 30.days,
    libraryItems: List<JellyfinItem> = emptyList(),
): List<SpotlightCandidate> {
    val cutoff = now - window

    fun JellyfinItem.datedOrNull(): DatedItem? {
        val addedAt =
            premiereDate
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: dateCreated?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return null
        return DatedItem(this, addedAt).takeIf { addedAt >= cutoff && addedAt <= now }
    }

    val showCandidates =
        (recentShows + libraryItems)
            .asSequence()
            .mapNotNull { item -> item.datedOrNull() }
            .filter { dated ->
                dated.item.type.equals("Series", ignoreCase = true) ||
                    dated.item.type.equals("Season", ignoreCase = true) ||
                    dated.item.type.equals("Episode", ignoreCase = true)
            }.groupBy { dated -> dated.item.spotlightGroupKey() }
            .values
            .map { group ->
                val newest = group.maxBy { it.addedAt }
                SpotlightCandidate(
                    displayItem = newest.item.toSpotlightDisplayItem(),
                    actionItem = newest.item,
                    addedAt = newest.addedAt,
                )
            }

    val movieCandidates =
        (recentMovies + libraryItems)
            .asSequence()
            .mapNotNull { item -> item.datedOrNull() }
            .filter { dated -> dated.item.type.equals("Movie", ignoreCase = true) }
            .map { dated ->
                SpotlightCandidate(
                    displayItem = dated.item,
                    actionItem = dated.item,
                    addedAt = dated.addedAt,
                )
            }.toList()

    return (showCandidates + movieCandidates)
        .distinctBy { candidate -> candidate.displayItem.id }
        .sortedWith(compareByDescending<SpotlightCandidate> { it.addedAt }.thenBy { it.displayItem.name })
}

private data class DatedItem(
    val item: JellyfinItem,
    val addedAt: Instant,
)

private fun JellyfinItem.spotlightGroupKey(): String =
    when {
        type.equals("Episode", ignoreCase = true) && !seasonId.isNullOrBlank() -> "season:$seasonId"
        type.equals("Season", ignoreCase = true) -> "season:$id"
        else -> "series:${seriesId ?: parentId ?: id}"
    }

private fun JellyfinItem.toSpotlightDisplayItem(): JellyfinItem {
    if (type.equals("Series", ignoreCase = true) || type.equals("Season", ignoreCase = true)) return this

    val displayId = seasonId ?: seriesId ?: parentId ?: id
    val seasonLabel =
        parentIndexNumber
            ?.takeIf { it > 0 }
            ?.let { season -> "${seriesName ?: name} - Season $season" }
    val displayName = seasonLabel ?: seriesName ?: name
    return copy(
        id = displayId,
        name = displayName,
        type = if (seasonId != null || parentIndexNumber != null) "Season" else "Series",
        parentId = parentId,
        primaryImageTag = seriesPrimaryImageTag ?: primaryImageTag,
        thumbImageTag = seriesThumbImageTag ?: thumbImageTag,
        backdropImageTag = seriesBackdropImageTag ?: backdropImageTag,
        overview = overview,
    )
}
