package app.jellystack.mobile.cast

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CastPermissionPolicyTest {
    @Test
    fun `android 12 and 12L cast discovery use location permissions`() {
        listOf(31, 32).forEach { sdkInt ->
            assertEquals(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                requiredCastRuntimePermissions(sdkInt),
            )
        }
    }

    @Test
    fun `android 13 and newer cast discovery use nearby devices only`() {
        listOf(33, 36).forEach { sdkInt ->
            assertEquals(
                listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                requiredCastRuntimePermissions(sdkInt),
            )
        }
    }

    @Test
    fun grantedPermissionOpensPicker() {
        assertEquals(
            CastPermissionAction.OpenPicker,
            nextCastPermissionAction(granted = true, requested = false, rationale = false),
        )
    }

    @Test
    fun firstCastTapExplainsPermissionBeforeRequestingIt() {
        assertEquals(
            CastPermissionAction.ShowInitialRationale,
            nextCastPermissionAction(granted = false, requested = false, rationale = false),
        )
    }

    @Test
    fun deniedPermissionCanBeRequestedAgainWhenAndroidAllowsIt() {
        assertEquals(
            CastPermissionAction.ShowRetryRationale,
            nextCastPermissionAction(granted = false, requested = true, rationale = true),
        )
    }

    @Test
    fun permanentlyDeniedPermissionRoutesToAppSettings() {
        assertEquals(
            CastPermissionAction.OpenAppSettings,
            nextCastPermissionAction(granted = false, requested = true, rationale = false),
        )
    }

    @Test
    fun coordinatorTargetsOnlyTheHostThatWasTapped() {
        var discoveryStarts = 0
        val coordinator =
            CastPermissionCoordinator(
                requestedBefore = true,
                permissionsGranted = { true },
                shouldShowRationale = { false },
                launchPermissions = {},
                startDiscovery = { discoveryStarts++ },
                openAppSettings = {},
                persistRequestedBefore = {},
            )

        coordinator.onCastAction(CastPickerHost.Player)

        val pending = coordinator.state.value.pendingPicker
        assertEquals(CastPickerHost.Player, pending?.host)
        assertEquals(1, discoveryStarts)
        coordinator.onPickerConsumed(checkNotNull(pending).token + 1)
        assertEquals(pending, coordinator.state.value.pendingPicker)
        coordinator.onPickerConsumed(pending.token)
        assertNull(coordinator.state.value.pendingPicker)
    }

    @Test
    fun permissionResultRechecksCurrentSystemState() {
        var granted = false
        var permissionLaunches = 0
        var persisted = false
        var discoveryStarts = 0
        val coordinator =
            CastPermissionCoordinator(
                requestedBefore = false,
                permissionsGranted = { granted },
                shouldShowRationale = { false },
                launchPermissions = { permissionLaunches++ },
                startDiscovery = { discoveryStarts++ },
                openAppSettings = {},
                persistRequestedBefore = { persisted = it },
            )
        coordinator.onCastAction(CastPickerHost.Shell)
        coordinator.requestPermissions()
        granted = true

        coordinator.onPermissionResult()

        assertEquals(1, permissionLaunches)
        assertEquals(true, persisted)
        assertEquals(true, coordinator.state.value.granted)
        assertEquals(1, discoveryStarts)
    }
}
