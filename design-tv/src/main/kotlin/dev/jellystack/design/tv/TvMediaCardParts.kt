@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** Height of the title and subtitle lines below a card's artwork, including the gap to the artwork. */
internal val TV_CARD_CAPTION_HEIGHT: Dp = 46.dp

internal val TvBadgeAvailableGreen = Color(0xFF4CC38A)
internal val TvBadgeRequestedAmber = Color(0xFFF2B84B)
private val BadgeHeight = 24.dp

/** The card's badge in its corner; decorative, because the card's own semantics carry the badge description. */
@Composable
internal fun TvMediaCardBadgeView(
    badge: TvMediaCardBadge,
    modifier: Modifier = Modifier,
) {
    when (badge) {
        is TvMediaCardBadge.Watched ->
            BadgeCircle(Icons.Default.Check, TvPurple, TvBackground.copy(alpha = 0.78f), modifier)
        is TvMediaCardBadge.SeerrAvailable ->
            BadgeCircle(Icons.Default.Check, TvBackground, TvBadgeAvailableGreen, modifier)
        is TvMediaCardBadge.Unplayed ->
            BadgePill(modifier, TvPurpleStrong) {
                Text(
                    badge.count.toString(),
                    color = Color.White,
                    fontSize = TvTextSize.BodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        is TvMediaCardBadge.SeerrPartial ->
            BadgePill(modifier, TvBackground.copy(alpha = 0.84f)) {
                BadgeLabel(badge.label, TvBadgeAvailableGreen)
            }
        is TvMediaCardBadge.SeerrRequested ->
            BadgePill(modifier, TvBadgeRequestedAmber) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TvBackground,
                    modifier = Modifier.size(14.dp),
                )
                BadgeLabel(badge.label, TvBackground)
            }
    }
}

@Composable
private fun BadgeLabel(
    label: String,
    color: Color,
) {
    Text(label, color = color, fontSize = TvTextSize.Caption, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun BadgeCircle(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier,
) {
    Box(
        modifier.size(BadgeHeight + 2.dp).background(background, TvShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BadgePill(
    modifier: Modifier,
    background: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .height(BadgeHeight)
            .background(background, TvShapes.Pill)
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** Watch progress along the bottom edge of the artwork. */
@Composable
internal fun TvMediaCardProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(TvPurple),
        )
    }
}

/** Title and subtitle under the artwork, one line each; the artwork may already carry a title of its own. */
@Composable
internal fun TvMediaCardCaption(
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp),
    ) {
        title?.let {
            Text(
                it,
                color = TvText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = TvTextSize.CardTitle,
            )
        }
        subtitle?.let {
            Text(
                it,
                color = TvTextMuted,
                fontSize = TvTextSize.BodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cast cards keep their text at the bottom of the artwork, over a scrim that keeps it readable on bright images. */
@Composable
internal fun TvMediaCardOverlayText(
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .tvBakedScrim(TvScrim.MEDIA_CARD_TEXT)
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        title?.let {
            Text(
                it,
                color = TvText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = TvTextSize.Subtitle,
            )
        }
        subtitle?.let {
            Text(
                it,
                color = TvTextMuted,
                fontSize = TvTextSize.BodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
