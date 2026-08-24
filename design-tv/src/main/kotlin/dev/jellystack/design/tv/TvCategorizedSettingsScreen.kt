@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MatchingDeclarationName",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import kotlinx.coroutines.launch

internal data class TvConnectionsFocusRecoveryRequest(
    val revision: Long,
    val removedServerType: ServerType,
)

@Composable
internal fun TvSettingsScreen(
    section: String?,
    settings: AppSettings,
    repository: AppSettingsRepository,
    serverRepository: ServerRepository,
    connectionCoordinator: ServerConnectionCoordinator,
    quickConnectCoordinator: JellyfinQuickConnectCoordinator,
    appVersion: String,
    strings: TvStrings,
    onOpenCategory: (TvSettingsCategory) -> Unit,
    onServersChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    val jellyfinServer = servers.firstOrNull { it.type == ServerType.JELLYFIN }
    val seerrServer = servers.firstOrNull { it.type == ServerType.JELLYSEERR }
    var showJellyfinConnect by remember { mutableStateOf(false) }
    var showSeerrConnect by remember { mutableStateOf(false) }
    var choiceDialog by remember { mutableStateOf<TvChoiceDialogState?>(null) }
    var pendingServerRemoval by remember { mutableStateOf<ManagedServer?>(null) }
    var connectionsFocusRecovery by remember { mutableStateOf<TvConnectionsFocusRecoveryRequest?>(null) }

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

    when (val category = TvSettingsCategory.fromRouteSection(section)) {
        null -> TvSettingsLandingScreen(strings, onOpenCategory, modifier)
        TvSettingsCategory.APPEARANCE ->
            TvSettingsCategoryPage(
                category = category,
                title = strings.settingsAppearance,
                targetIds = tvSettingsControlKeys(category).map(::tvSettingsControlTargetId),
                modifier = modifier,
            ) {
                TvAppearanceSettings(settings, repository, strings, ::showChoices)
            }
        TvSettingsCategory.PLAYBACK ->
            TvSettingsCategoryPage(
                category = category,
                title = strings.settingsPlaybackCategory,
                targetIds = tvSettingsControlKeys(category).map(::tvSettingsControlTargetId),
                modifier = modifier,
            ) {
                TvPlaybackSettings(settings, repository, strings, ::showChoices)
            }
        TvSettingsCategory.AUDIO_SUBTITLES ->
            TvSettingsCategoryPage(
                category = category,
                title = strings.settingsAudioSubtitles,
                targetIds = tvSettingsControlKeys(category).map(::tvSettingsControlTargetId),
                modifier = modifier,
            ) {
                TvAudioSubtitleSettings(settings, repository, strings, ::showChoices)
            }
        TvSettingsCategory.SEGMENT_SKIPPING ->
            TvSettingsCategoryPage(
                category = category,
                title = strings.segmentSkipping,
                targetIds = tvSettingsControlKeys(category).map(::tvSettingsControlTargetId),
                modifier = modifier,
            ) {
                TvSegmentSkipSettings(
                    settings = settings,
                    strings = strings,
                    onModeSelected = { type, mode -> repository.setSegmentSkipMode(type, mode) },
                    showTitle = false,
                )
            }
        TvSettingsCategory.CONNECTIONS -> {
            val manageJellyfinTarget = tvSettingsServerActionTargetId(jellyfinServer?.id ?: "new:jellyfin", "manage")
            val manageSeerrTarget = tvSettingsServerActionTargetId(seerrServer?.id ?: "new:seerr", "manage")
            val targetIds =
                buildList {
                    add(manageJellyfinTarget)
                    add(manageSeerrTarget)
                    servers.forEach { add(tvSettingsServerActionTargetId(it.id, "remove")) }
                }
            TvConnectionsFocusRecovery(
                request = connectionsFocusRecovery,
                manageJellyfinTarget = manageJellyfinTarget,
                manageSeerrTarget = manageSeerrTarget,
            )
            TvSettingsCategoryPage(
                category = category,
                title = strings.connections,
                targetIds = targetIds,
                modifier = modifier,
            ) {
                TvConnectionsSettings(
                    servers = servers,
                    jellyfinServer = jellyfinServer,
                    seerrServer = seerrServer,
                    appVersion = appVersion,
                    strings = strings,
                    manageJellyfinTarget = manageJellyfinTarget,
                    manageSeerrTarget = manageSeerrTarget,
                    onManageJellyfin = { showJellyfinConnect = true },
                    onManageSeerr = { showSeerrConnect = true },
                    onRemove = { pendingServerRemoval = it },
                )
            }
        }
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
    pendingServerRemoval?.let { target ->
        TvRemoveServerDialog(
            serverName = target.name,
            strings = strings,
            onRemove = {
                pendingServerRemoval = null
                scope.launch {
                    serverRepository.remove(target.id)
                    connectionsFocusRecovery =
                        TvConnectionsFocusRecoveryRequest(
                            revision = (connectionsFocusRecovery?.revision ?: 0L) + 1L,
                            removedServerType = target.type,
                        )
                    onServersChanged()
                }
            },
            onDismiss = { pendingServerRemoval = null },
        )
    }
}

@Composable
internal fun TvConnectionsFocusRecovery(
    request: TvConnectionsFocusRecoveryRequest?,
    manageJellyfinTarget: String,
    manageSeerrTarget: String,
) {
    val focusContext = LocalTvFocusContext.current ?: return
    LaunchedEffect(request) {
        request ?: return@LaunchedEffect
        withFrameNanos { }
        if (!focusContext.coordinator.needsContentRestoration(focusContext.routeKey)) return@LaunchedEffect
        val preferredTarget =
            when (request.removedServerType) {
                ServerType.JELLYSEERR -> manageSeerrTarget
                else -> manageJellyfinTarget
            }
        val restored =
            focusContext.coordinator.restoreFocus(
                routeKey = focusContext.routeKey,
                preferredTargetId = preferredTarget,
                includeFallback = false,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        if (restored is TvFocusRestoration.Failed) {
            focusContext.coordinator.restoreFocus(
                routeKey = focusContext.routeKey,
                requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
            )
        }
    }
}

@Composable
internal fun TvSettingsLandingScreen(
    strings: TvStrings,
    onOpenCategory: (TvSettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val targetIds = TvSettingsCategory.entries.map(::tvSettingsCategoryTargetId)
    TvRouteFocusMaterializer(
        ownerId = "settings-landing",
        targetIds = targetIds.toSet(),
        fallbackTargetIds = setOf(targetIds.first()),
    ) { targetId ->
        if (targetId in targetIds) {
            listState.scrollToItem(2)
            true
        } else {
            false
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(strings.settings, modifier = Modifier.tvHeading(), color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        item { Text(strings.settingsChooseCategory, color = TvTextMuted, fontSize = 19.sp) }
        item {
            TvSettingsGrid {
                TvSettingsCategory.entries.forEachIndexed { index, category ->
                    TvSettingTile(
                        title = category.title(strings),
                        value = category.description(strings),
                        focusToNavigationRailOnLeft = index % 3 == 0,
                        screenEntry = index == 0,
                        focusTargetId = tvSettingsCategoryTargetId(category),
                        onClick = { onOpenCategory(category) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvSettingsCategoryPage(
    category: TvSettingsCategory,
    title: String,
    targetIds: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    TvRouteFocusMaterializer(
        ownerId = "settings-${category.routeKey}",
        targetIds = targetIds.toSet(),
        fallbackTargetIds = targetIds.firstOrNull()?.let(::setOf).orEmpty(),
    ) { targetId ->
        if (targetId in targetIds) {
            listState.scrollToItem(1)
            true
        } else {
            false
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Text(title, modifier = Modifier.tvHeading(), color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
        item { content() }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun TvAppearanceSettings(
    settings: AppSettings,
    repository: AppSettingsRepository,
    strings: TvStrings,
    showChoices: (String, List<TvChoiceOption>) -> Unit,
) {
    TvSettingsGrid {
        TvSettingTile(
            strings.language,
            settings.appLanguage.label(strings),
            focusToNavigationRailOnLeft = true,
            screenEntry = true,
            focusTargetId = tvSettingsControlTargetId("language"),
        ) {
            showChoices(
                strings.language,
                AppLanguage.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.appLanguage) { repository.setAppLanguage(value) }
                },
            )
        }
        TvSettingTile(
            strings.homeSections,
            if (settings.useServerHomeSections) strings.on else strings.off,
            focusTargetId = tvSettingsControlTargetId("home-sections"),
            checked = settings.useServerHomeSections,
        ) {
            repository.setUseServerHomeSections(!settings.useServerHomeSections)
        }
    }
}

@Composable
internal fun TvPlaybackSettings(
    settings: AppSettings,
    repository: AppSettingsRepository,
    strings: TvStrings,
    showChoices: (String, List<TvChoiceOption>) -> Unit,
) {
    TvSettingsGrid {
        TvSettingTile(
            strings.quality,
            settings.wifiStreamingQuality.label(strings),
            focusToNavigationRailOnLeft = true,
            screenEntry = true,
            focusTargetId = tvSettingsControlTargetId("quality"),
        ) {
            showChoices(
                strings.quality,
                tvQualityOptions().map { value ->
                    TvChoiceOption(value.label(strings), value == settings.wifiStreamingQuality) {
                        repository.setWifiStreamingQuality(value)
                    }
                },
            )
        }
        TvSettingTile(
            strings.autoplay,
            settings.autoplayNextMode.label(strings),
            focusTargetId = tvSettingsControlTargetId("autoplay"),
        ) {
            showChoices(
                strings.autoplay,
                AutoplayNextMode.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.autoplayNextMode) {
                        repository.setAutoplayNextMode(value)
                    }
                },
            )
        }
        TvSettingTile(
            strings.resume,
            settings.resumeMode.label(strings),
            focusTargetId = tvSettingsControlTargetId("resume"),
        ) {
            showChoices(
                strings.resume,
                ResumeMode.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.resumeMode) { repository.setResumeMode(value) }
                },
            )
        }
        TvSettingTile(
            strings.seekBack,
            "${settings.seekBackSeconds}s",
            focusToNavigationRailOnLeft = true,
            focusTargetId = tvSettingsControlTargetId("seek-back"),
        ) {
            showChoices(
                strings.seekBack,
                tvSeekOptions().map { value ->
                    TvChoiceOption("${value}s", value == settings.seekBackSeconds) { repository.setSeekBackSeconds(value) }
                },
            )
        }
        TvSettingTile(
            strings.seekForward,
            "${settings.seekForwardSeconds}s",
            focusTargetId = tvSettingsControlTargetId("seek-forward"),
        ) {
            showChoices(
                strings.seekForward,
                tvSeekOptions().map { value ->
                    TvChoiceOption("${value}s", value == settings.seekForwardSeconds) { repository.setSeekForwardSeconds(value) }
                },
            )
        }
        TvSettingTile(
            strings.playbackSpeed,
            "${settings.defaultPlaybackSpeed}x",
            focusTargetId = tvSettingsControlTargetId("playback-speed"),
        ) {
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
            focusToNavigationRailOnLeft = true,
            focusTargetId = tvSettingsControlTargetId("stats"),
            checked = settings.statsForNerdsEnabled,
        ) {
            repository.setStatsForNerdsEnabled(!settings.statsForNerdsEnabled)
        }
        TvSettingTile(
            strings.trailerPreviews,
            if (settings.trailerPreviewsEnabled) strings.on else strings.off,
            focusTargetId = tvSettingsControlTargetId("trailer-previews"),
            checked = settings.trailerPreviewsEnabled,
        ) {
            repository.setTrailerPreviewsEnabled(!settings.trailerPreviewsEnabled)
        }
        TvSettingTile(
            strings.trailerPreviewSound,
            if (settings.trailerPreviewSoundEnabled) strings.on else strings.off,
            enabled = settings.trailerPreviewsEnabled,
            focusTargetId = tvSettingsControlTargetId("trailer-preview-sound"),
            checked = settings.trailerPreviewSoundEnabled,
        ) {
            repository.setTrailerPreviewSoundEnabled(!settings.trailerPreviewSoundEnabled)
        }
    }
}

@Composable
private fun TvAudioSubtitleSettings(
    settings: AppSettings,
    repository: AppSettingsRepository,
    strings: TvStrings,
    showChoices: (String, List<TvChoiceOption>) -> Unit,
) {
    TvSettingsGrid {
        TvSettingTile(
            strings.audioLanguage,
            settings.preferredAudioLanguage ?: strings.serverDefault,
            focusToNavigationRailOnLeft = true,
            screenEntry = true,
            focusTargetId = tvSettingsControlTargetId("audio-language"),
        ) {
            showChoices(
                strings.audioLanguage,
                preferredLanguageOptions(strings, settings.preferredAudioLanguage) { repository.setPreferredAudioLanguage(it) },
            )
        }
        TvSettingTile(
            strings.subtitleLanguage,
            settings.preferredSubtitleLanguage ?: strings.serverDefault,
            focusTargetId = tvSettingsControlTargetId("subtitle-language"),
        ) {
            showChoices(
                strings.subtitleLanguage,
                preferredLanguageOptions(strings, settings.preferredSubtitleLanguage) {
                    repository.setPreferredSubtitleLanguage(it)
                },
            )
        }
        TvSettingTile(
            strings.subtitleMode,
            settings.subtitleMode.label(strings),
            focusTargetId = tvSettingsControlTargetId("subtitle-mode"),
        ) {
            showChoices(
                strings.subtitleMode,
                SubtitleMode.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.subtitleMode) { repository.setSubtitleMode(value) }
                },
            )
        }
        TvSettingTile(
            strings.subtitleSize,
            settings.subtitleTextSize.label(strings),
            focusToNavigationRailOnLeft = true,
            focusTargetId = tvSettingsControlTargetId("subtitle-size"),
        ) {
            showChoices(
                strings.subtitleSize,
                SubtitleTextSize.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.subtitleTextSize) {
                        repository.setSubtitleTextSize(value)
                    }
                },
            )
        }
        TvSettingTile(
            strings.subtitleBackground,
            settings.subtitleBackground.label(strings),
            focusTargetId = tvSettingsControlTargetId("subtitle-background"),
        ) {
            showChoices(
                strings.subtitleBackground,
                SubtitleBackground.entries.map { value ->
                    TvChoiceOption(value.label(strings), value == settings.subtitleBackground) {
                        repository.setSubtitleBackground(value)
                    }
                },
            )
        }
    }
}

@Composable
private fun TvConnectionsSettings(
    servers: List<ManagedServer>,
    jellyfinServer: ManagedServer?,
    seerrServer: ManagedServer?,
    appVersion: String,
    strings: TvStrings,
    manageJellyfinTarget: String,
    manageSeerrTarget: String,
    onManageJellyfin: () -> Unit,
    onManageSeerr: () -> Unit,
    onRemove: (ManagedServer) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(TvSurface, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        servers.forEach { server ->
            key(server.id) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name, color = TvText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text("${server.type.name.label()}  •  ${strings.connected}", color = TvTextMuted)
                    }
                    TvActionButton(
                        strings.remove,
                        { onRemove(server) },
                        destructive = true,
                        focusToNavigationRailOnLeft = true,
                        focusTargetId = tvSettingsServerActionTargetId(server.id, "remove"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvActionButton(
                if (jellyfinServer == null) strings.addJellyfin else strings.manageJellyfin,
                onManageJellyfin,
                primary = jellyfinServer == null,
                focusToNavigationRailOnLeft = true,
                focusTargetId = manageJellyfinTarget,
            )
            TvActionButton(
                if (seerrServer == null) strings.addSeerr else strings.manageSeerr,
                onManageSeerr,
                primary = seerrServer == null,
                focusTargetId = manageSeerrTarget,
            )
        }
        Text("Jellystack TV $appVersion", color = TvTextMuted)
    }
}

@Composable
internal fun TvRemoveServerDialog(
    serverName: String,
    strings: TvStrings,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(serverName) {
        withFrameNanos { }
        runCatching { cancelFocusRequester.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(620.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                strings.removeServerConfirm.format(serverName),
                color = TvText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(strings.removeServerMessage, color = TvTextMuted, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    strings.remove,
                    onClick = onRemove,
                    destructive = true,
                    modifier = Modifier.width(180.dp),
                )
                TvActionButton(
                    strings.cancel,
                    onClick = onDismiss,
                    focusRequester = cancelFocusRequester,
                )
            }
        }
    }
}

private fun TvSettingsCategory.title(strings: TvStrings): String =
    when (this) {
        TvSettingsCategory.APPEARANCE -> strings.settingsAppearance
        TvSettingsCategory.PLAYBACK -> strings.settingsPlaybackCategory
        TvSettingsCategory.AUDIO_SUBTITLES -> strings.settingsAudioSubtitles
        TvSettingsCategory.SEGMENT_SKIPPING -> strings.segmentSkipping
        TvSettingsCategory.CONNECTIONS -> strings.connections
    }

private fun TvSettingsCategory.description(strings: TvStrings): String =
    when (this) {
        TvSettingsCategory.APPEARANCE -> strings.settingsAppearanceDescription
        TvSettingsCategory.PLAYBACK -> strings.settingsPlaybackDescription
        TvSettingsCategory.AUDIO_SUBTITLES -> strings.settingsAudioSubtitlesDescription
        TvSettingsCategory.SEGMENT_SKIPPING -> strings.settingsSegmentSkippingDescription
        TvSettingsCategory.CONNECTIONS -> strings.settingsConnectionsDescription
    }
