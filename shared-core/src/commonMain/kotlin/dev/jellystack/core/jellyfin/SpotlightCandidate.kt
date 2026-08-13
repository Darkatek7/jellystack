package dev.jellystack.core.jellyfin

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class SpotlightCandidate(
    val displayItem: JellyfinItem,
    val actionItem: JellyfinItem,
    val addedAt: Instant,
)

fun buildSpotlightCandidates(
    recentShows: List<JellyfinItem>,
    recentMovies: List<JellyfinItem>,
    now: Instant,
    window: Duration = 30.days,
    libraryItems: List<JellyfinItem> = emptyList(),
): List<SpotlightCandidate> {
    val cutoff = now - window

    return buildGroupedSpotlightCandidates(
        ((recentShows + libraryItems).filter { it.type.isSpotlightShowType() } +
            (recentMovies + libraryItems).filter { it.type.equals("Movie", ignoreCase = true) }).mapNotNull { item ->
            item.recencyOrNull()?.takeIf { it >= cutoff && it <= now }?.let { DatedItem(item, it) }
        },
    ).map { it.candidate }
        .sortedWith(compareByDescending<SpotlightCandidate> { it.addedAt }.thenBy { it.displayItem.name })
}

fun buildLatestSpotlightCandidates(
    recentShows: List<JellyfinItem>,
    recentMovies: List<JellyfinItem>,
    additionalItems: List<JellyfinItem> = emptyList(),
): List<SpotlightCandidate> =
    buildGroupedSpotlightCandidates(
        (recentShows + recentMovies + additionalItems).map { item -> DatedItem(item, item.recencyOrNull()) },
    ).let { groupedCandidates ->
        groupedCandidates.filter { it.hasRecency }.map { it.candidate } +
            groupedCandidates.filterNot { it.hasRecency }.map { it.candidate }
    }

private data class DatedItem(
    val item: JellyfinItem,
    val addedAt: Instant?,
)

private data class GroupedSpotlightCandidate(
    val candidate: SpotlightCandidate,
    val hasRecency: Boolean,
)

private fun buildGroupedSpotlightCandidates(items: List<DatedItem>): List<GroupedSpotlightCandidate> {
    val groups = linkedMapOf<String, MutableList<DatedItem>>()
    items.forEach { dated ->
        val groupKey = dated.item.spotlightCandidateGroupKey() ?: return@forEach
        groups.getOrPut(groupKey) { mutableListOf() }.add(dated)
    }

    return groups.values
        .map { group ->
            val selected =
                if (group.first().item.type.equals("Movie", ignoreCase = true)) {
                    group.first()
                } else {
                    group.maxWith(compareBy<DatedItem> { it.addedAt != null }.thenBy { it.addedAt })
                }
            GroupedSpotlightCandidate(
                candidate =
                    SpotlightCandidate(
                        displayItem = selected.item.toSpotlightDisplayItem(),
                        actionItem = selected.item,
                        addedAt = selected.addedAt ?: Instant.DISTANT_PAST,
                    ),
                hasRecency = selected.addedAt != null,
            )
        }.distinctBy { groupedCandidate -> groupedCandidate.candidate.displayItem.id }
}

private fun JellyfinItem.recencyOrNull(): Instant? =
    dateCreated
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: premiereDate?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun JellyfinItem.spotlightCandidateGroupKey(): String? =
    when {
        type.isSpotlightShowType() -> "show:${spotlightGroupKey()}"
        type.equals("Movie", ignoreCase = true) -> "movie:$id"
        else -> null
    }

private fun String.isSpotlightShowType(): Boolean =
    equals("Series", ignoreCase = true) ||
        equals("Season", ignoreCase = true) ||
        equals("Episode", ignoreCase = true)

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
        logoImageTag = seriesLogoImageTag ?: logoImageTag,
        artImageTag = seriesArtImageTag ?: artImageTag,
        bannerImageTag = seriesBannerImageTag ?: bannerImageTag,
        overview = overview,
    )
}
