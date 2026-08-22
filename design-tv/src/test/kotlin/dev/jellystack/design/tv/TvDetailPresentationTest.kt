package dev.jellystack.design.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDetailPresentationTest {
    @Test
    fun moviesAndSeriesUseGraphicLogosWhileEpisodesUseWhiteTitleText() {
        val movie = tvJellyfinHeroTitlePresentation(itemType = "Movie", logoTag = "movie-logo")
        val series = tvJellyfinHeroTitlePresentation(itemType = "Series", logoTag = "series-logo")
        val episode = tvJellyfinHeroTitlePresentation(itemType = "Episode", logoTag = "series-logo")
        val movieWithoutLogo = tvJellyfinHeroTitlePresentation(itemType = "Movie", logoTag = null)

        assertTrue(movie.useGraphicLogo)
        assertTrue(series.useGraphicLogo)
        assertFalse(episode.useGraphicLogo)
        assertEquals(TvText, episode.textColor)
        assertFalse(movieWithoutLogo.useGraphicLogo)
        assertEquals(TvText, movieWithoutLogo.textColor)
    }
}
