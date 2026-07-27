package dev.jellystack.design.biometric

internal enum class AndroidAppLockRoute {
    CombinedPrompt,
    BiometricThenCredential,
    CredentialOnly,
    Unavailable,
}

internal fun androidAppLockRoute(
    apiLevel: Int,
    deviceSecure: Boolean,
    strongBiometric: Boolean,
    weakBiometric: Boolean,
): AndroidAppLockRoute =
    when {
        !deviceSecure -> AndroidAppLockRoute.Unavailable
        apiLevel >= 30 -> AndroidAppLockRoute.CombinedPrompt
        strongBiometric || weakBiometric -> AndroidAppLockRoute.BiometricThenCredential
        else -> AndroidAppLockRoute.CredentialOnly
    }
