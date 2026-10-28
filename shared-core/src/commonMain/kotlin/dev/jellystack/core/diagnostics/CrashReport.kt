package dev.jellystack.core.diagnostics

import dev.jellystack.core.logging.redactSensitiveText
import kotlinx.serialization.Serializable

/** The last uncaught exception, stored locally so it can be read on the diagnostics screen after a restart. */
@Serializable
data class CrashReport(
    val timestampMillis: Long,
    val threadName: String,
    val appVersion: String,
    val stackTrace: String,
    val recentLog: List<String>,
)

/** Persists at most one [CrashReport]. Implementations must not throw from [write] callers' point of view beyond I/O errors. */
interface CrashReportStore {
    fun write(report: CrashReport)

    fun read(): CrashReport?

    fun clear()
}

private const val MAX_STACK_TRACE_LENGTH = 16_000

fun buildCrashReport(
    threadName: String,
    throwable: Throwable,
    appVersion: String,
    timestampMillis: Long,
    recentLog: List<String>,
): CrashReport =
    CrashReport(
        timestampMillis = timestampMillis,
        threadName = threadName,
        appVersion = appVersion,
        stackTrace = redactSensitiveText(throwable.stackTraceToString()).take(MAX_STACK_TRACE_LENGTH),
        recentLog = recentLog,
    )

/** Process-wide diagnostics state that the platform host wires up once at startup. */
object JellystackDiagnostics {
    val logBuffer = DiagnosticsLogBuffer()

    @kotlin.concurrent.Volatile
    var crashStore: CrashReportStore? = null

    fun lastCrash(): CrashReport? = crashStore?.read()

    fun clearLastCrash() {
        crashStore?.clear()
    }
}
