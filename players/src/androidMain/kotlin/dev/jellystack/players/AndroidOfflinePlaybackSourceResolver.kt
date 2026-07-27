package dev.jellystack.players

import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.downloads.OfflineMediaKind
import dev.jellystack.core.downloads.OfflineMediaStore
import java.io.File

class AndroidOfflinePlaybackSourceResolver(
    private val mediaStore: OfflineMediaStore,
) : OfflinePlaybackSourceResolver {
    override fun resolve(media: OfflineMedia): ResolvedPlaybackSource {
        val file = File(media.filePath)
        require(file.exists()) { "Offline media file missing at ${media.filePath}" }
        val subtitlePrefix = "${media.mediaId}::sub::"
        val subtitles =
            mediaStore
                .list()
                .asSequence()
                .filter { stored ->
                    stored.kind == OfflineMediaKind.SUBTITLE && stored.mediaId.startsWith(subtitlePrefix)
                }.mapNotNull { stored ->
                    val subtitleFile = File(stored.filePath)
                    if (!subtitleFile.exists()) {
                        null
                    } else {
                        val trackId = stored.mediaId.removePrefix(subtitlePrefix).ifBlank { subtitleFile.nameWithoutExtension }
                        ResolvedSubtitle(
                            trackId = trackId,
                            url = subtitleFile.toURI().toString(),
                            mimeType = stored.mimeType ?: "text/vtt",
                            isForced = false,
                            language = stored.language,
                            label = subtitleLabelFor(stored),
                        )
                    }
                }.toList()
                .sortedBy { subtitle -> subtitle.trackId.toIntOrNull() ?: Int.MAX_VALUE }
        return ResolvedPlaybackSource(
            url = file.toURI().toString(),
            headers = emptyMap(),
            mode = PlaybackMode.LOCAL,
            mimeType = media.mimeType,
            subtitles = subtitles,
            playSessionId = null,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
        )
    }

    private fun subtitleLabelFor(media: OfflineMedia): String {
        val filename =
            media.relativePath
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
        val language = media.language?.takeIf { it.isNotBlank() }
        return listOfNotNull(filename, language).joinToString(" - ").ifBlank { "Subtitle" }
    }
}
