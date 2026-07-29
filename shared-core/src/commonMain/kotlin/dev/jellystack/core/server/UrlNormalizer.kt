package dev.jellystack.core.server

import io.ktor.http.Url

fun normalizeBaseUrl(raw: String): String {
    val parsed =
        try {
            Url(raw)
        } catch (_: Throwable) {
            throw InvalidServerConfiguration("Server URL is not valid: $raw")
        }

    val scheme = parsed.protocol.name.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw InvalidServerConfiguration("Server URL must start with http or https")
    }
    if (parsed.host.isBlank()) {
        throw InvalidServerConfiguration("Server URL is missing host")
    }

    val normalizedPath = parsed.encodedPath.trimEnd('/')
    val portPart =
        when {
            parsed.port == parsed.protocol.defaultPort || parsed.port == 0 -> ""
            else -> ":${parsed.port}"
        }
    return buildString {
        append(scheme)
        append("://")
        append(parsed.host.lowercase())
        append(portPart)
        if (normalizedPath.isNotEmpty()) {
            append(normalizedPath.ensureLeadingSlash())
        }
    }
}

private fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"
