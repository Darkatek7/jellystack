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
internal fun JellyfinItem.tvCardText(strings: TvStrings): TvMediaCardText =
    tvCardText(strings.metadata.minutesLeft, strings.messages.untitled)

/**
 * [minutesLeft] is the "%d min left" format of the UI language. A server can send an item without a name, which
 * reads [untitled] instead.
 */
internal fun JellyfinItem.tvCardText(
    minutesLeft: String,
    untitled: String,
): TvMediaCardText {
    val progress = watchProgress()
    val timeLeft = progress?.remainingMinutes?.let { minutesLeft.format(it) }
    val episodeName = (episodeTitle?.takeIf { it.isNotBlank() } ?: name).takeIf { it.isNotBlank() }
    val series = seriesName?.takeIf { it.isNotBlank() }
    return if (type.equals("Episode", ignoreCase = true)) {
        TvMediaCardText(
            title = series ?: episodeName ?: untitled,
            subtitle =
                listOfNotNull(
                    tvEpisodeCode(parentIndexNumber, indexNumber),
                    timeLeft ?: episodeName.takeIf { series != null },
                ).joinToString(TV_CARD_SEPARATOR),
            progress = progress?.fraction,
        )
    } else {
        TvMediaCardText(
            title = episodeName ?: untitled,
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
        title =
            listOfNotNull(indexNumber?.let { "$it." }, (episodeTitle ?: name).takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { strings.messages.untitled },
        subtitle =
            progress?.remainingMinutes?.let { strings.metadata.minutesLeft.format(it) }
                ?: runtimeMinutes?.let { strings.metadata.minutesShort.format(it) },
        progress = progress?.fraction,
    )
}

/** "S1 E2": the one format for season and episode numbers across the TV app. */
internal fun tvEpisodeCode(
    season: Int?,
    episode: Int?,
): String = "S${season ?: 0} E${episode ?: 0}"

/** The line under a stage title: year, community rating, and age rating. */
internal fun JellyfinItem.tvStageMetadata(): String? =
    listOfNotNull(productionYear?.toString(), tvRatingLabel(communityRating), tvVisibleOfficialRating(officialRating))
        .joinToString(TV_CARD_SEPARATOR)
        .ifBlank { null }

/** The age rating worth showing; servers report "0" for titles without one. */
internal fun tvVisibleOfficialRating(rating: String?): String? {
    val trimmed = rating?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val numericRating = trimmed.toDoubleOrNull()
    return trimmed.takeIf { numericRating == null || numericRating > 0.0 }
}

/** The title logo of [item], or of its series for an episode; null when the server has none. */
internal fun tvJellyfinLogoUrl(
    baseUrl: String?,
    token: String?,
    item: JellyfinItem,
): String? {
    val logoTag = item.logoImageTag ?: item.parentLogoImageTag ?: return null
    return jellyfinImageUrl(baseUrl, token, item.seriesId ?: item.id, logoTag, "Logo", TvArtworkSize.LOGO.maxWidth)
}

internal const val TV_CARD_SEPARATOR = "  •  "
private const val TV_CARD_TICKS_PER_MINUTE = 600_000_000L
