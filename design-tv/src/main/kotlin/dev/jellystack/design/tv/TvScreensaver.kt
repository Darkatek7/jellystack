@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.delay

internal const val TV_SCREENSAVER_INTERVAL_MS = 20_000L
private const val TV_SCREENSAVER_FADE_MS = 2_500
private const val TV_SCREENSAVER_RETRY_MS = 5_000L
private const val TV_SCREENSAVER_IDLE_MOVE_MS = 60_000L

// The picture is drawn this much larger than the screen so that a per-picture shift never shows an edge.
private const val TV_SCREENSAVER_ZOOM = 1.06f
private const val TV_SCREENSAVER_SHIFT = 0.025f

/**
 * The system screensaver's content: [slides] cross-fading every 20 seconds with title and year small in a lower
 * corner. [slides] is null while they load and empty when there is nothing to show; both give a plain dark screen
 * with [appName]. Burn-in protection is a fixed shift of the picture and a change of caption corner per slide, so
 * nothing animates between the fades. Pictures are loaded before they fade in, so a slow or failing image never
 * shows a blank frame.
 */
@Composable
fun TvScreensaver(
    slides: List<TvScreensaverSlide>?,
    appName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    var frame by remember { mutableStateOf<TvScreensaverFrame?>(null) }
    LaunchedEffect(slides) {
        frame = null
        val rotation = TvScreensaverRotation(slides.orEmpty())
        var step = 0
        while (true) {
            val next = rotation.next() ?: break
            if (imageLoader.execute(tvScreensaverRequest(context, next)) is SuccessResult) {
                frame = TvScreensaverFrame(next, step++)
                delay(TV_SCREENSAVER_INTERVAL_MS)
            } else {
                // Offline or a broken image: try the next one without hammering the server.
                delay(TV_SCREENSAVER_RETRY_MS)
            }
        }
    }
    Box(modifier.fillMaxSize().background(TvBackground)) {
        Crossfade(
            targetState = frame,
            animationSpec = tween(TV_SCREENSAVER_FADE_MS),
            modifier = Modifier.fillMaxSize(),
            label = "tv-screensaver",
        ) { shown ->
            if (shown == null) {
                TvScreensaverIdle(appName)
            } else {
                TvScreensaverPicture(shown)
            }
        }
    }
}

private data class TvScreensaverFrame(
    val slide: TvScreensaverSlide,
    val step: Int,
)

@Composable
private fun TvScreensaverPicture(frame: TvScreensaverFrame) {
    val context = LocalPlatformContext.current
    val request = remember(frame.slide.imageUrl) { tvScreensaverRequest(context, frame.slide) }
    val shiftX = tvScreensaverShiftX(frame.step)
    val shiftY = tvScreensaverShiftY(frame.step)
    val captionAtStart = frame.step % 2 == 0
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = TV_SCREENSAVER_ZOOM
                    scaleY = TV_SCREENSAVER_ZOOM
                    translationX = shiftX * TV_SCREENSAVER_SHIFT * size.width
                    translationY = shiftY * TV_SCREENSAVER_SHIFT * size.height
                },
        )
        Box(Modifier.fillMaxSize().tvBakedScrim(TvScrim.SCREENSAVER_CAPTION))
        Column(
            Modifier
                .align(if (captionAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(
                    horizontal = TvLayoutTokens.SafeInsets.horizontal + 16.dp,
                    vertical = TvLayoutTokens.SafeInsets.vertical + 8.dp,
                ),
            horizontalAlignment = if (captionAtStart) Alignment.Start else Alignment.End,
        ) {
            Text(
                frame.slide.title,
                color = TvText,
                fontSize = TvTextSize.BodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            frame.slide.year?.let { year ->
                Text(year.toString(), color = TvTextMuted, fontSize = TvTextSize.BodySmall)
            }
        }
    }
}

/** The plain dark screen. The name changes place once a minute so that it never rests on one spot. */
@Composable
private fun TvScreensaverIdle(appName: String) {
    var position by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TV_SCREENSAVER_IDLE_MOVE_MS)
            position = (position + 1) % TV_SCREENSAVER_IDLE_ALIGNMENTS.size
        }
    }
    Box(Modifier.fillMaxSize()) {
        TvScreensaverIdleName(appName, TV_SCREENSAVER_IDLE_ALIGNMENTS[position])
    }
}

@Composable
private fun BoxScope.TvScreensaverIdleName(
    appName: String,
    alignment: Alignment,
) {
    Text(
        appName,
        color = TvTextMuted.copy(alpha = 0.45f),
        fontSize = TvTextSize.Body,
        modifier =
            Modifier
                .align(alignment)
                .padding(
                    horizontal = TvLayoutTokens.SafeInsets.horizontal + 16.dp,
                    vertical = TvLayoutTokens.SafeInsets.vertical + 8.dp,
                ),
    )
}

private val TV_SCREENSAVER_IDLE_ALIGNMENTS =
    listOf(Alignment.Center, Alignment.TopStart, Alignment.BottomEnd, Alignment.TopEnd, Alignment.BottomStart)

// The four corners in turn: the picture sits a little differently under the caption with every slide.
private fun tvScreensaverShiftX(step: Int): Float = if (step % 4 == 0 || step % 4 == 3) -1f else 1f

private fun tvScreensaverShiftY(step: Int): Float = if (step % 4 == 0 || step % 4 == 2) -1f else 1f

// Same size as the cinematic browse backdrops, so a picture loaded here is a memory-cache hit when it is drawn.
private fun tvScreensaverRequest(
    context: PlatformContext,
    slide: TvScreensaverSlide,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(slide.imageUrl)
        .size(width = 1920, height = 1080)
        .build()
