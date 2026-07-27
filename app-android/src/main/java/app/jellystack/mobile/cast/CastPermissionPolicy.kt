package app.jellystack.mobile.cast

import android.Manifest

private const val ANDROID_13_API_LEVEL = 33

enum class CastPermissionAction {
    ShowInitialRationale,
    ShowRetryRationale,
    OpenAppSettings,
    OpenPicker,
}

internal fun nextCastPermissionAction(
    granted: Boolean,
    requested: Boolean,
    rationale: Boolean,
): CastPermissionAction =
    when {
        granted -> CastPermissionAction.OpenPicker
        !requested -> CastPermissionAction.ShowInitialRationale
        rationale -> CastPermissionAction.ShowRetryRationale
        else -> CastPermissionAction.OpenAppSettings
    }

internal fun requiredCastRuntimePermissions(sdkInt: Int): List<String> =
    if (sdkInt >= ANDROID_13_API_LEVEL) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

enum class CastPickerHost {
    Shell,
    Player,
}

data class PendingCastPicker(
    val token: Long,
    val host: CastPickerHost,
)

data class CastPermissionUiState(
    val granted: Boolean,
    val requested: Boolean,
    val rationale: Boolean,
    val pendingPicker: PendingCastPicker? = null,
)
