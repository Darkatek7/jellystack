package dev.jellystack.core.security

import kotlin.jvm.JvmInline

@JvmInline
value class SecretValue internal constructor(
    private val raw: String,
) {
    fun reveal(): String = raw

    override fun toString(): String = REDACTED

    companion object {
        private const val REDACTED = "██"
    }
}

fun secretValue(raw: String): SecretValue = SecretValue(raw)
