package dev.jellystack.core.diagnostics

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashRecordingTest {
    private lateinit var directory: File

    @BeforeTest
    fun setUp() {
        directory = File.createTempFile("crash-recording", "").also { it.delete() }
        directory.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun crashReportRoundTripsThroughTheFileAndClears() {
        val store = FileCrashReportStore(File(directory, "nested/last-crash.json"))
        assertNull(store.read())
        val report =
            CrashReport(
                timestampMillis = 42L,
                threadName = "main",
                appVersion = "1.2.3",
                stackTrace = "java.lang.IllegalStateException: boom",
                recentLog = listOf("a", "b"),
            )

        store.write(report)

        assertEquals(report, store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun corruptCrashFileReadsAsNoCrash() {
        val file = File(directory, "last-crash.json").apply { writeText("{not json") }

        assertNull(FileCrashReportStore(file).read())
    }

    @Test
    fun handlerWritesSanitizedReportWithLogTailAndDelegatesToThePreviousHandler() {
        val store = FileCrashReportStore(File(directory, "last-crash.json"))
        val logBuffer = DiagnosticsLogBuffer(nowMillis = { 0L })
        logBuffer.record(DiagnosticsLogLevel.DEBUG, "before the crash")
        var delegatedThread: Thread? = null
        var delegatedError: Throwable? = null
        val handler =
            CrashReportingExceptionHandler(
                store = store,
                logBuffer = logBuffer,
                appVersion = "9.9",
                previous = { thread, error ->
                    delegatedThread = thread
                    delegatedError = error
                },
                nowMillis = { 7L },
            )
        val thread = Thread.currentThread()
        val error = IllegalStateException("failed for https://jelly.example.com:8096/Users/abc?api_key=tokenvalue")

        handler.uncaughtException(thread, error)

        assertSame(thread, delegatedThread)
        assertSame(error, delegatedError)
        val stored = store.read()!!
        assertEquals(7L, stored.timestampMillis)
        assertEquals("9.9", stored.appVersion)
        assertEquals(thread.name, stored.threadName)
        assertTrue("IllegalStateException" in stored.stackTrace)
        assertFalse("tokenvalue" in stored.stackTrace)
        assertTrue(stored.recentLog.any { "before the crash" in it })
    }

    @Test
    fun handlerStillDelegatesWhenTheReportCannotBeWritten() {
        val failingStore =
            object : CrashReportStore {
                override fun write(report: CrashReport): Unit = throw java.io.IOException("disk full")

                override fun read(): CrashReport? = null

                override fun clear() = Unit
            }
        var delegated = false
        val handler =
            CrashReportingExceptionHandler(
                store = failingStore,
                logBuffer = DiagnosticsLogBuffer(),
                appVersion = "1",
                previous = { _, _ -> delegated = true },
            )

        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertTrue(delegated)
    }
}
