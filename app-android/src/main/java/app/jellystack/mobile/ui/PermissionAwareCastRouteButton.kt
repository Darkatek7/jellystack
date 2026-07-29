@file:Suppress("FunctionName", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MaxLineLength")

package app.jellystack.mobile.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import app.jellystack.mobile.R
import app.jellystack.mobile.cast.CastPermissionAction
import app.jellystack.mobile.cast.CastPermissionUiState
import app.jellystack.mobile.cast.CastPickerHost
import app.jellystack.mobile.cast.nextCastPermissionAction
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.jellystack.players.cast.CastConnectionState

@Composable
internal fun PermissionAwareCastRouteButton(
    castState: CastConnectionState,
    permissionState: CastPermissionUiState,
    host: CastPickerHost,
    onAction: (CastPickerHost) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickerConsumed: (Long) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalContentColor.current,
) {
    val description =
        when (castState) {
            is CastConnectionState.Connected -> stringResource(R.string.player_cast_connected, castState.deviceName)
            is CastConnectionState.Connecting -> stringResource(R.string.player_cast_connecting_accessibility)
            else -> stringResource(R.string.player_cast_disconnected)
        }
    val pending = permissionState.pendingPicker?.takeIf { it.host == host }
    val action =
        nextCastPermissionAction(
            granted = permissionState.granted,
            requested = permissionState.requested,
            rationale = permissionState.rationale,
        )
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }
    var dismissedDialogToken by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = modifier.size(48.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (permissionState.granted) {
            AndroidView(
                modifier = Modifier.size(48.dp).alpha(0f),
                factory = { context ->
                    MediaRouteButton(context).apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        CastButtonFactory.setUpMediaRouteButton(context, this)
                        contentDescription = description
                        routeButton = this
                    }
                },
                update = { button ->
                    button.isEnabled = castState !is CastConnectionState.Connecting
                    routeButton = button
                },
            )
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = castState !is CastConnectionState.Connecting,
                onClick = { onAction(host) },
            ) {
                Icon(
                    imageVector =
                        if (castState is CastConnectionState.Connected) {
                            Icons.Filled.CastConnected
                        } else {
                            Icons.Filled.Cast
                        },
                    contentDescription = null,
                    tint = iconTint,
                )
            }
        } else {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { onAction(host) },
            ) {
                Icon(Icons.Filled.Cast, contentDescription = null, tint = iconTint)
            }
        }
    }

    LaunchedEffect(pending?.token, action, routeButton) {
        val request = pending ?: return@LaunchedEffect
        if (action == CastPermissionAction.OpenPicker && routeButton != null) {
            routeButton?.performClick()
            onPickerConsumed(request.token)
        }
    }

    if (
        pending != null &&
        action != CastPermissionAction.OpenPicker &&
        dismissedDialogToken != pending.token
    ) {
        val settingsRequired = action == CastPermissionAction.OpenAppSettings
        AlertDialog(
            onDismissRequest = {
                dismissedDialogToken = pending.token
                onPickerConsumed(pending.token)
            },
            title = { Text(stringResource(R.string.cast_permission_title)) },
            text = {
                Text(
                    stringResource(
                        when (action) {
                            CastPermissionAction.ShowInitialRationale -> R.string.cast_permission_initial_message
                            CastPermissionAction.ShowRetryRationale -> R.string.cast_permission_retry_message
                            CastPermissionAction.OpenAppSettings -> R.string.cast_permission_settings_message
                            CastPermissionAction.OpenPicker -> error("Picker action does not show a dialog")
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissedDialogToken = pending.token
                        if (settingsRequired) onOpenSettings() else onRequestPermissions()
                    },
                ) {
                    Text(
                        stringResource(
                            if (settingsRequired) R.string.cast_permission_open_settings else R.string.cast_permission_continue,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dismissedDialogToken = pending.token
                        onPickerConsumed(pending.token)
                    },
                ) {
                    Text(stringResource(R.string.cast_permission_not_now))
                }
            },
        )
    }
}
