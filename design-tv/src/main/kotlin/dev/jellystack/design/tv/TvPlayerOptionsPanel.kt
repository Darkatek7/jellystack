@file:Suppress("FunctionName", "FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength", "TooManyFunctions")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import dev.jellystack.players.syncplay.SyncPlayErrorCode
import dev.jellystack.players.syncplay.SyncPlayUiState

@Composable
internal fun TvPlayerHeader(
    primaryTitle: String,
    secondaryTitle: String?,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvPlayerIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = backDescription,
            onClick = onBack,
            size = 58.dp,
            iconSize = 30.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(primaryTitle, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            secondaryTitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 17.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun TvPlayerOptionRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    checked: Boolean? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    val description = listOfNotNull(title, summary?.takeIf(String::isNotBlank)).joinToString(", ")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(78.dp)
                .graphicsLayer {
                    scaleX = if (focused) 1.025f else 1f
                    scaleY = if (focused) 1.025f else 1f
                }.background(TvSurfaceRaised, shape)
                .border(if (focused) 3.dp else 1.dp, if (focused) TvPurple else Color.White.copy(alpha = 0.08f), shape)
                .clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                    this.selected = selected
                    checked?.let { toggleableState = if (it) ToggleableState.On else ToggleableState.Off }
                }.clickable(onClick = onClick)
                .focusable()
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) TvPurple else Color.White, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, maxLines = 1)
            summary?.takeIf(String::isNotBlank)?.let {
                Text(it, color = TvTextMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke() ?: Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TvTextMuted)
    }
}

@Composable
internal fun TvPlayerSelectionRow(
    title: String,
    metadata: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPlayerOptionRow(
        icon = if (selected) Icons.Default.Check else Icons.Default.ChevronRight,
        title = title,
        summary = metadata,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = TvPurple, modifier = Modifier.size(27.dp))
        },
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun TvPlayerOptionsPanel(
    navigation: TvPlayerPanelNavigation,
    state: PlaybackState.Active,
    syncState: SyncPlayUiState,
    strings: TvStrings,
    onBack: () -> Unit,
    onOpenFromMore: (TvPlayerPanel) -> Unit,
    onAudioSelected: (String) -> Unit,
    onSubtitleSelected: (String?) -> Unit,
    onQualitySelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onStatsToggled: (Boolean) -> Unit,
    syncPlay: SyncPlayCoordinator,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember(navigation.current) { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val statsFocus = remember { FocusRequester() }
    val syncFocus = remember { FocusRequester() }
    val restoreRequester =
        when (navigation.restoreFocusTo) {
            TvPlayerPanel.AUDIO -> audioFocus
            TvPlayerPanel.SUBTITLES -> subtitleFocus
            TvPlayerPanel.QUALITY -> qualityFocus
            TvPlayerPanel.SPEED -> speedFocus
            TvPlayerPanel.SYNCPLAY -> syncFocus
            else -> statsFocus
        }
    LaunchedEffect(navigation.current, navigation.restoreFocusTo) {
        when {
            navigation.current == TvPlayerPanel.MORE && navigation.restoreFocusTo != null -> restoreRequester.requestFocus()
            navigation.current == TvPlayerPanel.MORE -> audioFocus.requestFocus()
            else -> firstFocus.requestFocus()
        }
    }
    Column(
        modifier =
            modifier
                .width(560.dp)
                .fillMaxHeight()
                .background(TvBackground.copy(alpha = 0.985f))
                .padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvPlayerPanelHeader(
            title = navigation.current.title(strings),
            root = navigation.current == TvPlayerPanel.MORE,
            strings = strings,
            onBack = onBack,
        )
        when (navigation.current) {
            TvPlayerPanel.MORE ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        TvPlayerOptionRow(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            strings.audio,
                            state.audioSummary(strings),
                            false,
                            { onOpenFromMore(TvPlayerPanel.AUDIO) },
                            Modifier.focusRequester(audioFocus),
                        )
                    }
                    item {
                        TvPlayerOptionRow(
                            Icons.Default.Subtitles,
                            strings.subtitles,
                            state.subtitleSummary(strings),
                            false,
                            { onOpenFromMore(TvPlayerPanel.SUBTITLES) },
                            Modifier.focusRequester(subtitleFocus),
                        )
                    }
                    item {
                        TvPlayerOptionRow(
                            Icons.Default.HighQuality,
                            strings.quality,
                            state.qualitySummary(strings),
                            false,
                            { onOpenFromMore(TvPlayerPanel.QUALITY) },
                            Modifier.focusRequester(qualityFocus),
                        )
                    }
                    item {
                        TvPlayerOptionRow(
                            Icons.Default.Speed,
                            strings.playbackSpeed,
                            "${state.playbackSpeed.cleanSpeed()}x",
                            false,
                            { onOpenFromMore(TvPlayerPanel.SPEED) },
                            Modifier.focusRequester(speedFocus),
                        )
                    }
                    item {
                        TvPlayerOptionRow(
                            Icons.Default.QueryStats,
                            strings.statsForNerds,
                            if (state.statsForNerdsEnabled) strings.on else strings.off,
                            state.statsForNerdsEnabled,
                            { onStatsToggled(!state.statsForNerdsEnabled) },
                            Modifier.focusRequester(statsFocus),
                            trailing = { TvPlayerToggle(state.statsForNerdsEnabled) },
                            checked = state.statsForNerdsEnabled,
                        )
                    }
                    item {
                        TvPlayerOptionRow(
                            Icons.Default.Groups,
                            strings.syncPlay,
                            if (syncState.canJoin) syncState.currentGroup?.name ?: strings.off else strings.syncPlayNoPermission,
                            false,
                            {
                                if (syncState.canJoin) syncPlay.refresh()
                                onOpenFromMore(TvPlayerPanel.SYNCPLAY)
                            },
                            Modifier.focusRequester(syncFocus),
                        )
                    }
                }
            TvPlayerPanel.AUDIO ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.stream.audioTracks.isEmpty()) {
                        item {
                            TvPlayerOptionRow(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                strings.noResults,
                                null,
                                false,
                                onBack,
                                Modifier.focusRequester(firstFocus),
                                trailing = {},
                            )
                        }
                    }
                    itemsIndexed(state.stream.audioTracks, key = { _, track -> track.id }) { index, track ->
                        val selectedIndex =
                            state.stream.audioTracks
                                .indexOfFirst { it.id == state.audioTrack?.id }
                                .takeIf { it >= 0 } ?: 0
                        val selected = index == selectedIndex
                        TvPlayerSelectionRow(
                            title = track.title ?: track.language ?: strings.audioTrack.format(track.streamIndex),
                            metadata = listOfNotNull(track.language, track.codec).distinct().joinToString(" · ").ifBlank { null },
                            selected = selected,
                            onClick = { onAudioSelected(track.id) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            TvPlayerPanel.SUBTITLES ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        TvPlayerSelectionRow(
                            strings.off,
                            null,
                            state.subtitleTrack == null,
                            { onSubtitleSelected(null) },
                            Modifier.focusRequester(firstFocus),
                        )
                    }
                    items(state.stream.subtitleTracks, key = { it.id }) { track ->
                        TvPlayerSelectionRow(
                            title = track.title ?: track.language ?: strings.subtitleTrack.format(track.streamIndex),
                            metadata = listOfNotNull(track.language, track.format.name).distinct().joinToString(" · "),
                            selected = state.subtitleTrack?.id == track.id,
                            onClick = { onSubtitleSelected(track.id) },
                        )
                    }
                }
            TvPlayerPanel.QUALITY ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.qualityOptions.isEmpty()) {
                        item {
                            TvPlayerOptionRow(
                                Icons.Default.HighQuality,
                                strings.noResults,
                                null,
                                false,
                                onBack,
                                Modifier.focusRequester(firstFocus),
                                trailing = {},
                            )
                        }
                    }
                    itemsIndexed(state.qualityOptions, key = { _, option -> option.id }) { index, option ->
                        val selectedIndex = state.qualityOptions.indexOfFirst { it.id == state.selectedQualityId }.takeIf { it >= 0 } ?: 0
                        val selected = index == selectedIndex
                        TvPlayerSelectionRow(
                            option.label.ifBlank { strings.automatic },
                            option.mode.label(strings),
                            selected,
                            { onQualitySelected(option.id) },
                            if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            TvPlayerPanel.SPEED -> {
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(speeds) { index, speed ->
                        val selected = state.playbackSpeed == speed
                        TvPlayerSelectionRow(
                            "${speed.cleanSpeed()}x",
                            null,
                            selected,
                            { onSpeedSelected(speed) },
                            if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
            TvPlayerPanel.SYNCPLAY ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    syncState.error?.let { error ->
                        item { Text(error.message(strings), color = Color(0xFFFFA59E), fontSize = 16.sp) }
                    }
                    if (!syncState.canJoin) {
                        item {
                            TvPlayerOptionRow(
                                Icons.Default.Groups,
                                strings.syncPlayNoPermission,
                                null,
                                false,
                                onBack,
                                Modifier.focusRequester(firstFocus),
                                trailing = {},
                            )
                        }
                    } else {
                        syncState.currentGroup?.let { group ->
                            item {
                                TvPlayerOptionRow(
                                    Icons.Default.Groups,
                                    strings.useCurrentItemAsQueue,
                                    group.name,
                                    false,
                                    syncPlay::setCurrentPlaybackAsQueue,
                                    Modifier.focusRequester(firstFocus),
                                    trailing = {},
                                )
                            }
                            item {
                                TvPlayerOptionRow(
                                    Icons.Default.Close,
                                    strings.leaveGroup,
                                    group.name,
                                    false,
                                    syncPlay::leaveGroup,
                                    trailing = {},
                                )
                            }
                        } ?: run {
                            if (syncState.canCreate) {
                                item {
                                    TvPlayerOptionRow(
                                        Icons.Default.Groups,
                                        strings.createGroup,
                                        null,
                                        false,
                                        { syncPlay.createGroup("Jellystack TV") },
                                        Modifier.focusRequester(firstFocus),
                                        trailing = {},
                                    )
                                }
                            }
                            if (syncState.groups.isEmpty() && !syncState.loading) {
                                item {
                                    TvPlayerOptionRow(
                                        Icons.Default.Groups,
                                        strings.noResults,
                                        null,
                                        false,
                                        onBack,
                                        if (syncState.canCreate) Modifier else Modifier.focusRequester(firstFocus),
                                        trailing = {},
                                    )
                                }
                            }
                            itemsIndexed(syncState.groups, key = { _, group -> group.id }) { index, group ->
                                val rowModifier =
                                    if (!syncState.canCreate && index == 0) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    }
                                TvPlayerOptionRow(
                                    Icons.Default.Groups,
                                    strings.joinGroup.format(group.name),
                                    group.state,
                                    false,
                                    { syncPlay.joinGroup(group) },
                                    rowModifier,
                                )
                            }
                        }
                    }
                }
            TvPlayerPanel.NONE -> Unit
        }
    }
}

@Composable
private fun TvPlayerPanelHeader(
    title: String,
    root: Boolean,
    strings: TvStrings,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TvPlayerIconButton(
            if (root) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
            if (root) strings.close else strings.back,
            onBack,
            size = 52.dp,
            iconSize = 26.dp,
        )
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TvPlayerToggle(checked: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(54.dp)
            .height(30.dp)
            .background(if (checked) TvPurple else Color.White.copy(alpha = 0.18f), shape)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

@Composable
internal fun TvStatsForNerdsOverlay(
    state: PlaybackState.Active,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val stats = state.runtimeStats
    val entries =
        listOfNotNull(
            stats.playbackMode?.let { strings.mode to it },
            stats.width?.let { width -> stats.height?.let { strings.resolution to "$width × $it" } },
            listOfNotNull(stats.videoCodec, stats.audioCodec).joinToString(" / ").takeIf(String::isNotBlank)?.let { strings.video to it },
            stats.videoBitrate?.let { strings.bitrate to "%.1f Mbps".format(it / 1_000_000f) },
            stats.droppedFrames?.let { strings.droppedFrames to it.toString() },
        )
    Column(
        modifier.background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(strings.statsForNerds, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        entries.forEach { (label, value) -> Text("$label: $value", color = TvTextMuted, fontSize = 14.sp) }
    }
}

private fun PlaybackState.Active.audioSummary(strings: TvStrings): String =
    audioTrack
        ?.let { listOfNotNull(it.title, it.language, it.codec).distinct().joinToString(" · ") }
        ?.takeIf(String::isNotBlank) ?: strings.automatic

private fun PlaybackState.Active.subtitleSummary(strings: TvStrings): String =
    subtitleTrack
        ?.let { listOfNotNull(it.title, it.language, it.format.name).distinct().joinToString(" · ") }
        ?.takeIf(String::isNotBlank) ?: strings.off

private fun PlaybackState.Active.qualitySummary(strings: TvStrings): String =
    qualityOptions.firstOrNull { it.id == selectedQualityId }?.label?.takeIf(String::isNotBlank) ?: strings.automatic

private fun Float.cleanSpeed(): String = if (this % 1f == 0f) toInt().toString() else toString()

private fun SyncPlayErrorCode.message(strings: TvStrings): String =
    when (this) {
        SyncPlayErrorCode.ACCESS_DENIED -> strings.syncPlayNoPermission
        SyncPlayErrorCode.UNAUTHORIZED -> strings.syncPlayUnauthorized
        SyncPlayErrorCode.NETWORK -> strings.syncPlayNetworkError
        SyncPlayErrorCode.INVALID_RESPONSE -> strings.syncPlayInvalidResponse
    }

private fun TvPlayerPanel.title(strings: TvStrings): String =
    when (this) {
        TvPlayerPanel.MORE -> strings.moreOptions
        TvPlayerPanel.AUDIO -> strings.audio
        TvPlayerPanel.SUBTITLES -> strings.subtitles
        TvPlayerPanel.QUALITY -> strings.quality
        TvPlayerPanel.SPEED -> strings.playbackSpeed
        TvPlayerPanel.SYNCPLAY -> strings.syncPlay
        TvPlayerPanel.NONE -> ""
    }
