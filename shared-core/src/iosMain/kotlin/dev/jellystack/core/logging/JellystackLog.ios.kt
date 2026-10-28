package dev.jellystack.core.logging

import dev.jellystack.core.diagnostics.DiagnosticsLogLevel
import dev.jellystack.core.diagnostics.JellystackDiagnostics
import io.github.aakira.napier.Napier

actual object JellystackLog {
    private const val TAG = "Jellystack"
    private var enabled = false

    actual fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    actual fun d(message: String) {
        JellystackDiagnostics.logBuffer.record(DiagnosticsLogLevel.DEBUG, message)
        if (!enabled) return
        JellystackLogMonitor.notifyDebug(message)
        Napier.d(message, tag = TAG)
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        JellystackDiagnostics.logBuffer.record(DiagnosticsLogLevel.ERROR, message, throwable)
        if (!enabled) return
        JellystackLogMonitor.notifyError(message, throwable)
        Napier.e(message, tag = TAG, throwable = throwable)
    }

    actual fun w(
        message: String,
        throwable: Throwable?,
    ) {
        JellystackDiagnostics.logBuffer.record(DiagnosticsLogLevel.WARN, message, throwable)
        if (!enabled) return
        Napier.w(message, tag = TAG, throwable = throwable)
    }
}
