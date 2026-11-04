@file:Suppress("LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyfin.SeriesPlaybackReason
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.players.AudioTrack

/** Separator of the one-line facts in a detail hero: "2024 · 2h 46m · PG-13". */
internal const val TV_DETAIL_META_SEPARATOR = "  ·  "

private const val MINUTES_PER_HOUR = 60L

/** "2h 46m" / "2 Std 46 Min", or "45m" under an hour; null without a runtime. */
internal fun tvRuntimeLabel(
    minutes: Long?,
    strings: TvStrings,
): String? {
    val total = minutes?.takeIf { it > 0L } ?: return null
    val copy = strings.detailScreen
    val hours = total / MINUTES_PER_HOUR
    val rest = total % MINUTES_PER_HOUR
    return if (hours > 0L) copy.runtimeHoursMinutesFormat.format(hours, rest) else copy.runtimeMinutesFormat.format(rest)
}

/**
 * The line under the title: year, runtime, age rating, community rating, an extra such as the unplayed count, and
 * when the title would end. Parts the item does not have are left out.
 */
internal fun tvDetailMetaLine(
    year: String?,
    runtimeMinutes: Long?,
    officialRating: String?,
    communityRating: Double?,
    endsAt: String?,
    strings: TvStrings,
    extra: String? = null,
): String =
    listOfNotNull(
        year?.takeIf(String::isNotBlank),
        tvRuntimeLabel(runtimeMinutes, strings),
        tvVisibleOfficialRating(officialRating),
        tvRatingLabel(communityRating),
        extra,
        endsAt,
    ).joinToString(TV_DETAIL_META_SEPARATOR)

/** The episode a series plays next, as the primary action of the series page names it. */
internal data class TvDetailNextEpisode(
    val season: Int?,
    val episode: Int?,
    /** The episode is partly watched, so playing it continues. */
    val inProgress: Boolean,
)

/** What "Play" on a series page starts, by the same rule as the launcher: in progress, else first unwatched, else first. */
internal fun tvNextEpisodeOf(episodes: List<JellyfinItem>): TvDetailNextEpisode? =
    SeriesPlaybackTargetResolver().resolve(episodes, completedDownloadIds = emptySet())?.let { target ->
        TvDetailNextEpisode(
            season = target.episode.parentIndexNumber,
            episode = target.episode.indexNumber,
            inProgress = target.reason == SeriesPlaybackReason.CONTINUE,
        )
    }

/**
 * The label of the one primary pill: "Play", "Continue · 38 min left", or for a series the episode it plays
 * ("Play S2 E3" / "Continue S2 E3").
 */
internal fun tvDetailPrimaryLabel(
    resumeRemainingMinutes: Long?,
    hasResume: Boolean,
    nextEpisode: TvDetailNextEpisode?,
    strings: TvStrings,
): String {
    val copy = strings.detailScreen
    return when {
        nextEpisode != null && nextEpisode.inProgress ->
            copy.continueEpisodeFormat.format(nextEpisode.season ?: 0, nextEpisode.episode ?: 0)
        nextEpisode != null -> copy.playEpisodeFormat.format(nextEpisode.season ?: 0, nextEpisode.episode ?: 0)
        hasResume && resumeRemainingMinutes != null && resumeRemainingMinutes > 0L ->
            copy.continueRemainingFormat.format(strings.metadata.minutesLeft.format(resumeRemainingMinutes))
        hasResume -> strings.continueLabel
        else -> strings.play
    }
}

/** Whether "Start over" belongs next to the primary pill: only when playing would continue something. */
internal fun tvDetailOffersStartOver(
    hasResume: Boolean,
    nextEpisode: TvDetailNextEpisode?,
): Boolean = if (nextEpisode != null) nextEpisode.inProgress else hasResume

/** The credits the Details section shows, only those the item has. */
internal fun tvJellyfinDetailRows(
    detail: JellyfinItemDetail,
    strings: TvStrings,
): List<TvDetailRow> {
    val copy = strings.detailScreen
    val directors = detail.people.filter { it.type.equals("Director", ignoreCase = true) }.map { it.name }
    val writers = detail.people.filter { it.type.equals("Writer", ignoreCase = true) }.map { it.name }
    val streams =
        detail.mediaSources
            .firstOrNull()
            ?.streams
            .orEmpty()
    val audioLanguages =
        streams
            .filter { it.type == JellyfinMediaStreamType.AUDIO }
            .mapNotNull { stream ->
                tvAudioButtonLabel(
                    AudioTrack(
                        id = stream.index.toString(),
                        language = stream.language,
                        title = null,
                        codec = stream.codec,
                        isDefault = stream.isDefault,
                        streamIndex = stream.index,
                    ),
                    strings.locale,
                    withChannels = false,
                )
            }.distinct()
    val subtitleLanguages =
        detail.mediaSources
            .firstOrNull()
            ?.tvSubtitleOptions(strings)
            .orEmpty()
            .map { it.title }
            .distinct()
    return listOf(
        copy.directors to directors,
        copy.writers to writers,
        copy.studios to detail.studios,
        copy.audioLanguages to audioLanguages,
        copy.subtitleLanguages to subtitleLanguages,
    ).filter { (_, values) -> values.isNotEmpty() }
        .map { (label, values) -> TvDetailRow(label, values.joinToString(", ")) }
}

/** The cast shown on a page: the credits that are not already named under Details as director or writer. */
internal fun JellyfinItemDetail.tvCastPeople() =
    people.filterNot { it.type.equals("Director", ignoreCase = true) || it.type.equals("Writer", ignoreCase = true) }

/** Unplayed episodes of a series as "4 unplayed", or null when everything was watched or nothing is known. */
internal fun tvUnplayedEpisodesLabel(
    episodes: List<JellyfinItem>,
    strings: TvStrings,
): String? {
    val unplayed = episodes.count { it.type.equals("Episode", ignoreCase = true) && !it.isPlayed }
    return if (unplayed > 0) strings.detailScreen.unplayedFormat.format(unplayed) else null
}
