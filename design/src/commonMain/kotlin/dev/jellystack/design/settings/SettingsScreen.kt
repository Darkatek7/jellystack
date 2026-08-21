@file:Suppress("FunctionName")

package dev.jellystack.design.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.core.preferences.ThemeMode
import dev.jellystack.core.privacy.AppPrivacyStatus
import dev.jellystack.core.privacy.RuntimePermissionStatus
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricLockState
import dev.jellystack.core.server.ServerType
import dev.jellystack.design.ShellTestTags
import dev.jellystack.design.navigation.ShellModal
import dev.jellystack.design.navigation.ShellModalOwner
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.active_server
import jellystack_mobile.design.generated.resources.app_lock
import jellystack_mobile.design.generated.resources.app_lock_authentication_unavailable
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.confirm_remove_server
import jellystack_mobile.design.generated.resources.connect_server
import jellystack_mobile.design.generated.resources.privacy_app_lock_body
import jellystack_mobile.design.generated.resources.privacy_app_lock_title
import jellystack_mobile.design.generated.resources.privacy_cast_body
import jellystack_mobile.design.generated.resources.privacy_cast_title
import jellystack_mobile.design.generated.resources.privacy_local_storage_body
import jellystack_mobile.design.generated.resources.privacy_local_storage_title
import jellystack_mobile.design.generated.resources.privacy_network_body
import jellystack_mobile.design.generated.resources.privacy_network_title
import jellystack_mobile.design.generated.resources.privacy_notifications_body
import jellystack_mobile.design.generated.resources.privacy_notifications_title
import jellystack_mobile.design.generated.resources.privacy_open_policy
import jellystack_mobile.design.generated.resources.privacy_open_source
import jellystack_mobile.design.generated.resources.privacy_status_granted
import jellystack_mobile.design.generated.resources.privacy_status_not_applicable
import jellystack_mobile.design.generated.resources.privacy_status_not_granted
import jellystack_mobile.design.generated.resources.privacy_summary
import jellystack_mobile.design.generated.resources.privacy_title
import jellystack_mobile.design.generated.resources.remove_server
import jellystack_mobile.design.generated.resources.run_setup_guide
import jellystack_mobile.design.generated.resources.settings_about
import jellystack_mobile.design.generated.resources.settings_close
import jellystack_mobile.design.generated.resources.settings_connection_attention
import jellystack_mobile.design.generated.resources.settings_connection_ready
import jellystack_mobile.design.generated.resources.settings_connections
import jellystack_mobile.design.generated.resources.settings_edit
import jellystack_mobile.design.generated.resources.settings_help
import jellystack_mobile.design.generated.resources.settings_lock_default
import jellystack_mobile.design.generated.resources.settings_lock_enabled
import jellystack_mobile.design.generated.resources.settings_lock_locked
import jellystack_mobile.design.generated.resources.settings_no_connections
import jellystack_mobile.design.generated.resources.settings_playback
import jellystack_mobile.design.generated.resources.settings_security
import jellystack_mobile.design.generated.resources.settings_segment_commercials
import jellystack_mobile.design.generated.resources.settings_segment_credits
import jellystack_mobile.design.generated.resources.settings_segment_intros
import jellystack_mobile.design.generated.resources.settings_segment_mode_auto
import jellystack_mobile.design.generated.resources.settings_segment_mode_button
import jellystack_mobile.design.generated.resources.settings_segment_mode_off
import jellystack_mobile.design.generated.resources.settings_segment_previews
import jellystack_mobile.design.generated.resources.settings_segment_recaps
import jellystack_mobile.design.generated.resources.settings_segments_explanation
import jellystack_mobile.design.generated.resources.settings_segments_title
import jellystack_mobile.design.generated.resources.settings_title
import jellystack_mobile.design.generated.resources.settings_version
import jellystack_mobile.design.generated.resources.theme_dark
import jellystack_mobile.design.generated.resources.theme_light
import jellystack_mobile.design.generated.resources.theme_system
import jellystack_mobile.design.generated.resources.theme_title
import jellystack_mobile.design.generated.resources.use_server
import jellystack_mobile.design.generated.resources.whats_new
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onShellModalChange: (ShellModalOwner?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<SettingsConnectionUi?>(null) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }
    val dispatchAction: (SettingsAction) -> Unit = { action ->
        if (action == SettingsAction.ClearAllDownloads) {
            confirmClearDownloads = true
        } else {
            onAction(action)
        }
    }
    val closeRemoval = {
        pendingRemoval = null
        onShellModalChange(null)
    }
    val requestRemoval: (SettingsConnectionUi) -> Unit = { connection ->
        pendingRemoval = connection
        onShellModalChange(
            ShellModalOwner(
                modal = ShellModal.ServerRemoval,
                dismiss = closeRemoval,
            ),
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.selectedSection?.let { sectionLabel(it) }
                            ?: stringResource(Res.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.selectedSection == null) {
                                onAction(SettingsAction.Close)
                            } else {
                                onAction(SettingsAction.SelectSection(null))
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_close),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            if (maxWidth >= 720.dp) {
                ExpandedSettings(
                    state = state,
                    onAction = dispatchAction,
                    onRequestRemoval = requestRemoval,
                    onShowPicker = { picker = it },
                )
            } else {
                CompactSettings(
                    state = state,
                    onAction = dispatchAction,
                    onRequestRemoval = requestRemoval,
                    onShowPicker = { picker = it },
                )
            }
        }
    }

    pendingRemoval?.let { connection ->
        AlertDialog(
            onDismissRequest = closeRemoval,
            title = { Text(stringResource(Res.string.confirm_remove_server)) },
            text = { Text("${serviceLabel(connection.type)} • ${connection.name}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeRemoval()
                        onAction(SettingsAction.RemoveConnection(connection.id))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.remove_server))
                }
            },
            dismissButton = {
                TextButton(onClick = closeRemoval) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    picker?.let { activePicker ->
        var pickerQuery by remember(activePicker) { mutableStateOf("") }
        val visibleOptions =
            activePicker.options.filter { option -> option.label.contains(pickerQuery, ignoreCase = true) }
        ModalBottomSheet(onDismissRequest = { picker = null }) {
            Text(
                text = activePicker.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (activePicker.options.size > 8) {
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    label = { Text(l10n("Search options", "Optionen suchen")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            visibleOptions.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                picker = null
                                option.onSelected()
                            }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        option.supportingText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(l10n("Delete all downloads?", "Alle Downloads löschen?")) },
            text = {
                Text(
                    l10n(
                        "Downloaded videos and subtitle files will be removed from this device.",
                        "Heruntergeladene Videos und Untertitel werden von diesem Gerät entfernt.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearDownloads = false
                        onAction(SettingsAction.ClearAllDownloads)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(l10n("Delete all", "Alle löschen"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

private data class SettingsPicker(
    val title: String,
    val options: List<SettingsPickerOption>,
)

private data class SettingsPickerOption(
    val label: String,
    val selected: Boolean,
    val supportingText: String? = null,
    val onSelected: () -> Unit,
)

@Composable
private fun CompactSettings(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onRequestRemoval: (SettingsConnectionUi) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    if (state.selectedSection == null) {
        SettingsHub(state, onAction)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionContent(state.selectedSection, state, onAction, onRequestRemoval, onShowPicker)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExpandedSettings(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onRequestRemoval: (SettingsConnectionUi) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(0.38f)
                    .testTag(ShellTestTags.PRIMARY_PANE)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsHub(state, onAction, embedded = true)
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(0.62f)
                    .testTag(ShellTestTags.SECONDARY_PANE)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            SettingsSectionContent(
                state.selectedSection ?: SettingsSection.Playback,
                state,
                onAction,
                onRequestRemoval,
                onShowPicker,
            )
        }
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onRequestRemoval: (SettingsConnectionUi) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    when (section) {
        SettingsSection.Playback -> PlaybackCard(state, onAction, onShowPicker)
        SettingsSection.AudioSubtitles -> AudioSubtitlesCard(state, onAction, onShowPicker)
        SettingsSection.AppearanceLanguage -> AppearanceCard(state, onAction, onShowPicker)
        SettingsSection.Downloads -> DownloadsCard(state, onAction)
        SettingsSection.Security ->
            SecurityCard(
                capability = state.appLockCapability,
                enabled = state.appLockEnabled,
                lockState = state.appLockState,
                onAction = onAction,
            )
        SettingsSection.Connections -> ConnectionsCard(state.connections, onAction, onRequestRemoval)
        SettingsSection.About -> HelpCard(state.appVersion, state.privacyStatus, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsHub(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    embedded: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val mainSections =
        listOf(
            SettingsSection.Playback,
            SettingsSection.AudioSubtitles,
            SettingsSection.AppearanceLanguage,
            SettingsSection.Downloads,
        )
    val compactSections = listOf(SettingsSection.Security, SettingsSection.Connections, SettingsSection.About)
    val localizedSectionLabels = (mainSections + compactSections).associateWith { sectionLabel(it) }
    val matchesQuery: (SettingsSection) -> Boolean = { section ->
        query.isBlank() ||
            localizedSectionLabels.getValue(section).contains(query, ignoreCase = true) ||
            sectionSearchTerms(section).any { it.contains(query, ignoreCase = true) }
    }
    val visibleMain = mainSections.filter(matchesQuery)
    val visibleCompact = compactSections.filter(matchesQuery)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (embedded) 0.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(l10n("Search settings", "Einstellungen suchen")) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsCard(Icons.Filled.Movie, l10n("Quick settings", "Schnelleinstellungen")) {
            if (state.platformCapabilities.autoplayNextEpisode) {
                SettingSwitchRow(
                    title = l10n("Autoplay next episode", "Nächste Episode automatisch abspielen"),
                    summary = autoplayLabel(state.appSettings.autoplayNextMode),
                    checked = state.appSettings.autoplayNextMode != AutoplayNextMode.OFF,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetAutoplayNextMode(
                                if (it) AutoplayNextMode.COUNTDOWN else AutoplayNextMode.OFF,
                            ),
                        )
                    },
                )
                HorizontalDivider()
            }
            SettingNavigationRow(
                title = l10n("Mobile streaming quality", "Mobile Streamingqualität"),
                value = qualityLabel(state.appSettings.mobileStreamingQuality),
                onClick = { onAction(SettingsAction.SelectSection(SettingsSection.Playback)) },
            )
        }
        if (visibleMain.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                visibleMain.chunked(2).forEach { rowSections ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowSections.forEach { section ->
                            SettingsSectionTile(
                                section = section,
                                onClick = { onAction(SettingsAction.SelectSection(section)) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        if (rowSections.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (visibleCompact.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag(SettingsTestTags.COMPACT_CONTAINER),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    visibleCompact.forEachIndexed { index, section ->
                        if (index > 0) HorizontalDivider()
                        SettingNavigationRow(
                            title = sectionLabel(section),
                            value = null,
                            icon = sectionIcon(section),
                            modifier = Modifier.testTag(SettingsTestTags.compactRow(section)),
                            onClick = { onAction(SettingsAction.SelectSection(section)) },
                        )
                    }
                }
            }
        }
        if (!embedded) Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionTile(
    section: SettingsSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .testTag(SettingsTestTags.sectionCard(section))
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(sectionIcon(section), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(sectionLabel(section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    val appLanguageTitle = l10n("App language", "App-Sprache")
    val spotlightIntervalTitle = l10n("Spotlight interval", "Spotlight-Intervall")
    val secondsLabel = l10n("seconds", "Sekunden")
    SettingsCard(Icons.Filled.Palette, l10n("Appearance & language", "Darstellung & Sprache")) {
        Text(stringResource(Res.string.theme_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { option ->
                val (icon, label) =
                    when (option) {
                        ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness to Res.string.theme_system
                        ThemeMode.LIGHT -> Icons.Filled.LightMode to Res.string.theme_light
                        ThemeMode.DARK -> Icons.Filled.DarkMode to Res.string.theme_dark
                    }
                FilterChip(
                    selected = state.themeMode == option,
                    onClick = { onAction(SettingsAction.SetTheme(option)) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        if (state.platformCapabilities.appLanguageSelection) {
            SettingNavigationRow(
                title = appLanguageTitle,
                value = appLanguageLabel(state.appSettings.appLanguage),
                icon = Icons.Filled.Language,
                onClick = {
                    onShowPicker(
                        SettingsPicker(
                            title = appLanguageTitle,
                            options =
                                AppLanguage.entries.map { language ->
                                    SettingsPickerOption(
                                        label = appLanguageLabel(language),
                                        selected = language == state.appSettings.appLanguage,
                                        onSelected = { onAction(SettingsAction.SetAppLanguage(language)) },
                                    )
                                },
                        ),
                    )
                },
            )
            HorizontalDivider()
        }
        SettingSwitchRow(
            title = l10n("Use server Home layout", "Server-Home-Layout verwenden"),
            summary =
                l10n(
                    "Use Home Sections when the plugin is available",
                    "Home Sections verwenden, wenn das Plugin verfügbar ist",
                ),
            checked = state.appSettings.useServerHomeSections,
            onCheckedChange = { onAction(SettingsAction.SetUseServerHomeSections(it)) },
        )
        HorizontalDivider()
        SettingSwitchRow(
            title = l10n("Auto-cycle spotlight", "Spotlight automatisch wechseln"),
            summary = l10n("Move to the next title automatically", "Automatisch zum nächsten Titel wechseln"),
            checked = state.appSettings.spotlightAutoCycle,
            onCheckedChange = { onAction(SettingsAction.SetSpotlightAutoCycle(it)) },
        )
        SettingNavigationRow(
            title = spotlightIntervalTitle,
            value = "${state.appSettings.spotlightIntervalSeconds} $secondsLabel",
            onClick = {
                onShowPicker(
                    numberPicker(
                        title = spotlightIntervalTitle,
                        values = listOf(6, 8, 10, 15),
                        selected = state.appSettings.spotlightIntervalSeconds,
                        suffix = secondsLabel,
                    ) { onAction(SettingsAction.SetSpotlightIntervalSeconds(it)) },
                )
            },
        )
    }
}

@Composable
private fun PlaybackCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    val rewindTitle = l10n("Rewind interval", "Rücksprung")
    val forwardTitle = l10n("Fast-forward interval", "Vorsprung")
    val secondsLabel = l10n("seconds", "Sekunden")
    val segmentModeLabels =
        mapOf(
            SegmentSkipMode.OFF to stringResource(Res.string.settings_segment_mode_off),
            SegmentSkipMode.SHOW_BUTTON to stringResource(Res.string.settings_segment_mode_button),
            SegmentSkipMode.AUTO_SKIP to stringResource(Res.string.settings_segment_mode_auto),
        )
    val segmentSettings =
        listOf(
            Triple(
                stringResource(Res.string.settings_segment_intros),
                state.appSettings.introSkipMode,
                { mode: SegmentSkipMode -> onAction(SettingsAction.SetIntroSkipMode(mode)) },
            ),
            Triple(
                stringResource(Res.string.settings_segment_recaps),
                state.appSettings.recapSkipMode,
                { mode: SegmentSkipMode -> onAction(SettingsAction.SetRecapSkipMode(mode)) },
            ),
            Triple(
                stringResource(Res.string.settings_segment_credits),
                state.appSettings.outroSkipMode,
                { mode: SegmentSkipMode -> onAction(SettingsAction.SetOutroSkipMode(mode)) },
            ),
            Triple(
                stringResource(Res.string.settings_segment_previews),
                state.appSettings.previewSkipMode,
                { mode: SegmentSkipMode -> onAction(SettingsAction.SetPreviewSkipMode(mode)) },
            ),
            Triple(
                stringResource(Res.string.settings_segment_commercials),
                state.appSettings.commercialSkipMode,
                { mode: SegmentSkipMode -> onAction(SettingsAction.SetCommercialSkipMode(mode)) },
            ),
        )
    SettingsCard(Icons.Filled.Movie, stringResource(Res.string.settings_playback)) {
        QualityRow(l10n("Wi-Fi streaming quality", "WLAN-Streamingqualität"), state.appSettings.wifiStreamingQuality, onShowPicker) {
            onAction(SettingsAction.SetWifiQuality(it))
        }
        HorizontalDivider()
        QualityRow(l10n("Mobile streaming quality", "Mobile Streamingqualität"), state.appSettings.mobileStreamingQuality, onShowPicker) {
            onAction(SettingsAction.SetMobileQuality(it))
        }
        HorizontalDivider()
        if (state.platformCapabilities.autoplayNextEpisode) {
            EnumPickerRow(
                title = l10n("Autoplay next episode", "Nächste Episode automatisch"),
                selectedLabel = autoplayLabel(state.appSettings.autoplayNextMode),
                titleForPicker = l10n("Autoplay next episode", "Nächste Episode automatisch"),
                options = AutoplayNextMode.entries,
                selected = state.appSettings.autoplayNextMode,
                label = ::autoplayLabel,
                onShowPicker = onShowPicker,
            ) { onAction(SettingsAction.SetAutoplayNextMode(it)) }
        }
        HorizontalDivider()
        Text(stringResource(Res.string.settings_segments_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.settings_segments_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        segmentSettings.forEach { (title, selected, onSelected) ->
            EnumPickerRow(
                title = title,
                selectedLabel = segmentModeLabels.getValue(selected),
                titleForPicker = title,
                options = SegmentSkipMode.entries,
                selected = selected,
                label = segmentModeLabels::getValue,
                onShowPicker = onShowPicker,
                onSelected = onSelected,
            )
        }
        HorizontalDivider()
        EnumPickerRow(
            title = l10n("Resume playback", "Wiedergabe fortsetzen"),
            selectedLabel = resumeLabel(state.appSettings.resumeMode),
            titleForPicker = l10n("Resume playback", "Wiedergabe fortsetzen"),
            options = ResumeMode.entries,
            selected = state.appSettings.resumeMode,
            label = ::resumeLabel,
            onShowPicker = onShowPicker,
        ) { onAction(SettingsAction.SetResumeMode(it)) }
        SettingNavigationRow(
            title = rewindTitle,
            value = "${state.appSettings.seekBackSeconds} $secondsLabel",
            onClick = {
                onShowPicker(
                    numberPicker(rewindTitle, listOf(5, 10, 15, 30, 60), state.appSettings.seekBackSeconds, secondsLabel) {
                        onAction(SettingsAction.SetSeekBackSeconds(it))
                    },
                )
            },
        )
        SettingNavigationRow(
            title = forwardTitle,
            value = "${state.appSettings.seekForwardSeconds} $secondsLabel",
            onClick = {
                onShowPicker(
                    numberPicker(forwardTitle, listOf(5, 10, 15, 30, 60), state.appSettings.seekForwardSeconds, secondsLabel) {
                        onAction(SettingsAction.SetSeekForwardSeconds(it))
                    },
                )
            },
        )
    }
}

@Composable
private fun AudioSubtitlesCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onShowPicker: (SettingsPicker) -> Unit,
) {
    SettingsCard(Icons.Filled.Subtitles, l10n("Audio & subtitles", "Audio & Untertitel")) {
        LanguageRow(l10n("Preferred audio language", "Bevorzugte Audiosprache"), state.appSettings.preferredAudioLanguage, onShowPicker) {
            onAction(SettingsAction.SetPreferredAudioLanguage(it))
        }
        LanguageRow(
            l10n("Preferred subtitle language", "Bevorzugte Untertitelsprache"),
            state.appSettings.preferredSubtitleLanguage,
            onShowPicker,
        ) {
            onAction(SettingsAction.SetPreferredSubtitleLanguage(it))
        }
        EnumPickerRow(
            title = l10n("Subtitle mode", "Untertitelmodus"),
            selectedLabel = subtitleModeLabel(state.appSettings.subtitleMode),
            titleForPicker = l10n("Subtitle mode", "Untertitelmodus"),
            options = SubtitleMode.entries,
            selected = state.appSettings.subtitleMode,
            label = ::subtitleModeLabel,
            onShowPicker = onShowPicker,
        ) { onAction(SettingsAction.SetSubtitleMode(it)) }
        SettingSwitchRow(
            title = l10n("Remember choices per series", "Auswahl pro Serie merken"),
            summary = l10n("Reuse selected audio and subtitles for later episodes", "Audio und Untertitel für weitere Episoden übernehmen"),
            checked = state.appSettings.rememberSeriesTracks,
            onCheckedChange = { onAction(SettingsAction.SetRememberSeriesTracks(it)) },
        )
        OutlinedButton(onClick = { onAction(SettingsAction.ClearRememberedTracks) }) {
            Text(l10n("Clear remembered choices", "Gemerkte Auswahl löschen"))
        }
        if (state.platformCapabilities.subtitleAppearance) {
            Text(l10n("Subtitle appearance", "Untertiteldarstellung"), style = MaterialTheme.typography.titleMedium)
            SegmentedOptions(
                options = SubtitleTextSize.entries,
                selected = state.appSettings.subtitleTextSize,
                label = ::subtitleSizeLabel,
            ) { onAction(SettingsAction.SetSubtitleTextSize(it)) }
            SegmentedOptions(
                options = SubtitleBackground.entries,
                selected = state.appSettings.subtitleBackground,
                label = ::subtitleBackgroundLabel,
            ) { onAction(SettingsAction.SetSubtitleBackground(it)) }
        }
    }
}

@Composable
private fun DownloadsCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsCard(Icons.Filled.Download, l10n("Downloads", "Downloads")) {
        if (state.platformCapabilities.meteredDownloadPolicy) {
            SettingSwitchRow(
                title = l10n("Download only on Wi-Fi", "Nur über WLAN herunterladen"),
                summary =
                    l10n(
                        "Downloads wait and continue automatically on an unmetered network",
                        "Downloads warten und werden im WLAN automatisch fortgesetzt",
                    ),
                checked = state.appSettings.downloadsWifiOnly,
                onCheckedChange = { onAction(SettingsAction.SetDownloadsWifiOnly(it)) },
            )
        }
        SettingValueRow(title = l10n("Download quality", "Downloadqualität"), value = "Original", icon = Icons.Filled.Storage)
        Text(
            "${state.downloadCount} items · ${formatBytes(state.downloadedBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onAction(SettingsAction.ClearAllDownloads) },
            enabled = state.downloadCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(l10n("Delete all downloads", "Alle Downloads löschen"))
        }
    }
}

@Composable
private fun SecurityCard(
    capability: BiometricCapability,
    enabled: Boolean,
    lockState: BiometricLockState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsCard(Icons.Filled.Lock, stringResource(Res.string.settings_security)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.app_lock), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = securityDescription(capability, enabled, lockState),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = enabled,
                enabled = enabled || capability.secureCredentialAvailable,
                onCheckedChange = { onAction(SettingsAction.SetAppLock(it)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionsCard(
    connections: List<SettingsConnectionUi>,
    onAction: (SettingsAction) -> Unit,
    onRequestRemoval: (SettingsConnectionUi) -> Unit,
) {
    SettingsCard(Icons.Filled.Devices, stringResource(Res.string.settings_connections)) {
        if (connections.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_no_connections),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        listOf(ServerType.JELLYFIN, ServerType.JELLYSEERR).forEachIndexed { index, type ->
            if (index > 0) HorizontalDivider()
            Text(serviceLabel(type), style = MaterialTheme.typography.titleMedium)
            connections.filter { it.type == type }.forEach { connection ->
                ConnectionCard(connection, onAction, onRequestRemoval)
            }
            OutlinedButton(
                onClick = { onAction(SettingsAction.AddConnection(type)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${stringResource(Res.string.connect_server)} ${serviceLabel(type)}")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(
    connection: SettingsConnectionUi,
    onAction: (SettingsAction) -> Unit,
    onRequestRemoval: (SettingsConnectionUi) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color =
            if (connection.isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(connection.name, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ready = connection.health == SettingsConnectionHealth.Ready
                Icon(
                    imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (ready) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
                Text(
                    text =
                        if (ready) {
                            stringResource(Res.string.settings_connection_ready)
                        } else {
                            stringResource(Res.string.settings_connection_attention)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (connection.isActive) {
                    Text(
                        text = "• ${stringResource(Res.string.active_server)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connection.isActive) {
                    TextButton(onClick = { onAction(SettingsAction.ActivateConnection(connection.id)) }) {
                        Text(stringResource(Res.string.use_server))
                    }
                }
                TextButton(onClick = { onAction(SettingsAction.EditConnection(connection.id)) }) {
                    Text(stringResource(Res.string.settings_edit))
                }
                TextButton(onClick = { onRequestRemoval(connection) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.remove_server))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpCard(
    appVersion: String,
    privacyStatus: AppPrivacyStatus,
    onAction: (SettingsAction) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsCard(Icons.AutoMirrored.Filled.HelpOutline, stringResource(Res.string.settings_help)) {
        Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.settings_version, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction(SettingsAction.RunSetup) }) {
                Text(stringResource(Res.string.run_setup_guide))
            }
            OutlinedButton(onClick = { onAction(SettingsAction.ShowWhatsNew) }) {
                Text(stringResource(Res.string.whats_new))
            }
        }
    }
    SettingsCard(Icons.Filled.Lock, stringResource(Res.string.privacy_title)) {
        Text(
            text = stringResource(Res.string.privacy_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionExplanationRow(
            icon = Icons.Filled.Language,
            title = stringResource(Res.string.privacy_network_title),
            body = stringResource(Res.string.privacy_network_body),
        )
        PermissionExplanationRow(
            icon = Icons.Filled.Devices,
            title = stringResource(Res.string.privacy_cast_title),
            body = stringResource(Res.string.privacy_cast_body),
            status =
                when {
                    privacyStatus.nearbyDevices != RuntimePermissionStatus.NotApplicable ->
                        privacyStatus.nearbyDevices
                    else -> privacyStatus.legacyLocation
                },
        )
        PermissionExplanationRow(
            icon = Icons.Filled.Info,
            title = stringResource(Res.string.privacy_notifications_title),
            body = stringResource(Res.string.privacy_notifications_body),
            status = privacyStatus.notifications,
        )
        PermissionExplanationRow(
            icon = Icons.Filled.Storage,
            title = stringResource(Res.string.privacy_local_storage_title),
            body = stringResource(Res.string.privacy_local_storage_body),
        )
        PermissionExplanationRow(
            icon = Icons.Filled.Lock,
            title = stringResource(Res.string.privacy_app_lock_title),
            body = stringResource(Res.string.privacy_app_lock_body),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { uriHandler.openUri(SOURCE_REPOSITORY_URL) }) {
                Text(stringResource(Res.string.privacy_open_source))
            }
            OutlinedButton(
                onClick = {
                    val privacyUrl =
                        if (Locale.current.language == "de") {
                            PRIVACY_POLICY_DE_URL
                        } else {
                            PRIVACY_POLICY_URL
                        }
                    uriHandler.openUri(privacyUrl)
                },
            ) {
                Text(stringResource(Res.string.privacy_open_policy))
            }
        }
    }
}

@Composable
private fun PermissionExplanationRow(
    icon: ImageVector,
    title: String,
    body: String,
    status: RuntimePermissionStatus? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let {
                Text(
                    text =
                        stringResource(
                            when (it) {
                                RuntimePermissionStatus.Granted -> Res.string.privacy_status_granted
                                RuntimePermissionStatus.NotGranted -> Res.string.privacy_status_not_granted
                                RuntimePermissionStatus.NotApplicable -> Res.string.privacy_status_not_applicable
                            },
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (it == RuntimePermissionStatus.Granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

private const val SOURCE_REPOSITORY_URL = "https://github.com/Darkatek7/jellystack"
private const val PRIVACY_POLICY_URL = "https://github.com/Darkatek7/jellystack/blob/main/docs/privacy.md"
private const val PRIVACY_POLICY_DE_URL = "https://github.com/Darkatek7/jellystack/blob/main/privacy-policy-de"

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingNavigationRow(
    title: String,
    value: String?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            value?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal object SettingsTestTags {
    const val COMPACT_CONTAINER = "settings-compact-container"

    fun sectionCard(section: SettingsSection): String = "settings-section-${section.name}"

    fun compactRow(section: SettingsSection): String = "settings-compact-row-${section.name}"
}

@Composable
private fun SettingValueRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QualityRow(
    title: String,
    selected: StreamingQualityPreference,
    onShowPicker: (SettingsPicker) -> Unit,
    onSelected: (StreamingQualityPreference) -> Unit,
) {
    SettingNavigationRow(title = title, value = qualityLabel(selected)) {
        onShowPicker(
            SettingsPicker(
                title = title,
                options =
                    StreamingQualityPreference.entries.map { quality ->
                        SettingsPickerOption(
                            label = qualityLabel(quality),
                            selected = quality == selected,
                            supportingText =
                                if (quality ==
                                    StreamingQualityPreference.AUTO
                                ) {
                                    "Jellyfin chooses the best available stream"
                                } else {
                                    null
                                },
                            onSelected = { onSelected(quality) },
                        )
                    },
            ),
        )
    }
}

@Composable
private fun LanguageRow(
    title: String,
    selected: String?,
    onShowPicker: (SettingsPicker) -> Unit,
    onSelected: (String?) -> Unit,
) {
    val deviceLanguage = Locale.current.language.lowercase()
    val languages =
        listOf(
            null to "Server default",
            deviceLanguage to "Device language",
            "en" to "English",
            "de" to "Deutsch",
            "es" to "Español",
            "fr" to "Français",
            "it" to "Italiano",
            "ja" to "日本語",
            "ko" to "한국어",
            "pt" to "Português",
            "zh" to "中文",
        )
    SettingNavigationRow(title = title, value = languages.firstOrNull { it.first == selected }?.second ?: selected.orEmpty()) {
        onShowPicker(
            SettingsPicker(
                title = title,
                options =
                    languages.map { (code, label) ->
                        SettingsPickerOption(
                            label = label,
                            selected = code == selected,
                            onSelected = { onSelected(code) },
                        )
                    },
            ),
        )
    }
}

@Composable
private fun <T> EnumPickerRow(
    title: String,
    selectedLabel: String,
    titleForPicker: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onShowPicker: (SettingsPicker) -> Unit,
    onSelected: (T) -> Unit,
) {
    SettingNavigationRow(title = title, value = selectedLabel) {
        onShowPicker(
            SettingsPicker(
                title = titleForPicker,
                options = options.map { option -> SettingsPickerOption(label(option), option == selected) { onSelected(option) } },
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SegmentedOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelected(option) }, label = { Text(label(option)) })
        }
    }
}

private fun numberPicker(
    title: String,
    values: List<Int>,
    selected: Int,
    suffix: String,
    onSelected: (Int) -> Unit,
): SettingsPicker =
    SettingsPicker(
        title = title,
        options = values.map { value -> SettingsPickerOption("$value $suffix", value == selected) { onSelected(value) } },
    )

private fun qualityLabel(quality: StreamingQualityPreference): String =
    if (quality == StreamingQualityPreference.AUTO) {
        "Automatic"
    } else {
        val bitrate = quality.maxBitrate ?: 0
        val speed = if (bitrate >= 1_000_000) "${bitrate / 1_000_000} Mbps" else "${bitrate / 1_000} Kbps"
        "$speed · ${quality.maxHeight}p"
    }

private fun autoplayLabel(mode: AutoplayNextMode): String =
    when (mode) {
        AutoplayNextMode.OFF -> "Off"
        AutoplayNextMode.COUNTDOWN -> "10-second countdown"
        AutoplayNextMode.IMMEDIATE -> "Immediately"
    }

private fun resumeLabel(mode: ResumeMode): String =
    when (mode) {
        ResumeMode.RESUME -> "Resume automatically"
        ResumeMode.ASK -> "Always ask"
        ResumeMode.RESTART -> "Start from beginning"
    }

private fun subtitleModeLabel(mode: SubtitleMode): String =
    when (mode) {
        SubtitleMode.SERVER_DEFAULT -> "Server default"
        SubtitleMode.OFF -> "Off"
        SubtitleMode.FORCED_ONLY -> "Forced only"
        SubtitleMode.PREFERRED_ALWAYS -> "Preferred language"
        SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS -> "When audio language differs"
    }

private fun appLanguageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SYSTEM -> "System default"
        AppLanguage.ENGLISH -> "English"
        AppLanguage.GERMAN -> "Deutsch"
    }

private fun subtitleSizeLabel(size: SubtitleTextSize): String =
    when (size) {
        SubtitleTextSize.SYSTEM -> "System"
        SubtitleTextSize.SMALL -> "Small"
        SubtitleTextSize.MEDIUM -> "Medium"
        SubtitleTextSize.LARGE -> "Large"
    }

private fun subtitleBackgroundLabel(background: SubtitleBackground): String =
    when (background) {
        SubtitleBackground.SYSTEM -> "System"
        SubtitleBackground.NONE -> "None"
        SubtitleBackground.TRANSLUCENT -> "Translucent"
        SubtitleBackground.DARK -> "Dark"
    }

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "${bytes / 1_073_741_824L} GB"
        bytes >= 1_048_576L -> "${bytes / 1_048_576L} MB"
        bytes >= 1_024L -> "${bytes / 1_024L} KB"
        else -> "$bytes B"
    }

@Composable
private fun securityDescription(
    capability: BiometricCapability,
    enabled: Boolean,
    lockState: BiometricLockState,
): String =
    when {
        enabled && lockState is BiometricLockState.Unlocked ->
            stringResource(Res.string.settings_lock_enabled)
        enabled && lockState is BiometricLockState.Locked ->
            stringResource(Res.string.settings_lock_locked)
        enabled && lockState is BiometricLockState.Error ->
            lockState.reason.ifBlank { stringResource(Res.string.app_lock_authentication_unavailable) }
        else -> capability.description ?: stringResource(Res.string.settings_lock_default)
    }

@Composable
private fun sectionLabel(section: SettingsSection): String =
    when (section) {
        SettingsSection.Playback -> stringResource(Res.string.settings_playback)
        SettingsSection.AudioSubtitles -> l10n("Audio & subtitles", "Audio & Untertitel")
        SettingsSection.AppearanceLanguage -> l10n("Appearance", "Darstellung")
        SettingsSection.Downloads -> "Downloads"
        SettingsSection.Security -> stringResource(Res.string.settings_security)
        SettingsSection.Connections -> stringResource(Res.string.settings_connections)
        SettingsSection.About -> stringResource(Res.string.settings_about)
    }

@Composable
private fun l10n(
    english: String,
    german: String,
): String = if (Locale.current.language == "de") german else english

private fun sectionIcon(section: SettingsSection): ImageVector =
    when (section) {
        SettingsSection.Playback -> Icons.Filled.Movie
        SettingsSection.AudioSubtitles -> Icons.Filled.Subtitles
        SettingsSection.AppearanceLanguage -> Icons.Filled.Palette
        SettingsSection.Downloads -> Icons.Filled.Download
        SettingsSection.Security -> Icons.Filled.Lock
        SettingsSection.Connections -> Icons.Filled.Devices
        SettingsSection.About -> Icons.Filled.Info
    }

private fun sectionSearchTerms(section: SettingsSection): List<String> =
    when (section) {
        SettingsSection.Playback ->
            listOf(
                "quality",
                "streaming",
                "autoplay",
                "resume",
                "seek",
                "segments",
                "intro",
                "credits",
                "Qualität",
                "Fortsetzen",
                "Sprung",
                "Segmente",
                "Abspann",
            )
        SettingsSection.AudioSubtitles -> listOf("audio", "subtitle", "language", "Untertitel", "Sprache")
        SettingsSection.AppearanceLanguage ->
            listOf(
                "theme",
                "light",
                "dark",
                "spotlight",
                "language",
                "Design",
                "Hell",
                "Dunkel",
                "Sprache",
            )
        SettingsSection.Downloads -> listOf("wifi", "storage", "quality", "WLAN", "Speicher", "Qualität")
        SettingsSection.Security -> listOf("lock", "biometric", "Sperre", "Biometrie")
        SettingsSection.Connections -> listOf("server", "Jellyfin", "Seerr", "Verbindung")
        SettingsSection.About -> listOf("version", "setup", "what's new", "Version", "Neu")
    }

private fun serviceLabel(type: ServerType): String =
    when (type) {
        ServerType.JELLYFIN -> "Jellyfin"
        ServerType.JELLYSEERR -> "Seerr"
        ServerType.SONARR -> "Sonarr"
        ServerType.RADARR -> "Radarr"
    }
