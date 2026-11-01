package dev.jellystack.core.jellyseerr

import dev.jellystack.network.mediarium.MediariumRequestDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerifiedRequestIdentityTest {
    private val pending = MediariumRequestDto(7, "movie", "Fictional title", status = "pending").toSummary().copy(tmdbId = 101)
    private val known = mapOf((JellyseerrMediaType.MOVIE to 101) to pending)

    @Test
    fun matchingApprovalHistoryUpdatesTheKnownSubmissionWithoutTitleMatching() {
        val refreshed = pending.copy(tmdbId = null, title = "Renamed title", requestStatus = JellyseerrRequestStatus.APPROVED)
        val resolved = refreshed.withVerifiedMediaIdentity(known)
        assertEquals(101, resolved.tmdbId)
        assertEquals(JellyseerrRequestStatus.APPROVED, resolved.requestStatus)
        assertEquals("Renamed title", resolved.title)
    }

    @Test
    fun sameTitleWithDifferentRequestIdDoesNotAcquireAnIdentity() {
        val unrelated = pending.copy(id = 8, tmdbId = null)
        assertNull(unrelated.withVerifiedMediaIdentity(known).tmdbId)
    }

    @Test
    fun requestIdForDifferentMediaTypeDoesNotAcquireAnIdentity() {
        val unrelated = pending.copy(mediaType = JellyseerrMediaType.TV, tmdbId = null)
        assertNull(unrelated.withVerifiedMediaIdentity(known).tmdbId)
    }

    @Test
    fun explicitServerIdentityTakesPrecedenceOverRememberedSubmission() {
        assertEquals(102, pending.copy(tmdbId = 102).withVerifiedMediaIdentity(known).tmdbId)
    }
}
