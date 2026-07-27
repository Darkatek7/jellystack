package dev.jellystack.design.biometric

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.jellystack.core.security.BiometricAuthResult
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricUnlocker
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.app_lock_authentication_unavailable
import jellystack_mobile.design.generated.resources.app_lock_cancel
import jellystack_mobile.design.generated.resources.app_lock_credential_ready
import jellystack_mobile.design.generated.resources.app_lock_enroll_device
import jellystack_mobile.design.generated.resources.app_lock_prompt
import jellystack_mobile.design.generated.resources.app_lock_ready
import jellystack_mobile.design.generated.resources.app_lock_security_update
import jellystack_mobile.design.generated.resources.app_lock_temporarily_unavailable
import jellystack_mobile.design.generated.resources.app_lock_title
import jellystack_mobile.design.generated.resources.app_lock_use_device_credential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricPlatformState(): BiometricPlatformState {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val copy =
        AndroidAppLockCopy(
            title = stringResource(Res.string.app_lock_title),
            prompt = stringResource(Res.string.app_lock_prompt),
            cancel = stringResource(Res.string.app_lock_cancel),
            useDeviceCredential = stringResource(Res.string.app_lock_use_device_credential),
            enrollDevice = stringResource(Res.string.app_lock_enroll_device),
            ready = stringResource(Res.string.app_lock_ready),
            credentialReady = stringResource(Res.string.app_lock_credential_ready),
            temporarilyUnavailable = stringResource(Res.string.app_lock_temporarily_unavailable),
            securityUpdate = stringResource(Res.string.app_lock_security_update),
            authenticationUnavailable = stringResource(Res.string.app_lock_authentication_unavailable),
        )
    val capabilityFlow =
        remember(activity, copy) {
            MutableStateFlow(
                activity?.let { evaluateCapability(it, copy) }
                    ?: BiometricCapability(
                        status = BiometricCapability.Status.UNSUPPORTED,
                        description = copy.temporarilyUnavailable,
                    ),
            )
        }
    val refreshCapability =
        remember(activity, copy) {
            {
                capabilityFlow.value =
                    activity?.let { evaluateCapability(it, copy) }
                        ?: BiometricCapability(
                            status = BiometricCapability.Status.UNSUPPORTED,
                            description = copy.temporarilyUnavailable,
                        )
            }
        }
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                        refreshCapability()
                    }
                }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }
    val unlocker =
        remember(activity, copy) {
            activity?.let {
                AndroidBiometricUnlocker(
                    activity = it,
                    capabilityState = capabilityFlow,
                    refreshCapability = refreshCapability,
                    copy = copy,
                )
            }
        }
    DisposableEffect(unlocker) {
        onDispose { unlocker?.cancel() }
    }
    return remember(capabilityFlow, unlocker) {
        BiometricPlatformState(
            capability = capabilityFlow.asStateFlow(),
            unlocker = unlocker,
        )
    }
}

private data class AndroidAppLockCopy(
    val title: String,
    val prompt: String,
    val cancel: String,
    val useDeviceCredential: String,
    val enrollDevice: String,
    val ready: String,
    val credentialReady: String,
    val temporarilyUnavailable: String,
    val securityUpdate: String,
    val authenticationUnavailable: String,
)

private class AndroidBiometricUnlocker(
    private val activity: FragmentActivity,
    private val capabilityState: MutableStateFlow<BiometricCapability>,
    private val refreshCapability: () -> Unit,
    private val copy: AndroidAppLockCopy,
) : BiometricUnlocker {
    private val executor = ContextCompat.getMainExecutor(activity)
    private var activePrompt: BiometricPrompt? = null
    private var credentialJob: Job? = null

    override suspend fun authenticate(): BiometricAuthResult =
        withContext(Dispatchers.Main.immediate) {
            val snapshot = androidCapabilitySnapshot(activity)
            when (snapshot.route) {
                AndroidAppLockRoute.Unavailable -> BiometricAuthResult.Failure(copy.enrollDevice)
                AndroidAppLockRoute.CredentialOnly -> confirmDeviceCredential()
                AndroidAppLockRoute.CombinedPrompt,
                AndroidAppLockRoute.BiometricThenCredential,
                -> authenticateWithPrompt(snapshot.route)
            }.also { refreshCapability() }
        }

    private suspend fun authenticateWithPrompt(route: AndroidAppLockRoute): BiometricAuthResult =
        suspendCancellableCoroutine { continuation ->
            fun finish(result: BiometricAuthResult) {
                if (continuation.isActive) continuation.resume(result)
            }

            fun startCredentialFallback() {
                activePrompt = null
                credentialJob?.cancel()
                credentialJob =
                    CoroutineScope(Dispatchers.Main.immediate).launch {
                        finish(confirmDeviceCredential())
                    }
            }

            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            finish(BiometricAuthResult.Success)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (!continuation.isActive) return
                            when {
                                route == AndroidAppLockRoute.BiometricThenCredential &&
                                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                                    startCredentialFallback()
                                route == AndroidAppLockRoute.BiometricThenCredential &&
                                    (
                                        errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                                            errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                                    ) -> {
                                    capabilityState.value =
                                        capabilityState.value.copy(
                                            status = BiometricCapability.Status.LOCKED_OUT,
                                        )
                                    startCredentialFallback()
                                }
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                                    errorCode == BiometricPrompt.ERROR_TIMEOUT ->
                                    finish(BiometricAuthResult.Cancelled)
                                else -> finish(BiometricAuthResult.Failure(errString.toString()))
                            }
                        }
                    },
                )
            activePrompt = prompt
            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
                credentialJob?.cancel()
                activePrompt = null
            }
            prompt.authenticate(promptInfo(route))
        }.also {
            activePrompt = null
            credentialJob = null
        }

    private fun promptInfo(route: AndroidAppLockRoute): BiometricPrompt.PromptInfo {
        val builder =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(copy.title)
                .setSubtitle(copy.prompt)
        when (route) {
            AndroidAppLockRoute.CombinedPrompt ->
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            AndroidAppLockRoute.BiometricThenCredential ->
                builder
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK,
                    ).setNegativeButtonText(copy.useDeviceCredential)
            else -> builder.setNegativeButtonText(copy.cancel)
        }
        return builder.build()
    }

    private suspend fun confirmDeviceCredential(): BiometricAuthResult {
        val keyguard = ContextCompat.getSystemService(activity, KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(copy.title, copy.prompt)
        return CredentialConfirmationFragment.confirm(activity, intent, copy.authenticationUnavailable)
    }

    override fun cancel() {
        activePrompt?.cancelAuthentication()
        activePrompt = null
        credentialJob?.cancel()
        credentialJob = null
        CredentialConfirmationFragment.cancel(activity)
    }
}

private data class AndroidCapabilitySnapshot(
    val route: AndroidAppLockRoute,
    val deviceSecure: Boolean,
    val strongStatus: Int,
    val weakStatus: Int,
)

private fun androidCapabilitySnapshot(activity: FragmentActivity): AndroidCapabilitySnapshot {
    val manager = BiometricManager.from(activity)
    val keyguard = ContextCompat.getSystemService(activity, KeyguardManager::class.java)
    val deviceSecure = keyguard?.isDeviceSecure == true
    val strongStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    val weakStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    return AndroidCapabilitySnapshot(
        route =
            androidAppLockRoute(
                apiLevel = Build.VERSION.SDK_INT,
                deviceSecure = deviceSecure,
                strongBiometric = strongStatus == BiometricManager.BIOMETRIC_SUCCESS,
                weakBiometric = weakStatus == BiometricManager.BIOMETRIC_SUCCESS,
            ),
        deviceSecure = deviceSecure,
        strongStatus = strongStatus,
        weakStatus = weakStatus,
    )
}

private fun evaluateCapability(
    activity: FragmentActivity,
    copy: AndroidAppLockCopy,
): BiometricCapability {
    val snapshot = androidCapabilitySnapshot(activity)
    val status =
        when {
            snapshot.strongStatus == BiometricManager.BIOMETRIC_SUCCESS ||
                snapshot.weakStatus == BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricCapability.Status.AVAILABLE
            snapshot.strongStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                snapshot.weakStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricCapability.Status.NOT_ENROLLED
            snapshot.strongStatus == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
                snapshot.weakStatus == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricCapability.Status.UNSUPPORTED
            else -> BiometricCapability.Status.UNAVAILABLE
        }
    val description =
        when {
            !snapshot.deviceSecure -> copy.enrollDevice
            status == BiometricCapability.Status.AVAILABLE -> copy.ready
            snapshot.route == AndroidAppLockRoute.CredentialOnly -> copy.credentialReady
            snapshot.strongStatus == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                snapshot.weakStatus == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                copy.securityUpdate
            else -> copy.credentialReady
        }
    return BiometricCapability(
        status = status,
        title = copy.title,
        description = description,
        secureCredentialAvailable = snapshot.deviceSecure,
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext?.findFragmentActivity()
        else -> null
    }
