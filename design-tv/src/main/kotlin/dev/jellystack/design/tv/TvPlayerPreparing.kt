@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackState

/** What the player route shows while a start is under way; every field is optional. */
internal data class TvPlayerPreparingModel(
    val title: String?,
    val subtitle: String?,
    val artworkUrl: String?,
)

internal data class TvPlayerImageSource(
    val baseUrl: String?,
    val token: String?,
)

/** Prefers the item the user chose, then the controller's own metadata. */
internal fun tvPlayerPreparingModel(
    playback: PlaybackState,
    start: TvPlaybackStartState,
    images: TvPlayerImageSource,
): TvPlayerPreparingModel {
    val item = (start as? TvPlaybackStartState.Starting)?.item
    if (item != null) {
        val artwork = resolveTvJellyfinArtwork(item)
        return TvPlayerPreparingModel(
            title = item.seriesName?.takeIf(String::isNotBlank) ?: item.name,
            subtitle = item.episodeSubtitle(),
            artworkUrl = jellyfinImageUrl(images.baseUrl, images.token, artwork, TvArtworkSize.HERO),
        )
    }
    val metadata = (playback as? PlaybackState.Preparing)?.metadata
    return TvPlayerPreparingModel(
        title = metadata?.seriesName?.takeIf(String::isNotBlank) ?: metadata?.title?.takeIf(String::isNotBlank),
        subtitle = metadata.episodeSubtitle(),
        artworkUrl = metadata?.artworkUrl,
    )
}

private fun JellyfinItem.episodeSubtitle(): String? =
    if (seriesName.isNullOrBlank()) {
        null
    } else {
        val code =
            if (parentIndexNumber != null && indexNumber != null) {
                tvEpisodeCode(parentIndexNumber, indexNumber)
            } else {
                null
            }
        listOfNotNull(code, (episodeTitle ?: name).takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { null }
    }

private fun PlaybackMetadata?.episodeSubtitle(): String? {
    val metadata = this
    return if (metadata == null || metadata.seriesName.isNullOrBlank()) {
        null
    } else {
        val code =
            if (metadata.seasonNumber != null && metadata.episodeNumber != null) {
                tvEpisodeCode(metadata.seasonNumber, metadata.episodeNumber)
            } else {
                null
            }
        listOfNotNull(code, (metadata.episodeName ?: metadata.title)?.takeIf(String::isNotBlank))
            .joinToString(" · ")
            .ifBlank { null }
    }
}

/** Artwork and title behind a progress indicator, shown until the first frame can play. */
@Composable
internal fun TvPlayerPreparing(
    model: TvPlayerPreparingModel,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().tvStatusSemantics(label)) {
        if (model.artworkUrl != null) {
            AsyncImage(
                model = model.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            model.title?.let { title ->
                Text(
                    title,
                    color = TvText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            model.subtitle?.let { subtitle ->
                Text(subtitle, color = TvTextMuted, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = TvPurple)
            Text(label, color = TvTextMuted, fontSize = 20.sp)
        }
    }
}
