package dev.jellystack.core.logging

import android.util.Log

actual object JellystackLog {
    private const val TAG = "Jellystack"
    private var enabled = false

    actual fun configure(enabled: Boolean) {
        this.enabled = enabled
    }

    actual fun d(message: String) {
        if (!enabled) return
        JellystackLogMonitor.notifyDebug(message)
        runCatching { Log.d(TAG, message) }
            .onFailure { println("$TAG D: $message") }
    }

    actual fun e(
        message: String,
        throwable: Throwable?,
    ) {
        if (!enabled) return
        JellystackLogMonitor.notifyError(message, throwable)
        runCatching {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }.onFailure {
            if (throwable != null) {
                println("$TAG E: $message\n${throwable.stackTraceToString()}")
            } else {
                println("$TAG E: $message")
            }
        }
    }

    actual fun w(
        message: String,
        throwable: Throwable?,
    ) {
        if (!enabled) return
        runCatching {
            if (throwable != null) {
                Log.w(TAG, message, throwable)
            } else {
                Log.w(TAG, message)
            }
        }.onFailure {
            if (throwable != null) {
                println("$TAG W: $message\n${throwable.stackTraceToString()}")
            } else {
                println("$TAG W: $message")
            }
        }
    }
}
