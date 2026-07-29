package dev.jellystack.core.jellyfin

import kotlinx.datetime.Instant

enum class SeriesPlaybackReason {
    CONTINUE,
    PLAY,
    RESTART,
}

data class SeriesPlaybackTarget(
    val episode: JellyfinItem,
    val reason: SeriesPlaybackReason,
)

class SeriesPlaybackTargetResolver {
    fun resolve(
        episodes: List<JellyfinItem>,
        completedDownloadIds: Set<String>,
    ): SeriesPlaybackTarget? {
        val available =
            episodes
                .asSequence()
                .filter { it.type.equals("Episode", ignoreCase = true) }
                .filter { it.id in completedDownloadIds || it.hasAvailableLocation() }
                .sortedWith(episodeComparator)
                .toList()
        if (available.isEmpty()) return null

        val inProgress =
            available
                .filter { (it.playedPercentage ?: 0.0) > 0.0 && (it.playedPercentage ?: 0.0) < 98.0 }
                .maxWithOrNull(compareBy<JellyfinItem> { it.lastPlayedInstant() }.then(episodeComparator))
        if (inProgress != null) {
            return SeriesPlaybackTarget(inProgress, SeriesPlaybackReason.CONTINUE)
        }

        val unwatched = available.firstOrNull { (it.playedPercentage ?: 0.0) <= 0.0 }
        if (unwatched != null) {
            return SeriesPlaybackTarget(unwatched, SeriesPlaybackReason.PLAY)
        }

        return SeriesPlaybackTarget(available.first(), SeriesPlaybackReason.RESTART)
    }

    fun orderedCandidates(
        episodes: List<JellyfinItem>,
        completedDownloadIds: Set<String>,
    ): List<SeriesPlaybackTarget> {
        val primary = resolve(episodes, completedDownloadIds) ?: return emptyList()
        val remaining =
            episodes
                .filter { it.id != primary.episode.id }
                .filter { it.type.equals("Episode", ignoreCase = true) }
                .filter { it.id in completedDownloadIds || it.hasAvailableLocation() }
                .sortedWith(episodeComparator)
                .map { SeriesPlaybackTarget(it, SeriesPlaybackReason.PLAY) }
        return listOf(primary) + remaining
    }

    private fun JellyfinItem.hasAvailableLocation(): Boolean =
        locationType.equals("FileSystem", ignoreCase = true) ||
            locationType.equals("Offline", ignoreCase = true)

    private fun JellyfinItem.lastPlayedInstant(): Instant =
        lastPlayed
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.DISTANT_PAST

    private companion object {
        val episodeComparator =
            compareBy<JellyfinItem>(
                { episode ->
                    when {
                        (episode.parentIndexNumber ?: -1) > 0 -> 0
                        episode.parentIndexNumber == 0 -> 1
                        else -> 2
                    }
                },
                { it.parentIndexNumber ?: Int.MAX_VALUE },
                { it.indexNumber ?: Int.MAX_VALUE },
                { it.name },
            )
    }
}
