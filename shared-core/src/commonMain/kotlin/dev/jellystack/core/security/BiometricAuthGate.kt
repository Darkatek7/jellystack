package dev.jellystack.core.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BiometricAuthGate(
    private val preferences: BiometricLockPreferenceRepository,
    dispatcher: CoroutineDispatcher,
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _lockState =
        MutableStateFlow(
            if (preferences.isEnabled()) {
                BiometricLockState.Locked
            } else {
                BiometricLockState.Disabled
            },
        )
    private val _capability =
        MutableStateFlow(
            BiometricCapability(
                status = BiometricCapability.Status.UNSUPPORTED,
                title = null,
                description = null,
            ),
        )
    private val _autoPrompt = MutableStateFlow(false)
    private var unlockJob: Job? = null
    private var activeUnlocker: BiometricUnlocker? = null

    val lockState: StateFlow<BiometricLockState> = _lockState.asStateFlow()

    val capability: StateFlow<BiometricCapability> = _capability.asStateFlow()

    val isEnabled: StateFlow<Boolean> = preferences.enabled

    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    fun updateCapability(capability: BiometricCapability) {
        val previous = _capability.value
        _capability.value = capability
        if (!preferences.isEnabled()) return
        if (capability.isAuthenticationReady) {
            if (_lockState.value is BiometricLockState.Error) {
                _lockState.value = BiometricLockState.Locked
            }
            if (!previous.isAuthenticationReady && _lockState.value == BiometricLockState.Locked) {
                _autoPrompt.value = true
            }
        } else {
            val reason = capability.description.orEmpty()
            _lockState.value = BiometricLockState.Error(reason)
            cancelPendingUnlock()
            _autoPrompt.value = false
        }
    }

    fun setEnabled(enabled: Boolean): Boolean = if (enabled) enable() else disable()

    fun enable(): Boolean {
        if (preferences.isEnabled()) return true
        val capability = _capability.value
        if (!capability.isAuthenticationReady) {
            val reason = capability.description.orEmpty()
            _lockState.value = BiometricLockState.Error(reason)
            _autoPrompt.value = false
            return false
        }
        preferences.setEnabled(true)
        _lockState.value = BiometricLockState.Locked
        _autoPrompt.value = true
        return true
    }

    fun disable(): Boolean {
        if (!preferences.isEnabled()) return true
        val state = _lockState.value
        val canDisable = state == BiometricLockState.Unlocked || state == BiometricLockState.Disabled
        if (!canDisable) return false
        cancelPendingUnlock()
        preferences.setEnabled(false)
        _lockState.value = BiometricLockState.Disabled
        _autoPrompt.value = false
        return true
    }

    fun unlock(unlocker: BiometricUnlocker?): Boolean {
        if (!preferences.isEnabled()) return false
        val capability = _capability.value
        if (!capability.isAuthenticationReady) {
            val reason = capability.description.orEmpty()
            _lockState.value = BiometricLockState.Error(reason)
            return false
        }
        if (unlocker == null) {
            _lockState.value = BiometricLockState.Error("")
            return false
        }
        if (_lockState.value == BiometricLockState.Unlocking) return true
        cancelPendingUnlock()
        _lockState.value = BiometricLockState.Unlocking
        _autoPrompt.value = false
        activeUnlocker = unlocker
        unlockJob =
            scope.launch {
                val result =
                    runCatching { unlocker.authenticate() }
                        .getOrElse { error ->
                            BiometricAuthResult.Failure(error.message, error)
                        }
                activeUnlocker = null
                when (result) {
                    BiometricAuthResult.Success -> _lockState.value = BiometricLockState.Unlocked
                    BiometricAuthResult.Cancelled -> _lockState.value = BiometricLockState.Locked
                    is BiometricAuthResult.Failure -> {
                        val reason = result.message.orEmpty()
                        _lockState.value = BiometricLockState.Error(reason)
                    }
                }
            }
        return true
    }

    fun clearError() {
        if (_lockState.value is BiometricLockState.Error) {
            _lockState.value =
                if (preferences.isEnabled()) {
                    BiometricLockState.Locked
                } else {
                    BiometricLockState.Disabled
                }
        }
    }

    fun onAppBackgrounded() {
        if (!preferences.isEnabled()) return
        if (_lockState.value == BiometricLockState.Unlocking) {
            _autoPrompt.value = false
            return
        }
        _lockState.value = BiometricLockState.Locked
        _autoPrompt.value = false
    }

    fun onAppForegrounded() {
        if (!preferences.isEnabled()) return
        if (_capability.value.isAuthenticationReady) {
            if (_lockState.value == BiometricLockState.Disabled) {
                _lockState.value = BiometricLockState.Locked
            }
            _autoPrompt.value = true
        } else {
            _autoPrompt.value = false
        }
    }

    fun dispose() {
        cancelPendingUnlock()
        scope.cancel()
    }

    private fun cancelPendingUnlock() {
        unlockJob?.cancel()
        unlockJob = null
        activeUnlocker?.cancel()
        activeUnlocker = null
    }
}
