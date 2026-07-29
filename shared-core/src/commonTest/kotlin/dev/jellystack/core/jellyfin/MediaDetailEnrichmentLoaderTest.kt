package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaDetailEnrichmentLoaderTest {
    @Test
    fun loadsSimilarAndSeerrByTmdbId() =
        runTest {
            val requested = mutableListOf<Pair<Int, JellyseerrMediaType>>()
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> listOf(item(id = "item-1"), item(id = "similar")) },
                    fetchSeerrDetail = { tmdbId, mediaType ->
                        requested += tmdbId to mediaType
                        seerrDetail(tmdbId, mediaType)
                    },
                )

            val enrichment =
                loader.load(
                    item = item(type = "Movie"),
                    detail = detail(providerIds = mapOf("Tmdb" to "42")),
                )

            assertEquals(listOf(42 to JellyseerrMediaType.MOVIE), requested)
            assertEquals("similar", enrichment.similarItems.single().id)
            assertEquals(42, enrichment.seerrDetail?.tmdbId)
        }

    @Test
    fun keepsSuccessfulSeerrResultWhenSimilarFails() =
        runTest {
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> error("Similar unavailable") },
                    fetchSeerrDetail = { tmdbId, mediaType -> seerrDetail(tmdbId, mediaType) },
                )

            val enrichment =
                loader.load(
                    item = item(type = "Movie"),
                    detail = detail(providerIds = mapOf("TmDb" to "42")),
                )

            assertTrue(enrichment.similarItems.isEmpty())
            assertEquals(42, enrichment.seerrDetail?.tmdbId)
        }

    @Test
    fun keepsSuccessfulSimilarResultsWhenSeerrFails() =
        runTest {
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> listOf(item(id = "similar")) },
                    fetchSeerrDetail = { _, _ -> error("Seerr unavailable") },
                )

            val enrichment =
                loader.load(
                    item = item(type = "Series"),
                    detail = detail(providerIds = mapOf("TMDB" to "84")),
                )

            assertEquals("similar", enrichment.similarItems.single().id)
            assertNull(enrichment.seerrDetail)
        }

    @Test
    fun episodesNeverExposeSeriesRatingsThroughSeerr() =
        runTest {
            var seerrCalled = false
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> emptyList() },
                    fetchSeerrDetail = { _, _ ->
                        seerrCalled = true
                        null
                    },
                )

            val enrichment =
                loader.load(
                    item = item(type = "Episode"),
                    detail = detail(providerIds = mapOf("Tmdb" to "99")),
                )

            assertTrue(!seerrCalled)
            assertNull(enrichment.seerrDetail)
        }

    @Test
    fun missingTmdbIdSkipsOptionalSeerrConnection() =
        runTest {
            var seerrCalled = false
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> listOf(item(id = "similar")) },
                    fetchSeerrDetail = { _, _ ->
                        seerrCalled = true
                        null
                    },
                )

            val enrichment =
                loader.load(
                    item = item(type = "Movie"),
                    detail = detail(providerIds = mapOf("Imdb" to "tt123")),
                )

            assertTrue(!seerrCalled)
            assertEquals("similar", enrichment.similarItems.single().id)
            assertNull(enrichment.seerrDetail)
        }

    @Test
    fun cancellationIsNeverDowngradedToMissingEnrichment() =
        runTest {
            val loader =
                MediaDetailEnrichmentLoader(
                    fetchSimilarItems = { _, _ -> throw CancellationException("navigation changed") },
                    fetchSeerrDetail = { _, _ -> null },
                )

            assertFailsWith<CancellationException> {
                loader.load(
                    item = item(type = "Movie"),
                    detail = detail(providerIds = emptyMap()),
                )
            }
        }

    private fun item(
        id: String = "item-1",
        type: String = "Movie",
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = "Title",
        sortName = null,
        overview = null,
        type = type,
        mediaType = "Video",
        locationType = "FileSystem",
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
    )

    private fun detail(providerIds: Map<String, String>) =
        JellyfinItemDetail(
            id = "item-1",
            name = "Title",
            overview = null,
            taglines = emptyList(),
            runTimeTicks = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            genres = emptyList(),
            studios = emptyList(),
            primaryImageTag = null,
            backdropImageTags = emptyList(),
            mediaSources = emptyList(),
            providerIds = providerIds,
        )

    private fun seerrDetail(
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ) = JellyseerrMediaDetail(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = "Title",
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
    )
}
