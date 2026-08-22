package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TvHomeDetailNavigationTest {
    @Test
    fun homeCollectionFolderOpensLibraryInsteadOfDetail() {
        val library = item(id = "movies", name = "Movies", type = "CollectionFolder")

        assertEquals(
            TvHomeJellyfinDestination.Library(libraryId = "movies", title = "Movies"),
            tvHomeJellyfinDestination(library),
        )
    }

    @Test
    fun homeMediaDetailKeepsOriginalItemWhenBrowseCacheMisses() =
        runTest {
            val homeItem = item(id = "movie-1", name = "Movie", type = "Movie")
            val detail = detail(id = homeItem.id, name = homeItem.name)

            val loaded =
                loadTvJellyfinDetailBase(
                    itemId = homeItem.id,
                    initialItem = homeItem,
                    cachedItem = { null },
                    loadDetail = { detail },
                )

            assertEquals(homeItem, loaded.item)
            assertEquals(detail, loaded.detail)
        }

    @Test
    fun detailCacheMissTerminatesAsErrorInsteadOfLoadingForever() =
        runTest {
            assertFailsWith<TvDetailLoadException> {
                loadTvJellyfinDetailBase(
                    itemId = "missing",
                    initialItem = null,
                    cachedItem = { null },
                    loadDetail = { detail(id = "missing", name = "Missing") },
                )
            }.let { thrown ->
                assertEquals(TvDetailLoadErrorKind.ITEM_UNAVAILABLE, thrown.kind)
            }
        }

    private fun item(
        id: String,
        name: String,
        type: String,
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = name,
        sortName = name,
        overview = null,
        type = type,
        mediaType = null,
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

    private fun detail(
        id: String,
        name: String,
    ) = JellyfinItemDetail(
        id = id,
        name = name,
        overview = null,
        taglines = emptyList(),
        runTimeTicks = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        genres = emptyList(),
        studios = emptyList(),
        primaryImageTag = null,
        backdropImageTags = emptyList(),
        mediaSources = emptyList(),
    )
}
