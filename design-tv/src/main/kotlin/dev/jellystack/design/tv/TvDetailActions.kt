@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

internal const val TV_DETAIL_ROUND_BUTTON_DP = 58
private const val TV_DETAIL_ROUND_LABEL_HEIGHT_DP = 22

/** Toggle states of the detail actions; [trailerError] and [actionFailed] show a line below them. */
internal data class TvDetailHeroActionsState(
    val primaryLabel: String,
    val showStartOver: Boolean,
    val favorite: Boolean,
    val played: Boolean,
    val trailerError: Boolean,
    val actionFailed: Boolean = false,
)

/** [onTrailer] is null without a trailer and [onMore] without menu entries; those buttons are then hidden. */
internal class TvDetailHeroActionCallbacks(
    val onPlay: () -> Unit,
    val onStartOver: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePlayed: () -> Unit,
    val onTrailer: (() -> Unit)?,
    val onMore: (() -> Unit)? = null,
)

/**
 * The one button language of a detail page: a primary pill with a contextual label, a secondary "Start over" pill
 * when playing would resume, then round icon buttons. The modifiers come from the detail focus layout.
 */
@Composable
internal fun TvDetailHeroActions(
    state: TvDetailHeroActionsState,
    callbacks: TvDetailHeroActionCallbacks,
    strings: TvStrings,
    primaryActionModifier: Modifier,
    actionRowModifier: Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TV_DETAIL_ACTION_GAP_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TvActionButton(
            state.primaryLabel,
            primary = true,
            leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
            onClick = callbacks.onPlay,
            modifier = primaryActionModifier.then(actionRowModifier).widthIn(min = TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
        )
        if (state.showStartOver) {
            TvActionButton(
                strings.detailScreen.startOver,
                leading = { Icon(Icons.Default.Replay, null, tint = TvText) },
                onClick = callbacks.onStartOver,
                modifier = actionRowModifier,
            )
        }
        TvDetailRoundButton(
            icon = if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = strings.favorite,
            selected = state.favorite,
            onClick = callbacks.onToggleFavorite,
            modifier = actionRowModifier,
        )
        TvDetailRoundButton(
            icon = Icons.Default.CheckCircle,
            label = strings.watched,
            selected = state.played,
            onClick = callbacks.onTogglePlayed,
            modifier = actionRowModifier,
        )
        callbacks.onTrailer?.let { onTrailer ->
            TvDetailRoundButton(Icons.Default.LocalMovies, strings.trailer, onTrailer, actionRowModifier)
        }
        callbacks.onMore?.let { onMore ->
            TvDetailRoundButton(Icons.Default.MoreHoriz, strings.more, onMore, actionRowModifier)
        }
    }
    if (state.trailerError) {
        Text(strings.trailerOpenFailed, color = TvError, fontSize = TvTextSize.Body)
    }
    if (state.actionFailed) {
        Text(strings.diagnostics.actionFailed, color = TvError, fontSize = TvTextSize.Body)
    }
}

/**
 * A round icon button whose [label] shows under it while it has focus. The label's room is always reserved, so
 * focus never shifts the rows below.
 */
@Composable
internal fun TvDetailRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.width(TV_DETAIL_ROUND_BUTTON_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TV_DETAIL_ROUND_BUTTON_DP.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                }.tvFocusable(onClick = onClick, shape = TvShapes.Pill, onFocusChanged = { focused = it })
                .background(if (selected) TvPurpleStrong.copy(alpha = 0.55f) else TvSurfaceRaised, TvShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) TvPurple else TvText, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(TV_DETAIL_ROUND_LABEL_HEIGHT_DP.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                label,
                color = TvText,
                fontSize = TvTextSize.BodySmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(unbounded = true).graphicsLayer { alpha = if (focused) 1f else 0f },
            )
        }
    }
}

/**
 * The focusable line under the actions that names the audio, subtitles and version the next play will use; OK
 * opens the picker.
 */
@Composable
internal fun TvDetailTrackLine(
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = summary }
            .tvFocusable(
                onClick = onClick,
                shape = TvShapes.Pill,
                scale = 1.03f,
                fill = Color.Black.copy(alpha = 0.4f),
            ).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Tune, null, tint = TvTextMuted, modifier = Modifier.size(20.dp))
        Text(summary, color = TvText, fontSize = TvTextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
