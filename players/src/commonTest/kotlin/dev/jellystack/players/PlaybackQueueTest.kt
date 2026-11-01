package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
