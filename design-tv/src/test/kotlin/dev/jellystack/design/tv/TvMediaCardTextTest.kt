package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvMediaCardTextTest {
    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun inProgressEpisodeLeadsWithSeriesAndShowsTimeLeft() {
        val text =
            episode("eps2.4_m4ster-s1ave.aes", season = 2, number = 6)
                .copy(seriesName = "Mr. Robot", runTimeTicks = 49 * MINUTE, positionTicks = 26 * MINUTE)
                .tvCardText(strings)

        assertEquals("Mr. Robot", text.title)
        assertEquals("S2 E6  •  23 min left", text.subtitle)
        assertEquals(26f / 49f, requireNotNull(text.progress), 0.001f)
    }

    @Test
    fun unstartedEpisodeShowsItsTitleInsteadOfTimeLeft() {
        val text = episode("Pilot", season = 1, number = 1).copy(seriesName = "Example Show").tvCardText(strings)

        assertEquals("Example Show", text.title)
        assertEquals("S1 E1  •  Pilot", text.subtitle)
        assertNull(text.progress)
    }

    @Test
    fun episodeWithoutSeriesNameFallsBackToItsOwnTitle() {
        val text = episode("Pilot", season = 1, number = 1).tvCardText(strings)

        assertEquals("Pilot", text.title)
        assertEquals("S1 E1", text.subtitle)
    }

    @Test
    fun moviesShowYearAndTimeLeftOrRating() {
        val movie = item("Movie", "Obsession").copy(productionYear = 2026)
        val inProgress = movie.copy(runTimeTicks = 100 * MINUTE, positionTicks = 82 * MINUTE).tvCardText(strings)
        val unstarted = movie.copy(communityRating = 8.2).tvCardText(strings)

        assertEquals("2026  •  18 min left", inProgress.subtitle)
        assertEquals("2026  •  ${tvRatingLabel(8.2)}", unstarted.subtitle)
        assertNull(item("Movie", "Untitled").tvCardText(strings).subtitle)
    }

    @Test
    fun episodesInsideASeriesLeadWithTheirNumberAndShowRuntimeOrTimeLeft() {
        val pilot = episode("Pilot", season = 1, number = 1).copy(runTimeTicks = 47 * MINUTE)
        val unstarted = pilot.tvEpisodeCardText(strings)
        val inProgress = pilot.copy(positionTicks = 40 * MINUTE).tvEpisodeCardText(strings)

        assertEquals("1. Pilot", unstarted.title)
        assertEquals("47 min", unstarted.subtitle)
        assertNull(unstarted.progress)
        assertEquals("7 min left", inProgress.subtitle)
        assertNull(item("Episode", "Special").tvEpisodeCardText(strings).subtitle)
    }

    private fun episode(
        name: String,
        season: Int,
        number: Int,
    ) = item("Episode", name).copy(parentIndexNumber = season, indexNumber = number)

    private fun item(
        type: String,
        name: String,
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

    private companion object {
        const val MINUTE = 600_000_000L
    }
}
