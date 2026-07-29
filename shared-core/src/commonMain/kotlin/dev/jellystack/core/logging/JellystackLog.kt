package dev.jellystack.core.logging

expect object JellystackLog {
    fun configure(enabled: Boolean)

    fun d(message: String)

    fun e(
        message: String,
        throwable: Throwable? = null,
    )

    fun w(
        message: String,
        throwable: Throwable? = null,
    )
}
