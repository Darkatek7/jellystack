package dev.jellystack.design.jellyseerr

import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals

class SeerrExternalPlaybackTest {
    @Test
    fun trailerOpensFromEveryDetailOriginAndReportsItsExactEntry() {
        val trailer =
            JellyseerrMediaTrailer(
                name = "Official trailer",
                site = "YouTube",
                type = "Trailer",
                key = "abc123",
                url = "https://video.test/official",
            )

        SeerrDetailOrigin.entries.forEach { origin ->
            val entry = entry(origin)
            val opened = mutableListOf<String>()
            val reported = mutableListOf<Pair<SeerrDetailEntry, JellyseerrMediaTrailer?>>()

            handleSeerrTrailerSelection(
                entry = entry,
                trailer = trailer,
                openUri = opened::add,
                onTrailer = { selectedEntry, selectedTrailer ->
                    reported += selectedEntry to selectedTrailer
                },
            )

            assertEquals(listOf("https://video.test/official"), opened, origin.name)
            assertEquals(
                listOf<Pair<SeerrDetailEntry, JellyseerrMediaTrailer?>>(entry to trailer),
                reported,
                origin.name,
            )
        }
    }

    @Test
    fun youtubeVideoFallsBackToAppAndWebUrisWhenNoUrlWasProvided() {
        val video =
            JellyseerrMediaVideo(
                id = "video",
                name = "Teaser",
                site = "YouTube",
                type = "Teaser",
                key = "fallback-key",
                url = null,
                official = true,
                publishedAt = null,
            )
        val attempted = mutableListOf<String>()

        handleSeerrVideoSelection(
            video = video,
            openUri = { uri ->
                attempted += uri
                if (uri.startsWith("vnd.youtube")) error("YouTube app is unavailable")
            },
        )

        assertEquals(
            listOf(
                "vnd.youtube://fallback-key",
                "https://www.youtube.com/watch?v=fallback-key",
            ),
            attempted,
        )
    }

    private fun entry(origin: SeerrDetailOrigin): SeerrDetailEntry {
        val item =
            JellyseerrSearchItem(
                tmdbId = 10,
                mediaType = JellyseerrMediaType.MOVIE,
                title = "Movie",
                overview = null,
                releaseYear = null,
                posterPath = null,
                backdropPath = null,
                mediaInfoId = null,
                tvdbId = null,
                availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
                requests = emptyList(),
            )
        return SeerrDetailEntry(
            key = SeerrDetailKey(item.mediaType, item.tmdbId),
            item = item,
            origin = origin,
        )
    }
}
