package dev.jellystack.players

import dev.jellystack.core.downloads.OfflineMedia

interface OfflinePlaybackSourceResolver {
    fun resolve(media: OfflineMedia): ResolvedPlaybackSource
}

object NoOfflinePlaybackSourceResolver : OfflinePlaybackSourceResolver {
    override fun resolve(media: OfflineMedia): ResolvedPlaybackSource =
        throw UnsupportedOperationException("Offline playback not supported on this platform.")
}
