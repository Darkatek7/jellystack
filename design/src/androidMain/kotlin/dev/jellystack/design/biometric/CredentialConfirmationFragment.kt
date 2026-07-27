package dev.jellystack.design.biometric

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.jellystack.core.security.BiometricAuthResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class CredentialConfirmationFragment : Fragment() {
    private var pending: CancellableContinuation<BiometricAuthResult>? = null
    private lateinit var launcher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher =
            requireActivity().activityResultRegistry.register(
                TAG,
                this,
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                complete(
                    if (result.resultCode == Activity.RESULT_OK) {
                        BiometricAuthResult.Success
                    } else {
                        BiometricAuthResult.Cancelled
                    },
                )
            }
    }

    suspend fun confirm(
        intent: Intent?,
        failureMessage: String,
    ): BiometricAuthResult {
        if (intent == null) {
            return BiometricAuthResult.Failure(failureMessage)
        }
        return suspendCancellableCoroutine { continuation ->
            if (pending != null) {
                continuation.resume(BiometricAuthResult.Failure(failureMessage))
                return@suspendCancellableCoroutine
            }
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            runCatching { launcher.launch(intent) }
                .onFailure { error ->
                    complete(BiometricAuthResult.Failure(error.message, error))
                }
        }
    }

    fun cancelPending() {
        complete(BiometricAuthResult.Cancelled)
    }

    override fun onDestroy() {
        complete(BiometricAuthResult.Cancelled)
        if (::launcher.isInitialized) launcher.unregister()
        super.onDestroy()
    }

    private fun complete(result: BiometricAuthResult) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(result)
    }

    companion object {
        private const val TAG = "jellystack-device-credential"

        suspend fun confirm(
            activity: FragmentActivity,
            intent: Intent?,
            failureMessage: String,
        ): BiometricAuthResult =
            withContext(Dispatchers.Main.immediate) {
                val manager = activity.supportFragmentManager
                val existing = manager.findFragmentByTag(TAG) as? CredentialConfirmationFragment
                val fragment =
                    existing
                        ?: CredentialConfirmationFragment().also {
                            if (manager.isStateSaved) {
                                return@withContext BiometricAuthResult.Failure(
                                    failureMessage,
                                )
                            }
                            manager.beginTransaction().add(it, TAG).commitNow()
                        }
                fragment.confirm(intent, failureMessage)
            }

        fun cancel(activity: FragmentActivity) {
            (activity.supportFragmentManager.findFragmentByTag(TAG) as? CredentialConfirmationFragment)
                ?.cancelPending()
        }
    }
}
