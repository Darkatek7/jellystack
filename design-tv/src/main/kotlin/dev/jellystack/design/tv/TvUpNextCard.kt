@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** The next episode at the end of an episode: its still, "Up next", the title, and a play label. */
@Composable
internal fun TvUpNextCard(
    action: TvPlaybackActionModel,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val shape = TvShapes.Card
    Row(
        modifier
            .width(UP_NEXT_CARD_WIDTH_DP.dp)
            .tvFocusable(
                onClick = onClick,
                shape = shape,
                focusTargetId = action.id,
                providedFocusRequester = focusRequester,
            ).semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(action.label, action.detail).joinToString(": ")
            }.background(TvSurfaceRaised.copy(alpha = 0.96f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(UP_NEXT_STILL_WIDTH_DP.dp)
                .height((UP_NEXT_STILL_WIDTH_DP * 9 / 16).dp)
                .clip(TvShapes.Badge)
                .background(TvSurface),
        ) {
            action.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            action.kicker?.let {
                Text(
                    it,
                    color = TvPurple,
                    fontSize = TvTextSize.Caption,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Text(
                action.detail.orEmpty(),
                color = TvText,
                fontSize = TvTextSize.CardTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TvTextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    action.label,
                    color = TvTextMuted,
                    fontSize = TvTextSize.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val UP_NEXT_CARD_WIDTH_DP = 400
private const val UP_NEXT_STILL_WIDTH_DP = 144
