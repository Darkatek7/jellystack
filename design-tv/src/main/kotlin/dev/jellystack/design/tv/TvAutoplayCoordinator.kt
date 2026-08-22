@file:Suppress("FunctionName", "FunctionNaming", "MatchingDeclarationName")

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
import dev.jellystack.players.PlaybackContinuationState

internal data class TvAutoplayPromptModel(
    val title: String,
    val secondsRemaining: Int,
)

internal fun tvAutoplayPromptModel(state: PlaybackContinuationState): TvAutoplayPromptModel? =
    state.nextTarget
        ?.let { target ->
            state.countdownSecondsRemaining?.let { seconds -> TvAutoplayPromptModel(target.title, seconds) }
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
    model: TvAutoplayPromptModel,
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
            Text(model.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvText)
            Text(strings.playingInSeconds.format(model.secondsRemaining), fontSize = 19.sp, color = TvTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(strings.playNow, onPlayNow, primary = true)
                TvActionButton(strings.cancel, onCancel)
            }
        }
    }
}
