package dev.jellystack.players

import dev.jellystack.core.downloads.OfflineMedia
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

class IosOfflinePlaybackSourceResolver : OfflinePlaybackSourceResolver {
    private val fileManager = NSFileManager.defaultManager()

    override fun resolve(media: OfflineMedia): ResolvedPlaybackSource {
        require(fileManager.fileExistsAtPath(media.filePath)) { "Offline media file missing at ${media.filePath}" }
        val fileUrl = NSURL.fileURLWithPath(media.filePath)
        val absoluteUrl = fileUrl.absoluteString ?: "file://${media.filePath}"
        return ResolvedPlaybackSource(
            url = absoluteUrl,
            headers = emptyMap(),
            mode = PlaybackMode.LOCAL,
            mimeType = media.mimeType,
            subtitles = emptyList(),
            playSessionId = null,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
        )
    }
}
