package dev.jellystack.core.jellyseerr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeerrRequestCommandTest {
    @Test
    fun primaryCommandsAreStatusAwareAndAvailableIsNeverAnAction() {
        assertEquals(
            SeerrPrimaryAction.Request,
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.MOVIE,
                mediaStatus = null,
                hasRequest = false,
                requestableSeasons = emptyList(),
            ).primaryAction,
        )
        listOf(
            JellyseerrMediaStatus.PENDING,
            JellyseerrMediaStatus.PROCESSING,
            JellyseerrMediaStatus.AVAILABLE,
        ).forEach { status ->
            val command =
                resolveSeerrRequestCommand(
                    mediaType = JellyseerrMediaType.MOVIE,
                    mediaStatus = status,
                    hasRequest = status != JellyseerrMediaStatus.AVAILABLE,
                    requestableSeasons = emptyList(),
                )
            assertNull(command.primaryAction)
            assertTrue(command.showStatus)
        }
        assertEquals(
            SeerrPrimaryAction.RequestMoreSeasons,
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.TV,
                mediaStatus = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                hasRequest = true,
                requestableSeasons = listOf(2, 3),
            ).primaryAction,
        )
    }

    @Test
    fun requestCommandHidesMovieActionWithoutMoviePermission() {
        val tvOnly =
            JellyseerrRequestCapabilities.fromPermissions(JellyseerrPermission.REQUEST_TV)

        val movie =
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.MOVIE,
                mediaStatus = null,
                hasRequest = false,
                requestableSeasons = emptyList(),
                capabilities = tvOnly,
            )
        val show =
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.TV,
                mediaStatus = null,
                hasRequest = false,
                requestableSeasons = emptyList(),
                capabilities = tvOnly,
            )

        assertNull(movie.primaryAction)
        assertTrue(movie.permissionDenied)
        assertEquals(SeerrPrimaryAction.Request, show.primaryAction)
        assertFalse(show.permissionDenied)
    }
}
