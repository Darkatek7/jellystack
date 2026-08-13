@file:Suppress(
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.SeerrConnectionResult
import dev.jellystack.core.server.SeerrLoginCredentials
import dev.jellystack.core.server.SeerrServerInput
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import kotlinx.coroutines.launch

@Composable
internal fun TvSettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    serverRepository: ServerRepository,
    connectionCoordinator: ServerConnectionCoordinator,
    quickConnectCoordinator: JellyfinQuickConnectCoordinator,
    appVersion: String,
    strings: TvStrings,
    onServersChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    val jellyfinServer = servers.firstOrNull { it.type == dev.jellystack.core.server.ServerType.JELLYFIN }
    val seerrServer = servers.firstOrNull { it.type == dev.jellystack.core.server.ServerType.JELLYSEERR }
    var showJellyfinConnect by remember { mutableStateOf(false) }
    var showSeerrConnect by remember { mutableStateOf(false) }
    var choiceDialog by remember { mutableStateOf<TvChoiceDialogState?>(null) }

    fun showChoices(
        title: String,
        options: List<TvChoiceOption>,
    ) {
        choiceDialog = TvChoiceDialogState(title, options)
    }
    if (showJellyfinConnect) {
        TvConnectionScreen(
            coordinator = connectionCoordinator,
            quickConnectCoordinator = quickConnectCoordinator,
            appVersion = appVersion,
            strings = strings,
            onConnected = {
                showJellyfinConnect = false
                onServersChanged()
            },
            onDismiss = { showJellyfinConnect = false },
            existingServerId = jellyfinServer?.id,
            initialDisplayName = jellyfinServer?.name ?: "Jellyfin",
            initialBaseUrl = jellyfinServer?.baseUrl.orEmpty(),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Text(strings.settings, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
        item { TvSectionTitle(strings.appearancePlayback) }
        item {
            TvSettingsGrid {
                TvSettingTile(
                    strings.language,
                    settings.appLanguage.label(strings),
                    focusToNavigationRailOnLeft = true,
                    screenEntry = true,
                ) {
                    showChoices(
                        strings.language,
                        AppLanguage.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.appLanguage) {
                                repository.setAppLanguage(value)
                            }
                        },
                    )
                }
                TvSettingTile(strings.quality, settings.wifiStreamingQuality.label(strings)) {
                    showChoices(
                        strings.quality,
                        tvQualityOptions().map { value ->
                            TvChoiceOption(value.label(strings), value == settings.wifiStreamingQuality) {
                                repository.setWifiStreamingQuality(value)
                            }
                        },
                    )
                }
                TvSettingTile(strings.audioLanguage, settings.preferredAudioLanguage ?: strings.serverDefault) {
                    showChoices(
                        strings.audioLanguage,
                        preferredLanguageOptions(strings, settings.preferredAudioLanguage) {
                            repository.setPreferredAudioLanguage(it)
                        },
                    )
                }
                TvSettingTile(strings.subtitleLanguage, settings.preferredSubtitleLanguage ?: strings.serverDefault) {
                    showChoices(
                        strings.subtitleLanguage,
                        preferredLanguageOptions(strings, settings.preferredSubtitleLanguage) {
                            repository.setPreferredSubtitleLanguage(it)
                        },
                    )
                }
                TvSettingTile(strings.subtitleMode, settings.subtitleMode.label(strings)) {
                    showChoices(
                        strings.subtitleMode,
                        SubtitleMode.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.subtitleMode) { repository.setSubtitleMode(value) }
                        },
                    )
                }
                TvSettingTile(strings.subtitleSize, settings.subtitleTextSize.label(strings)) {
                    showChoices(
                        strings.subtitleSize,
                        SubtitleTextSize.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.subtitleTextSize) {
                                repository.setSubtitleTextSize(value)
                            }
                        },
                    )
                }
                TvSettingTile(strings.subtitleBackground, settings.subtitleBackground.label(strings)) {
                    showChoices(
                        strings.subtitleBackground,
                        SubtitleBackground.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.subtitleBackground) {
                                repository.setSubtitleBackground(value)
                            }
                        },
                    )
                }
                TvSettingTile(strings.autoplay, settings.autoplayNextMode.label(strings)) {
                    showChoices(
                        strings.autoplay,
                        AutoplayNextMode.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.autoplayNextMode) {
                                repository.setAutoplayNextMode(value)
                            }
                        },
                    )
                }
                TvSettingTile(strings.resume, settings.resumeMode.label(strings)) {
                    showChoices(
                        strings.resume,
                        ResumeMode.entries.map { value ->
                            TvChoiceOption(value.label(strings), value == settings.resumeMode) { repository.setResumeMode(value) }
                        },
                    )
                }
                TvSettingTile(strings.seekBack, "${settings.seekBackSeconds}s") {
                    showChoices(
                        strings.seekBack,
                        tvSeekOptions().map { value ->
                            TvChoiceOption("${value}s", value == settings.seekBackSeconds) { repository.setSeekBackSeconds(value) }
                        },
                    )
                }
                TvSettingTile(strings.seekForward, "${settings.seekForwardSeconds}s") {
                    showChoices(
                        strings.seekForward,
                        tvSeekOptions().map { value ->
                            TvChoiceOption("${value}s", value == settings.seekForwardSeconds) { repository.setSeekForwardSeconds(value) }
                        },
                    )
                }
                TvSettingTile(strings.playbackSpeed, "${settings.defaultPlaybackSpeed}x") {
                    showChoices(
                        strings.playbackSpeed,
                        tvSpeedOptions().map { value ->
                            TvChoiceOption("${value}x", value == settings.defaultPlaybackSpeed) {
                                repository.setDefaultPlaybackSpeed(value)
                            }
                        },
                    )
                }
                TvSettingTile(
                    strings.statsForNerds,
                    if (settings.statsForNerdsEnabled) strings.on else strings.off,
                ) {
                    repository.setStatsForNerdsEnabled(!settings.statsForNerdsEnabled)
                }
                TvSettingTile(
                    strings.homeSections,
                    if (settings.useServerHomeSections) strings.on else strings.off,
                ) {
                    repository.setUseServerHomeSections(!settings.useServerHomeSections)
                }
                TvSettingTile(
                    strings.trailerPreviews,
                    if (settings.trailerPreviewsEnabled) strings.on else strings.off,
                ) {
                    repository.setTrailerPreviewsEnabled(!settings.trailerPreviewsEnabled)
                }
                TvSettingTile(
                    strings.trailerPreviewSound,
                    if (settings.trailerPreviewSoundEnabled) strings.on else strings.off,
                    enabled = settings.trailerPreviewsEnabled,
                ) {
                    repository.setTrailerPreviewSoundEnabled(!settings.trailerPreviewSoundEnabled)
                }
            }
        }
        item { TvSectionTitle(strings.connections) }
        item {
            Column(
                Modifier.fillMaxWidth().background(TvSurface, RoundedCornerShape(22.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                servers.forEach { server ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = TvText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            Text("${server.type.name.label()}  •  ${strings.connected}", color = TvTextMuted)
                        }
                        TvActionButton(strings.remove, {
                            scope.launch {
                                serverRepository.remove(server.id)
                                onServersChanged()
                            }
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvActionButton(
                        if (jellyfinServer == null) strings.addJellyfin else strings.manageJellyfin,
                        { showJellyfinConnect = true },
                        primary = jellyfinServer == null,
                    )
                    TvActionButton(
                        if (seerrServer == null) strings.addSeerr else strings.manageSeerr,
                        { showSeerrConnect = true },
                        primary = seerrServer == null,
                    )
                }
            }
        }
        item { Text("Jellystack TV $appVersion", color = TvTextMuted) }
        item { Spacer(Modifier.height(40.dp)) }
    }
    if (showSeerrConnect) {
        TvSeerrConnectDialog(
            coordinator = connectionCoordinator,
            appVersion = appVersion,
            strings = strings,
            existingServerId = seerrServer?.id,
            initialUrl = seerrServer?.baseUrl.orEmpty(),
            onDismiss = { showSeerrConnect = false },
            onConnected = {
                showSeerrConnect = false
                onServersChanged()
            },
        )
    }
    choiceDialog?.let { dialog ->
        TvChoiceDialog(dialog, cancelLabel = strings.cancel, onDismiss = { choiceDialog = null }) { option ->
            option.onSelect()
            choiceDialog = null
        }
    }
}

private data class TvChoiceOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

private data class TvChoiceDialogState(
    val title: String,
    val options: List<TvChoiceOption>,
)

@Composable
private fun TvChoiceDialog(
    state: TvChoiceDialogState,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onSelect: (TvChoiceOption) -> Unit,
) {
    val initialFocus = remember(state.title) { FocusRequester() }
    val initialIndex = state.options.indexOfFirst { it.selected }.coerceAtLeast(0)
    LaunchedEffect(state) { initialFocus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .heightIn(max = 650.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(26.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.title, color = TvText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(state.options) { index, option ->
                    TvActionButton(
                        option.label,
                        { onSelect(option) },
                        modifier = if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier,
                        primary = option.selected,
                    )
                }
            }
            TvActionButton(cancelLabel, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvSettingsGrid(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 3,
        content = content,
    )
}

@Composable
private fun TvSettingTile(
    title: String,
    value: String,
    focusToNavigationRailOnLeft: Boolean = false,
    screenEntry: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .tvScreenEntryFocus(screenEntry)
            .width(330.dp)
            .height(112.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .background(TvSurface, RoundedCornerShape(20.dp))
            .tvFocusable(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
            ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, color = TvText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TvPurple, fontSize = 16.sp)
    }
}

@Composable
private fun TvSeerrConnectDialog(
    coordinator: ServerConnectionCoordinator,
    appVersion: String,
    strings: TvStrings,
    existingServerId: String?,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember(existingServerId, initialUrl) { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useJellyfin by remember { mutableStateOf(true) }
    var needsCredentials by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val urlFocus = remember { FocusRequester() }
    val jellyfinMethodFocus = remember { FocusRequester() }
    val seerrMethodFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(strings.connectSeerr, color = TvText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                url,
                { url = it },
                label = { Text(strings.serverUrl) },
                singleLine = true,
                colors = tvOutlinedTextFieldColors(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(urlFocus)
                        .focusProperties {
                            down = if (needsCredentials) jellyfinMethodFocus else connectFocus
                        },
            )
            if (needsCredentials) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        strings.jellyfinAccount,
                        { useJellyfin = true },
                        modifier =
                            Modifier
                                .focusRequester(jellyfinMethodFocus)
                                .focusProperties {
                                    right = seerrMethodFocus
                                    down = usernameFocus
                                },
                        primary = useJellyfin,
                    )
                    TvActionButton(
                        strings.seerrAccount,
                        { useJellyfin = false },
                        modifier =
                            Modifier
                                .focusRequester(seerrMethodFocus)
                                .focusProperties {
                                    left = jellyfinMethodFocus
                                    down = usernameFocus
                                },
                        primary = !useJellyfin,
                    )
                }
                OutlinedTextField(
                    username,
                    { username = it },
                    label = { Text(if (useJellyfin) strings.username else strings.email) },
                    singleLine = true,
                    colors = tvOutlinedTextFieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocus)
                            .focusProperties {
                                up = if (useJellyfin) jellyfinMethodFocus else seerrMethodFocus
                                down = passwordFocus
                            },
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(strings.password) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = tvOutlinedTextFieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus)
                            .focusProperties {
                                up = usernameFocus
                                down = connectFocus
                            },
                )
            }
            error?.let { Text(it, color = Color(0xFFFFA59E)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    strings.connect,
                    primary = true,
                    modifier = Modifier.focusRequester(connectFocus),
                    onClick = {
                        scope.launch {
                            val input =
                                SeerrServerInput(
                                    name = "Seerr",
                                    baseUrl = url,
                                    serverId = existingServerId,
                                    appVersion = appVersion,
                                )
                            if (!needsCredentials) {
                                when (val result = coordinator.connectSeerrAutomatically(input)) {
                                    is SeerrConnectionResult.Connected -> onConnected()
                                    is SeerrConnectionResult.CredentialsRequired -> {
                                        needsCredentials = true
                                        username = result.suggestedUsername.orEmpty()
                                        error = result.reason
                                    }
                                    is SeerrConnectionResult.ConnectionFailed -> error = result.reason
                                }
                            } else {
                                runCatching {
                                    coordinator.connectSeerrManually(
                                        input,
                                        if (useJellyfin) {
                                            SeerrLoginCredentials.Jellyfin(username, password)
                                        } else {
                                            SeerrLoginCredentials.Local(username, password)
                                        },
                                    )
                                }.onSuccess { onConnected() }.onFailure { error = it.message }
                                password = ""
                            }
                        }
                    },
                )
                TvActionButton(strings.cancel, onDismiss)
            }
        }
    }
    LaunchedEffect(needsCredentials) {
        if (needsCredentials) jellyfinMethodFocus.requestFocus() else urlFocus.requestFocus()
    }
}

private fun AppLanguage.label(strings: TvStrings): String =
    when (this) {
        AppLanguage.SYSTEM -> strings.systemDefault
        AppLanguage.ENGLISH -> strings.english
        AppLanguage.GERMAN -> strings.german
    }

private fun SubtitleMode.label(strings: TvStrings): String =
    when (this) {
        SubtitleMode.SERVER_DEFAULT -> strings.serverDefault
        SubtitleMode.OFF -> strings.off
        SubtitleMode.FORCED_ONLY -> strings.forcedOnly
        SubtitleMode.PREFERRED_ALWAYS -> strings.preferredAlways
        SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS -> strings.preferredWhenAudioDiffers
    }

private fun SubtitleTextSize.label(strings: TvStrings): String =
    when (this) {
        SubtitleTextSize.SYSTEM -> strings.systemDefault
        SubtitleTextSize.SMALL -> strings.small
        SubtitleTextSize.MEDIUM -> strings.medium
        SubtitleTextSize.LARGE -> strings.large
    }

private fun SubtitleBackground.label(strings: TvStrings): String =
    when (this) {
        SubtitleBackground.SYSTEM -> strings.systemDefault
        SubtitleBackground.NONE -> strings.none
        SubtitleBackground.TRANSLUCENT -> strings.translucent
        SubtitleBackground.DARK -> strings.dark
    }

private fun AutoplayNextMode.label(strings: TvStrings): String =
    when (this) {
        AutoplayNextMode.OFF -> strings.off
        AutoplayNextMode.COUNTDOWN -> strings.countdown
        AutoplayNextMode.IMMEDIATE -> strings.immediate
    }

private fun ResumeMode.label(strings: TvStrings): String =
    when (this) {
        ResumeMode.RESUME -> strings.continueLabel
        ResumeMode.ASK -> strings.ask
        ResumeMode.RESTART -> strings.restart
    }

private fun String.label() = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun StreamingQualityPreference.label(strings: TvStrings): String =
    maxHeight?.let { "${it}p  •  ${(maxBitrate ?: 0) / 1_000_000f} Mbps" } ?: strings.automatic

private fun tvQualityOptions(): List<StreamingQualityPreference> =
    listOf(
        StreamingQualityPreference.AUTO,
        StreamingQualityPreference.MBPS_120_2160P,
        StreamingQualityPreference.MBPS_40_2160P,
        StreamingQualityPreference.MBPS_20_2160P,
        StreamingQualityPreference.MBPS_8_1080P,
        StreamingQualityPreference.MBPS_4_720P,
    )

private fun tvSeekOptions() = listOf(5, 10, 15, 30, 60)

private fun tvSpeedOptions() = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun preferredLanguageOptions(
    strings: TvStrings,
    selected: String?,
    onSelect: (String?) -> Unit,
): List<TvChoiceOption> =
    listOf(
        TvChoiceOption(strings.serverDefault, selected == null) { onSelect(null) },
        TvChoiceOption(strings.english, selected == "en") { onSelect("en") },
        TvChoiceOption(strings.german, selected == "de") { onSelect("de") },
    )
