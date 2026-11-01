package dev.jellystack.core.livetv

import dev.jellystack.network.jellyfin.JellyfinLiveTvChannelDto
import dev.jellystack.network.jellyfin.JellyfinLiveTvProgramDto
import kotlinx.datetime.Instant

/** A Live TV channel. [number] is shown as the tuner reports it ("12", "12.1"). */
data class LiveTvChannel(
    val id: String,
    val number: String?,
    val name: String,
    val imageTag: String?,
)

/** One entry of the guide. [subtitle] is the episode title when the guide has one. */
data class LiveTvProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val start: Instant,
    val end: Instant,
)

/** What is on a channel at one moment, and what follows. */
data class LiveTvNowNext(
    val now: LiveTvProgram? = null,
    val next: LiveTvProgram? = null,
) {
    /** When this stops being true: the end of the running program, else the start of the next one. */
    val changesAt: Instant?
        get() = now?.end ?: next?.start
}

internal fun JellyfinLiveTvChannelDto.toDomain(): LiveTvChannel {
    val number = (channelNumber ?: this.number)?.takeIf(String::isNotBlank)
    return LiveTvChannel(
        id = id,
        number = number,
        name = name?.takeIf(String::isNotBlank) ?: number.orEmpty(),
        imageTag = imageTags?.get("Primary"),
    )
}

/** The program, or null when the guide entry has no channel or no usable start and end. */
internal fun JellyfinLiveTvProgramDto.toDomain(): LiveTvProgram? {
    val channel = channelId ?: return null
    val start = startDate?.parseInstant() ?: return null
    val end = endDate?.parseInstant() ?: return null
    if (end <= start) return null
    return LiveTvProgram(
        id = id,
        channelId = channel,
        title = name.orEmpty(),
        subtitle = episodeTitle?.takeIf(String::isNotBlank),
        overview = overview?.takeIf(String::isNotBlank),
        start = start,
        end = end,
    )
}

// Jellyfin writes seven fractional digits ("2026-11-01T18:00:00.0000000Z"); a malformed date drops the entry.
private fun String.parseInstant(): Instant? =
    try {
        Instant.parse(this)
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * The program running at [at] and the one after it. A program is running from its start up to, but not including, its
 * end. With overlapping entries the one that started last runs; the next one starts at or after the running one's end,
 * or, with nothing running, is the first to start after [at].
 */
fun List<LiveTvProgram>.nowAndNext(at: Instant): LiveTvNowNext {
    val now = filter { it.start <= at && at < it.end }.maxByOrNull { it.start }
    val after = now?.end ?: at
    val next =
        filter { if (now == null) it.start > after else it.start >= after }
            .minByOrNull { it.start }
    return LiveTvNowNext(now, next)
}

/** How far through the program [at] is, from 0 to 1. */
fun LiveTvProgram.progressAt(at: Instant): Float {
    val total = (end - start).inWholeMilliseconds
    if (total <= 0L) return 0f
    return ((at - start).inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f)
}
