package dev.jellystack.players

/**
 * The session after the engine's play-when-ready flag became [playWhenReady], or null when nothing changes.
 *
 * Controller-initiated play/pause calls update the session before they reach the engine, so their echo from
 * the engine is a no-op here; only changes the controller did not ask for (audio focus loss and regain, the
 * output becoming noisy) alter the session. A finished item stays finished: its "paused" is not a user pause.
 */
internal fun PlaybackSession.withEnginePlayWhenReady(playWhenReady: Boolean): PlaybackSession? {
    if (phase == PlaybackPhase.Ended) return null
    val paused = !playWhenReady
    return if (isPaused == paused) null else copy(isPaused = paused)
}
