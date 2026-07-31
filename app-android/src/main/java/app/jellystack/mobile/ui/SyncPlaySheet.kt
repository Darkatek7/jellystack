package app.jellystack.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jellystack.mobile.R
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import dev.jellystack.players.syncplay.SyncPlayUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncPlaySheet(
    state: SyncPlayUiState,
    coordinator: SyncPlayCoordinator,
    canCreate: Boolean,
    canJoin: Boolean,
    onDismiss: () -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { coordinator.refresh() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.syncplay_title), style = MaterialTheme.typography.headlineSmall)
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            val current = state.currentGroup
            if (current != null) {
                Text(current.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.syncplay_participants, current.participants.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = coordinator::setCurrentPlaybackAsQueue) {
                        Text(stringResource(R.string.syncplay_share_current))
                    }
                    TextButton(onClick = coordinator::leaveGroup) {
                        Text(stringResource(R.string.syncplay_leave))
                    }
                }
                if (state.playlistItemId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(onClick = coordinator::requestPrevious) {
                            Text(stringResource(R.string.syncplay_previous))
                        }
                        TextButton(onClick = coordinator::requestNext) {
                            Text(stringResource(R.string.syncplay_next))
                        }
                    }
                }
            } else {
                if (canCreate) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it.take(80) },
                        label = { Text(stringResource(R.string.syncplay_group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            coordinator.createGroup(groupName)
                            groupName = ""
                        },
                        enabled = groupName.isNotBlank() && !state.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.syncplay_create))
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.syncplay_available_groups), style = MaterialTheme.typography.titleMedium)
                if (!canJoin) {
                    Text(stringResource(R.string.syncplay_no_permission))
                } else if (state.groups.isEmpty() && !state.loading) {
                    Text(stringResource(R.string.syncplay_no_groups), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(state.groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.syncplay_participants, group.participants.size),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { coordinator.joinGroup(group) }) {
                                    Text(stringResource(R.string.syncplay_join))
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.player_close))
            }
        }
    }
}
