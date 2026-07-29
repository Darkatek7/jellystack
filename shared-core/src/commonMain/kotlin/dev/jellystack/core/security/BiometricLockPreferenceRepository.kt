package dev.jellystack.core.security

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_BIOMETRIC_ENABLED = "security.biometric.enabled"

class BiometricLockPreferenceRepository(
    private val settings: Settings,
) {
    private val isEnabledInitial =
        if (settings.hasKey(KEY_BIOMETRIC_ENABLED)) {
            settings.getBoolean(KEY_BIOMETRIC_ENABLED, defaultValue = false)
        } else {
            false
        }
    private val _enabled = MutableStateFlow(isEnabledInitial)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        if (_enabled.value == enabled) return
        settings.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
        _enabled.value = enabled
    }
}
