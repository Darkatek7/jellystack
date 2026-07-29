package dev.jellystack.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import dev.jellystack.core.preferences.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeController(
    initialMode: ThemeMode,
    initialSystemDark: Boolean,
    private val onModeChanged: ((ThemeMode) -> Unit)? = null,
) {
    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()
    private var systemDark = initialSystemDark
    private val _isDark = MutableStateFlow(resolve(initialMode, initialSystemDark))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    constructor(
        initialIsDark: Boolean,
        onThemeChanged: ((Boolean) -> Unit)? = null,
    ) : this(
        initialMode = if (initialIsDark) ThemeMode.DARK else ThemeMode.LIGHT,
        initialSystemDark = initialIsDark,
        onModeChanged = { mode -> onThemeChanged?.invoke(mode == ThemeMode.DARK) },
    )

    fun toggle() {
        setMode(if (_isDark.value) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun set(isDark: Boolean) {
        setMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun setMode(mode: ThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        refreshEffectiveTheme()
        onModeChanged?.invoke(mode)
    }

    fun updateSystemDark(isDark: Boolean) {
        if (systemDark == isDark) return
        systemDark = isDark
        if (_mode.value == ThemeMode.SYSTEM) refreshEffectiveTheme()
    }

    private fun refreshEffectiveTheme() {
        _isDark.value = resolve(_mode.value, systemDark)
    }

    private companion object {
        fun resolve(
            mode: ThemeMode,
            systemDark: Boolean,
        ): Boolean =
            when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
    }
}

val LocalThemeController =
    staticCompositionLocalOf<ThemeController> {
        error("ThemeController not provided")
    }
