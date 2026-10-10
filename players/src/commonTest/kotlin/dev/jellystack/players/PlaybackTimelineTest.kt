package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackTimelineTest {
    private val chapters =
        listOf(
            chapter(0, 0L),
            chapter(1, 60_000L),
            chapter(2, 120_000L),
        )

    @Test
    fun chapterAtAndNextChapterFollowThePosition() {
        assertEquals(1, chapters.chapterAt(90_000L)?.index)
        assertEquals(2, chapters.chapterAt(120_000L)?.index)
        assertEquals(120_000L, chapters.nextChapterStart(60_000L))
        assertNull(chapters.nextChapterStart(130_000L))
        assertNull(listOf(chapter(0, 10_000L)).chapterAt(5_000L))
    }

    @Test
    fun previousRestartsTheChapterUnlessItJustStarted() {
        assertEquals(60_000L, chapters.previousChapterStart(90_000L), "well into chapter 2: restart it")
        assertEquals(0L, chapters.previousChapterStart(61_000L), "just after the start: go to chapter 1")
        assertEquals(0L, chapters.previousChapterStart(1_000L), "the first chapter can only restart")
        assertNull(emptyList<PlaybackChapter>().previousChapterStart(1_000L))
    }

    @Test
    fun endTimeUsesTheRemainingTimeAtThePlaybackSpeed() {
        assertEquals(1_600_000L, playbackEndsAtMs(nowMs = 1_000_000L, positionMs = 400_000L, durationMs = 1_000_000L, playbackSpeed = 1f))
        assertEquals(1_300_000L, playbackEndsAtMs(nowMs = 1_000_000L, positionMs = 400_000L, durationMs = 1_000_000L, playbackSpeed = 2f))
        assertEquals(1_000_000L, playbackEndsAtMs(nowMs = 1_000_000L, positionMs = 2_000_000L, durationMs = 1_000_000L, playbackSpeed = 1f))
        assertNull(playbackEndsAtMs(nowMs = 0L, positionMs = 0L, durationMs = null, playbackSpeed = 1f))
        assertEquals(600_000L, playbackEndsAtMs(nowMs = 0L, positionMs = 0L, durationMs = 600_000L, playbackSpeed = 0f))
    }

    @Test
    fun markersSkipTheStartChapterAndKeepSegmentRanges() {
        val markers =
            playbackTimelineMarkers(
                durationMs = 200_000L,
                chapters = chapters,
                segments =
                    listOf(
                        PlaybackSegment("intro", PlaybackSegmentType.INTRO, 10_000L, 30_000L),
                        PlaybackSegment("empty", PlaybackSegmentType.RECAP, 50_000L, 50_000L),
                    ),
            )

        assertEquals(listOf(0.3f, 0.6f), markers.chapterStarts)
        assertEquals(listOf(PlaybackTimelineRange(PlaybackSegmentType.INTRO, 0.05f, 0.15f)), markers.segments)
        assertEquals(PlaybackTimelineMarkers(), playbackTimelineMarkers(null, chapters, emptyList()))
    }

    private fun chapter(
        index: Int,
        startMs: Long,
    ) = PlaybackChapter(index = index, name = "Chapter ${index + 1}", startPositionMs = startMs, imageTag = null)
}
