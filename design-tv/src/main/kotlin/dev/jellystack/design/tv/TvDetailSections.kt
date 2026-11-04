@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson

/**
 * The Details section at the bottom of every detail page: the full overview, the tagline and the credits. It is the
 * body focus target, so the D-pad can scroll the page down to it; a bar at its start shows the focus.
 */
@Composable
internal fun TvDetailDetailsSection(
    section: TvDetailSection.Details,
    bodyFocusModifier: Modifier,
    strings: TvStrings,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(start = TV_DETAIL_START_DP.dp, end = 48.dp)
            .onFocusChanged { focused = it.hasFocus }
            .then(bodyFocusModifier)
            .fillMaxWidth(0.78f)
            .drawBehind {
                if (focused) {
                    drawRoundRect(
                        color = TvPurple,
                        topLeft = Offset(-14.dp.toPx(), 0f),
                        size = Size(4.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvSectionTitle(strings.detailText.details)
        section.tagline?.let {
            Text(it, color = TvPurple, fontSize = TvTextSize.BodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(section.text ?: strings.detailText.noOverview, color = TvText, fontSize = TvTextSize.BodyLarge, lineHeight = 27.sp)
        section.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(row.label, color = TvTextMuted, fontSize = TvTextSize.Body, modifier = Modifier.width(150.dp))
                Text(row.value, color = TvText, fontSize = TvTextSize.Body, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Season selector as tabs: text with an underline under the selected one. OK on a tab shows that season. */
@Composable
internal fun TvDetailSeasonTabs(
    section: TvDetailSection.Seasons,
    strings: TvStrings,
    focusModifiers: TvDetailSectionFocusModifiers,
    onSelectSeason: (Int) -> Unit,
) {
    val selectedIndex = section.selectedIndex.coerceIn(section.groups.indices)
    Column(
        Modifier
            .padding(start = TV_DETAIL_START_DP.dp, end = 48.dp)
            .then(focusModifiers.navigationModifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LazyRow(
            state = focusModifiers.horizontalListState,
            modifier = Modifier.tvRowBleed(start = TV_DETAIL_START_DP.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = TV_DETAIL_START_DP.dp - 6.dp, end = 48.dp, top = 6.dp, bottom = 6.dp),
        ) {
            itemsIndexed(section.groups, key = { _, group -> "season-${group.seasonNumber ?: Int.MAX_VALUE}" }) { index, group ->
                val label = group.seasonNumber?.let { strings.detailText.seasonNumber.format(it) } ?: strings.detailText.specials
                val itemId = section.itemIds[index]
                TvDetailSeasonTab(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelectSeason(index) },
                    modifier = focusModifiers.itemModifier(itemId),
                    focusRequester = focusModifiers.itemFocusRequester(itemId),
                )
            }
        }
    }
}

@Composable
private fun TvDetailSeasonTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester?,
) {
    Text(
        label,
        color = if (selected) TvText else TvTextMuted,
        fontSize = TvTextSize.SectionTitle,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier =
            modifier
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }.tvFocusable(
                    onClick = onClick,
                    shape = TvShapes.Pill,
                    scale = 1.04f,
                    providedFocusRequester = focusRequester,
                    fill = Color.Transparent,
                ).drawBehind {
                    if (selected) {
                        drawRoundRect(
                            color = TvPurple,
                            topLeft = Offset(18.dp.toPx(), size.height - 6.dp.toPx()),
                            size = Size(size.width - 36.dp.toPx(), 3.dp.toPx()),
                            cornerRadius = CornerRadius(1.5.dp.toPx()),
                        )
                    }
                }.padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** Similar titles of Jellyfin: the same cards as every media row. */
@Composable
internal fun TvDetailSimilarRow(
    title: String,
    items: List<JellyfinItem>,
    homeState: JellyfinHomeState,
    onOpenItem: (JellyfinItem) -> Unit,
    focusModifiers: TvDetailSectionFocusModifiers,
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
            items(items, key = JellyfinItem::id, contentType = { "media-card" }) { item ->
                val hasLandscapeArtwork = item.seriesThumbImageTag != null || item.thumbImageTag != null
                TvMediaCard(
                    title = item.episodeTitle ?: item.name,
                    subtitle =
                        listOfNotNull(item.productionYear?.toString(), tvRatingLabel(item.communityRating))
                            .joinToString(TV_CARD_SEPARATOR),
                    watched = item.isPlayed,
                    imageUrl =
                        jellyfinImageUrl(
                            homeState.imageBaseUrl,
                            homeState.imageAccessToken,
                            item.seriesId ?: item.id,
                            item.seriesThumbImageTag ?: item.thumbImageTag ?: item.primaryImageTag,
                            if (hasLandscapeArtwork) "Thumb" else "Primary",
                        ),
                    artworkFit =
                        if (hasLandscapeArtwork) {
                            TvMediaCardArtworkFit.CROP
                        } else {
                            TvMediaCardArtworkFit.CONTAIN_PORTRAIT
                        },
                    onClick = { onOpenItem(item) },
                    modifier = focusModifiers.itemModifier(item.id),
                    providedFocusRequester = focusModifiers.itemFocusRequester(item.id),
                )
            }
        }
    }
}

/**
 * The sections of a Jellyfin detail page in the order of [uiState]: Seasons, Episodes or More from season, Cast,
 * Similar, Details. Every section is exactly one lazy item, which the focus layout relies on.
 */
internal fun LazyListScope.tvJellyfinDetailSections(
    uiState: TvDetailUiState,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    bodyFocusModifier: Modifier,
    sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>,
    onOpenItem: (JellyfinItem) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onOpenPerson: (JellyfinPerson) -> Unit = {},
) {
    uiState.sections.forEach { section ->
        when (section) {
            is TvDetailSection.Seasons ->
                item(section.id) {
                    TvDetailSeasonTabs(section, strings, sectionFocusModifiers.getValue(section.id), onSelectSeason)
                }
            is TvDetailSection.Episodes ->
                item(section.id) {
                    // Season tabs right above already say what the row holds.
                    val title =
                        section.moreFromSeason
                            ?.let { strings.detailScreen.moreFromSeasonFormat.format(it) }
                            ?: strings.detailText.episodes.takeIf { uiState.sections.none { it is TvDetailSection.Seasons } }
                    TvDetailEpisodeRow(title, section, homeState, strings, onOpenItem, sectionFocusModifiers.getValue(section.id))
                }
            is TvDetailSection.Cast ->
                item(section.id) {
                    val cards =
                        section.items.mapNotNull { it as? TvDetailCastItem.Jellyfin }.map { castItem ->
                            val person = castItem.person
                            TvDetailCastCard(
                                id = castItem.id,
                                name = person.name,
                                role = person.role,
                                imageUrl =
                                    jellyfinImageUrl(
                                        homeState.imageBaseUrl,
                                        homeState.imageAccessToken,
                                        person.id,
                                        person.primaryImageTag,
                                        "Primary",
                                        TvArtworkSize.PORTRAIT_CARD.maxWidth,
                                    ),
                                onClick = { onOpenPerson(person) },
                            )
                        }
                    TvDetailCastRow(strings.detailText.cast, cards, sectionFocusModifiers.getValue(section.id))
                }
            is TvDetailSection.Similar ->
                item(section.id) {
                    val items = section.items.mapNotNull { (it as? TvDetailSimilarItem.Jellyfin)?.item }
                    TvDetailSimilarRow(strings.detailText.similar, items, homeState, onOpenItem, sectionFocusModifiers.getValue(section.id))
                }
            is TvDetailSection.Details ->
                item(section.id) { TvDetailDetailsSection(section, bodyFocusModifier, strings) }
            is TvDetailSection.Ratings -> Unit
        }
    }
    item("detail-bottom-spacer") { Spacer(Modifier.height(50.dp)) }
}
