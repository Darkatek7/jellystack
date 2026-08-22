package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSeasonPresentationTest {
    private fun episode(
        id: String,
        season: Int?,
        index: Int? = null,
        positionTicks: Long? = null,
    ): JellyfinItem =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = id,
            sortName = null,
            overview = null,
            type = "Episode",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = "series",
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = positionTicks,
            playedPercentage = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = index,
            parentIndexNumber = season,
            seriesName = "Series",
            seasonId = null,
            episodeTitle = id,
            lastPlayed = null,
        )

    @Test
    fun groupsEpisodesByParentSeasonInOrder() {
        val groups =
            buildTvSeasonGroups(
                listOf(
                    episode("s2e2", 2, 2),
                    episode("s1e1", 1, 1),
                    episode("s2e1", 2, 1),
                    episode("special", null, 1),
                ),
            )
        assertEquals(listOf(1, 2, null), groups.map { it.seasonNumber })
        assertEquals(listOf("s1e1"), groups[0].episodes.map { it.id })
        assertEquals(listOf("s2e1", "s2e2"), groups[1].episodes.map { it.id })
        assertEquals(listOf("special"), groups[2].episodes.map { it.id })
    }

    @Test
    fun emptyEpisodesProduceNoGroups() {
        assertTrue(buildTvSeasonGroups(emptyList()).isEmpty())
    }

    @Test
    fun defaultIndexPrefersSeasonOfInProgressEpisode() {
        val groups =
            buildTvSeasonGroups(
                listOf(
                    episode("s1e1", 1, 1),
                    episode("s2e1", 2, 1, positionTicks = 60_000_000L),
                ),
            )
        assertEquals(1, defaultTvSeasonIndex(groups))
    }

    @Test
    fun defaultIndexFallsBackToSeasonContainingFallbackEpisode() {
        val groups =
            buildTvSeasonGroups(
                listOf(
                    episode("s1e1", 1, 1),
                    episode("s2e1", 2, 1),
                ),
            )
        assertEquals(1, defaultTvSeasonIndex(groups, fallbackEpisodeId = "s2e1"))
    }

    @Test
    fun defaultIndexDefaultsToFirstSeason() {
        val groups =
            buildTvSeasonGroups(
                listOf(episode("s1e1", 1, 1), episode("s2e1", 2, 1)),
            )
        assertEquals(0, defaultTvSeasonIndex(groups))
        assertEquals(0, defaultTvSeasonIndex(emptyList()))
    }

    @Test
    fun detailLoadErrorsMapToLocalizedCopy() {
        val strings = TvStrings.current(dev.jellystack.core.preferences.AppLanguage.ENGLISH)
        val itemError = TvDetailLoadException(TvDetailLoadErrorKind.ITEM_UNAVAILABLE)
        val detailsError = TvDetailLoadException(TvDetailLoadErrorKind.DETAILS_UNAVAILABLE)
        assertEquals(strings.detailUnavailable, tvDetailErrorMessage(itemError, strings))
        assertEquals(strings.detailLoadFailed, tvDetailErrorMessage(detailsError, strings))
        assertEquals(strings.detailLoadFailed, tvDetailErrorMessage(IllegalStateException("boom"), strings))
    }

    @Test
    fun resumePositionLabelConvertsTicksToClockTime() {
        // 39,120,500,000 ticks == 3,912,050 ms == 1:05:12.
        assertEquals("1:05:12", tvResumePositionLabel(39_120_500_000L))
        assertEquals("6:14", tvResumePositionLabel(3_740_000_000L))
        assertEquals("0:00", tvResumePositionLabel(0L))
        assertEquals("0:00", tvResumePositionLabel(null))
    }
}
