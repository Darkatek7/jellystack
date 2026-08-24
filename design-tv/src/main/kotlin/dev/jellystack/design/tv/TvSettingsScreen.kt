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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.core.server.SeerrConnectionResult
import dev.jellystack.core.server.SeerrLoginCredentials
import dev.jellystack.core.server.SeerrServerInput
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.players.PlaybackSegmentType
import kotlinx.coroutines.launch

@Composable
internal fun TvSegmentSkipSettings(
    settings: AppSettings,
    strings: TvStrings,
    onModeSelected: (PlaybackSegmentType, SegmentSkipMode) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    var choiceDialog by remember { mutableStateOf<TvChoiceDialogState?>(null) }
    val models = tvSegmentSkipSettingModels(settings, strings, onModeSelected)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) TvSectionTitle(strings.segmentSkipping)
        Text(strings.serverSegmentExplanation, color = TvTextMuted, fontSize = 17.sp)
        TvSettingsGrid {
            models.forEachIndexed { index, model ->
                TvSettingTile(
                    title = model.title,
                    value = model.mode.label(strings),
                    focusToNavigationRailOnLeft = index == 0 || index == 3,
                    focusTargetId = tvSettingsSegmentControlTargetId(model.type),
                ) {
                    choiceDialog =
                        TvChoiceDialogState(
                            title = model.title,
                            options =
                                SegmentSkipMode.entries.map { mode ->
                                    TvChoiceOption(mode.label(strings), mode == model.mode) {
                                        model.onModeSelected(mode)
                                    }
                                },
                        )
                }
            }
        }
    }
    choiceDialog?.let { dialog ->
        TvChoiceDialog(dialog, cancelLabel = strings.cancel, onDismiss = { choiceDialog = null }) { option ->
            option.onSelect()
            choiceDialog = null
        }
    }
}

internal data class TvChoiceOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

internal data class TvChoiceDialogState(
    val title: String,
    val options: List<TvChoiceOption>,
)

@Composable
internal fun TvChoiceDialog(
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
                        selected = option.selected,
                    )
                }
            }
            TvActionButton(cancelLabel, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvSettingsGrid(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = tvSettingsColumnCount(maxWidth.value, fontScale)
        val spacing = TvLayoutTokens.CardSpacing
        val tileWidth = (maxWidth - (spacing * (columns - 1))) / columns
        CompositionLocalProvider(LocalTvSettingsTileWidth provides tileWidth) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                maxItemsInEachRow = columns,
                content = content,
            )
        }
    }
}

private val LocalTvSettingsTileWidth = staticCompositionLocalOf { 260.dp }

@Composable
internal fun TvSettingTile(
    title: String,
    value: String,
    focusToNavigationRailOnLeft: Boolean = false,
    screenEntry: Boolean = false,
    enabled: Boolean = true,
    focusTargetId: String,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .tvScreenEntryFocus(screenEntry, focusTargetId)
            .width(LocalTvSettingsTileWidth.current)
            .height(112.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .background(TvSurface, RoundedCornerShape(20.dp))
            .semantics {
                checked?.let { toggleableState = if (it) ToggleableState.On else ToggleableState.Off }
            }.tvFocusable(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                focusTargetId = focusTargetId,
            ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, color = TvText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TvPurple, fontSize = 16.sp)
    }
}

@Composable
internal fun TvSeerrConnectDialog(
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
                        selected = useJellyfin,
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
                        selected = !useJellyfin,
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

internal fun AppLanguage.label(strings: TvStrings): String =
    when (this) {
        AppLanguage.SYSTEM -> strings.systemDefault
        AppLanguage.ENGLISH -> strings.english
        AppLanguage.GERMAN -> strings.german
    }

internal fun SubtitleMode.label(strings: TvStrings): String =
    when (this) {
        SubtitleMode.SERVER_DEFAULT -> strings.serverDefault
        SubtitleMode.OFF -> strings.off
        SubtitleMode.FORCED_ONLY -> strings.forcedOnly
        SubtitleMode.PREFERRED_ALWAYS -> strings.preferredAlways
        SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS -> strings.preferredWhenAudioDiffers
    }

internal fun SubtitleTextSize.label(strings: TvStrings): String =
    when (this) {
        SubtitleTextSize.SYSTEM -> strings.systemDefault
        SubtitleTextSize.SMALL -> strings.small
        SubtitleTextSize.MEDIUM -> strings.medium
        SubtitleTextSize.LARGE -> strings.large
    }

internal fun SubtitleBackground.label(strings: TvStrings): String =
    when (this) {
        SubtitleBackground.SYSTEM -> strings.systemDefault
        SubtitleBackground.NONE -> strings.none
        SubtitleBackground.TRANSLUCENT -> strings.translucent
        SubtitleBackground.DARK -> strings.dark
    }

internal fun AutoplayNextMode.label(strings: TvStrings): String =
    when (this) {
        AutoplayNextMode.OFF -> strings.off
        AutoplayNextMode.COUNTDOWN -> strings.countdown
        AutoplayNextMode.IMMEDIATE -> strings.immediate
    }

internal fun SegmentSkipMode.label(strings: TvStrings): String =
    when (this) {
        SegmentSkipMode.OFF -> strings.off
        SegmentSkipMode.SHOW_BUTTON -> strings.showSkipButton
        SegmentSkipMode.AUTO_SKIP -> strings.skipAutomatically
    }

internal fun AppSettingsRepository.setSegmentSkipMode(
    type: PlaybackSegmentType,
    mode: SegmentSkipMode,
) {
    when (type) {
        PlaybackSegmentType.INTRO -> setIntroSkipMode(mode)
        PlaybackSegmentType.RECAP -> setRecapSkipMode(mode)
        PlaybackSegmentType.OUTRO -> setOutroSkipMode(mode)
        PlaybackSegmentType.PREVIEW -> setPreviewSkipMode(mode)
        PlaybackSegmentType.COMMERCIAL -> setCommercialSkipMode(mode)
    }
}

private fun tvSettingsSegmentControlTargetId(type: PlaybackSegmentType): String =
    tvSettingsControlTargetId("segment-${type.name.lowercase()}")

internal fun ResumeMode.label(strings: TvStrings): String =
    when (this) {
        ResumeMode.RESUME -> strings.continueLabel
        ResumeMode.ASK -> strings.ask
        ResumeMode.RESTART -> strings.restart
    }

internal fun String.label() = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal fun StreamingQualityPreference.label(strings: TvStrings): String =
    maxHeight?.let { "${it}p  •  ${(maxBitrate ?: 0) / 1_000_000f} Mbps" } ?: strings.automatic

internal fun tvQualityOptions(): List<StreamingQualityPreference> =
    listOf(
        StreamingQualityPreference.AUTO,
        StreamingQualityPreference.MBPS_120_2160P,
        StreamingQualityPreference.MBPS_40_2160P,
        StreamingQualityPreference.MBPS_20_2160P,
        StreamingQualityPreference.MBPS_8_1080P,
        StreamingQualityPreference.MBPS_4_720P,
    )

internal fun tvSeekOptions() = listOf(5, 10, 15, 30, 60)

internal fun tvSpeedOptions() = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal fun preferredLanguageOptions(
    strings: TvStrings,
    selected: String?,
    onSelect: (String?) -> Unit,
): List<TvChoiceOption> =
    listOf(
        TvChoiceOption(strings.serverDefault, selected == null) { onSelect(null) },
        TvChoiceOption(strings.english, selected == "en") { onSelect("en") },
        TvChoiceOption(strings.german, selected == "de") { onSelect("de") },
    )
