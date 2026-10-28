package dev.jellystack.core.diagnostics

import android.content.Context
import dev.jellystack.core.logging.JellystackLog
import kotlinx.datetime.Clock
import java.io.File
import java.io.IOException

/**
 * Records the uncaught exception and the tail of the log buffer, then hands over to [previous] so the process
 * still dies the way the platform intends. It never swallows the exception.
 */
class CrashReportingExceptionHandler(
    private val store: CrashReportStore,
    private val logBuffer: DiagnosticsLogBuffer,
    private val appVersion: String,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            logBuffer.record(DiagnosticsLogLevel.ERROR, "Uncaught exception on ${thread.name}", throwable)
            store.write(
                buildCrashReport(
                    threadName = thread.name,
                    throwable = throwable,
                    appVersion = appVersion,
                    timestampMillis = nowMillis(),
                    recentLog = logBuffer.snapshot(),
                ),
            )
        } catch (_: IOException) {
            // The process is already going down; losing the report must not hide the original crash.
        } catch (_: RuntimeException) {
            // Same: recording is best effort.
        }
        previous?.uncaughtException(thread, throwable)
    }
}

/** Android entry point: call once from `Application.onCreate`. */
object AndroidDiagnostics {
    private const val DIRECTORY = "diagnostics"
    private const val CRASH_FILE = "last-crash.json"

    @Synchronized
    fun install(
        context: Context,
        appVersion: String,
    ) {
        if (JellystackDiagnostics.crashStore != null) return
        // noBackupFilesDir keeps the report out of cloud backups and device transfers.
        val store = FileCrashReportStore(File(File(context.noBackupFilesDir, DIRECTORY), CRASH_FILE))
        JellystackDiagnostics.crashStore = store
        Thread.setDefaultUncaughtExceptionHandler(
            CrashReportingExceptionHandler(
                store = store,
                logBuffer = JellystackDiagnostics.logBuffer,
                appVersion = appVersion,
                previous = Thread.getDefaultUncaughtExceptionHandler(),
            ),
        )
        JellystackLog.d("Diagnostics crash recorder installed")
    }
}
