@file:Suppress("FunctionName", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** Height of the hero of a movie or episode; the content column sits on its bottom edge. */
internal const val TV_DETAIL_HERO_HEIGHT_DP = 520

/** Series are shorter so the season tabs peek above the fold. */
internal const val TV_DETAIL_SERIES_HERO_HEIGHT_DP = 392

internal const val TV_DETAIL_START_DP = 108
private const val TV_DETAIL_OVERVIEW_LINES = 3

/**
 * Backdrop, then the information a viewer decides by: title or logo, the one-line facts, genres or series line,
 * format badges and three lines of overview; [actions] go below them in the same column.
 */
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
    Box(Modifier.fillMaxSize().tvBakedScrim(TvScrim.DETAIL_HERO))
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = TV_DETAIL_START_DP.dp, end = 48.dp, bottom = 30.dp)
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (model.logoUrl != null) {
            AsyncImage(
                model = model.logoUrl,
                contentDescription = model.title,
                modifier = Modifier.widthIn(max = 380.dp).heightIn(max = 100.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                model.title,
                color = model.titleColor,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (model.metaLine.isNotBlank()) {
            Text(model.metaLine, color = TvText, fontSize = TvTextSize.BodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (model.subtitle.isNotBlank()) {
            Text(model.subtitle, color = TvTextMuted, fontSize = TvTextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (model.badges.isNotEmpty()) TvTechBadgeRow(model.badges)
        TvDetailHeroOverview(model.tagline, model.overview)
        actions()
    }
}

/** The tagline and three lines of the overview; the full text is in the Details section. */
@Composable
internal fun TvDetailHeroOverview(
    tagline: String?,
    overview: String?,
) {
    tagline?.let {
        Text(
            it,
            color = TvPurple,
            fontSize = TvTextSize.Body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    overview?.let {
        Text(
            it,
            color = TvText.copy(alpha = 0.9f),
            fontSize = TvTextSize.Body,
            lineHeight = 22.sp,
            maxLines = TV_DETAIL_OVERVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the detail hero shows above its actions; [logoUrl] replaces the title text when the item has a logo,
 * [metaLine] holds year, runtime, ratings and "Ends at", [subtitle] the genres or the series and episode, and
 * [badges] are format labels such as 4K or Dolby Atmos.
 */
internal data class TvDetailHeroModel(
    val title: String,
    val backdropUrl: String?,
    val logoUrl: String?,
    val titleColor: Color,
    val metaLine: String,
    val subtitle: String,
    val badges: List<String> = emptyList(),
    val tagline: String? = null,
    val overview: String? = null,
)

/** The line under the Seerr title while the detail loads or failed to load. */
@Composable
internal fun TvSeerrDetailLoadingLine(
    status: TvSeerrDetailStatus,
    strings: TvStrings,
) {
    when (status) {
        TvSeerrDetailStatus.LOADING ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = TvPurple, strokeWidth = 2.dp)
                Text(strings.messages.loadingDetails, color = TvTextMuted, fontSize = TvTextSize.BodyLarge)
            }
        TvSeerrDetailStatus.FAILED -> Text(strings.detailText.detailLoadFailed, color = TvError, fontSize = TvTextSize.BodyLarge)
        TvSeerrDetailStatus.READY -> Unit
    }
}
