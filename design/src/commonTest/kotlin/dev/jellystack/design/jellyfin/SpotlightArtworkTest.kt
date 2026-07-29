package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotlightArtworkTest {
    @Test
    fun movieUsesItsOwnBackdropBeforeEveryOtherImage() {
        val item =
            item().copy(
                primaryImageTag = "primary",
                thumbImageTag = "thumb",
                backdropImageTag = "backdrop",
                logoImageTag = "logo",
                artImageTag = "art",
                bannerImageTag = "banner",
            )

        assertEquals(
            SpotlightArtwork("movie-1", "backdrop", "Backdrop"),
            item.selectSpotlightArtwork(),
        )
    }

    @Test
    fun episodeDerivedSeasonUsesSeriesBackdropOwner() {
        val item =
            item().copy(
                id = "season-1",
                type = "Season",
                seriesId = "series-1",
                backdropImageTag = "series-backdrop",
                seriesBackdropImageTag = "series-backdrop",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-backdrop", "Backdrop"),
            item.selectSpotlightArtwork(),
        )
    }

    @Test
    fun movieUsesThumbWhenBackdropIsMissing() {
        val item = item().copy(thumbImageTag = "thumb")

        assertEquals(
            SpotlightArtwork("movie-1", "thumb", "Thumb"),
            item.selectSpotlightArtwork(),
        )
    }

    @Test
    fun episodeDerivedSeasonUsesSeriesThumbOwnerWhenBackdropIsMissing() {
        val item =
            item().copy(
                id = "season-1",
                type = "Season",
                seriesId = "series-1",
                thumbImageTag = "series-thumb",
                seriesThumbImageTag = "series-thumb",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-thumb", "Thumb"),
            item.selectSpotlightArtwork(),
        )
    }

    @Test
    fun primaryLogoBannerAndArtAreNotLandscapeFallbacks() {
        val item =
            item().copy(
                primaryImageTag = "primary",
                logoImageTag = "logo",
                bannerImageTag = "banner",
                artImageTag = "art",
            )

        assertNull(item.selectSpotlightArtwork())
    }

    @Test
    fun movieUsesItsOwnClearLogo() {
        val item = item().copy(logoImageTag = "movie-logo")

        assertEquals(
            SpotlightArtwork("movie-1", "movie-logo", "Logo"),
            item.selectSpotlightLogoArtwork(),
        )
    }

    @Test
    fun episodeDerivedSeasonUsesSeriesClearLogoOwner() {
        val item =
            item().copy(
                id = "season-1",
                type = "Season",
                seriesId = "series-1",
                parentLogoImageTag = "series-logo",
                seriesLogoImageTag = "series-logo",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-logo", "Logo"),
            item.selectSpotlightLogoArtwork(),
        )
    }

    @Test
    fun episodeDerivedSeriesUsesItsResolvedSeriesIdForClearLogo() {
        val item =
            item().copy(
                id = "series-1",
                type = "Series",
                seriesId = "series-1",
                seriesLogoImageTag = "series-logo",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-logo", "Logo"),
            item.selectSpotlightLogoArtwork(),
        )
    }

    @Test
    fun showSpotlightPrefersSeriesClearLogoOverChildLogo() {
        val item =
            item().copy(
                id = "series-1",
                type = "Series",
                seriesId = "series-1",
                logoImageTag = "episode-logo",
                seriesLogoImageTag = "series-logo",
            )

        assertEquals(
            SpotlightArtwork("series-1", "series-logo", "Logo"),
            item.selectSpotlightLogoArtwork(),
        )
    }

    @Test
    fun missingClearLogoFallsBackToText() {
        assertNull(item().selectSpotlightLogoArtwork())
    }

    private fun item(): JellyfinItem =
        JellyfinItem(
            id = "movie-1",
            libraryId = "library",
            name = "Movie",
            sortName = "Movie",
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
