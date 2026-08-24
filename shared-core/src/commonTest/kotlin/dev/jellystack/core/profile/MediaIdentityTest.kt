package dev.jellystack.core.profile

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MediaIdentityTest {
    @Test
    fun tmdbTakesPrecedenceOverTvdbAndSourceLocalId() {
        val identity =
            MediaProviderIds(
                tmdbId = " 603 ",
                tvdbId = "123",
                sourceLocalId = "jellyfin-1",
            ).identityFor("Movie")

        assertEquals(MediaIdentity("movie", MediaIdentityProvider.TMDB, "603"), identity)
    }

    @Test
    fun tvdbIsUsedWhenTmdbIsMissingOrBlank() {
        val identity =
            MediaProviderIds(
                tmdbId = " ",
                tvdbId = " 81189 ",
                sourceLocalId = "jellyfin-2",
            ).identityFor("Series")

        assertEquals(MediaIdentity("series", MediaIdentityProvider.TVDB, "81189"), identity)
    }

    @Test
    fun sourceLocalIdIsTheConservativeFallback() {
        val identity = MediaProviderIds(sourceLocalId = " local-42 ").identityFor("Episode")

        assertEquals(MediaIdentity("episode", MediaIdentityProvider.SOURCE_LOCAL, "local-42"), identity)
    }

    @Test
    fun mediaTypeIsPartOfIdentity() {
        val ids = MediaProviderIds(tmdbId = "42")

        assertNotEquals(ids.identityFor("movie"), ids.identityFor("series"))
    }

    @Test
    fun titlesNeverInfluenceIdentity() {
        val first = savedMedia(title = "The Same Title", tmdbId = "1")
        val second = savedMedia(title = "The Same Title", tmdbId = "2")

        assertNotEquals(first.identity, second.identity)
    }

    private fun savedMedia(
        title: String,
        tmdbId: String,
    ): SavedMediaRecord =
        SavedMediaRecord(
            profileId = "profile",
            mediaType = "movie",
            providerIds = MediaProviderIds(tmdbId = tmdbId),
            title = title,
            posterPath = null,
            backdropPath = null,
            createdAt = Instant.fromEpochMilliseconds(1L),
            updatedAt = Instant.fromEpochMilliseconds(1L),
        )
}
