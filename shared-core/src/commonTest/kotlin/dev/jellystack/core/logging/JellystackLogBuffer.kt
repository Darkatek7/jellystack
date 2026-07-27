package dev.jellystack.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object JellystackLogBuffer {
    private val backing = MutableStateFlow<List<String>>(emptyList())

    private val debugObserver: (String) -> Unit = { message ->
        backing.update { it + "D:$message" }
    }
    private val errorObserver: (String, Throwable?) -> Unit = { message, throwable ->
        val suffix = throwable?.message?.let { ":$it" }.orEmpty()
        backing.update { it + "E:$message$suffix" }
    }

    init {
        JellystackLog.configure(enabled = true)
        JellystackLogMonitor.addDebugObserver(debugObserver)
        JellystackLogMonitor.addErrorObserver(errorObserver)
    }

    val entries: List<String>
        get() = backing.value

    fun clear() {
        backing.value = emptyList()
    }
}
