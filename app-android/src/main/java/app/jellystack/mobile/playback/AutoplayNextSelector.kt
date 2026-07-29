package app.jellystack.mobile.playback

import dev.jellystack.core.jellyfin.JellyfinItem

internal fun selectNextEpisode(
    episodes: List<JellyfinItem>,
    currentMediaId: String,
): JellyfinItem? {
    val ordered =
        episodes.sortedWith(
            compareBy<JellyfinItem>(
                { it.parentIndexNumber ?: Int.MAX_VALUE },
                { it.indexNumber ?: Int.MAX_VALUE },
                { it.id },
            ),
        )
    val currentIndex = ordered.indexOfFirst { it.id == currentMediaId }
    return ordered.getOrNull(currentIndex + 1).takeIf { currentIndex >= 0 }
}
