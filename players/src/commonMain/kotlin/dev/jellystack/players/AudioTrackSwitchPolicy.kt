package dev.jellystack.players

/**
 * Decides how an audio track change is carried out. A server-selected (HLS) rendition always needs a
 * new server stream for the chosen track. A directly played file can switch locally, unless the
 * device cannot decode the new track's codec: then the server has to transcode that track.
 */
internal fun shouldResolveAudioTrackOnServer(
    mode: PlaybackMode,
    isRemoteConnected: Boolean,
    canPlayTrackLocally: Boolean,
): Boolean =
    !isRemoteConnected &&
        when (mode) {
            PlaybackMode.HLS -> true
            PlaybackMode.DIRECT -> !canPlayTrackLocally
            PlaybackMode.LOCAL -> false
        }
