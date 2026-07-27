package dev.jellystack.design.jellyfin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayedStatusSupportTest {
    @Test
    fun mediaDetailsSupportPlayedStatus() {
        assertTrue(supportsPlayedStatus("Episode"))
        assertTrue(supportsPlayedStatus("Movie"))
        assertTrue(supportsPlayedStatus("Series"))
    }

    @Test
    fun nonVideoDetailsDoNotSupportPlayedStatus() {
        assertFalse(supportsPlayedStatus("Book"))
    }
}
