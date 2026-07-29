package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesPlaybackTargetResolverTest {
    private val resolver = SeriesPlaybackTargetResolver()

    @Test
    fun `continues the most recently played in-progress episode`() {
        val episodes =
            listOf(
                episode("s1e1", season = 1, number = 1, played = 45.0, lastPlayed = "2026-07-10T10:00:00Z"),
                episode("s2e2", season = 2, number = 2, played = 12.0, lastPlayed = "2026-07-11T10:00:00Z"),
                episode("s1e2", season = 1, number = 2),
            )

        val target = resolver.resolve(episodes, completedDownloadIds = emptySet())

        assertEquals("s2e2", target?.episode?.id)
        assertEquals(SeriesPlaybackReason.CONTINUE, target?.reason)
    }

    @Test
    fun `selects the first unwatched numbered episode before specials`() {
        val episodes =
            listOf(
                episode("special", season = 0, number = 1),
                episode("s2e1", season = 2, number = 1),
                episode("s1e2", season = 1, number = 2),
                episode("s1e1", season = 1, number = 1, played = 100.0),
            )

        val target = resolver.resolve(episodes, completedDownloadIds = emptySet())

        assertEquals("s1e2", target?.episode?.id)
        assertEquals(SeriesPlaybackReason.PLAY, target?.reason)
    }

    @Test
    fun `uses season two when season one is unavailable`() {
        val episodes =
            listOf(
                episode("s1e1", season = 1, number = 1, locationType = "Virtual"),
                episode("s2e1", season = 2, number = 1),
            )

        assertEquals("s2e1", resolver.resolve(episodes, emptySet())?.episode?.id)
    }

    @Test
    fun `treats completed downloads as available`() {
        val downloaded = episode("offline", season = 3, number = 1, locationType = "Virtual")

        val target = resolver.resolve(listOf(downloaded), completedDownloadIds = setOf("offline"))

        assertEquals("offline", target?.episode?.id)
    }

    @Test
    fun `falls back to the first available episode when all are watched`() {
        val episodes =
            listOf(
                episode("unknown", season = null, number = 1, played = 100.0),
                episode("special", season = 0, number = 1, played = 100.0),
                episode("s2e1", season = 2, number = 1, played = 100.0),
            )

        val target = resolver.resolve(episodes, emptySet())

        assertEquals("s2e1", target?.episode?.id)
        assertEquals(SeriesPlaybackReason.RESTART, target?.reason)
    }

    private fun episode(
        id: String,
        season: Int?,
        number: Int?,
        played: Double? = null,
        lastPlayed: String? = null,
        locationType: String? = "FileSystem",
    ) = JellyfinItem(
        id = id,
        libraryId = "shows",
        name = id,
        sortName = id,
        overview = null,
        type = "Episode",
        mediaType = "Video",
        locationType = locationType,
        taglines = emptyList(),
        parentId = "season-$season",
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = "series",
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = 1_000_000,
        positionTicks = null,
        playedPercentage = played,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = number,
        parentIndexNumber = season,
        seriesName = "Series",
        seasonId = "season-$season",
        episodeTitle = id,
        lastPlayed = lastPlayed,
    )
}
