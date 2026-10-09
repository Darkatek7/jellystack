@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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

/** OK opens [onDetails], the remote's Play key calls [onPlay], Left and Right call [onCarouselMove]. */
internal data class TvHomeHeroCallbacks(
    val onPlay: () -> Unit,
    val onDetails: () -> Unit,
    val onVerticalMove: (TvHomeVerticalDirection) -> Unit,
    val onHeroFocused: () -> Unit,
    val onCarouselMove: (TvHomeCarouselDirection) -> Unit,
)

/** Crossfade between spotlights; instant when the user asked for reduced motion. */
@Composable
internal fun tvHomeHeroFadeMillis(): Int = if (LocalTvFocusAppearance.current.reducedMotion) 0 else TV_HOME_HERO_FADE_MS

internal fun tvHomeHeroFade(millis: Int): ContentTransform = fadeIn(tween(millis)).togetherWith(fadeOut(tween(millis)))

private const val TV_HOME_HERO_FADE_MS = 240

/**
 * The spotlight as one focusable card: text and page dots over [TvHomeBackdrop], no buttons. While it
 * has focus a hint row names the keys; the hero keeps its size, so the rows keep their position.
 */
@Composable
internal fun TvHeroCarousel(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    callbacks: TvHomeHeroCallbacks,
    strings: TvStrings,
    primaryFocusRequester: FocusRequester,
) {
    val stageItem = model.stageItem
    val previewing = trailer.state.showsTvHomeStagePreview(stageItem.id, model.stagePresentationId)
    val fadeMs = tvHomeHeroFadeMillis()
    val playLabel = if ((stageItem.positionTicks ?: 0L) > 0L) strings.continueLabel else strings.play
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TV_HOME_HERO_HEIGHT_DP.dp)
                .testTag("tv-home-preview-stage")
                .onFocusChanged { if (it.hasFocus) callbacks.onHeroFocused() }
                .focusRequester(primaryFocusRequester)
                .tvHomeSpotlightKeys(callbacks)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(playLabel) {
                                callbacks.onPlay()
                                true
                            },
                        )
                }.tvFocusable(
                    onClick = callbacks.onDetails,
                    shape = TvShapes.Surface,
                    scale = 1f,
                    onFocusChanged = { focused = it },
                    focusTargetId = TV_HOME_PRIMARY_TARGET,
                ),
    ) {
        if (previewing) {
            TvTrailerPreviewChrome(
                label = strings.trailer,
                previewSoundEnabled = trailer.soundEnabled,
                previewProgress = trailer.progress.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedContent(
            targetState = model,
            contentKey = { it.stageItem.id },
            transitionSpec = { tvHomeHeroFade(fadeMs) },
            modifier = Modifier.fillMaxSize(),
            label = "tv-home-hero-text",
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                TvHeroText(
                    model = shown,
                    strings = strings,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 28.dp, top = 26.dp, end = 24.dp, bottom = 84.dp)
                            .fillMaxWidth(0.52f),
                )
            }
        }
        if (focused) {
            TvHomeSpotlightHints(
                playLabel = playLabel,
                detailsLabel = strings.details,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 26.dp),
            )
        }
        val page = model.page
        if (page.count > 1 && model.showCarouselContext) {
            TvHomeHeroIndicator(
                page = page,
                contentDescription = strings.metadata.spotlightPosition.format(page.index + 1, page.count),
                // Below the clock, which sits in the screen's top-right corner.
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 16.dp),
            )
        }
    }
}

private fun Modifier.tvHomeSpotlightKeys(callbacks: TvHomeHeroCallbacks): Modifier =
    onPreviewKeyEvent { event ->
        val press = event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (press) callbacks.onCarouselMove(TvHomeCarouselDirection.PREVIOUS)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (press) callbacks.onCarouselMove(TvHomeCarouselDirection.NEXT)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (press && event.nativeKeyEvent.repeatCount == 0) callbacks.onPlay()
                true
            }
            else -> false
        }
    }.tvHomeVerticalFocus(callbacks.onVerticalMove)

/** "[OK] Details   [▶] Play": what the remote does on the focused spotlight. */
@Composable
private fun TvHomeSpotlightHints(
    playLabel: String,
    detailsLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvKeyHint(label = detailsLabel) {
            Text(TV_KEY_OK, color = TvText, fontSize = TvTextSize.Label, fontWeight = FontWeight.Bold)
        }
        TvKeyHint(label = playLabel) {
            Icon(Icons.Default.PlayArrow, null, tint = TvText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TvKeyHint(
    label: String,
    key: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .heightIn(min = 26.dp)
                .widthIn(min = 30.dp)
                .border(1.5.dp, TvText.copy(alpha = 0.7f), TvShapes.Badge)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            key()
        }
        Text(label, color = TvText, fontSize = TvTextSize.Body, fontWeight = FontWeight.SemiBold)
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
