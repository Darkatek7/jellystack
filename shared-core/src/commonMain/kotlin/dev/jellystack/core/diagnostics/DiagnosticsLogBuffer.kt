package dev.jellystack.core.diagnostics

import dev.jellystack.core.logging.redactSensitiveText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Severity letter stored in front of every diagnostics log line. */
enum class DiagnosticsLogLevel(
    val letter: Char,
) {
    DEBUG('D'),
    WARN('W'),
    ERROR('E'),
}

/**
 * In-memory ring buffer of the most recent log lines, kept for the on-device diagnostics screen and the crash
 * report. Text is redacted before it is stored, so the buffer never holds tokens or full server URLs. Nothing in
 * here leaves the device.
 */
class DiagnosticsLogBuffer(
    val capacity: Int = DEFAULT_CAPACITY,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val backing = MutableStateFlow<List<String>>(emptyList())

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun record(
        level: DiagnosticsLogLevel,
        message: String,
        throwable: Throwable? = null,
    ) {
        // Redact the message and the exception separately so a header-style secret cannot swallow the other part.
        val text =
            buildString {
                append(redactSensitiveText(message))
                if (throwable != null) {
                    append(" | ")
                    append(throwable::class.simpleName ?: "Throwable")
                    throwable.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(redactSensitiveText(it)) }
                }
            }
        val body = text.replace(LINE_BREAKS, " ").take(MAX_LINE_LENGTH)
        val line = "${Instant.fromEpochMilliseconds(nowMillis())} ${level.letter} $body"
        backing.update { current -> (current + line).takeLast(capacity) }
    }

    /** The buffered lines, oldest first. */
    fun snapshot(): List<String> = backing.value

    fun clear() {
        backing.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 300
        const val MAX_LINE_LENGTH = 600
        private val LINE_BREAKS = Regex("[\r\n]+")
    }
}
