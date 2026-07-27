package dev.jellystack.core.logging

private val URL_PATTERN = Regex("^(?<scheme>[a-zA-Z][a-zA-Z0-9+.-]*://)?(?<host>[A-Za-z0-9._-]+)(?::(?<port>\\d+))?")
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+$")

fun sanitizeForLog(value: String?): String = sanitizeIdentifier(value)

fun sanitizeIdentifier(value: String?): String {
    if (value == null) return "<null>"
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "<empty>"
    return when {
        EMAIL_PATTERN.matches(trimmed) -> maskEmail(trimmed)
        trimmed.contains("://") -> sanitizeUrl(trimmed)
        else -> maskGeneric(trimmed)
    }
}

fun sanitizeUrl(value: String?): String {
    if (value == null) return "<null>"
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "<empty>"
    val match = URL_PATTERN.find(trimmed)
    val scheme = match?.groups?.get("scheme")?.value ?: ""
    val host = match?.groups?.get("host")?.value
    val port =
        match
            ?.groups
            ?.get("port")
            ?.value
            ?.let { ":$it" } ?: ""
    val base =
        if (host != null) {
            buildString {
                append(scheme)
                append(host)
                append(port)
            }
        } else {
            return maskGeneric(trimmed)
        }
    return "$base/..."
}

private fun maskEmail(email: String): String {
    val parts = email.split("@")
    if (parts.size != 2) return maskGeneric(email)
    val local = parts[0]
    val domain = parts[1]
    if (local.isEmpty()) return "***@$domain"
    val visibleStart = local.first()
    val visibleEnd = local.last()
    val obscured =
        when {
            local.length <= 2 -> "$visibleStart***"
            else -> "$visibleStart***$visibleEnd"
        }
    return "$obscured@$domain"
}

private fun maskGeneric(value: String): String {
    if (value.length <= 1) return "***"
    val start = value.first()
    val end = value.last()
    return "$start***$end"
}
