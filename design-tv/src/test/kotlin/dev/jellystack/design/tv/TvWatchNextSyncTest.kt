package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvWatchNextSyncTest {
    private val minute = 60L * 10_000_000L

    @Test
    fun aResumedMovieBecomesAContinueCardWithPositionAndDuration() {
        val items =
            buildTvWatchNextItems(
                continueWatching = listOf(movie(1, runMinutes = 100, positionMinutes = 40, thumbTag = "thumb")),
                nextUp = emptyList(),
            )

        val card = items.single()
        assertEquals(TvWatchNextKind.CONTINUE, card.kind)
        assertEquals(TvWatchNextMediaType.MOVIE, card.mediaType)
        assertEquals(100 * 60_000L, card.durationMs)
        assertEquals(40 * 60_000L, card.positionMs)
        assertEquals(TvWatchNextArtAspect.LANDSCAPE_16_9, card.artwork?.aspect)
    }

    @Test
    fun anEpisodeCarriesSeriesNameAndNumbersAndNoPositionWhenItIsNextUp() {
        val items =
            buildTvWatchNextItems(
                continueWatching = emptyList(),
                nextUp = listOf(episode(2, series = "Example Show", season = 2, number = 5, primaryTag = "p")),
            )

        val card = items.single()
        assertEquals(TvWatchNextKind.NEXT, card.kind)
        assertEquals("Example Show", card.title)
        assertEquals("Episode 5", card.episodeTitle)
        assertEquals(2, card.seasonNumber)
        assertEquals(5, card.episodeNumber)
        assertNull(card.positionMs)
        assertEquals(TvWatchNextArtAspect.PORTRAIT_2_3, card.artwork?.aspect)
    }

    @Test
    fun anItemInBothListsIsPublishedOnceAsContinue() {
        val shared = resumedEpisode(3, runMinutes = 40, positionMinutes = 10)

        val items = buildTvWatchNextItems(listOf(shared), listOf(shared))

        assertEquals(listOf(TvWatchNextKind.CONTINUE), items.map { it.kind })
    }

    @Test
    fun resumeCardsWithoutUsablePositionAndItemsOfOtherTypesAreLeftOut() {
        val items =
            buildTvWatchNextItems(
                continueWatching =
                    listOf(
                        movie(4, runMinutes = null, positionMinutes = 5),
                        movie(5, runMinutes = 10, positionMinutes = 10),
                        movie(6, runMinutes = 10, positionMinutes = 5).copy(id = "not-a-guid"),
                        movie(7, runMinutes = 10, positionMinutes = 5).copy(type = "Series"),
                    ),
                nextUp = emptyList(),
            )

        assertTrue(items.isEmpty())
    }

    @Test
    fun eachRowIsLimited() {
        val resume = (1..30).map { movie(it, runMinutes = 100, positionMinutes = 10) }
        val next = (31..60).map { episode(it, series = "Show", season = 1, number = it) }

        val items = buildTvWatchNextItems(resume, next)

        assertEquals(TV_WATCH_NEXT_LIMIT_PER_KIND, items.count { it.kind == TvWatchNextKind.CONTINUE })
        assertEquals(TV_WATCH_NEXT_LIMIT_PER_KIND, items.count { it.kind == TvWatchNextKind.NEXT })
    }

    private fun id(n: Int): String = n.toString(16).padStart(32, '0')

    private fun movie(
        n: Int,
        runMinutes: Long?,
        positionMinutes: Long?,
        thumbTag: String? = null,
    ): JellyfinItem =
        base(id(n), "Movie").copy(
            name = "Example Movie $n",
            runTimeTicks = runMinutes?.times(minute),
            positionTicks = positionMinutes?.times(minute),
            thumbImageTag = thumbTag,
            lastPlayed = "2026-10-01T20:00:00.0000000Z",
        )

    private fun episode(
        n: Int,
        series: String,
        season: Int,
        number: Int,
        primaryTag: String? = null,
    ): JellyfinItem =
        base(id(n), "Episode").copy(
            name = "Episode $number",
            seriesName = series,
            seriesId = id(n + 1_000),
            parentIndexNumber = season,
            indexNumber = number,
            seriesPrimaryImageTag = primaryTag,
        )

    private fun resumedEpisode(
        n: Int,
        runMinutes: Long,
        positionMinutes: Long,
    ): JellyfinItem =
        episode(n, series = "Example Show", season = 1, number = 1)
            .copy(runTimeTicks = runMinutes * minute, positionTicks = positionMinutes * minute)

    private fun base(
        id: String,
        type: String,
    ) = JellyfinItem(
        id = id,
        libraryId = "library-1",
        name = "Item",
        sortName = null,
        overview = null,
        type = type,
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = "library-1",
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
}
