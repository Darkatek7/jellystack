package dev.jellystack.design.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinAdminNotice
import dev.jellystack.core.jellyfin.JellyfinAdminState
import dev.jellystack.core.jellyfin.JellyfinAdminUser
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.admin_activity
import jellystack_mobile.design.generated.resources.admin_approve
import jellystack_mobile.design.generated.resources.admin_cancel
import jellystack_mobile.design.generated.resources.admin_create_user
import jellystack_mobile.design.generated.resources.admin_decline
import jellystack_mobile.design.generated.resources.admin_delete
import jellystack_mobile.design.generated.resources.admin_delete_user_body
import jellystack_mobile.design.generated.resources.admin_delete_user_title
import jellystack_mobile.design.generated.resources.admin_disable
import jellystack_mobile.design.generated.resources.admin_enable
import jellystack_mobile.design.generated.resources.admin_error
import jellystack_mobile.design.generated.resources.admin_library_scan
import jellystack_mobile.design.generated.resources.admin_library_stats
import jellystack_mobile.design.generated.resources.admin_no_activity
import jellystack_mobile.design.generated.resources.admin_no_pending_requests
import jellystack_mobile.design.generated.resources.admin_notice_library_scan
import jellystack_mobile.design.generated.resources.admin_notice_password_reset
import jellystack_mobile.design.generated.resources.admin_notice_restart
import jellystack_mobile.design.generated.resources.admin_notice_user_created
import jellystack_mobile.design.generated.resources.admin_notice_user_deleted
import jellystack_mobile.design.generated.resources.admin_notice_user_updated
import jellystack_mobile.design.generated.resources.admin_password
import jellystack_mobile.design.generated.resources.admin_pending_requests
import jellystack_mobile.design.generated.resources.admin_refresh
import jellystack_mobile.design.generated.resources.admin_reset_password
import jellystack_mobile.design.generated.resources.admin_restart
import jellystack_mobile.design.generated.resources.admin_restart_body
import jellystack_mobile.design.generated.resources.admin_restart_title
import jellystack_mobile.design.generated.resources.admin_save
import jellystack_mobile.design.generated.resources.admin_server
import jellystack_mobile.design.generated.resources.admin_stat_albums
import jellystack_mobile.design.generated.resources.admin_stat_episodes
import jellystack_mobile.design.generated.resources.admin_stat_movies
import jellystack_mobile.design.generated.resources.admin_stat_series
import jellystack_mobile.design.generated.resources.admin_stat_songs
import jellystack_mobile.design.generated.resources.admin_user_disabled
import jellystack_mobile.design.generated.resources.admin_username
import jellystack_mobile.design.generated.resources.admin_users
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminScreen(
    state: JellyfinAdminState,
    pendingRequests: List<JellyseerrRequestSummary>,
    canManageRequests: Boolean,
    currentUserId: String?,
    onRefresh: () -> Unit,
    onLibraryScan: () -> Unit,
    onRestart: () -> Unit,
    onCreateUser: (String, String) -> Unit,
    onSetUserDisabled: (String, Boolean) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onApproveRequest: (JellyseerrRequestSummary) -> Unit,
    onDeclineRequest: (JellyseerrRequestSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var createUserVisible by remember { mutableStateOf(false) }
    var restartVisible by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<JellyfinAdminUser?>(null) }
    var passwordUser by remember { mutableStateOf<JellyfinAdminUser?>(null) }
    val pending = pendingRequests.filter { it.requestStatus == JellyseerrRequestStatus.PENDING }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.admin_server), style = MaterialTheme.typography.headlineMedium)
                    state.overview?.let { system ->
                        Text(
                            listOfNotNull(system.serverName, system.version?.let { "v$it" }).joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.admin_refresh))
                    }
                }
            }
        }
        state.notice?.let { notice ->
            item { FeedbackCard(notice.resource(), error = false) }
        }
        state.error?.let { error ->
            item { FeedbackCard(Res.string.admin_error, detail = error, error = true) }
        }
        item {
            Text(stringResource(Res.string.admin_library_stats), style = MaterialTheme.typography.titleLarge)
            val counts = state.overview?.counts
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                item { StatCard(Res.string.admin_stat_movies, counts?.movies ?: 0) }
                item { StatCard(Res.string.admin_stat_series, counts?.series ?: 0) }
                item { StatCard(Res.string.admin_stat_episodes, counts?.episodes ?: 0) }
                item { StatCard(Res.string.admin_stat_albums, counts?.albums ?: 0) }
                item { StatCard(Res.string.admin_stat_songs, counts?.songs ?: 0) }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onLibraryScan,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.admin_library_scan)) }
                OutlinedButton(
                    onClick = { restartVisible = true },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.admin_restart)) }
            }
        }
        if (canManageRequests) {
            item { Text(stringResource(Res.string.admin_pending_requests), style = MaterialTheme.typography.titleLarge) }
            if (pending.isEmpty()) {
                item { Text(stringResource(Res.string.admin_no_pending_requests), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(pending, key = { "request-${it.id}" }) { request ->
                    RequestAdminCard(request, onApproveRequest, onDeclineRequest, !state.isLoading)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.admin_users), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { createUserVisible = true }, enabled = !state.isLoading) {
                    Text(stringResource(Res.string.admin_create_user))
                }
            }
        }
        items(state.users, key = { "user-${it.id}" }) { user ->
            UserAdminCard(
                user = user,
                isCurrentUser = user.id == currentUserId,
                enabled = !state.isLoading,
                onToggleDisabled = { onSetUserDisabled(user.id, !user.isDisabled) },
                onResetPassword = { passwordUser = user },
                onDelete = { userToDelete = user },
            )
        }
        item { Text(stringResource(Res.string.admin_activity), style = MaterialTheme.typography.titleLarge) }
        if (state.activity.isEmpty()) {
            item { Text(stringResource(Res.string.admin_no_activity), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.activity, key = { "activity-${it.id}" }) { activity ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activity.name, style = MaterialTheme.typography.titleMedium)
                        activity.overview?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        activity.date?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (createUserVisible) {
        CredentialDialog(
            title = Res.string.admin_create_user,
            showUsername = true,
            onDismiss = { createUserVisible = false },
            onSave = { name, password ->
                createUserVisible = false
                onCreateUser(name, password)
            },
        )
    }
    passwordUser?.let { user ->
        CredentialDialog(
            title = Res.string.admin_reset_password,
            showUsername = false,
            onDismiss = { passwordUser = null },
            onSave = { _, password ->
                passwordUser = null
                onResetPassword(user.id, password)
            },
        )
    }
    userToDelete?.let { user ->
        ConfirmationDialog(
            title = Res.string.admin_delete_user_title,
            body = Res.string.admin_delete_user_body,
            onDismiss = { userToDelete = null },
            onConfirm = {
                userToDelete = null
                onDeleteUser(user.id)
            },
        )
    }
    if (restartVisible) {
        ConfirmationDialog(
            title = Res.string.admin_restart_title,
            body = Res.string.admin_restart_body,
            onDismiss = { restartVisible = false },
            onConfirm = {
                restartVisible = false
                onRestart()
            },
        )
    }
}

@Composable
private fun FeedbackCard(
    resource: StringResource,
    detail: String? = null,
    error: Boolean,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(stringResource(resource))
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatCard(
    label: StringResource,
    count: Int,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RequestAdminCard(
    request: JellyseerrRequestSummary,
    onApprove: (JellyseerrRequestSummary) -> Unit,
    onDecline: (JellyseerrRequestSummary) -> Unit,
    enabled: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(request.title ?: request.originalTitle ?: "#${request.id}", style = MaterialTheme.typography.titleMedium)
            request.requestedBy?.displayName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onApprove(request) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.admin_approve))
                }
                OutlinedButton(onClick = { onDecline(request) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.admin_decline))
                }
            }
        }
    }
}

@Composable
private fun UserAdminCard(
    user: JellyfinAdminUser,
    isCurrentUser: Boolean,
    enabled: Boolean,
    onToggleDisabled: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(user.name, style = MaterialTheme.typography.titleMedium)
                    if (user.isDisabled) Text(stringResource(Res.string.admin_user_disabled), color = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onToggleDisabled, enabled = enabled && !isCurrentUser) {
                    Text(stringResource(if (user.isDisabled) Res.string.admin_enable else Res.string.admin_disable))
                }
                TextButton(onClick = onResetPassword, enabled = enabled) {
                    Text(stringResource(Res.string.admin_reset_password))
                }
                TextButton(onClick = onDelete, enabled = enabled && !isCurrentUser) {
                    Text(stringResource(Res.string.admin_delete))
                }
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    title: StringResource,
    showUsername: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showUsername) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(Res.string.admin_username)) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.admin_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(username, password) }, enabled = !showUsername || username.isNotBlank()) {
                Text(stringResource(Res.string.admin_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: StringResource,
    body: StringResource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.admin_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) } },
    )
}

private fun JellyfinAdminNotice.resource(): StringResource =
    when (this) {
        JellyfinAdminNotice.LIBRARY_SCAN_STARTED -> Res.string.admin_notice_library_scan
        JellyfinAdminNotice.RESTART_REQUESTED -> Res.string.admin_notice_restart
        JellyfinAdminNotice.USER_CREATED -> Res.string.admin_notice_user_created
        JellyfinAdminNotice.USER_UPDATED -> Res.string.admin_notice_user_updated
        JellyfinAdminNotice.PASSWORD_RESET -> Res.string.admin_notice_password_reset
        JellyfinAdminNotice.USER_DELETED -> Res.string.admin_notice_user_deleted
    }
