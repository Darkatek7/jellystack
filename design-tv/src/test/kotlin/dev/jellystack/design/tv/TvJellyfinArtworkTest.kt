package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals

class TvJellyfinArtworkTest {
    @Test
    fun moviePrimaryArtworkUsesMovieIdInsteadOfParentLibraryId() {
        val movie = item(type = "Movie", seriesId = "library-1", primaryImageTag = "movie-primary")

        assertEquals(
            TvJellyfinArtwork(itemId = "item-1", imageTag = "movie-primary", imageType = "Primary"),
            resolveTvJellyfinArtwork(movie),
        )
    }

    @Test
    fun episodeSeriesBackdropKeepsBackdropTypeAndSeriesId() {
        val episode = item(type = "Episode", seriesId = "series-1", seriesBackdropImageTag = "series-backdrop")

        assertEquals(
            TvJellyfinArtwork(itemId = "series-1", imageTag = "series-backdrop", imageType = "Backdrop"),
            resolveTvJellyfinArtwork(episode),
        )
    }

    @Test
    fun episodeFallsBackToSeriesPrimaryArtwork() {
        val episode = item(type = "Episode", seriesId = "series-1", seriesPrimaryImageTag = "series-primary")

        assertEquals(
            TvJellyfinArtwork(itemId = "series-1", imageTag = "series-primary", imageType = "Primary"),
            resolveTvJellyfinArtwork(episode),
        )
    }

    private fun item(
        type: String,
        seriesId: String?,
        primaryImageTag: String? = null,
        seriesPrimaryImageTag: String? = null,
        seriesBackdropImageTag: String? = null,
    ): JellyfinItem =
        JellyfinItem(
            id = "item-1",
            libraryId = "library-1",
            name = "Item",
            sortName = null,
            overview = null,
            type = type,
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = "library-1",
            primaryImageTag = primaryImageTag,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = seriesId,
            seriesPrimaryImageTag = seriesPrimaryImageTag,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = seriesBackdropImageTag,
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
