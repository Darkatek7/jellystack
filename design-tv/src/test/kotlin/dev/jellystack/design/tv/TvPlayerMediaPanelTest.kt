package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.PlaybackChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPlayerMediaPanelTest {
    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun chapterRowsNumberUnnamedChaptersAndMarkTheOneBeingPlayed() {
        val chapters =
            listOf(
                PlaybackChapter(index = 0, name = "Opening", startPositionMs = 0L, imageTag = "a"),
                PlaybackChapter(index = 1, name = null, startPositionMs = 125_000L, imageTag = null),
            )

        val rows = tvChapterRows(chapters, positionMs = 130_000L, chapterNumberFormat = "Chapter %d") { it.imageTag }

        assertEquals(listOf("Opening", "Chapter 2"), rows.map { it.title })
        assertEquals(listOf("0:00", "2:05"), rows.map { it.subtitle })
        assertEquals(listOf(false, true), rows.map { it.current })
        assertEquals(listOf("a", null), rows.map { it.imageUrl })
    }

    @Test
    fun episodeRowsAreOrderedAndCarryWatchedProgressAndTheCurrentEpisode() {
        val episodes =
            listOf(
                episode("e3", number = 3),
                episode("e1", number = 1).copy(isPlayed = true),
                episode("e2", number = 2).copy(runTimeTicks = 30 * MINUTE, positionTicks = 10 * MINUTE),
            )

        val rows = tvEpisodeRows(episodes, currentId = "e2", strings = strings) { null }

        assertEquals(listOf("e1", "e2", "e3"), rows.map { it.key })
        assertEquals(listOf(true, false, false), rows.map { it.watched })
        assertEquals(listOf(false, true, false), rows.map { it.current })
        assertEquals("20 min left", rows[1].subtitle)
        assertNull(rows[0].progress)
    }

    private fun episode(
        id: String,
        number: Int,
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = "Episode $number",
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
        parentIndexNumber = 1,
        seriesName = "Series",
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

    private companion object {
        const val MINUTE = 600_000_000L
    }
}
