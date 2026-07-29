@file:Suppress("Filename", "ktlint:standard:filename")

package dev.jellystack.design.biometric

import androidx.compose.runtime.Composable
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricUnlocker
import kotlinx.coroutines.flow.StateFlow

/** Device-owner authentication state; the biometric name is retained for source compatibility. */
data class BiometricPlatformState(
    val capability: StateFlow<BiometricCapability>,
    val unlocker: BiometricUnlocker?,
)

@Composable
expect fun rememberBiometricPlatformState(): BiometricPlatformState
