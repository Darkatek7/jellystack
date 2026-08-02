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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showJellyfinConnect by remember { mutableStateOf(false) }
    var showSeerrConnect by remember { mutableStateOf(false) }
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
                ) { repository.setAppLanguage(settings.appLanguage.next()) }
                TvSettingTile(strings.quality, settings.wifiStreamingQuality.label(strings)) {
                    repository.setWifiStreamingQuality(settings.wifiStreamingQuality.nextTvQuality())
                }
                TvSettingTile(strings.audioLanguage, settings.preferredAudioLanguage ?: strings.serverDefault) {
                    repository.setPreferredAudioLanguage(settings.preferredAudioLanguage.nextPreferredLanguage())
                }
                TvSettingTile(strings.subtitleLanguage, settings.preferredSubtitleLanguage ?: strings.serverDefault) {
                    repository.setPreferredSubtitleLanguage(settings.preferredSubtitleLanguage.nextPreferredLanguage())
                }
                TvSettingTile(strings.subtitleMode, settings.subtitleMode.label(strings)) {
                    repository.setSubtitleMode(settings.subtitleMode.next())
                }
                TvSettingTile(strings.subtitleSize, settings.subtitleTextSize.label(strings)) {
                    repository.setSubtitleTextSize(settings.subtitleTextSize.next())
                }
                TvSettingTile(strings.subtitleBackground, settings.subtitleBackground.label(strings)) {
                    repository.setSubtitleBackground(settings.subtitleBackground.next())
                }
                TvSettingTile(strings.autoplay, settings.autoplayNextMode.label(strings)) {
                    repository.setAutoplayNextMode(settings.autoplayNextMode.next())
                }
                TvSettingTile(strings.resume, settings.resumeMode.label(strings)) { repository.setResumeMode(settings.resumeMode.next()) }
                TvSettingTile(strings.seekBack, "${settings.seekBackSeconds}s") {
                    repository.setSeekBackSeconds(settings.seekBackSeconds.nextSeek())
                }
                TvSettingTile(strings.seekForward, "${settings.seekForwardSeconds}s") {
                    repository.setSeekForwardSeconds(settings.seekForwardSeconds.nextSeek())
                }
                TvSettingTile(strings.playbackSpeed, "${settings.defaultPlaybackSpeed}x") {
                    repository.setDefaultPlaybackSpeed(settings.defaultPlaybackSpeed.nextSpeed())
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
            }
        }
        item { TvSectionTitle(strings.connections) }
        item {
            Column(
                Modifier.fillMaxWidth().background(TvSurface, RoundedCornerShape(22.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                serverRepository.currentServers().forEach { server ->
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
                    TvActionButton(strings.addJellyfin, { showJellyfinConnect = true }, primary = true)
                    TvActionButton(strings.addSeerr, { showSeerrConnect = true })
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
            onDismiss = { showSeerrConnect = false },
            onConnected = {
                showSeerrConnect = false
                onServersChanged()
            },
        )
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
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(330.dp)
            .height(112.dp)
            .background(TvSurface, RoundedCornerShape(20.dp))
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(20.dp))
            .padding(20.dp),
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
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useJellyfin by remember { mutableStateOf(true) }
    var needsCredentials by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(strings.connectSeerr, color = TvText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(url, { url = it }, label = { Text(strings.serverUrl) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (needsCredentials) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(strings.jellyfinAccount, { useJellyfin = true }, primary = useJellyfin)
                    TvActionButton(strings.seerrAccount, { useJellyfin = false }, primary = !useJellyfin)
                }
                OutlinedTextField(
                    username,
                    { username = it },
                    label = { Text(if (useJellyfin) strings.username else strings.email) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(strings.password) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = Color(0xFFFFA59E)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    strings.connect,
                    primary = true,
                    onClick = {
                        scope.launch {
                            val input = SeerrServerInput("Seerr", url, appVersion = appVersion)
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

private inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}

private fun StreamingQualityPreference.label(strings: TvStrings): String =
    maxHeight?.let { "${it}p  •  ${(maxBitrate ?: 0) / 1_000_000f} Mbps" } ?: strings.automatic

private fun StreamingQualityPreference.nextTvQuality(): StreamingQualityPreference {
    val options =
        listOf(
            StreamingQualityPreference.AUTO,
            StreamingQualityPreference.MBPS_120_2160P,
            StreamingQualityPreference.MBPS_40_2160P,
            StreamingQualityPreference.MBPS_20_2160P,
            StreamingQualityPreference.MBPS_8_1080P,
            StreamingQualityPreference.MBPS_4_720P,
        )
    return options[(options.indexOf(this).takeIf { it >= 0 } ?: 0).plus(1) % options.size]
}

private fun Int.nextSeek(): Int {
    val values = listOf(5, 10, 15, 30, 60)
    return values[(values.indexOf(this).takeIf { it >= 0 } ?: 0).plus(1) % values.size]
}

private fun Float.nextSpeed(): Float {
    val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    return values[(values.indexOf(this).takeIf { it >= 0 } ?: 2).plus(1) % values.size]
}

private fun String?.nextPreferredLanguage(): String? =
    when (this) {
        null -> "en"
        "en" -> "de"
        else -> null
    }
