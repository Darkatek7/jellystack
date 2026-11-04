package dev.jellystack.design.tv

import dev.jellystack.core.server.JellyfinSignInMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvConnectionContentTest {
    @Test
    fun quickConnectPageUrlPointsAtTheWebClientPage() {
        assertEquals(
            "https://jellyfin.example.com/web/#/quickconnect",
            quickConnectPageUrl("https://jellyfin.example.com/"),
        )
        assertEquals(
            "http://example.org:8096/jellyfin/web/#/quickconnect",
            quickConnectPageUrl("http://example.org:8096/jellyfin/web"),
        )
    }

    @Test
    fun quickConnectPageUrlIsNullForUnusableAddresses() {
        assertNull(quickConnectPageUrl(""))
        assertNull(quickConnectPageUrl("jellyfin.example.com"))
    }

    @Test
    fun displayNameFieldOnlyExistsAfterEditNameAndSitsBelowTheAddress() {
        assertEquals(
            listOf(TvConnectionField.URL),
            connectionFieldOrder(JellyfinSignInMethod.QUICK_CONNECT, editingName = false),
        )
        assertEquals(
            listOf(
                TvConnectionField.URL,
                TvConnectionField.DISPLAY_NAME,
                TvConnectionField.USERNAME,
                TvConnectionField.PASSWORD,
            ),
            connectionFieldOrder(JellyfinSignInMethod.PASSWORD, editingName = true),
        )
    }
}
