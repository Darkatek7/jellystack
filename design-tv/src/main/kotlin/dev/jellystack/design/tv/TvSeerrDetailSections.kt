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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

/** Ratings of the other services as plain tiles, the last section of a Seerr page. */
@Composable
private fun TvSeerrRatingsSection(
    ratings: JellyseerrMediaRatings,
    strings: TvStrings,
) {
    Column(
        Modifier.padding(start = TV_DETAIL_START_DP.dp, end = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSectionTitle(strings.detailScreen.ratings)
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOfNotNull(
                ratings.tmdb?.let { "$TV_BRAND_TMDB %.1f".format(it) },
                ratings.imdb?.let { "$TV_BRAND_IMDB %.1f".format(it) },
                ratings.rottenTomatoesCritics?.let { strings.metadata.rtCritics.format(it) },
                ratings.rottenTomatoesAudience?.let { strings.metadata.rtAudience.format(it) },
            ).forEach { value ->
                Box(
                    Modifier
                        .background(TvSurfaceRaised, TvShapes.Control)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Text(value, color = TvText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TvSeerrSimilarRow(
    title: String,
    items: List<TvDetailSimilarItem.Seerr>,
    focusModifiers: TvDetailSectionFocusModifiers,
    onOpenItem: (JellyseerrSearchItem) -> Unit,
) {
    Column(
        Modifier
            .padding(start = TV_DETAIL_START_DP.dp, end = 48.dp)
            .then(focusModifiers.navigationModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSectionTitle(title)
        LazyRow(
            state = focusModifiers.horizontalListState,
            modifier = Modifier.tvRowBleed(start = TV_DETAIL_START_DP.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = tvBleedRowPadding(0.dp, start = TV_DETAIL_START_DP.dp, end = 48.dp),
        ) {
            items(items, key = TvDetailSimilarItem.Seerr::id, contentType = { "media-card" }) { similarItem ->
                val item = similarItem.item
                TvMediaCard(
                    title = item.title,
                    imageUrl = tmdbImageUrl(item.backdropPath ?: item.posterPath, item.backdropPath != null),
                    onClick = { onOpenItem(item) },
                    subtitle = item.releaseYear,
                    artworkFit =
                        if (item.backdropPath == null && item.posterPath != null) {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        } else {
                            TvMediaCardArtworkFit.CROP
                        },
                    modifier = focusModifiers.itemModifier(similarItem.id),
                    providedFocusRequester = focusModifiers.itemFocusRequester(similarItem.id),
                )
            }
        }
    }
}

/** Sections of a Seerr detail page: Cast, Similar, Details, Ratings; each is exactly one lazy item. */
internal fun LazyListScope.tvSeerrDetailSections(
    uiState: TvDetailUiState,
    strings: TvStrings,
    bodyFocusModifier: Modifier,
    sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>,
    castActions: TvSeerrCastActions = TvSeerrCastActions.None,
    onOpenItem: (JellyseerrSearchItem) -> Unit,
) {
    uiState.sections.forEach { section ->
        when (section) {
            is TvDetailSection.Details -> item(section.id) { TvDetailDetailsSection(section, bodyFocusModifier, strings) }
            is TvDetailSection.Ratings -> item(section.id) { TvSeerrRatingsSection(section.values, strings) }
            is TvDetailSection.Cast ->
                item(section.id) {
                    TvSeerrCastSection(
                        title = strings.cast,
                        people = section.items.mapNotNull { it as? TvDetailCastItem.Seerr },
                        notFoundMessage = strings.detailScreen.castNotInLibrary,
                        actions = castActions,
                        focusModifiers = sectionFocusModifiers.getValue(section.id),
                    )
                }
            is TvDetailSection.Similar ->
                item(section.id) {
                    TvSeerrSimilarRow(
                        strings.similar,
                        section.items.mapNotNull { it as? TvDetailSimilarItem.Seerr },
                        sectionFocusModifiers.getValue(section.id),
                        onOpenItem,
                    )
                }
            is TvDetailSection.Seasons,
            is TvDetailSection.Episodes,
            -> Unit
        }
    }
    item("detail-bottom-spacer") { Spacer(Modifier.height(50.dp)) }
}
