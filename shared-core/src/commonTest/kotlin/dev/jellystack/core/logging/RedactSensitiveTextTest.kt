package dev.jellystack.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactSensitiveTextTest {
    @Test
    fun urlsKeepOnlySchemeHostAndPort() {
        assertEquals(
            "open https://media.example.com:8920/... now",
            redactSensitiveText("open https://media.example.com:8920/web/index.html?token=abc now"),
        )
    }

    @Test
    fun credentialsInHeadersPairsAndBearerTokensAreRemoved() {
        val redacted =
            redactSensitiveText(
                "Authorization: Bearer abc.def password=hunter2 api_key: k123 mail me@example.org id=0123456789abcdef0123456789abcdef",
            )

        listOf("abc.def", "hunter2", "k123", "me@example.org", "0123456789abcdef0123456789abcdef").forEach {
            assertFalse(it in redacted, "$it leaked in: $redacted")
        }
    }

    @Test
    fun plainTextIsLeftAlone() {
        assertEquals("Playback started for item 5", redactSensitiveText("Playback started for item 5"))
    }
}
