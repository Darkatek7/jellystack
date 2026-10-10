@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.players.AndroidPlayerEngine

/** Browse rows start where the home rows start, so cards never cover the stage text. */
internal val TV_CINEMATIC_ROWS_TOP: Dp = TvLayoutTokens.SafeInsets.vertical + TV_HOME_HERO_HEIGHT_DP.dp + 12.dp
internal val TV_CINEMATIC_STAGE_HEIGHT: Dp = TV_CINEMATIC_ROWS_TOP

/**
 * The focused card's artwork behind the whole screen, as on the home screen; a playing trailer replaces it.
 * The scrims only keep the text readable, so they fade with [uiAlpha] while the trailer has the screen.
 */
@Composable
internal fun TvCinematicBackdropLayer(
    backdrop: TvCinematicBackdrop,
    trailerEngine: AndroidPlayerEngine?,
    uiAlpha: State<Float>,
) {
    Box(Modifier.fillMaxSize()) {
        if (trailerEngine != null) {
            TvTrailerPreviewSurface(trailerEngine, Modifier.fillMaxSize().testTag("cinematic-preview-player-surface"))
        } else {
            val transitionMillis = if (LocalTvFocusAppearance.current.reducedMotion) 0 else backdrop.transitionMillis
            AnimatedContent(
                targetState = backdrop.url,
                modifier = Modifier.fillMaxSize().testTag("cinematic-backdrop"),
                transitionSpec = { fadeIn(tween(transitionMillis)) togetherWith fadeOut(tween(transitionMillis)) },
                label = "cinematic-backdrop-crossfade",
            ) { url ->
                TvHomeBackdropImage(url)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = uiAlpha.value }) {
            TvHomeBackdropScrims()
        }
    }
}

/**
 * The top of a cinematic browse screen, where the home spotlight sits: the focused card's text and, for a card
 * that plays, the key hints. It never takes focus; the rows below it do.
 */
@Composable
internal fun TvCinematicStage(
    hero: TvCinematicHero?,
    focusedCard: TvCinematicCard?,
    playback: TvCinematicCardPlayback?,
    uiAlpha: State<Float>,
) {
    Box(Modifier.fillMaxWidth().height(TV_HOME_SPOTLIGHT_HEIGHT).testTag("cinematic-preview-stage")) {
        TvHomeFadeLayer(uiAlpha, Modifier.fillMaxSize()) {
            TvCinematicStageText(
                hero = hero,
                card = focusedCard,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = TvLayoutTokens.ContentStart,
                            top = TvLayoutTokens.SafeInsets.vertical + 26.dp,
                            bottom = 84.dp,
                        ).fillMaxWidth(0.5f),
            )
            if (focusedCard != null && playback != null && playback.canPlay(focusedCard)) {
                val labels = playback.labels
                TvPlayKeyHints(
                    playLabel = if (focusedCard.resumeFraction != null) labels.resume else labels.play,
                    detailsLabel = labels.details,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = TvLayoutTokens.ContentStart, bottom = 26.dp),
                )
            }
        }
    }
}

@Composable
private fun TvCinematicStageText(
    hero: TvCinematicHero?,
    card: TvCinematicCard?,
    modifier: Modifier,
) {
    val fadeMs = tvHomeHeroFadeMillis()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // The eyebrow names the screen, so it stays put while the text under it changes with the focused card.
        hero?.eyebrow?.takeIf { card != null || it != hero.title }?.let { eyebrow ->
            Text(
                eyebrow,
                color = TvPurple,
                fontSize = TvTextSize.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedContent(
            targetState = card,
            contentKey = { it?.id },
            transitionSpec = { tvHomeHeroFade(fadeMs) },
            label = "cinematic-stage-text",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                TvStageTitle(shown?.title ?: hero?.title, shown?.logoUrl)
                val overview = if (shown != null) shown.overview else hero?.overview
                overview?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = TvTextMuted,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                (shown?.metadata ?: shown?.subtitle)?.let {
                    Text(it, color = TvTextMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
