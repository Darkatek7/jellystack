package dev.jellystack.ios

import androidx.compose.runtime.Composable
import kotlin.test.Test
import kotlin.test.assertNotNull

class ComposeEntryBridgeTest {
    @Test
    fun keepsZeroArgumentAndVersionedComposeBridges() {
        val zeroArgument: @Composable () -> Unit = ::ComposeEntry
        val versioned: @Composable (String) -> Unit = ::ComposeEntry

        assertNotNull(zeroArgument)
        assertNotNull(versioned)
    }
}
