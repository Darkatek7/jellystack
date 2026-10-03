@file:Suppress("FunctionNaming", "LongParameterList")

package dev.jellystack.design.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.players.AndroidPlayerEngine

/** Browse rows start below the preview stage so cards never cover the backdrop, trailer, or stage actions. */
internal val TV_CINEMATIC_ROWS_TOP: Dp = TvLayoutTokens.SafeInsets.vertical + TV_HOME_HERO_HEIGHT_DP.dp + 12.dp
internal val TV_CINEMATIC_STAGE_HEIGHT: Dp = TV_CINEMATIC_ROWS_TOP

@Composable
internal fun TvCinematicPreviewStage(
    backdrop: TvCinematicBackdrop,
    hero: TvCinematicHero?,
    focusedCard: TvCinematicCard?,
    actions: TvSelectedItemActions?,
    labels: TvSelectedItemActionLabels,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = 0.dp,
    onActionDown: (() -> Boolean)? = null,
    previewing: Boolean = false,
    previewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TV_CINEMATIC_STAGE_HEIGHT)
                .background(TvBackground)
                .testTag("cinematic-preview-stage"),
    ) {
        TvCinematicStageBackdrop(
            backdrop = backdrop,
            reducedMotion = reducedMotion,
            previewing = previewing,
            previewEngine = previewEngine,
        )
        if (previewing && previewEngine != null) {
            TvTrailerPreviewChrome(
                previewSoundEnabled = previewSoundEnabled,
                previewProgress = previewProgress?.value ?: 0f,
                modifier =
                    Modifier.fillMaxSize().padding(
                        start = TvLayoutTokens.ContentStart,
                        end = TvLayoutTokens.SafeInsets.horizontal,
                    ),
                badgeEndPadding = 0.dp,
            )
        }
        TvCinematicStageMetadata(
            hero = hero,
            focusedCard = focusedCard,
            actions = actions,
            labels = labels,
            onActionDown = onActionDown,
            previewing = previewing && previewEngine != null,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TvLayoutTokens.ContentStart,
                        end = TvLayoutTokens.SafeInsets.horizontal,
                        top = TvLayoutTokens.SafeInsets.vertical + contentTopInset,
                    ),
        )
    }
}

@Composable
private fun TvCinematicStageBackdrop(
    backdrop: TvCinematicBackdrop,
    reducedMotion: Boolean,
    previewing: Boolean,
    previewEngine: AndroidPlayerEngine?,
) {
    if (previewing && previewEngine != null) {
        TvTrailerPreviewSurface(
            previewEngine = previewEngine,
            modifier = Modifier.fillMaxSize().testTag("cinematic-preview-player-surface"),
        )
    } else {
        val transitionMillis = if (reducedMotion) 0 else backdrop.transitionMillis
        AnimatedContent(
            targetState = backdrop.url,
            modifier = Modifier.fillMaxSize().testTag("cinematic-backdrop"),
            transitionSpec = {
                fadeIn(tween(transitionMillis)) togetherWith fadeOut(tween(transitionMillis))
            },
            label = "cinematic-stage-backdrop-crossfade",
        ) { url ->
            if (url == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF171824), Color(0xFF2B2342), Color(0xFF11121A)),
                            ),
                        ),
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(TvBackground, TvBackground.copy(alpha = 0.9f), TvBackground.copy(alpha = 0.18f)),
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TvBackground.copy(alpha = 0.12f), Color.Transparent, TvBackground),
                ),
            ),
    )
}

@Composable
private fun TvCinematicStageMetadata(
    hero: TvCinematicHero?,
    focusedCard: TvCinematicCard?,
    actions: TvSelectedItemActions?,
    labels: TvSelectedItemActionLabels,
    onActionDown: (() -> Boolean)?,
    previewing: Boolean,
    modifier: Modifier,
) {
    val title = focusedCard?.title ?: hero?.title
    val subtitle = focusedCard?.subtitle
    val overview = focusedCard?.overview ?: hero?.overview
    if (title == null && subtitle == null && overview == null) return
    Column(
        modifier = modifier.fillMaxWidth(0.86f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hero?.eyebrow?.takeIf { focusedCard == null }?.let {
            Text(it, color = TvPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        title?.let {
            Text(
                text = it,
                color = TvText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag("cinematic-preview-title"),
            )
        }
        subtitle?.let {
            Text(
                text = it,
                color = TvTextMuted,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        overview?.let {
            Text(
                text = it,
                color = TvTextMuted,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (focusedCard != null && actions != null) {
            TvTrailerAwareActions(previewing = previewing) {
                TvSelectedItemActionStrip(focusedCard, labels, actions, onDown = onActionDown)
            }
        }
    }
}
