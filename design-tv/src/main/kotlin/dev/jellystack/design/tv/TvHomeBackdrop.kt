@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * replaces the artwork; [image] draws a still backdrop URL, so screenshot fixtures can supply their own.
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
        // The only trailer surface on the screen; it sits outside the crossfade so one engine never gets two.
        if (trailer.state.showsTvHomeStagePreview(item.id, model.stagePresentationId)) {
            trailer.surface(Modifier.fillMaxSize().testTag("tv-home-hero-preview-surface"))
        } else {
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

/** Keeps text and rows readable over full-screen artwork; shared by every screen with a full-bleed backdrop. */
@Composable
internal fun TvHomeBackdropScrims() {
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = TV_HOME_BACKDROP_SIDE_SCRIM)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = TV_HOME_BACKDROP_BOTTOM_SCRIM)))
    // Keeps the clock and the page dots readable when the artwork is bright in that corner.
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(TvBackground.copy(alpha = 0.62f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.minDimension * 0.75f,
                ),
            )
        },
    )
}

// Dark behind the hero text and the open rail, clear on the right where the artwork shows.
private val TV_HOME_BACKDROP_SIDE_SCRIM =
    arrayOf(
        0f to TvBackground.copy(alpha = 0.96f),
        0.3f to TvBackground.copy(alpha = 0.84f),
        0.58f to TvBackground.copy(alpha = 0.38f),
        0.82f to Color.Transparent,
    )

// A light veil under the clock, then dark from the rows down so row titles stay readable on bright artwork.
private val TV_HOME_BACKDROP_BOTTOM_SCRIM =
    arrayOf(
        0f to TvBackground.copy(alpha = 0.34f),
        0.22f to Color.Transparent,
        0.5f to TvBackground.copy(alpha = 0.3f),
        0.64f to TvBackground.copy(alpha = 0.88f),
        1f to TvBackground,
    )

/** The placeholder stays underneath, so a backdrop that is missing or fails to load still leaves a backdrop. */
@Composable
internal fun TvHomeBackdropImage(backdropUrl: String?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF171824), Color(0xFF2B2342), Color(0xFF11121A)))),
    )
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
