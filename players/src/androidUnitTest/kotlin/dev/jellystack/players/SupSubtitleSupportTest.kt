package dev.jellystack.players

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SupSubtitleSupportTest {
    @Test
    fun displaySetsAreSplitAtEndSegmentsAndTimedByTheirPresentationTimestamp() {
        val file =
            supSegment(pts = 90_000, type = 0x16, payload = byteArrayOf(1, 2)) +
                supSegment(pts = 90_000, type = 0x14, payload = byteArrayOf(3)) +
                supSegment(pts = 90_000, type = 0x80, payload = byteArrayOf()) +
                supSegment(pts = 180_000, type = 0x16, payload = byteArrayOf(4)) +
                supSegment(pts = 180_000, type = 0x80, payload = byteArrayOf())

        val sets = parseSupDisplaySets(file, file.size)

        assertEquals(listOf(1_000_000L, 2_000_000L), sets.map(SupDisplaySet::timeUs))
        assertContentEquals(
            byteArrayOf(0x16, 0, 2, 1, 2, 0x14, 0, 1, 3, 0x80.toByte(), 0, 0),
            sets.first().sections,
        )
    }

    @Test
    fun truncatedOrGarbledTailKeepsEveryCompleteDisplaySet() {
        val complete =
            supSegment(pts = 9_000, type = 0x16, payload = byteArrayOf(1)) +
                supSegment(pts = 9_000, type = 0x80, payload = byteArrayOf())
        val unfinished = supSegment(pts = 18_000, type = 0x16, payload = byteArrayOf(1, 2, 3))

        assertEquals(1, parseSupDisplaySets(complete + unfinished, complete.size + unfinished.size - 2).size)
        assertEquals(1, parseSupDisplaySets(complete + byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9), complete.size + 14).size)
        assertEquals(0, parseSupDisplaySets(ByteArray(4), 4).size)
    }

    @Test
    fun aDisplaySetThatNeverEndedIsDroppedWhenTheNextOneStarts() {
        val file =
            supSegment(pts = 9_000, type = 0x16, payload = byteArrayOf(1)) +
                supSegment(pts = 18_000, type = 0x16, payload = byteArrayOf(2)) +
                supSegment(pts = 18_000, type = 0x80, payload = byteArrayOf())

        assertEquals(listOf(200_000L), parseSupDisplaySets(file, file.size).map(SupDisplaySet::timeUs))
    }

    @Test
    fun subtitleEventsFollowTheDisplaySetTimes() {
        val subtitle =
            SupSubtitle(
                listOf(
                    SupDisplaySet(1_000_000, byteArrayOf()),
                    SupDisplaySet(3_000_000, byteArrayOf()),
                ),
            )

        assertEquals(2, subtitle.eventTimeCount)
        assertEquals(0, subtitle.getNextEventTimeIndex(0))
        assertEquals(1, subtitle.getNextEventTimeIndex(1_000_000))
        assertEquals(-1, subtitle.getNextEventTimeIndex(3_000_000))
        assertEquals(emptyList(), subtitle.getCues(500_000))
    }

    private fun supSegment(
        pts: Long,
        type: Int,
        payload: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write('P'.code)
        out.write('G'.code)
        listOf(pts, 0L).forEach { value ->
            out.write((value shr 24).toInt() and 0xFF)
            out.write((value shr 16).toInt() and 0xFF)
            out.write((value shr 8).toInt() and 0xFF)
            out.write(value.toInt() and 0xFF)
        }
        out.write(type)
        out.write(payload.size shr 8)
        out.write(payload.size and 0xFF)
        out.write(payload)
        return out.toByteArray()
    }
}
