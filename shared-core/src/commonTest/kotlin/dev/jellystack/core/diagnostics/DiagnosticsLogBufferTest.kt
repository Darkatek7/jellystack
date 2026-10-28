package dev.jellystack.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsLogBufferTest {
    @Test
    fun keepsOnlyTheNewestLinesInOrder() {
        val buffer = DiagnosticsLogBuffer(capacity = 3, nowMillis = { 0L })

        (1..5).forEach { buffer.record(DiagnosticsLogLevel.DEBUG, "line $it") }

        val lines = buffer.snapshot()
        assertEquals(3, lines.size)
        assertEquals(listOf("line 3", "line 4", "line 5"), lines.map { it.substringAfter(" D ") })
    }

    @Test
    fun redactsTokensAndServerUrlsBeforeStoring() {
        val buffer = DiagnosticsLogBuffer(nowMillis = { 0L })

        buffer.record(
            DiagnosticsLogLevel.WARN,
            "GET https://jelly.example.com:8920/Items?api_key=abc123 failed, X-Emby-Token=secretvalue Authorization: MediaBrowser Token=\"xyz\"",
            IllegalStateException("password=hunter2 at http://10.0.0.5:8096/Users/Me"),
        )

        val line = buffer.snapshot().single()
        assertTrue("https://jelly.example.com:8920/..." in line, line)
        listOf("abc123", "secretvalue", "xyz", "hunter2", "/Items", "/Users").forEach { secret ->
            assertFalse(secret in line, "$secret leaked in: $line")
        }
        assertTrue("IllegalStateException" in line, line)
    }

    @Test
    fun storesEachRecordAsOneLineWithLevelAndTimestamp() {
        val buffer = DiagnosticsLogBuffer(nowMillis = { 1_000L })

        buffer.record(DiagnosticsLogLevel.ERROR, "first\nsecond")

        assertEquals("1970-01-01T00:00:01Z E first second", buffer.snapshot().single())
    }

    @Test
    fun clearEmptiesTheBuffer() {
        val buffer = DiagnosticsLogBuffer()
        buffer.record(DiagnosticsLogLevel.DEBUG, "hello")

        buffer.clear()

        assertTrue(buffer.snapshot().isEmpty())
    }
}
