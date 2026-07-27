package dev.jellystack.core

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun platformNameIsNotBlank() {
        val platform = currentPlatform()
        assertTrue(platform.name.isNotBlank())
    }
}
