package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.players.formatPlaybackTime

/** A line of the track list: the heading of a disc, or one track. */
internal sealed interface TvTrackRow {
    data class DiscHeader(
        val disc: Int,
    ) : TvTrackRow

    /** [index] is the track's place in the list, which is where playing from it starts. */
    data class Track(
        val item: JellyfinItem,
        val index: Int,
        val number: Int,
        val duration: String?,
    ) : TvTrackRow
}

/** The lines of [tracks] in the order given; an album with several discs gets a heading before each disc. */
internal fun buildTrackRows(tracks: List<JellyfinItem>): List<TvTrackRow> =
    buildList {
        var lastDisc: Int? = null
        tracks.forEachIndexed { index, track ->
            val disc = tracks.discOf(track)
            if (disc != null && disc != lastDisc) add(TvTrackRow.DiscHeader(disc))
            lastDisc = disc ?: lastDisc
            add(TvTrackRow.Track(track, index, track.trackNumber(index + 1), track.durationLabel()))
        }
    }

/** Where each track's focus target sits in the lines of the list, to restore focus onto a scrolled-away track. */
internal fun trackFocusLocations(rows: List<TvTrackRow>): Map<String, Int> =
    buildMap {
        rows.forEachIndexed { rowIndex, row ->
            if (row is TvTrackRow.Track) put(tvLibraryTargetId(row.item.id), rowIndex)
        }
    }

/** "Album · 12 tracks · 41:07", the line under the title of an album page. */
internal fun trackListSubtitle(
    label: String,
    tracks: List<JellyfinItem>,
    strings: TvMusicStrings,
    separator: String = TV_CARD_SEPARATOR,
): String =
    listOfNotNull(
        label,
        strings.trackCount(tracks.size).takeIf { tracks.isNotEmpty() },
        tracks.totalDurationLabel(),
    ).joinToString(separator)

/** The track number shown in a list of tracks: its number on the disc, else its place in the list. */
internal fun JellyfinItem.trackNumber(position: Int): Int = indexNumber ?: position

/** The disc a track is on, or null when the album has only one disc (or the tracks name no disc). */
internal fun List<JellyfinItem>.discOf(track: JellyfinItem): Int? =
    track.parentIndexNumber?.takeIf { mapNotNull(JellyfinItem::parentIndexNumber).distinct().size > 1 }

/** The length of a track as "3:42", or null when the server did not say. */
internal fun JellyfinItem.durationLabel(): String? {
    val ticks = runTimeTicks?.takeIf { it > 0L } ?: return null
    return formatPlaybackTime(ticks / TICKS_PER_MS)
}

/** The length of all [this] tracks together as "41:07", or null when none says how long it is. */
internal fun List<JellyfinItem>.totalDurationLabel(): String? =
    sumOf { it.runTimeTicks ?: 0L }.takeIf { it > 0L }?.let { formatPlaybackTime(it / TICKS_PER_MS) }

private const val TICKS_PER_MS = 10_000L
