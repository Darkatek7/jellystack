package app.jellystack.mobile.playback

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoplayNextSelectorTest {
    @Test
    fun selectsChronologicalNextEpisodeAcrossSeasons() {
        val episodes =
            listOf(
                episode("s2e1", season = 2, number = 1),
                episode("s1e2", season = 1, number = 2),
                episode("s1e1", season = 1, number = 1),
            )

        assertEquals("s1e2", selectNextEpisode(episodes, "s1e1")?.id)
        assertEquals("s2e1", selectNextEpisode(episodes, "s1e2")?.id)
    }

    @Test
    fun missingOrFinalEpisodeHasNoNextEpisode() {
        val episodes = listOf(episode("one", 1, 1))

        assertNull(selectNextEpisode(episodes, "missing"))
        assertNull(selectNextEpisode(episodes, "one"))
    }

    private fun episode(
        id: String,
        season: Int,
        number: Int,
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
            positionTicks = null,
            playedPercentage = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = number,
            parentIndexNumber = season,
            seriesName = "Series",
            seasonId = null,
            episodeTitle = id,
            lastPlayed = null,
        )
}
