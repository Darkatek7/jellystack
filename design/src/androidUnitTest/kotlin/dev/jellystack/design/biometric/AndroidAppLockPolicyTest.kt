package dev.jellystack.design.biometric

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidAppLockPolicyTest {
    @Test
    fun api30UsesCombinedPrompt() {
        assertEquals(
            AndroidAppLockRoute.CombinedPrompt,
            androidAppLockRoute(
                apiLevel = 30,
                deviceSecure = true,
                strongBiometric = true,
                weakBiometric = true,
            ),
        )
    }

    @Test
    fun api24To29UsesBiometricThenCredentialOrCredentialOnly() {
        assertEquals(
            AndroidAppLockRoute.BiometricThenCredential,
            androidAppLockRoute(29, deviceSecure = true, strongBiometric = true, weakBiometric = true),
        )
        assertEquals(
            AndroidAppLockRoute.CredentialOnly,
            androidAppLockRoute(24, deviceSecure = true, strongBiometric = false, weakBiometric = false),
        )
        assertEquals(
            AndroidAppLockRoute.Unavailable,
            androidAppLockRoute(29, deviceSecure = false, strongBiometric = true, weakBiometric = true),
        )
    }
}
