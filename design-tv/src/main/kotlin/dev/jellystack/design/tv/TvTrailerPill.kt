@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * A small see-through pill that names the playing trailer and whether its sound is on; a thin line along its
 * bottom edge shows how far the trailer has played. It sits in a corner, so it never crosses text or rows.
 */
@Composable
internal fun TvTrailerPill(
    label: String,
    soundEnabled: Boolean,
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(TV_TRAILER_PILL_SHAPE)
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), TV_TRAILER_PILL_SHAPE)
            .drawWithContent {
                drawContent()
                // Read while drawing, so the playing trailer redraws only the line.
                val played = progress.value.coerceIn(0f, 1f)
                val lineHeight = 2.dp.toPx()
                drawRect(
                    color = TvPurple,
                    topLeft = Offset(0f, size.height - lineHeight),
                    size = Size(size.width * played, lineHeight),
                )
            }.padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("tv-trailer-pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = TV_TRAILER_PILL_CONTENT, fontSize = TvTextSize.Caption, fontWeight = FontWeight.SemiBold)
        Icon(
            if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            tint = TV_TRAILER_PILL_CONTENT,
            modifier = Modifier.size(15.dp),
        )
    }
}

private val TV_TRAILER_PILL_SHAPE = RoundedCornerShape(percent = 50)
private val TV_TRAILER_PILL_CONTENT = Color.White.copy(alpha = 0.92f)
