package dev.jellystack.design.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinActivityEntry
import dev.jellystack.core.jellyfin.JellyfinAdminCounts
import dev.jellystack.core.jellyfin.JellyfinAdminNotice
import dev.jellystack.core.jellyfin.JellyfinAdminState
import dev.jellystack.core.jellyfin.JellyfinAdminUser
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.navigation.AdminDestination
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.admin_activity
import jellystack_mobile.design.generated.resources.admin_activity_count
import jellystack_mobile.design.generated.resources.admin_activity_log
import jellystack_mobile.design.generated.resources.admin_approve
import jellystack_mobile.design.generated.resources.admin_cancel
import jellystack_mobile.design.generated.resources.admin_create_user
import jellystack_mobile.design.generated.resources.admin_current_user
import jellystack_mobile.design.generated.resources.admin_decline
import jellystack_mobile.design.generated.resources.admin_delete
import jellystack_mobile.design.generated.resources.admin_delete_user_body
import jellystack_mobile.design.generated.resources.admin_delete_user_title
import jellystack_mobile.design.generated.resources.admin_disable
import jellystack_mobile.design.generated.resources.admin_enable
import jellystack_mobile.design.generated.resources.admin_error
import jellystack_mobile.design.generated.resources.admin_last_updated
import jellystack_mobile.design.generated.resources.admin_library_scan
import jellystack_mobile.design.generated.resources.admin_library_stats
import jellystack_mobile.design.generated.resources.admin_manage_users
import jellystack_mobile.design.generated.resources.admin_no_activity
import jellystack_mobile.design.generated.resources.admin_no_pending_requests
import jellystack_mobile.design.generated.resources.admin_not_updated
import jellystack_mobile.design.generated.resources.admin_notice_library_scan
import jellystack_mobile.design.generated.resources.admin_notice_password_reset
import jellystack_mobile.design.generated.resources.admin_notice_restart
import jellystack_mobile.design.generated.resources.admin_notice_user_created
import jellystack_mobile.design.generated.resources.admin_notice_user_deleted
import jellystack_mobile.design.generated.resources.admin_notice_user_updated
import jellystack_mobile.design.generated.resources.admin_online
import jellystack_mobile.design.generated.resources.admin_password
import jellystack_mobile.design.generated.resources.admin_pending_count
import jellystack_mobile.design.generated.resources.admin_pending_requests
import jellystack_mobile.design.generated.resources.admin_refresh
import jellystack_mobile.design.generated.resources.admin_requested_by
import jellystack_mobile.design.generated.resources.admin_reset_password
import jellystack_mobile.design.generated.resources.admin_restart
import jellystack_mobile.design.generated.resources.admin_restart_body
import jellystack_mobile.design.generated.resources.admin_restart_title
import jellystack_mobile.design.generated.resources.admin_save
import jellystack_mobile.design.generated.resources.admin_server
import jellystack_mobile.design.generated.resources.admin_stat_albums
import jellystack_mobile.design.generated.resources.admin_stat_artists
import jellystack_mobile.design.generated.resources.admin_stat_books
import jellystack_mobile.design.generated.resources.admin_stat_episodes
import jellystack_mobile.design.generated.resources.admin_stat_movies
import jellystack_mobile.design.generated.resources.admin_stat_series
import jellystack_mobile.design.generated.resources.admin_stat_songs
import jellystack_mobile.design.generated.resources.admin_total_items
import jellystack_mobile.design.generated.resources.admin_user_actions
import jellystack_mobile.design.generated.resources.admin_user_active
import jellystack_mobile.design.generated.resources.admin_user_administrator
import jellystack_mobile.design.generated.resources.admin_user_count
import jellystack_mobile.design.generated.resources.admin_user_disabled
import jellystack_mobile.design.generated.resources.admin_user_management
import jellystack_mobile.design.generated.resources.admin_username
import jellystack_mobile.design.generated.resources.admin_view_all
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdminScreen(
    state: JellyfinAdminState,
    destination: AdminDestination,
    pendingRequests: List<JellyseerrRequestSummary>,
    canManageRequests: Boolean,
    currentUserId: String?,
    onRefresh: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenActivity: () -> Unit,
    onViewPendingRequests: () -> Unit,
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

    when (destination) {
        AdminDestination.Overview ->
            AdminOverview(
                state = state,
                pendingRequests = pendingRequests,
                canManageRequests = canManageRequests,
                onRefresh = onRefresh,
                onOpenUsers = onOpenUsers,
                onOpenActivity = onOpenActivity,
                onViewPendingRequests = onViewPendingRequests,
                onCreateUser = { createUserVisible = true },
                onLibraryScan = onLibraryScan,
                onRestart = { restartVisible = true },
                onApproveRequest = onApproveRequest,
                onDeclineRequest = onDeclineRequest,
                modifier = modifier,
            )
        AdminDestination.Users ->
            UserManagementScreen(
                users = state.users,
                currentUserId = currentUserId,
                enabled = !state.isLoading,
                onCreateUser = { createUserVisible = true },
                onToggleDisabled = { user -> onSetUserDisabled(user.id, !user.isDisabled) },
                onResetPassword = { passwordUser = it },
                onDelete = { userToDelete = it },
                modifier = modifier,
            )
        AdminDestination.Activity ->
            ActivityLogScreen(
                activity = state.activity,
                modifier = modifier,
            )
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
private fun AdminOverview(
    state: JellyfinAdminState,
    pendingRequests: List<JellyseerrRequestSummary>,
    canManageRequests: Boolean,
    onRefresh: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenActivity: () -> Unit,
    onViewPendingRequests: () -> Unit,
    onCreateUser: () -> Unit,
    onLibraryScan: () -> Unit,
    onRestart: () -> Unit,
    onApproveRequest: (JellyseerrRequestSummary) -> Unit,
    onDeclineRequest: (JellyseerrRequestSummary) -> Unit,
    modifier: Modifier,
) {
    val pending = remember(pendingRequests) { pendingRequests.filter { it.requestStatus == JellyseerrRequestStatus.PENDING } }
    val recentActivity = remember(state.activity) { latestAdminActivity(state.activity) }
    val profile = LocalResponsiveProfile.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = profile.horizontalContentPadding,
                vertical = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ServerOperationsCard(
                state = state,
                onRefresh = onRefresh,
                onLibraryScan = onLibraryScan,
                onRestart = onRestart,
            )
        }
        state.notice?.let { notice -> item { FeedbackCard(notice.resource(), error = false) } }
        state.error?.let { error -> item { FeedbackCard(Res.string.admin_error, detail = error, error = true) } }
        item { LibraryOverviewCard(counts = state.overview?.counts ?: JellyfinAdminCounts()) }
        if (canManageRequests) {
            item {
                PendingRequestsCard(
                    pending = pending,
                    enabled = !state.isLoading,
                    onViewAll = onViewPendingRequests,
                    onApprove = onApproveRequest,
                    onDecline = onDeclineRequest,
                )
            }
        }
        item {
            UserSummaryCard(
                users = state.users,
                onOpenUsers = onOpenUsers,
                onCreateUser = onCreateUser,
                enabled = !state.isLoading,
            )
        }
        item {
            RecentActivityCard(
                activity = recentActivity,
                onViewAll = onOpenActivity,
            )
        }
    }
}

@Composable
private fun ServerOperationsCard(
    state: JellyfinAdminState,
    onRefresh: () -> Unit,
    onLibraryScan: () -> Unit,
    onRestart: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = state.overview?.serverName?.takeIf(String::isNotBlank) ?: stringResource(Res.string.admin_server),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                state.overview
                                    ?.version
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { "v$it" },
                                stringResource(Res.string.admin_online).takeIf { state.overview != null },
                            ).joinToString(" - "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            state.lastRefreshedAt?.let {
                                stringResource(Res.string.admin_last_updated, it.shortLocalTime())
                            } ?: stringResource(Res.string.admin_not_updated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.admin_refresh))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onLibraryScan,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(Res.string.admin_library_scan), maxLines = 2)
                }
                OutlinedButton(
                    onClick = onRestart,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(Res.string.admin_restart), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun LibraryOverviewCard(counts: JellyfinAdminCounts) {
    val stats =
        listOf(
            AdminStat(stringResource(Res.string.admin_stat_movies), counts.movies, MaterialTheme.colorScheme.primary),
            AdminStat(stringResource(Res.string.admin_stat_series), counts.series, MaterialTheme.colorScheme.secondary),
            AdminStat(stringResource(Res.string.admin_stat_episodes), counts.episodes, MaterialTheme.colorScheme.tertiary),
            AdminStat(stringResource(Res.string.admin_stat_albums), counts.albums, MaterialTheme.colorScheme.primaryContainer),
            AdminStat(stringResource(Res.string.admin_stat_songs), counts.songs, MaterialTheme.colorScheme.secondaryContainer),
            AdminStat(stringResource(Res.string.admin_stat_artists), counts.artists, MaterialTheme.colorScheme.tertiaryContainer),
            AdminStat(stringResource(Res.string.admin_stat_books), counts.books, MaterialTheme.colorScheme.outline),
        )
    val total = remember(counts) { counts.totalItems() }
    val totalLabel = stringResource(Res.string.admin_total_items, total)

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(Res.string.admin_library_stats),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 560.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        LibraryDonut(stats, totalLabel)
                        AdminStatsGrid(stats, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        LibraryDonut(stats, totalLabel)
                        AdminStatsGrid(stats, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryDonut(
    stats: List<AdminStat>,
    totalLabel: String,
) {
    val total = stats.sumOf { it.count }.coerceAtLeast(0)
    Box(
        modifier = Modifier.size(150.dp).semantics { contentDescription = totalLabel },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            if (total == 0) {
                drawArc(
                    color = Color.Gray.copy(alpha = 0.28f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                )
            } else {
                var startAngle = -90f
                stats.filter { it.count > 0 }.forEach { stat ->
                    val sweep = 360f * stat.count / total
                    drawArc(
                        color = stat.color,
                        startAngle = startAngle + 1.4f,
                        sweepAngle = (sweep - 2.8f).coerceAtLeast(0.6f),
                        useCenter = false,
                        style = stroke,
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = totalLabel.substringAfter(' ', totalLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AdminStatsGrid(
    stats: List<AdminStat>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStats.forEach { stat ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stat.count.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = stat.color,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stat.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PendingRequestsCard(
    pending: List<JellyseerrRequestSummary>,
    enabled: Boolean,
    onViewAll: () -> Unit,
    onApprove: (JellyseerrRequestSummary) -> Unit,
    onDecline: (JellyseerrRequestSummary) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                title = stringResource(Res.string.admin_pending_requests),
                supportingText = stringResource(Res.string.admin_pending_count, pending.size),
                showViewAll = pending.isNotEmpty(),
                onViewAll = onViewAll,
            )
            if (pending.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.admin_no_pending_requests),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                RequestAdminContent(
                    request = pending.first(),
                    onApprove = onApprove,
                    onDecline = onDecline,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun UserSummaryCard(
    users: List<JellyfinAdminUser>,
    onOpenUsers: () -> Unit,
    onCreateUser: () -> Unit,
    enabled: Boolean,
) {
    Card(
        onClick = onOpenUsers,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.admin_user_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.admin_user_count, users.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(Res.string.admin_manage_users))
            }
            if (users.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    users.take(5).forEach { user -> UserAvatar(user.name) }
                }
            }
            Button(
                onClick = onCreateUser,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.admin_create_user))
            }
        }
    }
}

@Composable
private fun UserAvatar(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.initials(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecentActivityCard(
    activity: List<JellyfinActivityEntry>,
    onViewAll: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = stringResource(Res.string.admin_activity),
                supportingText = stringResource(Res.string.admin_activity_count, activity.size),
                showViewAll = activity.isNotEmpty(),
                onViewAll = onViewAll,
            )
            if (activity.isEmpty()) {
                Text(
                    text = stringResource(Res.string.admin_no_activity),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ActivityList(activity)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    supportingText: String,
    showViewAll: Boolean,
    onViewAll: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showViewAll) {
            TextButton(onClick = onViewAll, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.admin_view_all))
            }
        }
    }
}

@Composable
private fun RequestAdminContent(
    request: JellyseerrRequestSummary,
    onApprove: (JellyseerrRequestSummary) -> Unit,
    onDecline: (JellyseerrRequestSummary) -> Unit,
    enabled: Boolean,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            request.title ?: request.originalTitle ?: "#${request.id}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        request.requestedBy?.displayName?.let {
            Text(
                stringResource(Res.string.admin_requested_by, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onApprove(request) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.admin_approve))
            }
            OutlinedButton(
                onClick = { onDecline(request) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.admin_decline))
            }
        }
    }
}

@Composable
private fun UserManagementScreen(
    users: List<JellyfinAdminUser>,
    currentUserId: String?,
    enabled: Boolean,
    onCreateUser: () -> Unit,
    onToggleDisabled: (JellyfinAdminUser) -> Unit,
    onResetPassword: (JellyfinAdminUser) -> Unit,
    onDelete: (JellyfinAdminUser) -> Unit,
    modifier: Modifier,
) {
    val profile = LocalResponsiveProfile.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = profile.horizontalContentPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.admin_user_management),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.admin_user_count, users.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onCreateUser, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.admin_create_user))
                }
            }
        }
        items(users, key = { it.id }) { user ->
            UserRow(
                user = user,
                isCurrentUser = user.id == currentUserId,
                enabled = enabled,
                onToggleDisabled = { onToggleDisabled(user) },
                onResetPassword = { onResetPassword(user) },
                onDelete = { onDelete(user) },
            )
        }
    }
}

@Composable
private fun UserRow(
    user: JellyfinAdminUser,
    isCurrentUser: Boolean,
    enabled: Boolean,
    onToggleDisabled: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(user.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text =
                        buildList {
                            add(stringResource(if (user.isDisabled) Res.string.admin_user_disabled else Res.string.admin_user_active))
                            if (user.isAdministrator) add(stringResource(Res.string.admin_user_administrator))
                            if (isCurrentUser) add(stringResource(Res.string.admin_current_user))
                        }.joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.isDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.admin_user_actions, user.name),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (user.isDisabled) Res.string.admin_enable else Res.string.admin_disable)) },
                        onClick = {
                            menuExpanded = false
                            onToggleDisabled()
                        },
                        enabled = !isCurrentUser,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.admin_reset_password)) },
                        onClick = {
                            menuExpanded = false
                            onResetPassword()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.admin_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        enabled = !isCurrentUser,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityLogScreen(
    activity: List<JellyfinActivityEntry>,
    modifier: Modifier,
) {
    val profile = LocalResponsiveProfile.current
    val sortedActivity = remember(activity) { activity.sortedByDescending { it.date.orEmpty() } }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = profile.horizontalContentPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.admin_activity_log),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.admin_activity_count, sortedActivity.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sortedActivity.isEmpty()) {
            item { Text(stringResource(Res.string.admin_no_activity), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ActivityList(sortedActivity, Modifier.padding(horizontal = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityList(
    activity: List<JellyfinActivityEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        activity.forEachIndexed { index, entry ->
            ActivityRow(entry)
            if (index < activity.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ActivityRow(entry: JellyfinActivityEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = entry.severityColor().copy(alpha = 0.18f),
            modifier = Modifier.size(12.dp).padding(top = 2.dp),
        ) {}
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            entry.overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.date?.let {
                Text(
                    text = formatActivityTimestamp(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JellyfinActivityEntry.severityColor(): Color =
    when (severity?.lowercase()) {
        "error", "fatal" -> MaterialTheme.colorScheme.error
        "warn", "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
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

internal fun latestAdminActivity(
    activity: List<JellyfinActivityEntry>,
    limit: Int = 5,
): List<JellyfinActivityEntry> = activity.sortedByDescending { it.date.orEmpty() }.take(limit.coerceAtLeast(0))

internal fun JellyfinAdminCounts.totalItems(): Int = movies + series + episodes + albums + songs + artists + books

private fun String.initials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }

private fun Instant.shortLocalTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
}

private fun formatActivityTimestamp(value: String): String =
    runCatching { Instant.parse(value) }
        .map { instant ->
            val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            "${local.dayOfMonth.twoDigits()}.${local.monthNumber.twoDigits()}.${local.year} - " +
                "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
        }.getOrDefault(value)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private data class AdminStat(
    val label: String,
    val count: Int,
    val color: Color,
)

private fun JellyfinAdminNotice.resource(): StringResource =
    when (this) {
        JellyfinAdminNotice.LIBRARY_SCAN_STARTED -> Res.string.admin_notice_library_scan
        JellyfinAdminNotice.RESTART_REQUESTED -> Res.string.admin_notice_restart
        JellyfinAdminNotice.USER_CREATED -> Res.string.admin_notice_user_created
        JellyfinAdminNotice.USER_UPDATED -> Res.string.admin_notice_user_updated
        JellyfinAdminNotice.PASSWORD_RESET -> Res.string.admin_notice_password_reset
        JellyfinAdminNotice.USER_DELETED -> Res.string.admin_notice_user_deleted
    }
