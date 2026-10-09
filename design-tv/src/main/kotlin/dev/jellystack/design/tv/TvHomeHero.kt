@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem

/** What the home hero shows: the staged item and, while it is the carousel's own item, the carousel context. */
internal data class TvHomeHeroModel(
    val stageItem: JellyfinItem,
    val stagePresentationId: String?,
    val mode: TvHomeHeroMode,
    val page: TvHomeHeroPage,
    val showCarouselContext: Boolean,
    val imageBaseUrl: String?,
    val imageAccessToken: String?,
)

/** The spotlight trailer: its state, whether its sound is on, its progress, and the surface that shows it. */
internal class TvHomeHeroTrailer(
    val state: TvTrailerPreviewState,
    val soundEnabled: Boolean,
    val progress: State<Float>,
    val surface: @Composable (Modifier) -> Unit,
)

internal data class TvHomeHeroCallbacks(
    val onPlay: () -> Unit,
    val onDetails: () -> Unit,
    val onActionVerticalMove: (TvHomeVerticalDirection) -> Unit,
    val onHeroFocused: () -> Unit,
    val onCarouselMove: (TvHomeCarouselDirection) -> Unit,
    val onIndicatorVerticalMove: (TvHomeVerticalDirection) -> Unit,
)

@Composable
internal fun TvHeroCarousel(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    callbacks: TvHomeHeroCallbacks,
    strings: TvStrings,
    primaryFocusRequester: FocusRequester,
) {
    val shape = TvShapes.Surface
    val stageItem = model.stageItem
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TV_HOME_HERO_HEIGHT_DP.dp)
                .clip(shape)
                .background(TvBackground)
                .border(
                    width = 0.5.dp,
                    color = TvText.copy(alpha = 0.08f),
                    shape = shape,
                ).testTag("tv-home-preview-stage")
                .onFocusChanged { if (it.hasFocus) callbacks.onHeroFocused() },
    ) {
        // Keyed on the shown item, so moving between spotlights and row cards crossfades.
        AnimatedContent(
            targetState = stageItem,
            contentKey = { it.id },
            transitionSpec = { fadeIn(tween(240)).togetherWith(fadeOut(tween(240))) },
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            TvHeroSlide(model, trailer, strings)
        }
        TvTrailerAwareActions(
            previewing = trailer.state.showsTvHomeStagePreview(stageItem.id, model.stagePresentationId),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 22.dp),
        ) {
            TvHomeHeroButtons(stageItem, callbacks, strings, primaryFocusRequester)
        }
        val page = model.page
        if (page.count > 1 && model.showCarouselContext) {
            TvHomeHeroIndicator(
                page = page,
                contentDescription = strings.metadata.spotlightPosition.format(page.index + 1, page.count),
                onMove = callbacks.onCarouselMove,
                onVerticalMove = callbacks.onIndicatorVerticalMove,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun TvHomeHeroButtons(
    stageItem: JellyfinItem,
    callbacks: TvHomeHeroCallbacks,
    strings: TvStrings,
    primaryFocusRequester: FocusRequester,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvActionButton(
            label = if ((stageItem.positionTicks ?: 0L) > 0L) strings.continueLabel else strings.play,
            primary = true,
            onClick = callbacks.onPlay,
            leading = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF251450)) },
            modifier =
                Modifier
                    .width(180.dp)
                    .focusRequester(primaryFocusRequester)
                    .tvHomeVerticalFocus(callbacks.onActionVerticalMove),
            focusToNavigationRailOnLeft = true,
            focusTargetId = TV_HOME_PRIMARY_TARGET,
        )
        TvActionButton(
            label = strings.details,
            onClick = callbacks.onDetails,
            leading = { Icon(Icons.Default.Info, null, tint = TvText) },
            modifier =
                Modifier
                    .width(156.dp)
                    .tvHomeVerticalFocus(callbacks.onActionVerticalMove),
            focusTargetId = TV_HOME_DETAILS_TARGET,
        )
    }
}

@Composable
private fun TvHeroSlide(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    strings: TvStrings,
) {
    val item = model.stageItem
    val previewing = trailer.state.showsTvHomeStagePreview(item.id, model.stagePresentationId)
    Box(Modifier.fillMaxSize()) {
        if (previewing) {
            trailer.surface(Modifier.fillMaxSize().testTag("tv-home-hero-preview-surface"))
        } else {
            TvHeroBackdrop(
                jellyfinImageUrl(
                    model.imageBaseUrl,
                    model.imageAccessToken,
                    resolveTvHeroBackdrop(item),
                    TvArtworkSize.HERO,
                ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            TvBackground,
                            TvBackground.copy(alpha = 0.94f),
                            TvBackground.copy(alpha = 0.64f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(TvBackground.copy(alpha = 0.42f), Color.Transparent, TvBackground.copy(alpha = 0.94f)),
                    ),
                ),
        )
        if (previewing) {
            TvTrailerPreviewChrome(
                label = strings.trailer,
                previewSoundEnabled = trailer.soundEnabled,
                previewProgress = trailer.progress.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
        TvHeroText(
            model = model,
            strings = strings,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 26.dp, end = 24.dp, bottom = 84.dp)
                    .fillMaxWidth(0.52f),
        )
    }
}

@Composable
private fun TvHeroBackdrop(backdropUrl: String?) {
    if (backdropUrl == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF171824), Color(0xFF2B2342), Color(0xFF11121A)))),
        )
    } else {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun TvHeroText(
    model: TvHomeHeroModel,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val item = model.stageItem
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // The spotlight label describes the carousel, so a staged row card does not show it.
        if (model.showCarouselContext) TvHeroModeLabel(model.mode, strings)
        val logoTag = item.logoImageTag ?: item.parentLogoImageTag
        if (logoTag != null) {
            AsyncImage(
                model =
                    jellyfinImageUrl(
                        model.imageBaseUrl,
                        model.imageAccessToken,
                        item.seriesId ?: item.id,
                        logoTag,
                        "Logo",
                        TvArtworkSize.LOGO.maxWidth,
                    ),
                contentDescription = null,
                modifier = Modifier.widthIn(max = 390.dp).heightIn(max = 68.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                item.seriesName ?: item.name,
                color = TvText,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.overview?.takeIf(String::isNotBlank)?.let { overview ->
            Text(
                overview,
                color = TvTextMuted,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val metadata =
            listOfNotNull(
                item.productionYear?.toString(),
                tvRatingLabel(item.communityRating),
                item.officialRating,
            ).joinToString("  •  ")
        if (metadata.isNotBlank()) Text(metadata, color = TvTextMuted, fontSize = 15.sp)
    }
}

@Composable
private fun TvHeroModeLabel(
    mode: TvHomeHeroMode,
    strings: TvStrings,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, null, tint = TvPurple, modifier = Modifier.size(19.dp))
        Text(
            when (mode) {
                TvHomeHeroMode.RECENT -> strings.recentlyAdded
                TvHomeHeroMode.LATEST -> strings.latestAdditions
                TvHomeHeroMode.LIBRARY -> strings.fromYourLibrary
                TvHomeHeroMode.EMPTY -> strings.fromYourLibrary
            },
            color = TvPurple,
            fontSize = TvTextSize.Body,
            fontWeight = FontWeight.SemiBold,
        )
        if (mode == TvHomeHeroMode.RECENT) {
            Text("·  ${strings.lastThirtyDays}", color = TvTextMuted, fontSize = TvTextSize.CardTitle)
        }
    }
}

private fun resolveTvHeroBackdrop(item: JellyfinItem): TvJellyfinArtwork? {
    val seriesId = item.seriesId
    return when {
        !seriesId.isNullOrBlank() && !item.seriesBackdropImageTag.isNullOrBlank() ->
            TvJellyfinArtwork(seriesId, requireNotNull(item.seriesBackdropImageTag), "Backdrop")
        !item.backdropImageTag.isNullOrBlank() ->
            TvJellyfinArtwork(item.id, requireNotNull(item.backdropImageTag), "Backdrop")
        else -> resolveTvJellyfinArtwork(item)
    }
}

@Composable
internal fun TvEmptyHomeHero(
    state: JellyfinHomeState,
    strings: TvStrings,
    onRefresh: () -> Unit,
    primaryFocusRequester: FocusRequester,
    onVerticalMove: (TvHomeVerticalDirection) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TV_HOME_HERO_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(TvSurfaceRaised, TvPurpleStrong.copy(alpha = 0.32f), TvBackground),
                    ),
                ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp).fillMaxWidth(0.58f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = TvPurple, modifier = Modifier.size(18.dp))
                Text(TV_BRAND_JELLYSTACK, color = TvPurple, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(TV_BRAND_JELLYSTACK, color = TvText, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                state.homeErrorMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: if (state.isHomeLoading || state.isInitialLoading) strings.loading else strings.noResults,
                color = TvTextMuted,
                fontSize = 16.sp,
                maxLines = 2,
            )
            TvActionButton(
                label = strings.retry,
                onClick = onRefresh,
                primary = true,
                modifier =
                    Modifier
                        .width(180.dp)
                        .focusRequester(primaryFocusRequester)
                        .tvScreenEntryFocus(focusTargetId = TV_HOME_PRIMARY_TARGET)
                        .tvHomeVerticalFocus(onVerticalMove),
                focusToNavigationRailOnLeft = true,
                focusTargetId = TV_HOME_PRIMARY_TARGET,
            )
        }
    }
}
