package dev.jellystack.core.preferences

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceRepositoryTest {
    @Test
    fun emptyPreferencesDefaultToSystem() {
        val repository = ThemePreferenceRepository(InMemorySettings())

        assertEquals(ThemeMode.SYSTEM, repository.currentMode())
    }

    @Test
    fun legacyDarkBooleanMigratesToDarkMode() {
        val settings = InMemorySettings(mapOf("appearance.dark_theme_enabled" to true))
        val repository = ThemePreferenceRepository(settings)

        assertEquals(ThemeMode.DARK, repository.currentMode())
        assertEquals("DARK", settings.getString("appearance.theme_mode", ""))
    }

    @Test
    fun legacyLightBooleanMigratesToLightMode() {
        val settings = InMemorySettings(mapOf("appearance.dark_theme_enabled" to false))
        val repository = ThemePreferenceRepository(settings)

        assertEquals(ThemeMode.LIGHT, repository.currentMode())
    }

    @Test
    fun explicitModeTakesPrecedenceAndPersists() {
        val settings = InMemorySettings(mapOf("appearance.dark_theme_enabled" to true))
        val repository = ThemePreferenceRepository(settings)

        repository.setMode(ThemeMode.SYSTEM)

        assertEquals(ThemeMode.SYSTEM, repository.currentMode())
        assertEquals("SYSTEM", settings.getString("appearance.theme_mode", ""))
    }
}
