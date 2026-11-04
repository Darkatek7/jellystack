@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

private const val TV_DETAIL_CAST_AVATAR_DP = 120
private const val TV_DETAIL_CAST_ITEM_WIDTH_DP = 136

/** One person of the cast row; the same for Jellyfin and Seerr people. */
internal data class TvDetailCastCard(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
    /** Null for people that cannot be opened (Seerr), which still take focus so the row can be scrolled. */
    val onClick: (() -> Unit)?,
)

/** A round avatar with the name and the role below it; only the avatar carries the focus decoration. */
@Composable
internal fun TvDetailCastAvatar(
    card: TvDetailCastCard,
    modifier: Modifier = Modifier,
    providedFocusRequester: FocusRequester? = null,
) {
    Column(
        Modifier.width(TV_DETAIL_CAST_ITEM_WIDTH_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier
                .size(TV_DETAIL_CAST_AVATAR_DP.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(card.name, card.role).joinToString(", ")
                }.tvFocusable(
                    onClick = card.onClick,
                    shape = TvShapes.Pill,
                    providedFocusRequester = providedFocusRequester,
                ).background(TvSurfaceRaised, TvShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            // A person whose photo fails to load shows the initial instead of an empty circle.
            var imageFailed by remember(card.imageUrl) { mutableStateOf(false) }
            if (card.imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(TV_DETAIL_CAST_AVATAR_DP.dp),
                    onError = { imageFailed = true },
                )
            } else {
                Text(
                    card.name
                        .trim()
                        .take(1)
                        .uppercase(),
                    color = TvTextMuted,
                    fontSize = TvTextSize.Display,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            card.name,
            color = TvText,
            fontSize = TvTextSize.Body,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        card.role?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                color = TvTextMuted,
                fontSize = TvTextSize.BodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Cast section: a title and a row of round avatars. */
@Composable
internal fun TvDetailCastRow(
    title: String,
    cards: List<TvDetailCastCard>,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = TV_DETAIL_START_DP.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
        ) {
            items(cards, key = TvDetailCastCard::id, contentType = { "cast-avatar" }) { card ->
                TvDetailCastAvatar(
                    card = card,
                    modifier = focusModifiers.itemModifier(card.id),
                    providedFocusRequester = focusModifiers.itemFocusRequester(card.id),
                )
            }
        }
    }
}
