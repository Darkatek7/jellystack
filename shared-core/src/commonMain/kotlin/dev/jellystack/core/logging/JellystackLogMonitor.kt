package dev.jellystack.core.logging

object JellystackLogMonitor {
    private val debugObservers = mutableSetOf<(String) -> Unit>()
    private val errorObservers = mutableSetOf<(String, Throwable?) -> Unit>()

    fun addDebugObserver(observer: (String) -> Unit) {
        debugObservers += observer
    }

    fun removeDebugObserver(observer: (String) -> Unit) {
        debugObservers -= observer
    }

    fun addErrorObserver(observer: (String, Throwable?) -> Unit) {
        errorObservers += observer
    }

    fun removeErrorObserver(observer: (String, Throwable?) -> Unit) {
        errorObservers -= observer
    }

    internal fun notifyDebug(message: String) {
        val observers = debugObservers.toList()
        observers.forEach { observer -> observer(message) }
    }

    internal fun notifyError(
        message: String,
        throwable: Throwable?,
    ) {
        val observers = errorObservers.toList()
        observers.forEach { observer -> observer(message, throwable) }
    }
}
