package dev.jellystack.players

/**
 * Which recovery steps one playback session has already used. A fresh `play` starts with a clean state;
 * track, subtitle and quality switches keep it, so a title that only plays with the compatibility audio
 * rendition does not fall back to the failing stream on every switch.
 */
internal class PlaybackFallbackState {
    var directAttempted = false
    var hlsAudioAttempted = false
    var hlsForcedTranscodeAttempted = false
    var hlsContainerAttempted = false

    /** Set once the stereo AAC compatibility rendition was needed because the negotiated audio stayed silent. */
    var compatibilityAudioRequired = false
        private set

    fun onCompatibilityAudioPrepared() {
        compatibilityAudioRequired = true
    }

    fun reset() {
        directAttempted = false
        hlsAudioAttempted = false
        hlsForcedTranscodeAttempted = false
        hlsContainerAttempted = false
        compatibilityAudioRequired = false
    }

    /**
     * Next step of the fallback chain for [current] after a playback failure, or null when the chain is
     * exhausted. The step it returns counts as used.
     */
    fun nextFallback(
        current: PlaybackSession,
        originalError: Throwable?,
        audioOutputUnavailable: Boolean,
    ): PlaybackSourceOptions? {
        val audioOnlyFallback = audioOutputUnavailable || originalError.isAudioDecoderFailure()
        return when (current.stream.mode) {
            PlaybackMode.DIRECT -> nextDirectFallback(current, audioOnlyFallback)
            PlaybackMode.HLS -> nextHlsFallback(current, audioOnlyFallback)
            PlaybackMode.LOCAL -> null
        }
    }

    private fun nextDirectFallback(
        current: PlaybackSession,
        audioOnlyFallback: Boolean,
    ): PlaybackSourceOptions? {
        if (directAttempted) return null
        directAttempted = true
        hlsAudioAttempted = audioOnlyFallback
        hlsForcedTranscodeAttempted = !audioOnlyFallback
        return current.fallbackOptions(
            forceTranscoding = !audioOnlyFallback,
            forceAudioTranscoding = audioOnlyFallback,
        )
    }

    private fun nextHlsFallback(
        current: PlaybackSession,
        audioOnlyFallback: Boolean,
    ): PlaybackSourceOptions? =
        when {
            audioOnlyFallback && !hlsAudioAttempted -> {
                hlsAudioAttempted = true
                current.fallbackOptions(
                    forceAudioTranscoding = true,
                    preferFmp4Hls = current.source.segmentContainer.equals("mp4", true),
                )
            }
            !hlsForcedTranscodeAttempted -> {
                hlsForcedTranscodeAttempted = true
                current.fallbackOptions(forceTranscoding = true)
            }
            hlsContainerAttempted || current.source.segmentContainer.equals("mp4", true) -> null
            else -> {
                hlsContainerAttempted = true
                current.fallbackOptions(forceTranscoding = true, preferFmp4Hls = true)
            }
        }
}

private fun PlaybackSession.fallbackOptions(
    forceTranscoding: Boolean = false,
    forceAudioTranscoding: Boolean = false,
    preferFmp4Hls: Boolean = false,
): PlaybackSourceOptions =
    PlaybackSourceOptions(
        audioStreamIndex = audioTrack?.streamIndex,
        subtitleStreamIndex = subtitleTrack?.streamIndex ?: SUBTITLES_DISABLED_INDEX,
        playSessionId = source.playSessionId,
        forceTranscoding = forceTranscoding,
        forceAudioTranscoding = forceAudioTranscoding,
        preferFmp4Hls = preferFmp4Hls,
    )

private fun Throwable?.isAudioDecoderFailure(): Boolean {
    var current = this
    while (current != null) {
        val description = "${current::class.simpleName.orEmpty()} ${current.message.orEmpty()}".lowercase()
        if (
            "mediacodecaudiorenderer" in description ||
            "audio renderer" in description ||
            "audio decoder" in description
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
