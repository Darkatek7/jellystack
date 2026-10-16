package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSeasonStatus
import dev.jellystack.core.jellyseerr.SeerrPrimaryAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvSeerrRequestStateTest {
    @Test
    fun requestFromAnotherUserBlocksRequestingAndShowsMediaStatus() {
        val state =
            resolveTvSeerrRequestState(
                mediaType = JellyseerrMediaType.MOVIE,
                detail =
                    detail(
                        JellyseerrMediaType.MOVIE,
                        status = JellyseerrMediaStatus.PENDING,
                        requests = listOf(request(JellyseerrMediaType.MOVIE)),
                    ),
                activeRequest = null,
                capabilities = JellyseerrRequestCapabilities.ALL,
            )

        assertNull(state.command.primaryAction)
        assertTrue(state.command.showStatus)
        assertEquals(JellyseerrMediaStatus.PENDING, state.mediaStatus)
    }

    @Test
    fun untouchedMediaIsRequestable() {
        val state =
            resolveTvSeerrRequestState(
                mediaType = JellyseerrMediaType.MOVIE,
                detail = detail(JellyseerrMediaType.MOVIE, status = JellyseerrMediaStatus.UNKNOWN),
                activeRequest = null,
                capabilities = JellyseerrRequestCapabilities.ALL,
            )

        assertEquals(SeerrPrimaryAction.Request, state.command.primaryAction)
    }

    @Test
    fun ownRequestShowsItsStatusBeforeTheDetailReloads() {
        val state =
            resolveTvSeerrRequestState(
                mediaType = JellyseerrMediaType.MOVIE,
                detail = detail(JellyseerrMediaType.MOVIE, status = JellyseerrMediaStatus.UNKNOWN),
                activeRequest = request(JellyseerrMediaType.MOVIE, status = JellyseerrMediaStatus.PROCESSING),
                capabilities = JellyseerrRequestCapabilities.ALL,
            )

        assertNull(state.command.primaryAction)
        assertEquals(JellyseerrMediaStatus.PROCESSING, state.mediaStatus)
    }

    @Test
    fun partiallyAvailableShowOffersOnlySeasonsNoRequestCovers() {
        val state =
            resolveTvSeerrRequestState(
                mediaType = JellyseerrMediaType.TV,
                detail =
                    detail(
                        JellyseerrMediaType.TV,
                        status = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                        requests = listOf(request(JellyseerrMediaType.TV, seasons = listOf(1, 2))),
                        availableSeasons = listOf(1, 2, 3),
                    ),
                activeRequest = null,
                capabilities = JellyseerrRequestCapabilities.ALL,
            )

        assertEquals(SeerrPrimaryAction.RequestMoreSeasons, state.command.primaryAction)
        assertEquals(listOf(3), state.requestableSeasons)
    }

    private fun detail(
        mediaType: JellyseerrMediaType,
        status: JellyseerrMediaStatus,
        requests: List<JellyseerrRequestSummary> = emptyList(),
        availableSeasons: List<Int> = emptyList(),
    ) = JellyseerrMediaDetail(
        tmdbId = 1,
        mediaType = mediaType,
        title = "Example",
        year = null,
        overview = null,
        runtimeMinutes = null,
        genres = emptyList(),
        releaseDate = null,
        revenue = null,
        originalLanguage = null,
        productionCountries = emptyList(),
        studios = emptyList(),
        ratings = null,
        trailer = null,
        posterPath = null,
        backdropPath = null,
        jellyseerrUrl = null,
        jellyfinUrl = null,
        imdbId = null,
        tvdbId = null,
        availableSeasons = availableSeasons,
        availability = JellyseerrMediaAvailability(status, null),
        requests = requests,
    )

    private fun request(
        mediaType: JellyseerrMediaType,
        status: JellyseerrMediaStatus = JellyseerrMediaStatus.PENDING,
        seasons: List<Int> = emptyList(),
    ) = JellyseerrRequestSummary(
        id = 5,
        mediaId = 1,
        tmdbId = 1,
        tvdbId = null,
        title = "Example",
        originalTitle = null,
        mediaType = mediaType,
        requestStatus = JellyseerrRequestStatus.PENDING,
        availability = JellyseerrMediaAvailability(status, null),
        is4k = false,
        canRemoveFromService = false,
        createdAt = null,
        updatedAt = null,
        requestedBy = null,
        profileName = null,
        seasons = seasons.map { JellyseerrSeasonStatus(it, JellyseerrRequestStatus.APPROVED) },
        posterPath = null,
        backdropPath = null,
    )
}
