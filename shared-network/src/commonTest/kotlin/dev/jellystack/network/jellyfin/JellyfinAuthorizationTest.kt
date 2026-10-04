package dev.jellystack.network.jellyfin

import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class JellyfinAuthorizationTest {
    @Test
    fun percentEncodedIdentitySurvivesCommaSeparatedServerParsing() {
        val identity =
            JellyfinClientIdentity(
                appName = "Jellystack, Mobile",
                appVersion = "0.16.0+test",
                deviceName = "Wohnzimmer \"TV\" \\ Gerät",
                deviceId = "device,\"雪\"\\%20",
            )

        val header = jellyfinAuthorizationValue(identity, token = "token,+%\"\\")

        assertEquals(
            "MediaBrowser Client=\"Jellystack%2C%20Mobile\", " +
                "Device=\"Wohnzimmer%20%22TV%22%20%5C%20Ger%C3%A4t\", " +
                "DeviceId=\"device%2C%22%E9%9B%AA%22%5C%2520\", " +
                "Version=\"0.16.0%2Btest\", Token=\"token%2C%2B%25%22%5C\"",
            header,
        )
        assertEquals(
            mapOf(
                "Client" to identity.appName,
                "Device" to identity.deviceName,
                "DeviceId" to identity.deviceId,
                "Version" to identity.appVersion,
                "Token" to "token,+%\"\\",
            ),
            parseServerAuthorization(header),
        )
    }

    @Test
    fun identityValuesCannotIntroduceHeaderLines() {
        val identity =
            JellyfinClientIdentity(
                appVersion = "0.16.0",
                deviceName = "Name\r\nInjected: value",
                deviceId = "device-id",
            )

        val header = jellyfinAuthorizationValue(identity)

        assertFalse(header.contains('\r'))
        assertFalse(header.contains('\n'))
        assertEquals(identity.deviceName, parseServerAuthorization(header)["Device"])
    }

    @Test
    fun missingIdentityUsesTokenOnlyAuthorization() {
        val headers = HeadersBuilder()

        headers.appendJellyfinAuthorization(identity = null, token = "access,+%token")

        assertEquals("MediaBrowser Token=\"access%2C%2B%25token\"", headers[HttpHeaders.Authorization])
        assertEquals(mapOf("Token" to "access,+%token"), parseServerAuthorization(requireNotNull(headers[HttpHeaders.Authorization])))
    }

    @Test
    fun existingAuthorizationIsPreservedAndNeverDuplicated() {
        val headers = HeadersBuilder()
        headers.append("authorization", "MediaBrowser Token=\"existing-token\"")

        headers.appendJellyfinAuthorization(identity = identity(), token = "replacement-token")
        headers.appendJellyfinAuthorization(identity = null, token = "replacement-token")

        assertEquals(listOf("MediaBrowser Token=\"existing-token\""), headers.getAll(HttpHeaders.Authorization))
    }

    @Test
    fun requestsWithoutTokenKeepIdentityForLogin() {
        val headers = HeadersBuilder()

        headers.appendJellyfinAuthorization(identity = identity(), token = null)

        assertEquals(
            "MediaBrowser Client=\"Jellystack\", Device=\"Test%20device\", DeviceId=\"device-id\", Version=\"0.16.0\"",
            headers[HttpHeaders.Authorization],
        )
        assertFalse(parseServerAuthorization(requireNotNull(headers[HttpHeaders.Authorization])).containsKey("Token"))
    }

    @Test
    fun missingIdentityAndTokenDoNotAddAuthorization() {
        val headers = HeadersBuilder()

        headers.appendJellyfinAuthorization(identity = null, token = null)
        headers.appendJellyfinAuthorization(identity = null, token = " ")

        assertNull(headers[HttpHeaders.Authorization])
    }

    private fun identity() =
        JellyfinClientIdentity(
            appVersion = "0.16.0",
            deviceName = "Test device",
            deviceId = "device-id",
        )

    /** Mirrors the server's decode order so commas in values cannot become scheme separators. */
    private fun parseServerAuthorization(header: String): Map<String, String> =
        header.removePrefix("MediaBrowser ").split(',').associate { parameter ->
            val parts = parameter.trim().split('=', limit = 2)
            parts[0] to parts[1].removeSurrounding("\"").decodeURLPart()
        }
}
