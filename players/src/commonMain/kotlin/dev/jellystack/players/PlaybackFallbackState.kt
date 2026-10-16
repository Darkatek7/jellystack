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
}
