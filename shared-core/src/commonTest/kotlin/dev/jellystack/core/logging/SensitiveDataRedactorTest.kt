package dev.jellystack.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals

class SensitiveDataRedactorTest {
    @Test
    fun `sanitizeUrl strips path and query`() {
        val sanitized = sanitizeUrl("https://example.org:8096/jellyfin/api?token=secret")
        assertEquals("https://example.org:8096/...", sanitized)
    }

    @Test
    fun `sanitizeIdentifier masks email`() {
        val sanitized = sanitizeIdentifier("user.name@example.org")
        assertEquals("u***e@example.org", sanitized)
    }

    @Test
    fun `sanitizeIdentifier masks username`() {
        val sanitized = sanitizeIdentifier("administrator")
        assertEquals("a***r", sanitized)
    }

    @Test
    fun `sanitizeForLog handles null`() {
        val sanitized = sanitizeForLog(null)
        assertEquals("<null>", sanitized)
    }
}
