package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfileOption
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.all_seasons
import jellystack_mobile.design.generated.resources.close
import jellystack_mobile.design.generated.resources.request_profile_search
import jellystack_mobile.design.generated.resources.request_profile_title
import jellystack_mobile.design.generated.resources.request_season_search
import jellystack_mobile.design.generated.resources.request_seasons_title
import jellystack_mobile.design.generated.resources.season_number
import jellystack_mobile.design.generated.resources.server_default
import jellystack_mobile.design.generated.resources.server_default_supporting
import jellystack_mobile.design.generated.resources.submit_request
import org.jetbrains.compose.resources.stringResource

internal object RequestConfigurationTestTags {
    const val CONTENT = "request_configuration_content"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun RequestConfiguration(
    item: JellyseerrSearchItem,
    profiles: List<JellyseerrLanguageProfileOption>,
    availableSeasons: List<Int>,
    selected: JellyseerrRequestProfileSelection,
    seasonSelection: JellyseerrCreateSelection,
    requestAllAvailableSeasonsExplicitly: Boolean = false,
    isSubmitting: Boolean = false,
    initialFocusModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onSelect: (JellyseerrRequestProfileSelection) -> Unit,
    onSelectSeasons: (JellyseerrCreateSelection) -> Unit,
    onSubmit: (JellyseerrCreateSelection?) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options =
        listOf<JellyseerrRequestProfileSelection>(JellyseerrRequestProfileSelection.ServerDefault) +
            profiles.map(JellyseerrRequestProfileSelection::Profile)
    val sortedSeasons = remember(availableSeasons) { availableSeasons.distinct().sorted() }
    var profileQuery by rememberSaveable(item.mediaType, item.tmdbId) { mutableStateOf("") }
    var seasonQuery by rememberSaveable(item.mediaType, item.tmdbId) { mutableStateOf("") }
    val serverDefaultLabel = stringResource(Res.string.server_default)
    val visibleOptions =
        remember(options, profileQuery, serverDefaultLabel) {
            val query = profileQuery.trim()
            if (query.isEmpty()) {
                options
            } else {
                options.filter { option ->
                    when (option) {
                        JellyseerrRequestProfileSelection.ServerDefault ->
                            serverDefaultLabel.contains(query, ignoreCase = true)
                        is JellyseerrRequestProfileSelection.Profile ->
                            option.option.name.contains(query, ignoreCase = true) ||
                                option.option.serviceName?.contains(query, ignoreCase = true) == true
                    }
                }
            }
        }
    val visibleSeasons =
        remember(sortedSeasons, seasonQuery) {
            val query = seasonQuery.trim()
            if (query.isEmpty()) {
                sortedSeasons
            } else {
                sortedSeasons.filter { it.toString().contains(query) }
            }
        }
    val selectedSeasonNumbers =
        when (val current = seasonSelection) {
            JellyseerrCreateSelection.AllSeasons -> sortedSeasons.toSet()
            is JellyseerrCreateSelection.Seasons -> current.numbers.toSet()
        }
    val allSeasonsSelected = selectedSeasonNumbers == sortedSeasons.toSet()
    val canSubmit =
        item.mediaType != JellyseerrMediaType.TV ||
            seasonSelection == JellyseerrCreateSelection.AllSeasons ||
            selectedSeasonNumbers.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .testTag(RequestConfigurationTestTags.CONTENT),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                Text(stringResource(Res.string.request_profile_title), style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = profileQuery,
                    onValueChange = { profileQuery = it },
                    modifier = initialFocusModifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.request_profile_search)) },
                    singleLine = true,
                )
            }
            items(visibleOptions) { option ->
                val label =
                    when (option) {
                        JellyseerrRequestProfileSelection.ServerDefault -> stringResource(Res.string.server_default)
                        is JellyseerrRequestProfileSelection.Profile -> option.option.name
                    }
                val supportingText =
                    when (option) {
                        JellyseerrRequestProfileSelection.ServerDefault -> stringResource(Res.string.server_default_supporting)
                        is JellyseerrRequestProfileSelection.Profile -> option.option.serviceName
                    }
                RequestProfileRow(
                    label = label,
                    supportingText = supportingText,
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
            if (item.mediaType == JellyseerrMediaType.TV) {
                item {
                    Text(
                        stringResource(Res.string.request_seasons_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = seasonQuery,
                        onValueChange = { seasonQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(Res.string.request_season_search)) },
                        singleLine = true,
                    )
                }
                item {
                    SeasonRow(
                        label = stringResource(Res.string.all_seasons),
                        selected = allSeasonsSelected,
                        onClick = {
                            onSelectSeasons(
                                if (requestAllAvailableSeasonsExplicitly) {
                                    JellyseerrCreateSelection.Seasons(sortedSeasons)
                                } else {
                                    JellyseerrCreateSelection.AllSeasons
                                },
                            )
                        },
                    )
                }
                items(visibleSeasons) { season ->
                    SeasonRow(
                        label = stringResource(Res.string.season_number, season),
                        selected = season in selectedSeasonNumbers,
                        onClick = {
                            val next = selectedSeasonNumbers.toMutableSet()
                            if (!next.add(season)) next.remove(season)
                            onSelectSeasons(
                                if (next == sortedSeasons.toSet() && !requestAllAvailableSeasonsExplicitly) {
                                    JellyseerrCreateSelection.AllSeasons
                                } else {
                                    JellyseerrCreateSelection.Seasons(next.sorted())
                                },
                            )
                        },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.close))
                    }
                    Button(
                        onClick = {
                            onSubmit(seasonSelection.takeIf { item.mediaType == JellyseerrMediaType.TV })
                        },
                        enabled = canSubmit && !isSubmitting,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(Res.string.submit_request))
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestProfileRow(
    label: String,
    supportingText: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, softWrap = true)
            supportingText?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, softWrap = true)
            }
        }
    }
}

@Composable
private fun SeasonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
