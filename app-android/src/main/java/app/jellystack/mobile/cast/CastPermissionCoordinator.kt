@file:Suppress("LongParameterList")

package app.jellystack.mobile.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastPermissionCoordinator(
    requestedBefore: Boolean,
    private val permissionsGranted: () -> Boolean,
    private val shouldShowRationale: () -> Boolean,
    private val launchPermissions: () -> Unit,
    private val startDiscovery: () -> Unit,
    private val openAppSettings: () -> Unit,
    private val persistRequestedBefore: (Boolean) -> Unit,
) {
    private val mutableState =
        MutableStateFlow(
            CastPermissionUiState(
                granted = permissionsGranted(),
                requested = requestedBefore,
                rationale = shouldShowRationale(),
            ),
        )
    val state: StateFlow<CastPermissionUiState> = mutableState.asStateFlow()

    private var nextToken = 0L
    private var castActivated = false

    val shouldRunDiscovery: Boolean
        get() = castActivated && mutableState.value.granted

    fun onCastAction(host: CastPickerHost) {
        castActivated = true
        refreshFromSystem()
        mutableState.value =
            mutableState.value.copy(
                pendingPicker = PendingCastPicker(token = ++nextToken, host = host),
            )
        if (mutableState.value.granted) startDiscovery()
    }

    fun requestPermissions() {
        if (!mutableState.value.requested) {
            persistRequestedBefore(true)
        }
        mutableState.value = mutableState.value.copy(requested = true)
        launchPermissions()
    }

    fun openSettings() {
        openAppSettings()
    }

    fun onPermissionResult() {
        refreshFromSystem()
        if (mutableState.value.granted) {
            startDiscovery()
        } else {
            mutableState.value = mutableState.value.copy(pendingPicker = null)
        }
    }

    fun refreshFromSystem() {
        val granted = permissionsGranted()
        mutableState.value =
            mutableState.value.copy(
                granted = granted,
                rationale = !granted && shouldShowRationale(),
            )
    }

    fun onPickerConsumed(token: Long) {
        if (mutableState.value.pendingPicker?.token == token) {
            mutableState.value = mutableState.value.copy(pendingPicker = null)
        }
    }
}
