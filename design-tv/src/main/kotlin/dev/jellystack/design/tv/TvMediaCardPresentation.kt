package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.watchProgress

/** Text and progress shown on a Jellyfin media card. */
internal data class TvMediaCardText(
    val title: String,
    val subtitle: String?,
    val progress: Float?,
)

/**
 * Episodes lead with the series name and their S/E number, so "Mr. Robot · S2 E6" reads instead of a
 * raw episode file name. In-progress items show the time left; others show the episode title or rating.
 */
internal fun JellyfinItem.tvCardText(strings: TvStrings): TvMediaCardText {
    val progress = watchProgress()
    val timeLeft = progress?.remainingMinutes?.let { strings.metadata.minutesLeft.format(it) }
    val episodeName = episodeTitle ?: name
    val series = seriesName?.takeIf { it.isNotBlank() }
    return if (type.equals("Episode", ignoreCase = true)) {
        TvMediaCardText(
            title = series ?: episodeName,
            subtitle =
                listOfNotNull(
                    "S${parentIndexNumber ?: 0} E${indexNumber ?: 0}",
                    timeLeft ?: episodeName.takeIf { series != null },
                ).joinToString(TV_CARD_SEPARATOR),
            progress = progress?.fraction,
        )
    } else {
        TvMediaCardText(
            title = episodeName,
            subtitle =
                listOfNotNull(productionYear?.toString(), timeLeft ?: tvRatingLabel(communityRating))
                    .joinToString(TV_CARD_SEPARATOR)
                    .ifBlank { null },
            progress = progress?.fraction,
        )
    }
}

/**
 * Episode cards inside a series detail: the series is already on screen, so they lead with the
 * episode number and show the time left, or the runtime for unstarted episodes.
 */
internal fun JellyfinItem.tvEpisodeCardText(strings: TvStrings): TvMediaCardText {
    val progress = watchProgress()
    val runtimeMinutes = runTimeTicks?.takeIf { it > 0L }?.let { it / TV_CARD_TICKS_PER_MINUTE }
    return TvMediaCardText(
        title = listOfNotNull(indexNumber?.let { "$it." }, episodeTitle ?: name).joinToString(" "),
        subtitle =
            progress?.remainingMinutes?.let { strings.metadata.minutesLeft.format(it) }
                ?: runtimeMinutes?.let { strings.metadata.minutesShort.format(it) },
        progress = progress?.fraction,
    )
}

private const val TV_CARD_SEPARATOR = "  •  "
private const val TV_CARD_TICKS_PER_MINUTE = 600_000_000L
