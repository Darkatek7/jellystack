@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

internal const val TV_DETAIL_HERO_HEIGHT_DP = 520

/** Backdrop, title or logo, and the subtitle line; [actions] go below them in the same column. */
@Composable
internal fun BoxScope.TvDetailHero(
    model: TvDetailHeroModel,
    actions: @Composable () -> Unit,
) {
    AsyncImage(
        model = model.backdropUrl,
        contentDescription = model.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(TvBackground.copy(0.96f), TvBackground.copy(0.68f), Color.Transparent)),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.08f), TvBackground.copy(alpha = 0.12f), TvBackground),
            ),
        ),
    )
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 108.dp, end = 48.dp, bottom = 38.dp)
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (model.logoUrl != null) {
            AsyncImage(
                model = model.logoUrl,
                contentDescription = model.title,
                modifier = Modifier.widthIn(max = 380.dp).heightIn(max = 120.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                model.title,
                color = model.titleColor,
                fontSize = 46.sp,
                lineHeight = 49.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
        Text(model.subtitle, color = TvTextMuted, fontSize = 19.sp)
        actions()
    }
}

/** What the detail hero shows above its actions; [logoUrl] replaces the title text when the item has a logo. */
internal data class TvDetailHeroModel(
    val title: String,
    val backdropUrl: String?,
    val logoUrl: String?,
    val titleColor: Color,
    val subtitle: String,
)

/** Toggle states of the detail actions; [trailerError] shows the line below them. */
internal data class TvDetailHeroActionsState(
    val resume: Boolean,
    val favorite: Boolean,
    val played: Boolean,
    val trailerError: Boolean,
)

/** [onTrailer] is null when the item has no trailer, which hides that button. */
internal class TvDetailHeroActionCallbacks(
    val onPlay: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePlayed: () -> Unit,
    val onTrailer: (() -> Unit)?,
)

/** Play, Favorite, Watched and Trailer; the modifiers come from the detail focus layout. */
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvActionButton(
            if (state.resume) strings.continueLabel else strings.play,
            primary = true,
            leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
            onClick = callbacks.onPlay,
            modifier = primaryActionModifier.then(actionRowModifier).width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
        )
        TvCompactActionButton(
            label = strings.favorite,
            onClick = callbacks.onToggleFavorite,
            icon = if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            selected = state.favorite,
            modifier = actionRowModifier,
        )
        TvCompactActionButton(
            label = strings.watched,
            icon = Icons.Default.CheckCircle,
            selected = state.played,
            onClick = callbacks.onTogglePlayed,
            modifier = actionRowModifier,
        )
        callbacks.onTrailer?.let { onTrailer ->
            TvCompactActionButton(
                label = strings.trailer,
                icon = Icons.Default.LocalMovies,
                onClick = onTrailer,
                modifier = actionRowModifier,
            )
        }
    }
    if (state.trailerError) {
        Text(strings.trailerOpenFailed, color = Color(0xFFFFA59E), fontSize = 16.sp)
    }
}
