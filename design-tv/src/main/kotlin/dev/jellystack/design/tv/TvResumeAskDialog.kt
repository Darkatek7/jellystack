@file:Suppress("FunctionName")

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
import dev.jellystack.players.PlaybackStartPolicy

/** "Resume or restart?" prompt for [TvPlaybackLauncher]; `onAnswer(null)` cancels. */
@Composable
internal fun TvResumeAskDialog(
    positionLabel: String,
    strings: TvStrings,
    onAnswer: (PlaybackStartPolicy?) -> Unit,
) {
    Dialog(onDismissRequest = { onAnswer(null) }) {
        Column(
            Modifier
                .width(620.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(28.dp))
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                strings.resumeAskTitle,
                modifier = Modifier.tvHeading(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TvText,
            )
            Text(
                strings.continueFrom.format(positionLabel),
                fontSize = 19.sp,
                color = TvTextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    strings.continueLabel,
                    primary = true,
                    modifier = Modifier.width(230.dp),
                    onClick = { onAnswer(PlaybackStartPolicy.RESUME) },
                )
                TvActionButton(
                    strings.restart,
                    modifier = Modifier.width(230.dp),
                    onClick = { onAnswer(PlaybackStartPolicy.RESTART) },
                )
                TvActionButton(strings.cancel, onClick = { onAnswer(null) })
            }
        }
    }
}
