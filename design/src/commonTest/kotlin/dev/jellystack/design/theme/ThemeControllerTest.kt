package dev.jellystack.design.theme

import dev.jellystack.core.preferences.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeControllerTest {
    @Test
    fun systemModeTracksPlatformDarkChanges() {
        val controller = ThemeController(ThemeMode.SYSTEM, initialSystemDark = false)

        assertFalse(controller.isDark.value)
        controller.updateSystemDark(true)

        assertTrue(controller.isDark.value)
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
    }

    @Test
    fun explicitModeIgnoresPlatformChanges() {
        val controller = ThemeController(ThemeMode.DARK, initialSystemDark = false)

        controller.updateSystemDark(false)
        assertTrue(controller.isDark.value)

        controller.setMode(ThemeMode.LIGHT)
        controller.updateSystemDark(true)
        assertFalse(controller.isDark.value)
    }
}
