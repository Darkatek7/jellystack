package app.jellystack.tv

/** Platform-owned effects kept outside the Compose TV UI and made deterministic for tests. */
internal interface TvPlatformActions<E> {
    fun setKeepScreenOn(enabled: Boolean)

    fun markPlaybackStarted()

    fun handleMediaKey(event: E): Boolean

    fun stopTrailer()

    fun stopPlayback()

    fun releaseTrailer()

    fun releaseBridge()

    fun releasePlayback()
}

internal class TvPlatformActionCoordinator<E>(
    private val actions: TvPlatformActions<E>,
) {
    private var playbackWasActive = false

    fun onPlaybackActivityChanged(active: Boolean) {
        actions.setKeepScreenOn(active)
        if (active && !playbackWasActive) actions.markPlaybackStarted()
        playbackWasActive = active
    }

    fun dispatchMediaKey(
        event: E,
        playbackVisible: Boolean,
    ): Boolean = playbackVisible && actions.handleMediaKey(event)

    fun onStop(isChangingConfigurations: Boolean) {
        actions.stopTrailer()
        if (!isChangingConfigurations) actions.stopPlayback()
    }

    fun onDestroy() {
        actions.releaseTrailer()
        actions.releaseBridge()
        actions.releasePlayback()
    }
}
