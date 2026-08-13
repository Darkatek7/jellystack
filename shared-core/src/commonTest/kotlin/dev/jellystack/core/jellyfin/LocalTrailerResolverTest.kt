package dev.jellystack.core.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalTrailerResolverTest {
    @Test
    fun currentItemWinsBeforeEpisodeParent() =
        runTest {
            val requested = mutableListOf<String>()
            val resolver =
                LocalTrailerResolver(
                    fetchLocalTrailers = { id ->
                        requested += id
                        listOf(trailerItem("$id-trailer"))
                    },
                    fetchItemDetail = { detail(it) },
                )

            val result = resolver.resolve(LocalTrailerContext("episode", isEpisode = true, seriesId = "series"))

            assertEquals("episode-trailer", result?.item?.id)
            assertEquals(listOf("episode"), requested)
        }

    @Test
    fun episodeFallsBackToSeriesTrailer() =
        runTest {
            val resolver =
                LocalTrailerResolver(
                    fetchLocalTrailers = { id ->
                        if (id == "series") listOf(trailerItem("series-trailer")) else emptyList()
                    },
                    fetchItemDetail = { detail(it) },
                )

            val result = resolver.resolve(LocalTrailerContext("episode", isEpisode = true, seriesId = "series"))

            assertEquals("series-trailer", result?.item?.id)
        }

    @Test
    fun failuresAreSoftButCancellationPropagates() =
        runTest {
            val softResolver =
                LocalTrailerResolver(
                    fetchLocalTrailers = { error("offline") },
                    fetchItemDetail = { error("offline") },
                )
            assertNull(softResolver.resolve(LocalTrailerContext("movie", isEpisode = false, seriesId = null)))

            val cancellingResolver =
                LocalTrailerResolver(
                    fetchLocalTrailers = { throw CancellationException("closed") },
                    fetchItemDetail = { null },
                )
            assertFailsWith<CancellationException> {
                cancellingResolver.resolve(LocalTrailerContext("movie", isEpisode = false, seriesId = null))
            }
        }

    private fun detail(id: String) =
        JellyfinItemDetail(
            id,
            id,
            null,
            emptyList(),
            null,
            null,
            null,
            null,
            null,
            emptyList(),
            emptyList(),
            null,
            emptyList(),
            emptyList(),
            providerIds = emptyMap(),
        )

    private fun trailerItem(id: String) =
        JellyfinItem(
            id,
            null,
            id,
            null,
            null,
            "Trailer",
            "Video",
            null,
            emptyList(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )
}
