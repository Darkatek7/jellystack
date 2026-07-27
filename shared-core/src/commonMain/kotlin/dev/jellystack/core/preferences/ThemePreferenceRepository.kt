package dev.jellystack.core.preferences

import com.russhwolf.settings.Settings

private const val KEY_IS_DARK_THEME = "appearance.dark_theme_enabled"
private const val KEY_THEME_MODE = "appearance.theme_mode"

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class ThemePreferenceRepository(
    private val settings: Settings,
) {
    fun currentMode(): ThemeMode {
        val stored =
            settings
                .getStringOrNull(KEY_THEME_MODE)
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
        if (stored != null) return stored

        val migrated =
            if (settings.hasKey(KEY_IS_DARK_THEME)) {
                if (settings.getBoolean(KEY_IS_DARK_THEME, defaultValue = false)) ThemeMode.DARK else ThemeMode.LIGHT
            } else {
                ThemeMode.SYSTEM
            }
        settings.putString(KEY_THEME_MODE, migrated.name)
        return migrated
    }

    fun setMode(mode: ThemeMode) {
        settings.putString(KEY_THEME_MODE, mode.name)
    }

    fun currentTheme(): Boolean? =
        when (currentMode()) {
            ThemeMode.SYSTEM -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    fun setDarkTheme(isDark: Boolean) {
        settings.putBoolean(KEY_IS_DARK_THEME, isDark)
        setMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
}
