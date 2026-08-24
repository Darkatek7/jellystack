package dev.jellystack.core.profile

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileMyListRepositoryTest {
    @Test
    fun combinesFavoritesAndSavedTitlesByExactProviderIdentity() =
        runTest {
            val store = InMemoryProfileStore()
            val repository = ProfileMyListRepository(store, FixedProfileClock)
            val saved = seerrItem(tmdbId = 42, title = "Different title")
            repository.saveSeerr(PROFILE_A, saved)
            val available = jellyfinItem(id = "jf-42", title = "Canonical title", tmdbId = "42")

            val entries =
                repository.reconcile(
                    profileId = PROFILE_A,
                    jellyfinFavoriteIds = setOf(available.id),
                    resolveFavorite = { available.takeIf { item -> item.id == it } },
                    resolveIdentity = { available.takeIf { item -> item.mediaIdentity() == it } },
                )

            assertEquals(1, entries.size)
            assertTrue(entries.single().available)
            assertEquals("Canonical title", entries.single().title)
            assertEquals("Different title", entries.single().savedMedia?.title)
        }

    @Test
    fun neverMergesDifferentProviderIdsWithMatchingTitles() =
        runTest {
            val store = InMemoryProfileStore()
            val repository = ProfileMyListRepository(store)
            repository.saveSeerr(PROFILE_A, seerrItem(tmdbId = 99, title = "Shared title"))
            val favorite = jellyfinItem(id = "jf-1", title = "Shared title", tmdbId = "1")

            val entries =
                repository.reconcile(
                    profileId = PROFILE_A,
                    jellyfinFavoriteIds = setOf(favorite.id),
                    resolveFavorite = { favorite },
                    resolveIdentity = { null },
                )

            assertEquals(2, entries.size)
            assertTrue(entries.first().available)
            assertFalse(entries.last().available)
        }

    @Test
    fun savedTitlesRemainProfileLocalAndCanBeRemoved() =
        runTest {
            val store = InMemoryProfileStore()
            val repository = ProfileMyListRepository(store)
            val item = seerrItem(tmdbId = 7, title = "Seven")

            repository.saveSeerr(PROFILE_A, item)

            assertEquals(1, store.listSavedMedia(PROFILE_A).size)
            assertTrue(store.listSavedMedia(PROFILE_B).isEmpty())
            repository.removeSeerr(PROFILE_A, item)
            assertTrue(store.listSavedMedia(PROFILE_A).isEmpty())
        }

    @Test
    fun unavailableSavePreservesExactSeerrRouteData() =
        runTest {
            val store = InMemoryProfileStore()
            val repository = ProfileMyListRepository(store)
            repository.saveSeerr(PROFILE_A, seerrItem(tmdbId = 5, title = "Five"))

            val entry = repository.reconcile(PROFILE_A, emptySet(), { null }, { null }).single()

            assertFalse(entry.available)
            assertNull(entry.jellyfinItem)
            assertEquals("5", entry.identity.providerId)
        }

    private fun seerrItem(
        tmdbId: Int,
        title: String,
    ) = JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = JellyseerrMediaType.MOVIE,
        title = title,
        overview = null,
        releaseYear = null,
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(null, null),
        requests = emptyList(),
    )

    private fun jellyfinItem(
        id: String,
        title: String,
        tmdbId: String,
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = title,
        sortName = title,
        overview = null,
        type = "Movie",
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
        providerIds = MediaProviderIds(tmdbId = tmdbId, sourceLocalId = id),
    )

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}
