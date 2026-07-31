package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSectionArtworkTest {
    @Test
    fun landscapeEpisodeUsesSeriesBackdropAndSeriesOwner() {
        val episode =
            item().copy(
                id = "episode-1",
                type = "Episode",
                seriesId = "series-1",
                backdropImageTag = "series-backdrop",
                seriesBackdropImageTag = "series-backdrop",
                primaryImageTag = "episode-primary",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-backdrop", "Backdrop"),
            episode.selectHomeSectionArtwork(HomeSectionViewMode.LANDSCAPE),
        )
    }

    @Test
    fun landscapeLibraryFallsBackToItsPrimaryImageType() {
        val library =
            item().copy(
                id = "library-1",
                type = "CollectionFolder",
                primaryImageTag = "library-primary",
            )

        assertEquals(
            SpotlightArtwork("library-1", "library-primary", "Primary"),
            library.selectHomeSectionArtwork(HomeSectionViewMode.LANDSCAPE),
        )
    }

    @Test
    fun portraitEpisodeUsesSeriesPrimaryAndSeriesOwnerWhenAvailable() {
        val episode =
            item().copy(
                id = "episode-1",
                type = "Episode",
                seriesId = "series-1",
                primaryImageTag = "episode-primary",
                seriesPrimaryImageTag = "series-primary",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-primary", "Primary"),
            episode.selectHomeSectionArtwork(HomeSectionViewMode.PORTRAIT),
        )
    }

    private fun item(): JellyfinItem =
        JellyfinItem(
            id = "item-1",
            libraryId = "library",
            name = "Item",
            sortName = "Item",
            overview = null,
            type = "Movie",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = "library",
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
