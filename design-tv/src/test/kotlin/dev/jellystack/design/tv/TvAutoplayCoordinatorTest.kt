package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvAutoplayCoordinatorTest {
    @Test
    fun autoplayPromptIsDerivedFromTheSharedContinuationState() {
        val target = PlaybackContinuationTarget("episode-2", "Next") {}

        assertEquals(
            TvAutoplayPromptModel("Next", 7),
            tvAutoplayPromptModel(
                PlaybackContinuationState(nextTarget = target, countdownSecondsRemaining = 7),
            ),
        )
        assertNull(tvAutoplayPromptModel(PlaybackContinuationState(nextTarget = target)))
        assertNull(tvAutoplayPromptModel(PlaybackContinuationState(countdownSecondsRemaining = 7)))
    }

    @Test
    fun nextEpisodeSelectionIsChronologicalAcrossSeasons() {
        val episodes =
            listOf(
                episode("s2e1", 2, 1),
                episode("s1e2", 1, 2),
                episode("s1e1", 1, 1),
            )

        assertEquals("s1e2", selectNextTvEpisode(episodes, "s1e1")?.id)
        assertEquals("s2e1", selectNextTvEpisode(episodes, "s1e2")?.id)
        assertNull(selectNextTvEpisode(episodes, "s2e1"))
        assertNull(selectNextTvEpisode(episodes, "missing"))
    }

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = JellyfinItem(
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
