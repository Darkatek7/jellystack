package dev.jellystack.core.security

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricAuthGateTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun enableReturnsFalseWhenCapabilityUnavailable() =
        runTest(dispatcher) {
            val gate = BiometricAuthGate(BiometricLockPreferenceRepository(InMemorySettings()), dispatcher)
            assertFalse(gate.enable())
            assertEquals(BiometricLockState.Error(""), gate.lockState.value)
            assertFalse(gate.autoPrompt.value)
        }

    @Test
    fun enableUnlockAndLifecycleTransitions() =
        runTest(dispatcher) {
            val gate = BiometricAuthGate(BiometricLockPreferenceRepository(InMemorySettings()), dispatcher)
            gate.updateCapability(
                BiometricCapability(
                    status = BiometricCapability.Status.AVAILABLE,
                    title = "Biometric unlock",
                    description = "",
                    secureCredentialAvailable = true,
                ),
            )
            assertTrue(gate.enable())
            assertEquals(BiometricLockState.Locked, gate.lockState.value)
            assertTrue(gate.autoPrompt.value)
            assertFalse(gate.disable())
            assertEquals(BiometricLockState.Locked, gate.lockState.value)

            val unlocker =
                object : BiometricUnlocker {
                    override suspend fun authenticate(): BiometricAuthResult = BiometricAuthResult.Success

                    override fun cancel() {}
                }
            gate.unlock(unlocker)
            advanceUntilIdle()
            assertEquals(BiometricLockState.Unlocked, gate.lockState.value)
            assertFalse(gate.autoPrompt.value)

            gate.onAppBackgrounded()
            assertEquals(BiometricLockState.Locked, gate.lockState.value)
            assertFalse(gate.autoPrompt.value)
            gate.onAppForegrounded()
            assertTrue(gate.autoPrompt.value)

            gate.unlock(unlocker)
            advanceUntilIdle()
            assertEquals(BiometricLockState.Unlocked, gate.lockState.value)
            assertTrue(gate.disable())
            assertEquals(BiometricLockState.Disabled, gate.lockState.value)
        }

    @Test
    fun credentialOnlyCapabilityCanEnableAndUnlock() =
        runTest(dispatcher) {
            val gate = BiometricAuthGate(BiometricLockPreferenceRepository(InMemorySettings()), dispatcher)
            gate.updateCapability(
                BiometricCapability(
                    status = BiometricCapability.Status.NOT_ENROLLED,
                    secureCredentialAvailable = true,
                ),
            )

            assertTrue(gate.enable())
            assertEquals(BiometricLockState.Locked, gate.lockState.value)
            gate.unlock(SuccessUnlocker)
            advanceUntilIdle()
            assertEquals(BiometricLockState.Unlocked, gate.lockState.value)
        }

    @Test
    fun losingCredentialNeverDisablesOrUnlocksExistingLock() {
        val settings = InMemorySettings().apply { putBoolean("security.biometric.enabled", true) }
        val gate = BiometricAuthGate(BiometricLockPreferenceRepository(settings), dispatcher)

        gate.updateCapability(
            BiometricCapability(
                status = BiometricCapability.Status.UNAVAILABLE,
                secureCredentialAvailable = false,
            ),
        )

        assertTrue(gate.isEnabled.value)
        assertIs<BiometricLockState.Error>(gate.lockState.value)
        assertFalse(gate.disable())
    }

    @Test
    fun systemCredentialPauseDoesNotCancelAnActiveUnlock() =
        runTest(dispatcher) {
            val settings = InMemorySettings().apply { putBoolean("security.biometric.enabled", true) }
            val gate = BiometricAuthGate(BiometricLockPreferenceRepository(settings), dispatcher)
            gate.updateCapability(
                BiometricCapability(
                    status = BiometricCapability.Status.NOT_ENROLLED,
                    secureCredentialAvailable = true,
                ),
            )
            val unlocker = SuspendedFakeUnlocker()
            gate.unlock(unlocker)
            runCurrent()

            gate.onAppBackgrounded()
            unlocker.complete(BiometricAuthResult.Success)
            advanceUntilIdle()

            assertEquals(BiometricLockState.Unlocked, gate.lockState.value)
            assertFalse(unlocker.cancelled)
        }
}

private object SuccessUnlocker : BiometricUnlocker {
    override suspend fun authenticate(): BiometricAuthResult = BiometricAuthResult.Success

    override fun cancel() = Unit
}

private class SuspendedFakeUnlocker : BiometricUnlocker {
    private val result = CompletableDeferred<BiometricAuthResult>()
    var cancelled = false
        private set

    override suspend fun authenticate(): BiometricAuthResult = result.await()

    override fun cancel() {
        cancelled = true
    }

    fun complete(value: BiometricAuthResult) {
        result.complete(value)
    }
}

private class InMemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()

    override val keys: Set<String>
        get() = values.keys

    override val size: Int
        get() = values.size

    override fun clear() {
        values.clear()
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun hasKey(key: String): Boolean = values.containsKey(key)

    override fun putInt(
        key: String,
        value: Int,
    ) {
        values[key] = value
    }

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = (values[key] as? Int) ?: defaultValue

    override fun getIntOrNull(key: String): Int? = values[key] as? Int

    override fun putLong(
        key: String,
        value: Long,
    ) {
        values[key] = value
    }

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = (values[key] as? Long) ?: defaultValue

    override fun getLongOrNull(key: String): Long? = values[key] as? Long

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = (values[key] as? String) ?: defaultValue

    override fun getStringOrNull(key: String): String? = values[key] as? String

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        values[key] = value
    }

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = (values[key] as? Float) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = values[key] as? Float

    override fun putDouble(
        key: String,
        value: Double,
    ) {
        values[key] = value
    }

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = (values[key] as? Double) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = values[key] as? Double

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = (values[key] as? Boolean) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
}
