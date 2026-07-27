package dev.jellystack.core.logging

object JellystackLogBuffer {
    private val lock = Any()
    private val backing = mutableListOf<String>()

    private val debugObserver: (String) -> Unit = { message ->
        synchronized(lock) { backing += "D:$message" }
    }
    private val errorObserver: (String, Throwable?) -> Unit = { message, throwable ->
        val suffix = throwable?.message?.let { ":$it" }.orEmpty()
        synchronized(lock) { backing += "E:$message$suffix" }
    }

    init {
        JellystackLog.configure(enabled = true)
        JellystackLogMonitor.addDebugObserver(debugObserver)
        JellystackLogMonitor.addErrorObserver(errorObserver)
    }

    val entries: List<String>
        get() = synchronized(lock) { backing.toList() }

    fun clear() {
        synchronized(lock) { backing.clear() }
    }
}
