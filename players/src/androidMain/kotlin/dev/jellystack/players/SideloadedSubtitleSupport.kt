package dev.jellystack.players

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

/**
 * Media3 only merges [SubtitleConfiguration]s into the playback source inside
 * `DefaultMediaSourceFactory`. The engine builds Progressive/HLS sources directly so it can
 * attach Jellyfin request headers, therefore the sideloaded subtitles are merged here.
 *
 * Each subtitle is a lazily loaded single-sample source, so it is only downloaded once its track
 * is selected, and a failing subtitle download ends that track instead of failing playback.
 */
@UnstableApi
internal fun MediaSource.mergedWithSideloadedSubtitles(
    subtitles: List<SubtitleConfiguration>,
    dataSourceFactory: DataSource.Factory,
): MediaSource {
    if (subtitles.isEmpty()) return this
    val subtitleSourceFactory =
        SingleSampleMediaSource
            .Factory(dataSourceFactory)
            .setTreatLoadErrorsAsEndOfStream(true)
    val sources =
        buildList {
            add(this@mergedWithSideloadedSubtitles)
            subtitles.forEach { subtitle ->
                add(subtitleSourceFactory.createMediaSource(subtitle, C.TIME_UNSET))
            }
        }
    return MergingMediaSource(*sources.toTypedArray())
}

internal fun ResolvedSubtitle.toSubtitleConfiguration(): SubtitleConfiguration =
    SubtitleConfiguration
        .Builder(Uri.parse(url))
        .setMimeType(mimeType)
        .setLanguage(language)
        .setLabel(label ?: language ?: trackId)
        .setSelectionFlags(if (isForced) C.SELECTION_FLAG_FORCED else 0)
        .setRoleFlags(if (isForced) C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE else C.ROLE_FLAG_SUBTITLE)
        .setId(trackId)
        .build()

/**
 * Sideloaded single-sample subtitles deliver raw `text/vtt` and `application/x-subrip` samples,
 * which the default text renderer refuses to decode unless legacy decoding is enabled.
 */
@UnstableApi
internal class JellystackRenderersFactory(
    context: Context,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val renderer = TextRenderer(output, outputLooper)
        renderer.experimentalSetLegacyDecodingEnabled(true)
        out.add(renderer)
    }
}

/**
 * [MergingMediaSource] prefixes every merged format id with the index of its source (`0:` for
 * the playback source, `1:`, `2:`... for the sideloaded subtitles).
 */
private val MERGED_FORMAT_ID_PREFIX = Regex("^(\\d+):(.*)$", RegexOption.DOT_MATCHES_ALL)

/** Returns the id the format had before [MergingMediaSource] prefixed it with its source index. */
internal fun unmergedFormatId(formatId: String?): String? =
    formatId?.let { id ->
        MERGED_FORMAT_ID_PREFIX.matchEntire(id)?.groupValues?.get(2)
            ?: id
    }

/** Index of the merged source a format came from, or null when the format id carries no merge prefix. */
internal fun mergedSourceIndex(formatId: String?): Int? =
    formatId?.let { id ->
        MERGED_FORMAT_ID_PREFIX
            .matchEntire(id)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

/** True when a Media3 format id (possibly merge-prefixed) refers to the given app track id. */
internal fun formatIdMatches(
    formatId: String?,
    trackId: String?,
): Boolean {
    if (formatId == null || trackId == null) return false
    return formatId.equals(trackId, ignoreCase = true) ||
        unmergedFormatId(formatId)?.equals(trackId, ignoreCase = true) == true
}
