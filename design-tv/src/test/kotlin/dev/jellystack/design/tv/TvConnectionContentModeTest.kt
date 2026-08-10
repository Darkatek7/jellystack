package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvConnectionContentModeTest {
    @Test
    fun waitingForAuthorizationUsesCompactCodeContent() {
        val mode = connectionContentMode(quickConnectInProgress = true)

        assertFalse(mode.showEditableFields)
        assertFalse(mode.showConnectAction)
        assertTrue(mode.showWaitingInstructions)
    }

    @Test
    fun initialStateShowsEditableConnectionForm() {
        val mode = connectionContentMode(quickConnectInProgress = false)

        assertTrue(mode.showEditableFields)
        assertTrue(mode.showConnectAction)
        assertFalse(mode.showWaitingInstructions)
    }
}
