package dev.jellystack.core.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlNormalizerTest {
    @Test
    fun preservesIpv4PortAndReverseProxyPath() {
        assertEquals(
            "http://192.168.1.20:8096/jellyfin",
            normalizeBaseUrl("http://192.168.1.20:8096/jellyfin/"),
        )
    }

    @Test
    fun preservesBracketedIpv6Port() {
        assertEquals(
            "http://[fd00::1]:8096",
            normalizeBaseUrl("http://[fd00::1]:8096/"),
        )
    }

    @Test
    fun preservesHttpsCustomPortAndNestedReverseProxyPath() {
        assertEquals(
            "https://media.example.test:8443/services/jellyfin",
            normalizeBaseUrl("https://MEDIA.EXAMPLE.TEST:8443/services/jellyfin/?token=dummy#section"),
        )
    }

    @Test
    fun acceptsStandardHostnameAndRemovesOnlyTrailingSlash() {
        assertEquals(
            "https://media.example.test",
            normalizeBaseUrl("https://media.example.test/"),
        )
    }

    @Test
    fun missingProtocolHasActionableErrorWithoutEchoingAddress() {
        val rawAddress = "192.168.1.20:8096"

        val error =
            assertFailsWith<InvalidServerConfiguration> {
                normalizeBaseUrl(rawAddress)
            }

        assertTrue(error.message.orEmpty().contains("http://"))
        assertTrue(error.message.orEmpty().contains("https://"))
        assertFalse(error.message.orEmpty().contains(rawAddress))
    }

    @Test
    fun validationDistinguishesMissingProtocolFromMalformedAddress() {
        assertEquals(
            ServerAddressValidation.MissingProtocol,
            validateServerAddress("media.example.test:8096"),
        )
        assertEquals(
            ServerAddressValidation.Invalid,
            validateServerAddress("https://"),
        )
    }
}
