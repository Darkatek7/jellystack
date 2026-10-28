@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.diagnostics.CrashReport
import dev.jellystack.core.diagnostics.JellystackDiagnostics
import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal const val TV_DIAGNOSTICS_REFRESH_KEY = "diagnostics-refresh"
private const val TV_DIAGNOSTICS_CLEAR_KEY = "diagnostics-clear"
private const val TV_DIAGNOSTICS_MAX_CRASH_LINES = 40

/** What the Diagnostics page shows; read from local storage and memory only. */
internal data class TvDiagnosticsSnapshot(
    val crash: CrashReport?,
    /** Newest first. */
    val logLines: List<String>,
)

internal fun tvDiagnosticsSnapshot(
    crash: CrashReport?,
    logLines: List<String>,
): TvDiagnosticsSnapshot = TvDiagnosticsSnapshot(crash = crash, logLines = logLines.asReversed())

/**
 * Settings > Diagnostics: version, device, the last crash and the recent log. Every line is focusable, so the
 * D-pad scrolls through them. Nothing here is sent anywhere.
 */
@Composable
internal fun TvDiagnosticsSettings(
    appVersion: String,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val labels = strings.diagnostics
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope { tvCoroutineFailureHandler("diagnostics") }
    var refreshCount by remember { mutableIntStateOf(0) }
    val snapshot = rememberTvDiagnosticsSnapshot(refreshCount)
    val refreshTarget = tvSettingsControlTargetId(TV_DIAGNOSTICS_REFRESH_KEY)
    TvRouteFocusMaterializer(
        ownerId = "settings-${TvSettingsCategory.DIAGNOSTICS.routeKey}",
        targetIds = setOf(refreshTarget),
        fallbackTargetIds = setOf(refreshTarget),
    ) { targetId ->
        if (targetId == refreshTarget) {
            listState.scrollToItem(0)
            true
        } else {
            false
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = TvScreenPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TvDiagnosticsHeader(
                appVersion = appVersion,
                labels = labels,
                canClearCrash = snapshot?.crash != null,
                onRefresh = { refreshCount += 1 },
                onClearCrash = {
                    scope.launch {
                        withContext(Dispatchers.IO) { JellystackDiagnostics.clearLastCrash() }
                        refreshCount += 1
                    }
                },
            )
        }
        tvDiagnosticsCrashSection(snapshot?.crash, labels)
        tvDiagnosticsLogSection(snapshot?.logLines.orEmpty(), labels)
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun rememberTvDiagnosticsSnapshot(refreshCount: Int): TvDiagnosticsSnapshot? {
    var snapshot by remember { mutableStateOf<TvDiagnosticsSnapshot?>(null) }
    LaunchedEffect(refreshCount) {
        snapshot =
            withContext(Dispatchers.IO) {
                runSuspendCatching {
                    tvDiagnosticsSnapshot(JellystackDiagnostics.lastCrash(), JellystackDiagnostics.logBuffer.snapshot())
                }.getOrElse { failure ->
                    JellystackLog.w("Could not read the diagnostics", failure)
                    tvDiagnosticsSnapshot(null, JellystackDiagnostics.logBuffer.snapshot())
                }
            }
    }
    return snapshot
}

@Composable
private fun TvDiagnosticsHeader(
    appVersion: String,
    labels: TvDiagnosticsStrings,
    canClearCrash: Boolean,
    onRefresh: () -> Unit,
    onClearCrash: () -> Unit,
) {
    val refreshTarget = tvSettingsControlTargetId(TV_DIAGNOSTICS_REFRESH_KEY)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            labels.categoryTitle,
            modifier = Modifier.tvHeading(),
            color = TvText,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(labels.localOnlyNote, color = TvTextMuted, fontSize = 19.sp)
        Column(
            Modifier.fillMaxWidth().background(TvSurface, RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${labels.appVersionLabel}: $appVersion", color = TvText, fontSize = 19.sp)
            Text("${labels.deviceLabel}: ${tvDeviceDescription(labels)}", color = TvText, fontSize = 19.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvActionButton(
                labels.refresh,
                onClick = onRefresh,
                modifier = Modifier.tvScreenEntryFocus(focusTargetId = refreshTarget),
                focusToNavigationRailOnLeft = true,
                focusTargetId = refreshTarget,
            )
            if (canClearCrash) {
                TvActionButton(
                    labels.clearCrash,
                    onClick = onClearCrash,
                    destructive = true,
                    focusTargetId = tvSettingsControlTargetId(TV_DIAGNOSTICS_CLEAR_KEY),
                )
            }
        }
    }
}

private fun LazyListScope.tvDiagnosticsCrashSection(
    crash: CrashReport?,
    labels: TvDiagnosticsStrings,
) {
    item { DiagnosticsSectionTitle(labels.lastCrash) }
    if (crash == null) {
        item { Text(labels.noCrash, color = TvTextMuted, fontSize = 18.sp) }
    } else {
        item { DiagnosticsLine(tvCrashSummary(crash, labels), emphasized = true) }
        items(
            crash.stackTrace
                .lines()
                .filter { it.isNotBlank() }
                .take(TV_DIAGNOSTICS_MAX_CRASH_LINES),
        ) { line ->
            DiagnosticsLine(line)
        }
    }
}

private fun LazyListScope.tvDiagnosticsLogSection(
    logLines: List<String>,
    labels: TvDiagnosticsStrings,
) {
    item { Spacer(Modifier.height(8.dp)) }
    item { DiagnosticsSectionTitle(labels.recentLog) }
    if (logLines.isEmpty()) {
        item { Text(labels.noLogLines, color = TvTextMuted, fontSize = 18.sp) }
    } else {
        items(logLines) { line -> DiagnosticsLine(line) }
    }
}

@Composable
private fun DiagnosticsSectionTitle(title: String) {
    Text(title, modifier = Modifier.tvHeading(), color = TvText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
}

/** One focusable, read-only line; Left from it opens the navigation rail like everywhere else in Settings. */
@Composable
private fun DiagnosticsLine(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .tvFocusable(
                    onClick = null,
                    shape = RoundedCornerShape(10.dp),
                    scale = 1f,
                    focusToNavigationRailOnLeft = true,
                    fill = TvSurface,
                ).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (emphasized) TvText else TvTextMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun tvDeviceDescription(labels: TvDiagnosticsStrings): String =
    "${Build.MANUFACTURER} ${Build.MODEL}, ${labels.androidVersion(Build.VERSION.RELEASE, Build.VERSION.SDK_INT)}"

private fun tvCrashSummary(
    crash: CrashReport,
    labels: TvDiagnosticsStrings,
): String {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(crash.timestampMillis))
    return labels.crashSummary(time, crash.threadName, crash.appVersion)
}
