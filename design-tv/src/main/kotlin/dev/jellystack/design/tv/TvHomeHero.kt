@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
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

/**
 * The spotlight trailer: its state, whether its sound is on, its progress, and the surface that shows it.
 * [homeUiAlpha] is how much of the home UI shows over it, from [rememberTvHomeUiAlpha].
 */
internal class TvHomeHeroTrailer(
    val state: TvTrailerPreviewState,
    val soundEnabled: Boolean,
    val progress: State<Float>,
    val surface: @Composable (Modifier) -> Unit,
    val homeUiAlpha: State<Float>,
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
 * The spotlight is the whole top of the home screen, from edge to edge and down to the rows: text and page dots
 * over [TvHomeBackdrop], no buttons and no frame. Focus shows as a hint row that names the keys; the hero keeps
 * its size, so the rows keep their position.
 */
@Composable
internal fun TvHeroCarousel(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    callbacks: TvHomeHeroCallbacks,
    strings: TvStrings,
    primaryFocusRequester: FocusRequester,
) {
    val playLabel = if ((model.stageItem.positionTicks ?: 0L) > 0L) strings.continueLabel else strings.play
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TV_HOME_SPOTLIGHT_HEIGHT)
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
                    shape = TV_HOME_SPOTLIGHT_SHAPE,
                    scale = 1f,
                    onFocusChanged = { focused = it },
                    focusTargetId = TV_HOME_PRIMARY_TARGET,
                    focusIndication = TvFocusIndication.NONE,
                ),
    ) {
        // The spotlight keeps its focus while its content fades out for the trailer.
        TvHomeFadeLayer(trailer.homeUiAlpha, Modifier.fillMaxSize()) {
            TvHomeSpotlightContent(model, trailer, strings, playLabel, focused)
        }
    }
}

@Composable
private fun BoxScope.TvHomeSpotlightContent(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    strings: TvStrings,
    playLabel: String,
    focused: Boolean,
) {
    val previewing = trailer.state.showsTvHomeStagePreview(model.stageItem.id, model.stagePresentationId)
    val fadeMs = tvHomeHeroFadeMillis()
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
                        .padding(
                            start = TvLayoutTokens.ContentStart,
                            top = TvLayoutTokens.SafeInsets.vertical + 26.dp,
                            bottom = 84.dp,
                        ).fillMaxWidth(0.5f),
            )
        }
    }
    if (focused) {
        TvPlayKeyHints(
            playLabel = playLabel,
            detailsLabel = strings.details,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = TvLayoutTokens.ContentStart, bottom = 26.dp),
        )
    }
    // The spotlight does not page while a trailer plays, so its dots would only mislead.
    if (model.page.count > 1 && model.showCarouselContext && !previewing) {
        TvHomeSpotlightPageDots(model.page, strings, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun TvHomeSpotlightPageDots(
    page: TvHomeHeroPage,
    strings: TvStrings,
    modifier: Modifier,
) {
    TvHomeHeroIndicator(
        page = page,
        contentDescription = strings.metadata.spotlightPosition.format(page.index + 1, page.count),
        // Level with the key hints, below the clock's corner.
        modifier = modifier.padding(end = TvLayoutTokens.SafeInsets.horizontal + 16.dp, bottom = 35.dp),
    )
}

/** From the screen's top edge down to just above the first row. */
internal val TV_HOME_SPOTLIGHT_HEIGHT = TvLayoutTokens.SafeInsets.vertical + TV_HOME_HERO_HEIGHT_DP.dp

private val TV_HOME_SPOTLIGHT_SHAPE = RoundedCornerShape(0.dp)

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
        TvStageTitle(
            title = item.seriesName ?: item.name,
            logoUrl = tvJellyfinLogoUrl(model.imageBaseUrl, model.imageAccessToken, item),
        )
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
        item.tvStageMetadata()?.let { Text(it, color = TvTextMuted, fontSize = 15.sp) }
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
