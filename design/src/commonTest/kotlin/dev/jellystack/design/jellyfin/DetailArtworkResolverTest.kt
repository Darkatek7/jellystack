package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailArtworkResolverTest {
    @Test
    fun episodePrefersExplicitSeriesBackdropWithSeriesOwnership() {
        val item =
            episode().copy(
                seriesBackdropImageTag = "explicit-series-backdrop",
                backdropImageTag = "item-backdrop",
            )
        val detail =
            detail().copy(
                backdropImageTags = listOf("episode-backdrop"),
                parentBackdropImageTags = listOf("parent-backdrop"),
            )

        assertEquals(
            DetailArtwork("series-1", "Backdrop", "explicit-series-backdrop"),
            resolveHeroArtwork(item, detail),
        )
    }

    @Test
    fun episodeUsesParentBackdropWithSeriesOwnershipBeforeOwnedBackdrop() {
        val detail =
            detail().copy(
                backdropImageTags = listOf("episode-backdrop"),
                parentBackdropImageTags = listOf("parent-backdrop"),
            )

        assertEquals(
            DetailArtwork("series-1", "Backdrop", "parent-backdrop"),
            resolveHeroArtwork(episode(), detail),
        )
    }

    @Test
    fun episodeUsesDetailOwnedBackdropWithEpisodeOwnershipBeforeItemBackdrop() {
        val item = episode().copy(backdropImageTag = "item-backdrop")
        val detail = detail().copy(backdropImageTags = listOf("episode-backdrop"))

        assertEquals(
            DetailArtwork("episode-1", "Backdrop", "episode-backdrop"),
            resolveHeroArtwork(item, detail),
        )
    }

    @Test
    fun episodeUsesItemBackdropBeforeSeriesPrimary() {
        val item =
            episode().copy(
                backdropImageTag = "item-backdrop",
                seriesPrimaryImageTag = "series-primary",
            )

        assertEquals(
            DetailArtwork("episode-1", "Backdrop", "item-backdrop"),
            resolveHeroArtwork(item, detail()),
        )
    }

    @Test
    fun episodeUsesSeriesPrimaryWithSeriesOwnershipBeforeEpisodePrimary() {
        assertEquals(
            DetailArtwork("series-1", "Primary", "series-primary"),
            resolveHeroArtwork(episode(), detail()),
        )
    }

    @Test
    fun episodePrimaryFallsBackFromDetailToItem() {
        val withoutSeriesPrimary = episode().copy(seriesPrimaryImageTag = null)

        assertEquals(
            DetailArtwork("episode-1", "Primary", "detail-primary"),
            resolveHeroArtwork(withoutSeriesPrimary, detail()),
        )
        assertEquals(
            DetailArtwork("episode-1", "Primary", "item-primary"),
            resolveHeroArtwork(withoutSeriesPrimary, detail().copy(primaryImageTag = null)),
        )
    }

    @Test
    fun moviePrefersItsOwnDetailBackdrop() {
        val movie =
            episode().copy(
                id = "movie-1",
                type = "Movie",
                seriesId = "unrelated-series",
                seriesBackdropImageTag = "unrelated-backdrop",
            )
        val detail =
            detail().copy(
                id = "movie-1",
                backdropImageTags = listOf("movie-backdrop"),
                parentBackdropImageTags = listOf("unrelated-parent-backdrop"),
            )

        assertEquals(
            DetailArtwork("movie-1", "Backdrop", "movie-backdrop"),
            resolveHeroArtwork(movie, detail),
        )
    }

    @Test
    fun episodeKeepsSeriesClearlogoWithSeriesOwnership() {
        val item = episode().copy(seriesLogoImageTag = "series-logo")

        assertEquals(
            DetailArtwork("series-1", "Logo", "series-logo"),
            resolveLogoArtwork(item, detail().copy(logoImageTag = null)),
        )
    }

    private fun episode(): JellyfinItem =
        JellyfinItem(
            id = "episode-1",
            libraryId = "library",
            name = "Episode",
            sortName = "Episode",
            overview = null,
            type = "Episode",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = "season-1",
            primaryImageTag = "item-primary",
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = "series-1",
            seriesPrimaryImageTag = "series-primary",
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
            indexNumber = 1,
            parentIndexNumber = 1,
            seriesName = "Series",
            seasonId = "season-1",
            episodeTitle = "Episode",
            lastPlayed = null,
        )

    private fun detail(): JellyfinItemDetail =
        JellyfinItemDetail(
            id = "episode-1",
            name = "Episode",
            overview = null,
            taglines = emptyList(),
            runTimeTicks = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            genres = emptyList(),
            studios = emptyList(),
            primaryImageTag = "detail-primary",
            backdropImageTags = emptyList(),
            mediaSources = emptyList(),
        )
}
