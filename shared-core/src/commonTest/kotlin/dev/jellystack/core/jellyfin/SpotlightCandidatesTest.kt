package dev.jellystack.core.jellyfin

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SpotlightCandidatesTest {
    private val now = Instant.parse("2026-06-22T12:00:00Z")

    @Test
    fun includesItemsInsideThirtyDaysAndExcludesBoundaryOverflow() {
        val inside = movie("inside", "Inside", "2026-05-23T12:00:01Z")
        val outside = movie("outside", "Outside", "2026-05-23T11:59:59Z")

        val result = buildSpotlightCandidates(emptyList(), listOf(inside, outside), now)

        assertEquals(listOf("inside"), result.map { it.actionItem.id })
    }

    @Test
    fun collapsesEpisodesIntoOneSeriesCandidateUsingNewestEpisodeForAction() {
        val older = episode("e1", "series-1", "Show", "2026-06-10T12:00:00Z")
        val newer = episode("e2", "series-1", "Show", "2026-06-12T12:00:00Z")

        val result = buildSpotlightCandidates(listOf(older, newer), emptyList(), now)

        assertEquals(1, result.size)
        assertEquals("series-1", result.single().displayItem.id)
        assertEquals("Show", result.single().displayItem.name)
        assertEquals("e2", result.single().actionItem.id)
    }

    @Test
    fun excludesMissingMalformedAndFutureDatesAndRemovesDuplicates() {
        val valid = movie("movie", "Movie", "2026-06-20T12:00:00Z")
        val duplicate = valid.copy()
        val missing = movie("missing", "Missing", null)
        val malformed = movie("bad", "Bad", "yesterday")
        val future = movie("future", "Future", "2026-06-23T12:00:00Z")

        val result = buildSpotlightCandidates(emptyList(), listOf(valid, duplicate, missing, malformed, future), now)

        assertEquals(listOf("movie"), result.map { it.actionItem.id })
    }

    @Test
    fun ordersCombinedShowsAndMoviesNewestFirst() {
        val show = episode("episode", "series", "Show", "2026-06-18T12:00:00Z")
        val movie = movie("movie", "Movie", "2026-06-20T12:00:00Z")

        val result = buildSpotlightCandidates(listOf(show), listOf(movie), now)

        assertEquals(listOf("movie", "episode"), result.map { it.actionItem.id })
    }

    @Test
    fun prefersDateCreatedAndFallsBackToPremiereDateWhenMissingOrMalformed() {
        val createdInside =
            movie(
                id = "created",
                name = "Created",
                dateCreated = "2026-06-19T12:00:00Z",
                premiereDate = "2026-06-21T12:00:00Z",
            )
        val missingDateCreated = movie("missing", "Missing", null, "2026-06-20T12:00:00Z")
        val malformedDateCreated = movie("malformed", "Malformed", "yesterday", "2026-06-21T12:00:00Z")

        val result =
            buildSpotlightCandidates(
                emptyList(),
                listOf(createdInside, missingDateCreated, malformedDateCreated),
                now,
            )

        assertEquals(listOf("malformed", "missing", "created"), result.map { it.actionItem.id })
    }

    @Test
    fun excludesItemsWithValidFutureDateCreatedEvenWhenPremiereDateIsInsideWindow() {
        val futureCreated = movie("future", "Future", "2026-06-23T12:00:00Z", "2026-06-20T12:00:00Z")

        val result = buildSpotlightCandidates(emptyList(), listOf(futureCreated), now)

        assertEquals(emptyList(), result)
    }

    @Test
    fun latestCandidatesPreferDateCreatedAndFallBackToPremiereDateWhenNeeded() {
        val createdWins = movie("created", "Created", "2026-06-01T12:00:00Z", "2026-06-20T12:00:00Z")
        val malformedDateCreated = movie("malformed", "Malformed", "yesterday", "2026-06-19T12:00:00Z")
        val missingDateCreated = movie("missing", "Missing", null, "2026-06-18T12:00:00Z")

        val result =
            buildLatestSpotlightCandidates(
                recentShows = emptyList(),
                recentMovies = listOf(createdWins, malformedDateCreated, missingDateCreated),
            )

        assertEquals(listOf("created", "malformed", "missing"), result.map { it.actionItem.id })
        assertEquals(
            listOf(
                Instant.parse("2026-06-01T12:00:00Z"),
                Instant.parse("2026-06-19T12:00:00Z"),
                Instant.parse("2026-06-18T12:00:00Z"),
            ),
            result.map { it.addedAt },
        )
    }

    @Test
    fun latestCandidatesKeepSourceOrderWhileGroupingDeduplicatingAndPlacingUndatedLast() {
        val olderEpisode = episode("episode-1", "series", "Show", "2026-06-10T12:00:00Z")
        val newerEpisode = episode("episode-2", "series", "Show", "2026-06-12T12:00:00Z")
        val undatedSeries = item("undated", "Undated", "Series", null)
        val movie = movie("movie", "Movie", "2026-06-20T12:00:00Z")

        val result =
            buildLatestSpotlightCandidates(
                recentShows = listOf(olderEpisode, newerEpisode, undatedSeries),
                recentMovies = listOf(movie),
                additionalItems = listOf(movie.copy(name = "Duplicate movie")),
            )

        assertEquals(listOf("episode-2", "movie", "undated"), result.map { it.actionItem.id })
        assertEquals(
            listOf(
                Instant.parse("2026-06-12T12:00:00Z"),
                Instant.parse("2026-06-20T12:00:00Z"),
                Instant.DISTANT_PAST,
            ),
            result.map { it.addedAt },
        )
    }

    @Test
    fun latestCandidatesKeepFirstEncounteredMovieWhenDuplicateIsNewer() {
        val firstMovie = movie("movie", "First movie", "2026-06-10T12:00:00Z")
        val laterDuplicate = movie("movie", "Later duplicate", "2026-06-20T12:00:00Z")

        val result =
            buildLatestSpotlightCandidates(
                recentShows = emptyList(),
                recentMovies = listOf(firstMovie),
                additionalItems = listOf(laterDuplicate),
            )

        assertEquals("First movie", result.single().actionItem.name)
        assertEquals(Instant.parse("2026-06-10T12:00:00Z"), result.single().addedAt)
    }

    @Test
    fun strictAndLatestCandidatesKeepMovieDisplayItemsUnchanged() {
        val movie = movie("movie", "Movie", "2026-06-20T12:00:00Z")
        val strict = buildSpotlightCandidates(emptyList(), listOf(movie), now).single()
        val latest = buildLatestSpotlightCandidates(emptyList(), listOf(movie)).single()

        listOf(strict, latest).forEach { candidate ->
            assertEquals("movie", candidate.displayItem.id)
            assertEquals("Movie", candidate.displayItem.name)
            assertEquals("Movie", candidate.displayItem.type)
        }
    }

    @Test
    fun groupsReleasedEpisodesAsSeasonCandidates() {
        val episode =
            episode(
                id = "episode",
                seriesId = "series",
                seriesName = "Show",
                dateCreated = null,
                premiereDate = "2026-06-20T12:00:00Z",
                seasonId = "season-1",
                seasonNumber = 1,
            )

        val result = buildSpotlightCandidates(listOf(episode), emptyList(), now)

        assertEquals("season-1", result.single().displayItem.id)
        assertEquals("Show - Season 1", result.single().displayItem.name)
        assertEquals("episode", result.single().actionItem.id)
    }

    @Test
    fun groupedEpisodesKeepSeriesBrandArtwork() {
        val episode =
            episode("episode", "series", "Show", "2026-06-20T12:00:00Z").copy(
                seriesLogoImageTag = "series-logo",
                seriesArtImageTag = "series-art",
                seriesBannerImageTag = "series-banner",
            )

        val display = buildSpotlightCandidates(listOf(episode), emptyList(), now).single().displayItem

        assertEquals("series-logo", display.logoImageTag)
        assertEquals("series-art", display.artImageTag)
        assertEquals("series-banner", display.bannerImageTag)
    }

    private fun movie(
        id: String,
        name: String,
        dateCreated: String?,
        premiereDate: String? = null,
    ): JellyfinItem = item(id, name, "Movie", dateCreated, premiereDate)

    private fun episode(
        id: String,
        seriesId: String,
        seriesName: String,
        dateCreated: String?,
        premiereDate: String? = null,
        seasonId: String? = null,
        seasonNumber: Int? = null,
    ): JellyfinItem =
        item(
            id = id,
            name = "Episode $id",
            type = "Episode",
            dateCreated = dateCreated,
            premiereDate = premiereDate,
            parentId = seriesId,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonId = seasonId,
            parentIndexNumber = seasonNumber,
        )

    private fun item(
        id: String,
        name: String,
        type: String,
        dateCreated: String?,
        premiereDate: String? = null,
        parentId: String? = null,
        seriesId: String? = null,
        seriesName: String? = null,
        seasonId: String? = null,
        parentIndexNumber: Int? = null,
    ): JellyfinItem =
        JellyfinItem(
            id = id,
            libraryId = "library",
            name = name,
            sortName = name,
            overview = null,
            type = type,
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = parentId,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = seriesId,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = null,
            premiereDate = premiereDate,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = parentIndexNumber,
            seriesName = seriesName,
            seasonId = seasonId,
            episodeTitle = null,
            lastPlayed = null,
            dateCreated = dateCreated,
        )
}
