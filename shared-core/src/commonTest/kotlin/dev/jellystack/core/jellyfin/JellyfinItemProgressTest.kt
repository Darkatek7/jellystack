package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinItemProgressTest {
    @Test
    fun positionAndRuntimeGiveFractionAndRoundedUpRemainingMinutes() {
        val progress = item(runTimeTicks = 60 * MINUTE, positionTicks = 15 * MINUTE + MINUTE / 2).watchProgress()

        assertEquals(0.2583f, progress!!.fraction, 0.001f)
        assertEquals(45, progress.remainingMinutes)
    }

    @Test
    fun playedPercentageIsTheFallbackWithoutAPosition() {
        val progress = item(runTimeTicks = 100 * MINUTE, playedPercentage = 40.0).watchProgress()

        assertEquals(0.4f, progress!!.fraction, 0.001f)
        assertEquals(60, progress.remainingMinutes)
    }

    @Test
    fun unknownRuntimeKeepsTheBarButHasNoRemainingTime() {
        val progress = item(playedPercentage = 25.0).watchProgress()

        assertEquals(0.25f, progress!!.fraction, 0.001f)
        assertNull(progress.remainingMinutes)
    }

    @Test
    fun lastSecondsStillReportOneMinuteLeft() {
        assertEquals(1, item(runTimeTicks = 30 * MINUTE, positionTicks = 30 * MINUTE - 1).watchProgress()?.remainingMinutes)
    }

    @Test
    fun unstartedAndFinishedItemsHaveNoProgress() {
        assertNull(item(runTimeTicks = 30 * MINUTE).watchProgress())
        assertNull(item(runTimeTicks = 30 * MINUTE, positionTicks = 0L, playedPercentage = 0.0).watchProgress())
        assertNull(item(runTimeTicks = 30 * MINUTE, positionTicks = 30 * MINUTE).watchProgress())
        assertNull(item(playedPercentage = 100.0).watchProgress())
    }

    private fun item(
        runTimeTicks: Long? = null,
        positionTicks: Long? = null,
        playedPercentage: Double? = null,
    ) = JellyfinItem(
        id = "item",
        libraryId = null,
        name = "Episode",
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
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = runTimeTicks,
        positionTicks = positionTicks,
        playedPercentage = playedPercentage,
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
