package dev.jellystack.core.security

/**
 * Summarizes whether biometric authentication can be used on the current device.
 */
data class BiometricCapability(
    val status: Status,
    val title: String? = null,
    val description: String? = null,
    val secureCredentialAvailable: Boolean = false,
) {
    val isAuthenticationReady: Boolean get() = secureCredentialAvailable

    enum class Status {
        /** No biometric hardware or API support exists on the device/platform. */
        UNSUPPORTED,

        /** Hardware exists but is temporarily unavailable (e.g., permissions missing). */
        UNAVAILABLE,

        /** Hardware exists but the user has not enrolled any biometrics. */
        NOT_ENROLLED,

        /** Hardware exists but is currently locked out due to too many failures. */
        LOCKED_OUT,

        /** All prerequisites satisfied and the prompt can be displayed. */
        AVAILABLE,
    }
}

sealed interface BiometricAuthResult {
    data object Success : BiometricAuthResult

    data object Cancelled : BiometricAuthResult

    data class Failure(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : BiometricAuthResult
}

sealed interface BiometricLockState {
    data object Disabled : BiometricLockState

    data object Locked : BiometricLockState

    data object Unlocking : BiometricLockState

    data object Unlocked : BiometricLockState

    data class Error(
        val reason: String,
    ) : BiometricLockState
}

/**
 * Platform-specific authenticator capable of showing system biometric prompts.
 */
interface BiometricUnlocker {
    suspend fun authenticate(): BiometricAuthResult

    fun cancel()
}
