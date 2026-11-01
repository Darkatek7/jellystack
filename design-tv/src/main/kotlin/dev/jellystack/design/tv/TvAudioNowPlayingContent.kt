@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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

@Composable
internal fun TvAudioNowPlayingContent(
    nowPlaying: TvAudioNowPlaying,
    hasNext: Boolean,
    transport: TvAudioTransport,
    strings: TvStrings,
    playPauseFocus: FocusRequester,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = TV_AUDIO_SIDE_PADDING, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvAudioCover(nowPlaying.artworkUrl)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            TvAudioTrackText(nowPlaying, strings.messages.music.nowPlaying)
            Spacer(Modifier.height(28.dp))
            TvAudioProgress(nowPlaying)
            Spacer(Modifier.height(22.dp))
            TvAudioPlayerControls(nowPlaying.isPaused, hasNext, transport, strings, playPauseFocus)
        }
    }
}

@Composable
private fun TvAudioCover(url: String?) {
    Box(
        Modifier.size(TV_AUDIO_COVER_SIZE).clip(TvShapes.Card).background(TvSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(96.dp))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag("tv-audio-cover"),
            )
        }
    }
}

@Composable
private fun TvAudioTrackText(
    nowPlaying: TvAudioNowPlaying,
    label: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TvTextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = nowPlaying.title,
            color = TvText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("tv-audio-title"),
        )
        nowPlaying.artist?.let { TvAudioDetailLine(it, TvText, 22) }
        nowPlaying.album?.let { TvAudioDetailLine(it, TvTextMuted, 18) }
    }
}

@Composable
private fun TvAudioDetailLine(
    text: String,
    color: Color,
    fontSizeSp: Int,
) {
    Text(text, color = color, fontSize = fontSizeSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** How far into the track it is. Not a control: music is skipped between tracks, not scrubbed. */
@Composable
private fun TvAudioProgress(nowPlaying: TvAudioNowPlaying) {
    Column(Modifier.fillMaxWidth().testTag("tv-audio-progress"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(TvShapes.Pill)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxWidth(nowPlaying.progress).fillMaxHeight().background(TvPurple))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nowPlaying.positionLabel, color = TvTextMuted, fontSize = 16.sp)
            nowPlaying.durationLabel?.let { Text(it, color = TvTextMuted, fontSize = 16.sp) }
        }
    }
}

private val TV_AUDIO_COVER_SIZE = 300.dp
private val TV_AUDIO_SIDE_PADDING = 72.dp
