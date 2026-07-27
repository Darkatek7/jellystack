package dev.jellystack.core.logging

interface TelemetryTracker {
    fun track(
        event: String,
        properties: Map<String, Any?> = emptyMap(),
    )
}

class NoOpTelemetryTracker : TelemetryTracker {
    override fun track(
        event: String,
        properties: Map<String, Any?>,
    ) = Unit
}
