package dev.jellystack.design.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.jellystack.core.security.BiometricAuthResult
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricUnlocker
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.app_lock_authentication_unavailable
import jellystack_mobile.design.generated.resources.app_lock_cancel
import jellystack_mobile.design.generated.resources.app_lock_enroll_device
import jellystack_mobile.design.generated.resources.app_lock_prompt
import jellystack_mobile.design.generated.resources.app_lock_ready
import jellystack_mobile.design.generated.resources.app_lock_temporarily_unavailable
import jellystack_mobile.design.generated.resources.app_lock_title
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeNone
import platform.LocalAuthentication.LABiometryTypeOpticID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorDomain
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorTouchIDLockout
import platform.LocalAuthentication.LAErrorTouchIDNotAvailable
import platform.LocalAuthentication.LAErrorTouchIDNotEnrolled
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometricPlatformState(): BiometricPlatformState {
    val copy =
        IosAppLockCopy(
            title = stringResource(Res.string.app_lock_title),
            prompt = stringResource(Res.string.app_lock_prompt),
            cancel = stringResource(Res.string.app_lock_cancel),
            ready = stringResource(Res.string.app_lock_ready),
            enrollDevice = stringResource(Res.string.app_lock_enroll_device),
            temporarilyUnavailable = stringResource(Res.string.app_lock_temporarily_unavailable),
            authenticationUnavailable = stringResource(Res.string.app_lock_authentication_unavailable),
        )
    val capabilityState =
        remember(copy) {
            MutableStateFlow(evaluateCapability(copy))
        }
    val refreshCapability =
        remember(copy) {
            {
                capabilityState.value = evaluateCapability(copy)
            }
        }
    val unlocker =
        remember(copy) {
            IosBiometricUnlocker(
                capabilityState = capabilityState,
                refreshCapability = refreshCapability,
                copy = copy,
            )
        }
    DisposableEffect(unlocker) {
        val center = NSNotificationCenter.defaultCenter
        val activeObserver =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                refreshCapability()
            }
        val inactiveObserver =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                refreshCapability()
            }
        onDispose {
            unlocker.cancel()
            activeObserver?.let { center.removeObserver(it) }
            inactiveObserver?.let { center.removeObserver(it) }
        }
    }
    LaunchedEffect(Unit) { refreshCapability() }
    return remember(capabilityState, unlocker) {
        BiometricPlatformState(
            capability = capabilityState.asStateFlow(),
            unlocker = unlocker,
        )
    }
}

private data class IosAppLockCopy(
    val title: String,
    val prompt: String,
    val cancel: String,
    val ready: String,
    val enrollDevice: String,
    val temporarilyUnavailable: String,
    val authenticationUnavailable: String,
)

private class IosBiometricUnlocker(
    private val capabilityState: MutableStateFlow<BiometricCapability>,
    private val refreshCapability: () -> Unit,
    private val copy: IosAppLockCopy,
) : BiometricUnlocker {
    private var activeContext: LAContext? = null

    override suspend fun authenticate(): BiometricAuthResult =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            context.localizedCancelTitle = copy.cancel
            activeContext = context
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = copy.prompt,
                reply = { success, error ->
                    dispatch_async(dispatch_get_main_queue()) {
                        activeContext = null
                        refreshCapability()
                        val result =
                            when {
                                success -> BiometricAuthResult.Success
                                error != null -> mapAuthenticationError(error)
                                else -> BiometricAuthResult.Cancelled
                            }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                },
            )
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    context.invalidate()
                    activeContext = null
                }
            }
        }

    override fun cancel() {
        dispatch_async(dispatch_get_main_queue()) {
            activeContext?.invalidate()
            activeContext = null
        }
    }

    private fun mapAuthenticationError(error: NSError): BiometricAuthResult =
        when (error.domain) {
            LAErrorDomain -> {
                when (error.code.toInt()) {
                    LAErrorUserCancel,
                    LAErrorSystemCancel,
                    LAErrorAppCancel,
                    -> BiometricAuthResult.Cancelled

                    LAErrorBiometryLockout,
                    LAErrorTouchIDLockout,
                    ->
                        BiometricAuthResult.Failure(error.localizedDescription ?: copy.temporarilyUnavailable)

                    LAErrorAuthenticationFailed ->
                        BiometricAuthResult.Failure(error.localizedDescription ?: copy.authenticationUnavailable)

                    else -> BiometricAuthResult.Failure(error.localizedDescription ?: copy.authenticationUnavailable)
                }
            }

            else -> BiometricAuthResult.Failure(error.localizedDescription ?: copy.authenticationUnavailable)
        }
}

private fun evaluateCapability(copy: IosAppLockCopy): BiometricCapability =
    memScoped {
        val errorHolder = alloc<ObjCObjectVar<NSError?>>(null)
        val context = LAContext()
        val canEvaluate =
            context.canEvaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                error = errorHolder.ptr,
            )
        if (canEvaluate) {
            BiometricCapability(
                status = BiometricCapability.Status.AVAILABLE,
                title = context.biometryLabel(copy.title),
                description = copy.ready,
                secureCredentialAvailable = true,
            )
        } else {
            val error = errorHolder.value
            capabilityFromError(error, copy)
        }
    }

private fun capabilityFromError(
    error: NSError?,
    copy: IosAppLockCopy,
): BiometricCapability {
    val description = error?.localizedDescription ?: copy.temporarilyUnavailable
    if (error == null || error.domain != LAErrorDomain) {
        return BiometricCapability(
            status = BiometricCapability.Status.UNAVAILABLE,
            description = description,
            secureCredentialAvailable = false,
        )
    }
    return when (error.code.toInt()) {
        LAErrorBiometryNotAvailable, LAErrorTouchIDNotAvailable ->
            BiometricCapability(
                status = BiometricCapability.Status.UNSUPPORTED,
                description = description,
                secureCredentialAvailable = true,
            )

        LAErrorBiometryNotEnrolled, LAErrorTouchIDNotEnrolled ->
            BiometricCapability(
                status = BiometricCapability.Status.NOT_ENROLLED,
                description = description,
                secureCredentialAvailable = true,
            )

        LAErrorBiometryLockout, LAErrorTouchIDLockout ->
            BiometricCapability(
                status = BiometricCapability.Status.LOCKED_OUT,
                description = description,
                secureCredentialAvailable = true,
            )

        LAErrorPasscodeNotSet ->
            BiometricCapability(
                status = BiometricCapability.Status.UNAVAILABLE,
                description = copy.enrollDevice,
                secureCredentialAvailable = false,
            )

        else ->
            BiometricCapability(
                status = BiometricCapability.Status.UNAVAILABLE,
                description = description,
                secureCredentialAvailable = true,
            )
    }
}

private fun LAContext.biometryLabel(fallback: String): String =
    when (biometryType) {
        LABiometryTypeTouchID -> "Touch ID"
        LABiometryTypeFaceID -> "Face ID"
        LABiometryTypeOpticID -> "Optic ID"
        LABiometryTypeNone -> fallback
        else -> fallback
    }
