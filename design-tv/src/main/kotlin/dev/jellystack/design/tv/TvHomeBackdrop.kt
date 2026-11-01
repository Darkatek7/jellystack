@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinItem
import java.util.Date

/**
 * The spotlight's artwork behind the whole home screen, under the rail and the rows. A playing trailer
 * replaces the artwork once it shows a frame; [image] draws a still backdrop URL, so screenshot fixtures can
 * supply their own.
 */
@Composable
internal fun TvHomeBackdrop(
    model: TvHomeHeroModel,
    trailer: TvHomeHeroTrailer,
    modifier: Modifier = Modifier,
    image: @Composable (String?) -> Unit = { url -> TvHomeBackdropImage(url) },
) {
    val item = model.stageItem
    val fadeMs = tvHomeHeroFadeMillis()
    Box(modifier.fillMaxSize()) {
        val trailerShown = trailer.state.showsTvHomeStagePreview(item.id, model.stagePresentationId)
        // The only trailer surface on the screen; it sits outside the crossfade so one engine never gets two.
        if (trailerShown) {
            trailer.video.surface(Modifier.fillMaxSize().testTag("tv-home-hero-preview-surface"))
        }
        TvTrailerArtworkCover(covering = !trailerShown || !trailer.video.firstFrameShown.value) {
            AnimatedContent(
                targetState = item,
                contentKey = { it.id },
                transitionSpec = { tvHomeHeroFade(fadeMs) },
                modifier = Modifier.fillMaxSize(),
                label = "tv-home-backdrop",
            ) { shown ->
                image(tvHeroBackdropUrl(model, shown))
            }
        }
        // The scrims only keep the home UI readable, so they fade with it while the trailer has the screen.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = trailer.homeUiAlpha.value }) {
            TvHomeBackdropScrims()
        }
    }
}

/**
 * Keeps the artwork over a starting trailer until the trailer shows its first frame, so the stage never turns
 * black while the trailer loads. The artwork returns at once when the trailer stops.
 */
@Composable
internal fun TvTrailerArtworkCover(
    covering: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = covering,
        modifier = Modifier.fillMaxSize(),
        enter = EnterTransition.None,
        exit = fadeOut(tween(tvHomeHeroFadeMillis())),
        label = "tv-trailer-artwork-cover",
    ) {
        content()
    }
}

/** Keeps text and rows readable over full-screen artwork; shared by every screen with a full-bleed backdrop. */
@Composable
internal fun TvHomeBackdropScrims() {
    Box(Modifier.fillMaxSize().tvBakedScrim(TvScrim.HOME_BACKDROP))
}

/** The placeholder stays underneath, so a backdrop that is missing or fails to load still leaves a backdrop. */
@Composable
internal fun TvHomeBackdropImage(backdropUrl: String?) {
    Box(Modifier.fillMaxSize().tvBakedScrim(TvScrim.BACKDROP_PLACEHOLDER))
    if (backdropUrl != null) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Wall clock in the home screen's top-right corner; align it with [Alignment.TopEnd]. */
@Composable
internal fun TvHomeClock(
    time: String,
    modifier: Modifier = Modifier,
) {
    Text(
        time,
        color = TvText,
        fontSize = TvTextSize.BodyLarge,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier.padding(
                top = TvLayoutTokens.SafeInsets.vertical + 14.dp,
                end = TvLayoutTokens.SafeInsets.horizontal + 16.dp,
            ),
    )
}

/**
 * The playing trailer's pill right under [TvHomeClock]; align it with [Alignment.TopEnd]. It does not fade with
 * the rest of the UI, so it stays in place while the trailer has the screen.
 */
@Composable
internal fun TvTrailerPillUnderClock(
    label: String,
    soundEnabled: Boolean,
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    TvTrailerPill(
        label = label,
        soundEnabled = soundEnabled,
        progress = progress,
        modifier =
            modifier.padding(
                top = TvLayoutTokens.SafeInsets.vertical + 54.dp,
                end = TvLayoutTokens.SafeInsets.horizontal + 16.dp,
            ),
    )
}

/** The current time in the device's 12/24-hour format, updated every minute. */
@Composable
internal fun rememberTvClockLabel(): String {
    val nowMs = rememberTvClockMs()
    val format = DateFormat.getTimeFormat(LocalContext.current)
    return format.format(Date(nowMs))
}

private fun tvHeroBackdropUrl(
    model: TvHomeHeroModel,
    item: JellyfinItem,
): String? = tvJellyfinBackdropUrl(model.imageBaseUrl, model.imageAccessToken, item)

/** The full-screen backdrop for [item]: the series backdrop for episodes, then the item's own artwork. */
internal fun tvJellyfinBackdropUrl(
    baseUrl: String?,
    token: String?,
    item: JellyfinItem,
): String? {
    val artwork = resolveTvHeroBackdrop(item)
    return if (artwork != null) {
        jellyfinImageUrl(baseUrl, token, artwork, TvArtworkSize.HERO)
    } else {
        // Items from some sources arrive without image tags; the server still serves an untagged backdrop.
        jellyfinImageUrl(
            baseUrl = baseUrl,
            token = token,
            itemId = item.seriesId?.takeIf(String::isNotBlank) ?: item.id,
            tag = null,
            type = "Backdrop",
            maxWidth = TvArtworkSize.HERO.maxWidth,
        )
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
