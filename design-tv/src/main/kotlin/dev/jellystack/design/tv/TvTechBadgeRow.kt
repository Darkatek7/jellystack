@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** Format badges such as 4K, Dolby Vision and 5.1; outlined and unfocusable, so they read as facts, not buttons. */
@Composable
internal fun TvTechBadgeRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEach { label ->
            Text(
                label,
                color = TvText,
                fontSize = TvTextSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.32f), TvShapes.Badge)
                        .border(1.dp, TvText.copy(alpha = 0.45f), TvShapes.Badge)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}
