package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The saveable TV back stack persists routes through the @Serializable NavKey hierarchy.
 * Round-tripping every route guards that contract (see TvRouteBackStack).
 */
class TvNavigationSerializationTest {
    @Test
    fun backStackEncodeDecodeRoundTrip() {
        val routes: List<TvRoute> =
            listOf(
                TvRoute.Home,
                TvRoute.Library(libraryId = "lib-1", title = "Movies"),
                TvRoute.Settings(section = "playback"),
                TvRoute.JellyfinDetail(itemId = "item-42"),
                TvRoute.SeerrDetail(
                    tmdbId = 1234,
                    mediaType = JellyseerrMediaType.TV,
                    title = "Show",
                    overview = "Overview",
                    posterPath = "/p.jpg",
                    backdropPath = "/b.jpg",
                    releaseYear = "2024",
                    tvdbId = 99,
                ),
            )
        assertEquals(routes, TvRouteBackStack.decode(TvRouteBackStack.encode(routes)))
    }

    @Test
    fun corruptBackStackDecodesToNull() {
        assertNull(TvRouteBackStack.decode("not-json"))
        assertNull(TvRouteBackStack.decode("""[{"type":"Unknown","x":1}]"""))
    }

    @Test
    fun defaultsSurviveOmission() {
        // Round-trips through the real encoder, so default values (title = null) survive.
        val routes: List<TvRoute> =
            listOf(
                TvRoute.Library(libraryId = "lib"),
                TvRoute.Settings(),
                TvRoute.SeerrDetail(tmdbId = 5, mediaType = JellyseerrMediaType.MOVIE, title = "Film"),
            )
        assertEquals(routes, TvRouteBackStack.decode(TvRouteBackStack.encode(routes)))
    }
}
