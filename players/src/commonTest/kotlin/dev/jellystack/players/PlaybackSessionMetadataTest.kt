package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSessionMetadataTest {
    @Test
    fun artworkPrefersExplicitUrlThenPrimaryTag() {
        val explicit =
            request(
                metadata = PlaybackMetadata(title = "T", seriesId = null, seriesName = null, episodeName = null, artworkUrl = "https://art/1.jpg", primaryImageTag = null),
            )
        assertEquals("https://art/1.jpg", explicit.artworkUrl("https://jf.example"))

        val tagOnly =
            request(
                metadata = PlaybackMetadata(title = "T", seriesId = null, seriesName = null, episodeName = null, artworkUrl = null, primaryImageTag = "tag-1"),
            )
        assertEquals("https://jf.example/Items/item-1/Images/Primary?tag=tag-1", tagOnly.artworkUrl("https://jf.example"))
    }

    @Test
    fun artworkNeedsBaseUrlAndTag() {
        val tagOnly =
            request(
                metadata = PlaybackMetadata(title = "T", seriesId = null, seriesName = null, episodeName = null, artworkUrl = null, primaryImageTag = "tag-1"),
            )
        assertNull(tagOnly.artworkUrl(null))
        assertNull(tagOnly.artworkUrl("  "))

        val noArtworkAtAll =
            request(
                metadata = PlaybackMetadata(title = "T", seriesId = null, seriesName = null, episodeName = null, artworkUrl = null, primaryImageTag = null),
            )
        assertNull(noArtworkAtAll.artworkUrl("https://jf.example"))
        assertNull(request(metadata = null).artworkUrl("https://jf.example"))
    }

    @Test
    fun artistLineDescribesEpisodesBySeriesSeasonAndNumber() {
        val episode =
            PlaybackMetadata(
                title = "Pilot",
                seriesId = "s1",
                seriesName = "Show",
                episodeName = "Pilot",
                artworkUrl = null,
                primaryImageTag = null,
                seasonNumber = 2,
                episodeNumber = 7,
            )
        assertEquals("Show · S2 · E7", episode.sessionArtistLine())

        val seriesOnly =
            episode.copy(seasonNumber = null, episodeNumber = null)
        assertEquals("Show", seriesOnly.sessionArtistLine())
    }

    @Test
    fun artistLineEmptyForMoviesWithoutSeries() {
        val movie =
            PlaybackMetadata(title = "Film", seriesId = null, seriesName = null, episodeName = null, artworkUrl = null, primaryImageTag = null)
        assertNull(movie.sessionArtistLine())
        assertNull(movie.copy(seriesName = "  ").sessionArtistLine())
    }

    private fun request(
        mediaId: String = "item-1",
        metadata: PlaybackMetadata?,
    ): PlaybackRequest =
        PlaybackRequest(
            mediaId = mediaId,
            mediaSources = emptyList(),
            metadata = metadata,
        )
}
