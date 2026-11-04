package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMediaCardBadgeTest {
    private val strings = TvStrings.English

    @Test
    fun playedItemShowsWatchedEvenWithUnplayedCount() {
        val item = goldenItem("Series", "Show").copy(isPlayed = true, unplayedCount = 3)

        assertTrue(tvJellyfinCardBadge(item, strings) is TvMediaCardBadge.Watched)
    }

    @Test
    fun seriesWithUnplayedEpisodesShowsCount() {
        val item = goldenItem("Series", "Show").copy(unplayedCount = 7)

        assertEquals(7, (tvJellyfinCardBadge(item, strings) as TvMediaCardBadge.Unplayed).count)
    }

    @Test
    fun noBadgeForZeroCountMissingCountOrNonSeriesTypes() {
        assertNull(tvJellyfinCardBadge(goldenItem("Series", "Show").copy(unplayedCount = 0), strings))
        assertNull(tvJellyfinCardBadge(goldenItem("Series", "Show"), strings))
        assertNull(tvJellyfinCardBadge(goldenItem("Movie", "Film").copy(unplayedCount = 2), strings))
    }

    @Test
    fun seerrAvailabilityBeatsRequests() {
        val availability = JellyseerrMediaAvailability(standard = JellyseerrMediaStatus.AVAILABLE, `4k` = null)

        assertTrue(tvSeerrCardBadge(availability, listOf(request()), strings) is TvMediaCardBadge.SeerrAvailable)
    }

    @Test
    fun seerrFourKAvailabilityCountsToo() {
        val availability = JellyseerrMediaAvailability(standard = null, `4k` = JellyseerrMediaStatus.AVAILABLE)

        assertTrue(tvSeerrCardBadge(availability, emptyList(), strings) is TvMediaCardBadge.SeerrAvailable)
    }

    @Test
    fun seerrPartialAndRequestedStates() {
        fun badge(
            status: JellyseerrMediaStatus?,
            requests: List<dev.jellystack.core.jellyseerr.JellyseerrRequestSummary> = emptyList(),
        ) = tvSeerrCardBadge(JellyseerrMediaAvailability(status, null), requests, strings)

        assertEquals(TvMediaCardBadge.SeerrPartial("Partial"), badge(JellyseerrMediaStatus.PARTIALLY_AVAILABLE))
        assertEquals(TvMediaCardBadge.SeerrRequested("Requested"), badge(JellyseerrMediaStatus.PENDING))
        assertEquals(TvMediaCardBadge.SeerrRequested("Requested"), badge(JellyseerrMediaStatus.PROCESSING))
        assertEquals(TvMediaCardBadge.SeerrRequested("Requested"), badge(null, listOf(request())))
    }

    @Test
    fun declinedRequestAndUnknownStatusShowNoBadge() {
        val none = JellyseerrMediaAvailability(standard = null, `4k` = null)

        assertNull(tvSeerrCardBadge(none, listOf(request(JellyseerrRequestStatus.DECLINED)), strings))
        val unknown = JellyseerrMediaAvailability(JellyseerrMediaStatus.UNKNOWN, null)
        assertNull(tvSeerrCardBadge(unknown, emptyList(), strings))
    }

    private fun request(status: JellyseerrRequestStatus = JellyseerrRequestStatus.PENDING) =
        dev.jellystack.core.jellyseerr.JellyseerrRequestSummary(
            id = 1,
            mediaId = null,
            tmdbId = 1,
            tvdbId = null,
            title = null,
            originalTitle = null,
            mediaType = JellyseerrMediaType.MOVIE,
            requestStatus = status,
            availability = JellyseerrMediaAvailability(null, null),
            is4k = false,
            canRemoveFromService = false,
            createdAt = null,
            updatedAt = null,
            requestedBy = null,
            profileName = null,
            seasons = emptyList(),
            posterPath = null,
            backdropPath = null,
        )
}
