package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSectionNavigationTest {
    @Test
    fun collectionFolderOpensItsLibraryInsteadOfAnItemDetail() {
        assertEquals(
            HomeSectionJellyfinTarget.Library,
            item(type = "CollectionFolder").homeSectionJellyfinTarget(),
        )
    }

    @Test
    fun playableMediaStillOpensItsItemDetail() {
        assertEquals(
            HomeSectionJellyfinTarget.Detail,
            item(type = "Movie").homeSectionJellyfinTarget(),
        )
    }

    private fun item(type: String): JellyfinItem =
        JellyfinItem(
            id = "item-1",
            libraryId = "library",
            name = "Item",
            sortName = "Item",
            overview = null,
            type = type,
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
