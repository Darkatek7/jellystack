@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * The title on a home or browse stage: the item's logo when it has one, otherwise its name.
 * The logo keeps a fixed slot so the text below does not move when it arrives, and the name fills that
 * slot while a slow logo loads or when it fails.
 */
@Composable
internal fun TvStageTitle(
    title: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (logoUrl == null) {
        title?.let { TvStageTitleText(it, maxLines = 2, modifier = modifier) }
        return
    }
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var showName by remember(logoUrl) { mutableStateOf(false) }
    LaunchedEffect(logoUrl) {
        // A cached logo arrives within a frame or two; only a slow one shows the name meanwhile.
        delay(TV_STAGE_LOGO_NAME_DELAY_MS)
        showName = true
    }
    val logoAlpha by animateFloatAsState(if (logoLoaded) 1f else 0f, label = "stage-logo")
    Box(modifier.height(TV_STAGE_LOGO_HEIGHT), contentAlignment = Alignment.CenterStart) {
        if (showName && title != null && logoAlpha < 1f) {
            // The name fades out as the logo fades in, so the slot is never empty.
            TvStageTitleText(title, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1f - logoAlpha })
        }
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            modifier =
                Modifier
                    .widthIn(max = 390.dp)
                    .heightIn(max = TV_STAGE_LOGO_HEIGHT)
                    .graphicsLayer { alpha = logoAlpha }
                    .testTag("tv-stage-logo"),
            contentScale = ContentScale.Fit,
            onSuccess = { logoLoaded = true },
            onError = { showName = true },
        )
    }
}

@Composable
private fun TvStageTitleText(
    title: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = TvText,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }.testTag("tv-stage-title"),
    )
}

private val TV_STAGE_LOGO_HEIGHT = 68.dp
private const val TV_STAGE_LOGO_NAME_DELAY_MS = 400L
