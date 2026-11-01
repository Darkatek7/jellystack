package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackQueueTest {
    @Test
    fun theTargetAfterAnEntryIsTheNextEntryOfTheList() =
        runTest {
            val queue = PlaybackQueue().apply { replace(listOf(queueEntry("a"), queueEntry("b"), queueEntry("c"))) }
            val source = queue.source { target(it.id) }

            assertEquals("b", source.resolveNext("a")?.mediaId)
            assertEquals("c", source.resolveNext("b")?.mediaId)
        }

    @Test
    fun theLastEntryHasNoTargetAndAnUnknownEntryIsNotInTheQueue() =
        runTest {
            val queue = PlaybackQueue().apply { replace(listOf(queueEntry("a"), queueEntry("b"))) }
            val source = queue.source { target(it.id) }

            assertNull(source.resolveNext("b"))
            assertNull(source.resolveNext("elsewhere"))
            assertTrue(source.contains("b"))
            assertFalse(source.contains("elsewhere"))
        }

    @Test
    fun anEntryThatCannotBePlayedIsSkippedForTheOneAfterIt() =
        runTest {
            val queue =
                PlaybackQueue().apply {
                    replace(
                        listOf(queueEntry("a"), queueEntry("broken"), queueEntry("failing"), queueEntry("d")),
                    )
                }
            val source =
                queue.source { entry ->
                    when (entry.id) {
                        "broken" -> null
                        "failing" -> error("detail request failed")
                        else -> target(entry.id)
                    }
                }

            assertEquals("d", source.resolveNext("a")?.mediaId)
        }

    @Test
    fun aListWhoseRemainingEntriesCannotBePlayedEndsWithoutATarget() =
        runTest {
            val queue = PlaybackQueue().apply { replace(listOf(queueEntry("a"), queueEntry("broken"))) }
            val source = queue.source { null }

            assertNull(source.resolveNext("a"))
        }

    @Test
    fun replacingOrClearingTheQueueChangesWhatBelongsToIt() {
        val queue = PlaybackQueue().apply { replace(listOf(queueEntry("a"), queueEntry("a"), queueEntry("b"))) }
        assertEquals(listOf("b"), queue.entriesAfter("a").map { it.id })

        queue.replace(listOf(queueEntry("x")))
        assertFalse(queue.contains("a"))
        assertTrue(queue.contains("x"))

        queue.clear()
        assertFalse(queue.contains("x"))
    }

    @Test
    fun playFromAnIndexKeepsTheOrderAndStartsThere() {
        val queue = PlaybackQueue()

        val first = queue.start(listOf(queueEntry("a"), queueEntry("b"), queueEntry("c"), queueEntry("d")), startIndex = 2)

        assertEquals("c", first?.id)
        assertEquals(listOf("d"), queue.entriesAfter("c").map { it.id })
        assertEquals("b", queue.entryBefore("c")?.id)
        assertNull(queue.entryBefore("a"))
    }

    @Test
    fun anIndexOutsideTheListStartsAtTheFirstEntry() {
        val queue = PlaybackQueue()

        assertEquals("a", queue.start(listOf(queueEntry("a"), queueEntry("b")), startIndex = 9)?.id)
        assertNull(queue.start(emptyList()))
        assertFalse(queue.contains("a"))
    }

    @Test
    fun shuffleDecidesTheOrderOnceAndTheSameSeedGivesTheSameOrder() {
        val items = ('a'..'h').map { queueEntry(it.toString()) }
        val queue = PlaybackQueue()

        val first = queue.start(items, startIndex = null, shuffle = Random(7))
        val order = listOf(first!!.id) + queue.entriesAfter(first.id).map { it.id }

        assertEquals(items.map { it.id }.toSet(), order.toSet())
        assertEquals(items.size, order.size)
        assertNotEquals(items.map { it.id }, order)
        val again = PlaybackQueue().apply { start(items, startIndex = null, shuffle = Random(7)) }
        assertEquals(order, listOf(order.first()) + again.entriesAfter(order.first()).map { it.id })
        // Asking where to go from an entry never reorders the queue again.
        assertEquals(queue.entriesAfter(order[2]).map { it.id }, order.drop(3))
    }

    @Test
    fun shuffleFromAChosenEntryPlaysThatEntryFirstAndEveryOtherOnce() {
        val items = ('a'..'f').map { queueEntry(it.toString()) }
        val queue = PlaybackQueue()

        val first = queue.start(items, startIndex = 3, shuffle = Random(1))

        assertEquals("d", first?.id)
        assertNull(queue.entryBefore("d"))
        assertEquals(items.map { it.id }.toSet() - "d", queue.entriesAfter("d").map { it.id }.toSet())
        assertEquals(5, queue.entriesAfter("d").size)
    }

    private fun target(mediaId: String) = PlaybackContinuationTarget(mediaId = mediaId, title = mediaId, fromQueue = true) {}
}

private fun queueEntry(id: String): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = null,
        name = id,
        sortName = null,
        overview = null,
        type = "Movie",
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
