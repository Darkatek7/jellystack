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

private val FREE_TEXT_URL_PATTERN = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
private val FREE_TEXT_LINE_SECRET_PATTERN = Regex("""(?i)\b(authorization|set-cookie|cookie)\b(\s*[:=]\s*)[^\r\n]*""")
private val FREE_TEXT_BEARER_PATTERN = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
private val FREE_TEXT_KEYED_SECRET_PATTERN =
    Regex(
        """(?i)\b(x-emby-token|x-mediabrowser-token|api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|passwd|secret|pw)\b""" +
            """(["']?\s*[:=]\s*["']?)[^\s,;&"'}]+""",
    )
private val FREE_TEXT_EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
private val FREE_TEXT_LONG_HEX_PATTERN = Regex("""\b[A-Fa-f0-9]{32,}\b""")

/**
 * Redacts free-form log or stack trace text before it is stored for diagnostics: URLs shrink to scheme, host and
 * port, credentials in headers or key/value pairs and bearer tokens are replaced, email addresses and long hex
 * identifiers are masked. The result may still be incomplete for unknown secret formats, so callers must never
 * pass tokens or passwords in the first place; this is a safety net, not a licence to log them.
 */
fun redactSensitiveText(text: String): String {
    if (text.isEmpty()) return text
    var result = FREE_TEXT_URL_PATTERN.replace(text) { match -> sanitizeUrl(match.value) }
    result = FREE_TEXT_LINE_SECRET_PATTERN.replace(result) { match -> "${match.groupValues[1]}${match.groupValues[2]}<redacted>" }
    result = FREE_TEXT_BEARER_PATTERN.replace(result, "Bearer <redacted>")
    result = FREE_TEXT_KEYED_SECRET_PATTERN.replace(result) { match -> "${match.groupValues[1]}${match.groupValues[2]}<redacted>" }
    result = FREE_TEXT_EMAIL_PATTERN.replace(result) { match -> maskEmail(match.value) }
    result = FREE_TEXT_LONG_HEX_PATTERN.replace(result) { match -> maskGeneric(match.value) }
    return result
}
