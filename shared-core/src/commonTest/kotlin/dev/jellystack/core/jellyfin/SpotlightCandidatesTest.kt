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
    fun prefersPremiereDateAndFallsBackToDateCreated() {
        val releasedInside =
            movie(
                id = "released",
                name = "Released",
                dateCreated = "2026-01-01T12:00:00Z",
                premiereDate = "2026-06-20T12:00:00Z",
            )
        val createdInside = movie("created", "Created", "2026-06-19T12:00:00Z")

        val result = buildSpotlightCandidates(emptyList(), listOf(releasedInside, createdInside), now)

        assertEquals(listOf("released", "created"), result.map { it.actionItem.id })
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

    private fun movie(
        id: String,
        name: String,
        dateCreated: String?,
        premiereDate: String? = null,
    ): JellyfinItem =
        item(id, name, "Movie", dateCreated, premiereDate)

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
