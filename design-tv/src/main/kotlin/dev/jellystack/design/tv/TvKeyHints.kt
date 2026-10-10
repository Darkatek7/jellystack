@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** "[OK] Details   [▶] Play": what the remote does on the focused spotlight or card. */
@Composable
internal fun TvPlayKeyHints(
    playLabel: String,
    detailsLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvKeyHint(label = detailsLabel) {
            Text(TV_KEY_OK, color = TvText, fontSize = TvTextSize.Label, fontWeight = FontWeight.Bold)
        }
        TvKeyHint(label = playLabel) {
            Icon(Icons.Default.PlayArrow, null, tint = TvText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TvKeyHint(
    label: String,
    key: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .heightIn(min = 26.dp)
                .widthIn(min = 30.dp)
                .border(1.5.dp, TvText.copy(alpha = 0.7f), TvShapes.Badge)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            key()
        }
        Text(label, color = TvText, fontSize = TvTextSize.Body, fontWeight = FontWeight.SemiBold)
    }
}
