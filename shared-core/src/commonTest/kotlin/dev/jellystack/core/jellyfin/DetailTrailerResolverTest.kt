package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DetailTrailerResolverTest {
    @Test
    fun currentLocalTrailerWinsBeforeParentAndSeerr() =
        runTest {
            val requested = mutableListOf<String>()
            val resolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { id ->
                        requested += id
                        if (id == "episode") listOf(trailerItem("local")) else emptyList()
                    },
                    fetchItemDetail = { detail(it) },
                    fetchSeerrTrailer = { _, _ -> youtubeTrailer },
                )

            val source = resolver.resolve(context(id = "episode", episode = true, seriesId = "series"))

            assertEquals("local", assertIs<DetailTrailerSource.Local>(source).item.id)
            assertEquals(listOf("episode"), requested)
        }

    @Test
    fun episodeFallsBackToParentLocalThenSeerr() =
        runTest {
            val parentResolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { id -> if (id == "series") listOf(trailerItem("series-trailer")) else emptyList() },
                    fetchItemDetail = { detail(it) },
                    fetchSeerrTrailer = { _, _ -> youtubeTrailer },
                )
            assertEquals(
                "series-trailer",
                assertIs<DetailTrailerSource.Local>(
                    parentResolver.resolve(context("episode", true, "series")),
                ).item.id,
            )

            val seerrResolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { emptyList() },
                    fetchItemDetail = { detail(it, mapOf("Tmdb" to "42")) },
                    fetchSeerrTrailer = { tmdbId, isShow -> youtubeTrailer.takeIf { tmdbId == 42 && isShow } },
                )
            assertIs<DetailTrailerSource.YouTube>(seerrResolver.resolve(context("episode", true, "series")))
        }

    @Test
    fun lookupFailuresAreSoft() =
        runTest {
            val resolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { error("offline") },
                    fetchItemDetail = { error("offline") },
                    fetchSeerrTrailer = { _, _ -> error("offline") },
                )

            assertNull(resolver.resolve(context("movie", false, null)))
        }

    @Test
    fun seriesUsesTvTrailerLookup() =
        runTest {
            val resolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { emptyList() },
                    fetchItemDetail = { null },
                    fetchSeerrTrailer = { tmdbId, isShow ->
                        youtubeTrailer.takeIf { tmdbId == 42 && isShow }
                    },
                )

            assertIs<DetailTrailerSource.YouTube>(
                resolver.resolve(context("series", episode = false, seriesId = null, series = true)),
            )
        }

    @Test
    fun cancellationIsPropagated() =
        runTest {
            val resolver =
                DetailTrailerResolver(
                    fetchLocalTrailers = { throw CancellationException("screen closed") },
                    fetchItemDetail = { null },
                    fetchSeerrTrailer = { _, _ -> null },
                )

            assertFailsWith<CancellationException> {
                resolver.resolve(context("movie", episode = false, seriesId = null))
            }
        }

    private fun context(
        id: String,
        episode: Boolean,
        seriesId: String?,
        series: Boolean = false,
    ) = DetailTrailerContext(
        itemId = id,
        isEpisode = episode,
        seriesId = seriesId,
        detail = detail(id, mapOf("Tmdb" to "42")),
        isSeries = series,
    )

    private fun detail(
        id: String,
        providers: Map<String, String> = emptyMap(),
    ) = JellyfinItemDetail(
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
        providerIds = providers,
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
        )

    private val youtubeTrailer = JellyseerrMediaTrailer("Trailer", "YouTube", "Trailer", "abc", "https://youtu.be/abc")
}
