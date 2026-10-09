@file:Suppress("FunctionName", "FunctionNaming", "MatchingDeclarationName")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem

internal fun selectNextTvEpisode(
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
    val index = ordered.indexOfFirst { it.id == currentMediaId }
    return ordered.getOrNull(index + 1).takeIf { index >= 0 }
}
