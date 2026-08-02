@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class TvAutoplayTarget(
    val mediaId: String,
    val title: String,
    val play: suspend () -> Unit,
)

internal sealed interface TvAutoplayState {
    data object Idle : TvAutoplayState

    data class Countdown(
        val target: TvAutoplayTarget,
        val secondsRemaining: Int,
    ) : TvAutoplayState
}

internal class TvAutoplayCoordinator(
    private val scope: CoroutineScope,
    private val modeProvider: () -> AutoplayNextMode,
    private val resolveNext: suspend (mediaId: String, seriesId: String?) -> TvAutoplayTarget?,
) {
    private val mutableState = MutableStateFlow<TvAutoplayState>(TvAutoplayState.Idle)
    val state: StateFlow<TvAutoplayState> = mutableState.asStateFlow()

    private var handledMediaId: String? = null
    private var foreground = true
    private var resolutionJob: Job? = null
    private var countdownJob: Job? = null

    fun onPlaybackState(state: PlaybackState) {
        val active = state as? PlaybackState.Active ?: return
        if (active.phase != PlaybackPhase.Ended) return
        onPlaybackCompleted(active.mediaId, active.metadata?.seriesId)
    }

    internal fun onPlaybackCompleted(
        mediaId: String,
        seriesId: String?,
    ) {
        if (mediaId == handledMediaId) return
        handledMediaId = mediaId
        val mode = modeProvider()
        if (mode == AutoplayNextMode.OFF) return
        resolutionJob?.cancel()
        resolutionJob =
            scope.launch {
                val target = resolveNext(mediaId, seriesId) ?: return@launch
                if (mode == AutoplayNextMode.IMMEDIATE) {
                    launch(target)
                } else {
                    mutableState.value = TvAutoplayState.Countdown(target, COUNTDOWN_SECONDS)
                    continueCountdown()
                }
            }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) continueCountdown() else countdownJob?.cancel()
    }

    fun cancel() {
        resolutionJob?.cancel()
        countdownJob?.cancel()
        mutableState.value = TvAutoplayState.Idle
    }

    fun playNow() {
        val current = mutableState.value as? TvAutoplayState.Countdown ?: return
        launch(current.target)
    }

    fun release() {
        cancel()
    }

    private fun continueCountdown() {
        if (!foreground || countdownJob?.isActive == true) return
        val initial = mutableState.value as? TvAutoplayState.Countdown ?: return
        countdownJob =
            scope.launch {
                var remaining = initial.secondsRemaining
                while (remaining > 0 && foreground) {
                    delay(1_000L)
                    if (!foreground) return@launch
                    remaining -= 1
                    val current = mutableState.value as? TvAutoplayState.Countdown ?: return@launch
                    mutableState.value = current.copy(secondsRemaining = remaining)
                }
                if (remaining == 0) launch(initial.target)
            }
    }

    private fun launch(target: TvAutoplayTarget) {
        countdownJob?.cancel()
        mutableState.value = TvAutoplayState.Idle
        scope.launch { target.play() }
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 10
    }
}

internal fun selectNextTvEpisode(
    episodes: List<JellyfinItem>,
    currentMediaId: String,
): JellyfinItem? {
    val ordered =
        episodes.sortedWith(
            compareBy<JellyfinItem>(
                { it.parentIndexNumber ?: Int.MAX_VALUE },
                { it.indexNumber ?: Int.MAX_VALUE },
                { it.id },
            ),
        )
    val index = ordered.indexOfFirst { it.id == currentMediaId }
    return ordered.getOrNull(index + 1).takeIf { index >= 0 }
}

@Composable
internal fun TvAutoplayPrompt(
    state: TvAutoplayState.Countdown,
    strings: TvStrings,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .width(620.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(28.dp))
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(strings.nextEpisode, fontSize = 22.sp, color = TvPurple)
            Text(state.target.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvText)
            Text(strings.playingInSeconds.format(state.secondsRemaining), fontSize = 19.sp, color = TvTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(strings.playNow, onPlayNow, primary = true)
                TvActionButton(strings.cancel, onCancel)
            }
        }
    }
}
