package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvMediaCardPresentationTest {
    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun inProgressEpisodeLeadsWithSeriesAndShowsTimeLeft() {
        val text =
            item(
                type = "Episode",
                name = "eps2.4_m4ster-s1ave.aes",
                seriesName = "Mr. Robot",
                season = 2,
                episode = 6,
                runTimeTicks = 49 * MINUTE,
                positionTicks = 26 * MINUTE,
            ).tvCardText(strings)

        assertEquals("Mr. Robot", text.title)
        assertEquals("S2 E6  •  23 min left", text.subtitle)
        assertEquals(26f / 49f, text.progress!!, 0.001f)
    }

    @Test
    fun unstartedEpisodeShowsItsTitleInsteadOfTimeLeft() {
        val text = item(type = "Episode", name = "Pilot", seriesName = "Example Show", season = 1, episode = 1).tvCardText(strings)

        assertEquals("Example Show", text.title)
        assertEquals("S1 E1  •  Pilot", text.subtitle)
        assertNull(text.progress)
    }

    @Test
    fun episodeWithoutSeriesNameFallsBackToItsOwnTitle() {
        val text = item(type = "Episode", name = "Pilot", season = 1, episode = 1).tvCardText(strings)

        assertEquals("Pilot", text.title)
        assertEquals("S1 E1", text.subtitle)
    }

    @Test
    fun moviesShowYearAndTimeLeftOrRating() {
        val inProgress =
            item(type = "Movie", name = "Obsession", year = 2026, runTimeTicks = 100 * MINUTE, positionTicks = 82 * MINUTE)
                .tvCardText(strings)
        val unstarted = item(type = "Movie", name = "Obsession", year = 2026, rating = 8.2).tvCardText(strings)

        assertEquals("2026  •  18 min left", inProgress.subtitle)
        assertEquals("2026  •  ${tvRatingLabel(8.2)}", unstarted.subtitle)
        assertNull(item(type = "Movie", name = "Untitled").tvCardText(strings).subtitle)
    }

    @Test
    fun episodesInsideASeriesLeadWithTheirNumberAndShowRuntimeOrTimeLeft() {
        val unstarted = item(type = "Episode", name = "Pilot", episode = 1, runTimeTicks = 47 * MINUTE).tvEpisodeCardText(strings)
        val inProgress =
            item(type = "Episode", name = "Pilot", episode = 1, runTimeTicks = 47 * MINUTE, positionTicks = 40 * MINUTE)
                .tvEpisodeCardText(strings)

        assertEquals("1. Pilot", unstarted.title)
        assertEquals("47 min", unstarted.subtitle)
        assertNull(unstarted.progress)
        assertEquals("7 min left", inProgress.subtitle)
        assertNull(item(type = "Episode", name = "Special").tvEpisodeCardText(strings).subtitle)
    }

    private fun item(
        type: String,
        name: String,
        seriesName: String? = null,
        season: Int? = null,
        episode: Int? = null,
        year: Int? = null,
        rating: Double? = null,
        runTimeTicks: Long? = null,
        positionTicks: Long? = null,
    ): JellyfinItem =
        JellyfinItem(
            id = name,
            libraryId = null,
            name = name,
            sortName = null,
            overview = null,
            type = type,
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = seriesName,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = runTimeTicks,
            positionTicks = positionTicks,
            playedPercentage = null,
            productionYear = year,
            premiereDate = null,
            communityRating = rating,
            officialRating = null,
            indexNumber = episode,
            parentIndexNumber = season,
            seriesName = seriesName,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
        )

    private companion object {
        const val MINUTE = 600_000_000L
    }
}
