package dev.jellystack.core.server

import io.ktor.http.URLBuilder
import io.ktor.http.Url

sealed interface ServerAddressValidation {
    data class Valid(
        val normalizedUrl: String,
    ) : ServerAddressValidation

    data object Required : ServerAddressValidation

    data object MissingProtocol : ServerAddressValidation

    data object Invalid : ServerAddressValidation
}

fun validateServerAddress(raw: String): ServerAddressValidation {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return ServerAddressValidation.Required
    }
    if (!trimmed.hasHttpScheme()) {
        return ServerAddressValidation.MissingProtocol
    }
    val authority =
        trimmed
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if (authority.isBlank() || authority.any(Char::isWhitespace)) {
        return ServerAddressValidation.Invalid
    }
    val parsed =
        try {
            Url(trimmed)
        } catch (_: Throwable) {
            return ServerAddressValidation.Invalid
        }

    val scheme = parsed.protocol.name.lowercase()
    if (scheme != "http" && scheme != "https") {
        return ServerAddressValidation.MissingProtocol
    }
    if (parsed.host.isBlank()) {
        return ServerAddressValidation.Invalid
    }

    val normalizedUrl =
        URLBuilder(parsed)
            .apply {
                protocol = parsed.protocol
                host = parsed.host.lowercase()
                parameters.clear()
                fragment = ""
                user = null
                password = null
            }.buildString()
            .trimEnd('/')
    return ServerAddressValidation.Valid(normalizedUrl)
}

fun normalizeBaseUrl(raw: String): String =
    when (val validation = validateServerAddress(raw)) {
        is ServerAddressValidation.Valid -> validation.normalizedUrl
        ServerAddressValidation.Required ->
            throw InvalidServerConfiguration("Server URL is required")
        ServerAddressValidation.MissingProtocol ->
            throw InvalidServerConfiguration("Server URL must start with http:// or https://")
        ServerAddressValidation.Invalid ->
            throw InvalidServerConfiguration("Server URL is not valid")
    }

private fun String.hasHttpScheme(): Boolean =
    startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
