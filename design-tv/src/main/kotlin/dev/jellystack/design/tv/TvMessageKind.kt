@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A hint informs and stays neutral; an error says that something failed and uses the error colour and icon. */
internal enum class TvMessageKind { HINT, ERROR }

/** A wrapping message with an icon; it grows with the text instead of clipping it. */
@Composable
internal fun TvStatusMessage(
    text: String,
    kind: TvMessageKind,
    modifier: Modifier = Modifier,
) {
    val isError = kind == TvMessageKind.ERROR
    val tint = if (isError) TvError else TvTextMuted
    val background = if (isError) TvError.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.07f)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { if (isError) liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.Info,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(top = 2.dp).size(22.dp),
        )
        Text(text, color = if (isError) TvError else TvTextMuted, fontSize = 17.sp, lineHeight = 24.sp)
    }
}
