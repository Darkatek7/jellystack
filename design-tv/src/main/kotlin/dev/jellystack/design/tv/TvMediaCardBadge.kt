package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary

/**
 * What the top-right corner of a media card says about the item; at most one badge per card. Each badge carries its
 * spoken [description], already in the UI language, because the card's visuals are icons and numbers.
 */
internal sealed interface TvMediaCardBadge {
    val description: String

    /** The user has watched the item. */
    data class Watched(
        override val description: String,
    ) : TvMediaCardBadge

    /** A series or season with [count] episodes the user has not watched yet. */
    data class Unplayed(
        val count: Int,
        override val description: String,
    ) : TvMediaCardBadge

    /** Seerr: the item can be played on the server. */
    data class SeerrAvailable(
        override val description: String,
    ) : TvMediaCardBadge

    /** Seerr: part of the item (some seasons) is available; [label] is the short pill text. */
    data class SeerrPartial(
        val label: String,
    ) : TvMediaCardBadge {
        override val description: String get() = label
    }

    /** Seerr: a request is waiting for approval or being downloaded; [label] is the short pill text. */
    data class SeerrRequested(
        val label: String,
    ) : TvMediaCardBadge {
        override val description: String get() = label
    }
}

private val openRequestStatuses = setOf(JellyseerrRequestStatus.PENDING, JellyseerrRequestStatus.APPROVED)
private val unplayedCountTypes = setOf("series", "season")

/**
 * Watched wins over a count: a series the user finished has nothing left to count. Only series and seasons get a
 * count, because for an episode or movie the server's unplayed count is not a number of things to watch.
 */
internal fun tvJellyfinCardBadge(
    item: JellyfinItem,
    strings: TvStrings,
): TvMediaCardBadge? {
    val unplayed = item.unplayedCount?.takeIf { it > 0 && item.type.lowercase() in unplayedCountTypes }
    return when {
        item.isPlayed -> TvMediaCardBadge.Watched(strings.watched)
        unplayed != null -> TvMediaCardBadge.Unplayed(unplayed, strings.messages.cardBadges.unplayedCount(unplayed))
        else -> null
    }
}

/**
 * Availability beats a request: once something can be played, an old request is no news. A declined or failed request
 * shows no badge, because the card then offers the same action as for an item nobody asked for.
 */
internal fun tvSeerrCardBadge(
    availability: JellyseerrMediaAvailability,
    requests: List<JellyseerrRequestSummary>,
    strings: TvStrings,
): TvMediaCardBadge? {
    val statuses = listOf(availability.standard, availability.`4k`)
    return when {
        JellyseerrMediaStatus.AVAILABLE in statuses ->
            TvMediaCardBadge.SeerrAvailable(strings.seerr.availabilityAvailable)
        statuses.any { it == JellyseerrMediaStatus.PARTIALLY_AVAILABLE } ->
            TvMediaCardBadge.SeerrPartial(
                strings.messages.cardBadges.partial,
            )
        statuses.any { it == JellyseerrMediaStatus.PENDING || it == JellyseerrMediaStatus.PROCESSING } ->
            TvMediaCardBadge.SeerrRequested(strings.messages.cardBadges.requested)
        requests.any { it.requestStatus in openRequestStatuses } ->
            TvMediaCardBadge.SeerrRequested(strings.messages.cardBadges.requested)
        else -> null
    }
}
