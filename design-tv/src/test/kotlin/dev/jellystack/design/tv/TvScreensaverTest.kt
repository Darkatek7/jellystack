package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvScreensaverTest {
    @Test
    fun rotationShowsEverySlideOnceBeforeAnyRepeats() {
        val slides = (1..6).map(::slide)
        val rotation = TvScreensaverRotation(slides, Random(7))

        repeat(3) {
            val round = List(slides.size) { requireNotNull(rotation.next()).id }
            assertEquals(slides.map { it.id }.toSet(), round.toSet())
            assertEquals(slides.size, round.size)
        }
    }

    @Test
    fun rotationNeverStartsARoundWithTheSlideThatEndedTheLastOne() {
        val slides = (1..2).map(::slide)
        val rotation = TvScreensaverRotation(slides, Random(1))

        val shown = List(40) { requireNotNull(rotation.next()).id }

        assertTrue(shown.zipWithNext().none { (a, b) -> a == b })
    }

    @Test
    fun rotationWithNoSlidesShowsNothing() {
        assertNull(TvScreensaverRotation(emptyList()).next())
    }

    @Test
    fun rotationWithOneSlideKeepsShowingIt() {
        val rotation = TvScreensaverRotation(listOf(slide(1)))

        assertEquals("id-1", rotation.next()?.id)
        assertEquals("id-1", rotation.next()?.id)
    }

    @Test
    fun slidesSkipItemsWithoutABackdrop() {
        val slides =
            tvScreensaverSlides(
                items = listOf(item("a", backdropTag = "t1"), item("b"), item("c", backdropTag = " ")),
                baseUrl = "https://media.example",
                token = "dummy-token",
            )

        assertEquals(listOf("a"), slides.map { it.id })
    }

    @Test
    fun episodesShowTheSeriesBackdropOnceUnderTheSeriesTitle() {
        val slides =
            tvScreensaverSlides(
                items =
                    listOf(
                        item("e1", type = "Episode", seriesId = "s1", seriesBackdropTag = "st"),
                        item("e2", type = "Episode", seriesId = "s1", seriesBackdropTag = "st"),
                    ),
                baseUrl = "https://media.example",
                token = null,
            )

        assertEquals(1, slides.size)
        assertEquals("Show", slides.single().title)
        assertTrue(slides.single().imageUrl.contains("/Items/s1/Images/Backdrop"))
    }

    @Test
    fun slidesNeedAServerAddress() {
        val items = listOf(item("a", backdropTag = "t"))

        assertEquals(emptyList(), tvScreensaverSlides(items, baseUrl = null, token = "x"))
    }

    @Test
    fun noPickedProfileShowsNothingAndReadsNoLibrary() =
        runTest {
            var read = false
            val source = source(profileId = null, onRead = { read = true })

            assertEquals(emptyList(), source.load())
            assertEquals(false, read)
        }

    @Test
    fun pinProtectedProfileShowsNothingAndReadsNoLibrary() =
        runTest {
            var read = false
            val source = source(profileId = "p1", pinProtected = true, onRead = { read = true })

            assertEquals(emptyList(), source.load())
            assertEquals(false, read)
        }

    @Test
    fun openProfileWithCachedBackdropsGetsSlides() =
        runTest {
            assertEquals(listOf("a"), source(profileId = "p1").load().map { it.id })
        }

    @Test
    fun profileWithEmptyCacheShowsNothing() =
        runTest {
            assertEquals(emptyList(), source(profileId = "p1", items = emptyList()).load())
        }

    @Test
    fun failureWhileReadingShowsNothing() =
        runTest {
            val source =
                TvScreensaverSource(
                    activeProfileId = { "p1" },
                    isPinProtected = { false },
                    cachedItems = { error("database closed") },
                    connection = { TvScreensaverConnection("https://media.example", "dummy-token") },
                )

            assertEquals(emptyList(), source.load())
        }

    private fun source(
        profileId: String?,
        pinProtected: Boolean = false,
        items: List<JellyfinItem> = listOf(item("a", backdropTag = "t1")),
        onRead: () -> Unit = {},
    ) = TvScreensaverSource(
        activeProfileId = { profileId },
        isPinProtected = { pinProtected },
        cachedItems = {
            onRead()
            items
        },
        connection = { TvScreensaverConnection("https://media.example", "dummy-token") },
    )

    private fun slide(number: Int): TvScreensaverSlide {
        val url = "https://media.example/$number"
        return TvScreensaverSlide("id-$number", "Title $number", 2000 + number, url)
    }

    private fun item(
        id: String,
        type: String = "Movie",
        backdropTag: String? = null,
        seriesId: String? = null,
        seriesBackdropTag: String? = null,
    ): JellyfinItem =
        JellyfinItem(
            id = id,
            libraryId = "library-1",
            name = "Item $id",
            sortName = null,
            overview = null,
            type = type,
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = "library-1",
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = backdropTag,
            seriesId = seriesId,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = seriesBackdropTag,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = 2001,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = null,
            seriesName = if (type == "Episode") "Show" else null,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
        )
}
